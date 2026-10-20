// Stands in for the PennyWise app during relay testing: registers once, prints a
// pairing code, holds the AI-session WebSocket and answers tools with sample data.
// Usage: node scripts/fake-phone.mjs [relay-url]
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { Resolver } from "node:dns";
import https from "node:https";
import WebSocket from "ws";

// Optional: FAKE_PHONE_DNS=8.8.8.8 bypasses a stale local DNS cache right after a new deploy.
const resolver = process.env.FAKE_PHONE_DNS ? Object.assign(new Resolver(), {}) : null;
resolver?.setServers([process.env.FAKE_PHONE_DNS]);
const lookup = resolver
	? (host, opts, cb) => resolver.resolve4(host, (err, addrs) => (opts.all ? cb(err, addrs?.map((address) => ({ address, family: 4 }))) : cb(err, addrs?.[0], 4)))
	: undefined;

function post(url, headers = {}) {
	return new Promise((resolve, reject) => {
		const req = https.request(url, { method: "POST", headers, lookup }, (res) => {
			let body = "";
			res.on("data", (c) => (body += c));
			res.on("end", () => (res.statusCode < 300 ? resolve(JSON.parse(body)) : reject(new Error(`${url} → ${res.statusCode}`))));
		});
		req.on("error", reject);
		req.end();
	});
}

const RELAY = process.argv[2] ?? "https://ai.pennywise.zynth.dev";
const STATE = new URL("../.fake-phone.json", import.meta.url);

const SAMPLE = [
	{ date: "2026-10-02", merchant: "Swiggy", category: "Food & Dining", type: "EXPENSE", amount: "412.00", currency: "INR", account: "HDFC ••4821" },
	{ date: "2026-10-05", merchant: "Zomato", category: "Food & Dining", type: "EXPENSE", amount: "289.50", currency: "INR", account: "HDFC ••4821" },
	{ date: "2026-10-06", merchant: "Uber", category: "Transportation", type: "EXPENSE", amount: "236.00", currency: "INR", account: "ICICI ••1093" },
	{ date: "2026-10-09", merchant: "Netflix", category: "Entertainment", type: "EXPENSE", amount: "649.00", currency: "INR", account: "ICICI ••1093" },
	{ date: "2026-10-12", merchant: "Amazon", category: "Shopping", type: "EXPENSE", amount: "18.99", currency: "USD", account: "ICICI ••1093" },
	{ date: "2026-10-01", merchant: "Salary", category: "Salary", type: "INCOME", amount: "85000.00", currency: "INR", account: "HDFC ••4821" },
];

const inRange = (t, a) => (!a.from || t.date >= a.from) && (!a.to || t.date <= a.to);
const tools = {
	search_transactions: (a) => {
		const q = (a.query ?? "").toLowerCase();
		return SAMPLE.filter((t) => inRange(t, a) && (!a.type || t.type === a.type) && (!a.category || t.category === a.category) && (!q || t.merchant.toLowerCase().includes(q))).slice(0, a.limit ?? 50);
	},
	spending_summary: (a) => {
		const key = { category: (t) => t.category, merchant: (t) => t.merchant, month: (t) => t.date.slice(0, 7) }[a.group_by ?? "category"];
		const totals = {};
		for (const t of SAMPLE.filter((t) => inRange(t, a) && t.type === (a.type ?? "EXPENSE"))) {
			const g = (totals[key(t)] ??= {});
			g[t.currency] = ((Number(g[t.currency] ?? 0) * 100 + Number(t.amount) * 100) / 100).toFixed(2); // per currency, never mixed
		}
		return Object.entries(totals).map(([group, byCurrency]) => ({ group, totals: Object.entries(byCurrency).map(([currency, amount]) => ({ amount, currency })) }));
	},
	list_accounts: () => [
		{ account: "HDFC ••4821", balance: "54210.40", currency: "INR", asOf: "2026-10-12" },
		{ account: "ICICI ••1093 (credit card)", outstanding: "3914.00", currency: "INR", asOf: "2026-10-12" },
	],
	list_subscriptions: () => [{ merchant: "Netflix", amount: "649.00", currency: "INR", every: "month", next: "2026-11-09" }],
};

async function main() {
	let state = existsSync(STATE) ? JSON.parse(readFileSync(STATE, "utf8")) : null;
	if (!state) {
		state = await post(`${RELAY}/device`);
		writeFileSync(STATE, JSON.stringify(state));
	}
	const auth = { Authorization: `Device ${state.deviceId}.${state.secret}` };
	const pair = await post(`${RELAY}/device/pair`, auth);
	console.log(`\nConnector URL: ${RELAY}/mcp\nPairing code:  ${pair.code}  (valid ${pair.expiresIn / 60} min)\n`);

	const connect = () => {
		const ws = new WebSocket(`${RELAY.replace(/^http/, "ws")}/device/connect`, { headers: auth, lookup });
		ws.on("open", () => console.log("AI session on (fake phone connected)"));
		ws.on("message", (data) => {
			const s = data.toString();
			if (s === "pong") return;
			const msg = JSON.parse(s);
			const fn = tools[msg.name];
			const text = fn ? JSON.stringify(fn(msg.arguments ?? {})) : `Unknown tool ${msg.name}`;
			console.log(`→ ${msg.name} ${JSON.stringify(msg.arguments)}`);
			ws.send(JSON.stringify({ type: "result", id: msg.id, result: { content: [{ type: "text", text }], isError: !fn } }));
		});
		const ping = setInterval(() => ws.readyState === 1 && ws.send("ping"), 25_000);
		ws.on("close", () => { clearInterval(ping); console.log("line closed, reconnecting"); setTimeout(connect, 2000); });
		ws.on("error", (e) => console.log("line error:", e.message));
	};
	connect();
}
main();
