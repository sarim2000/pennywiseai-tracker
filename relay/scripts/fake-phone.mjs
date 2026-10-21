// Stands in for the PennyWise app during relay testing: registers once, prints a
// pairing code, holds the AI-session WebSocket and answers tools with sample data.
// Usage: node scripts/fake-phone.mjs [relay-url]
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { Resolver } from "node:dns";
import http from "node:http";
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
		const req = (url.startsWith("https") ? https : http).request(url, { method: "POST", headers, lookup }, (res) => {
			let body = "";
			res.on("data", (c) => (body += c));
			res.on("end", () => (res.statusCode < 300 ? resolve(JSON.parse(body)) : reject(new Error(`${url} → ${res.statusCode}`))));
		});
		req.on("error", reject);
		req.end();
	});
}

const RELAY = process.argv[2] ?? "https://ai.pennywise.zynth.dev";
const STATE = new URL(RELAY.includes("localhost") ? "../.fake-phone-local.json" : "../.fake-phone.json", import.meta.url);

// Six months of made-up sample data, deterministic so screenshots are repeatable.
const MERCHANTS = [
	["Swiggy", "Food & Dining", 180, 650], ["Zomato", "Food & Dining", 150, 700], ["Blinkit", "Groceries", 300, 1400],
	["BigBasket", "Groceries", 600, 2600], ["Uber", "Transportation", 120, 480], ["Rapido", "Transportation", 40, 160],
	["Amazon", "Shopping", 400, 4200], ["Myntra", "Shopping", 700, 3200], ["Netflix", "Entertainment", 649, 649],
	["BookMyShow", "Entertainment", 250, 900], ["Airtel", "Bills & Utilities", 599, 599], ["BESCOM", "Bills & Utilities", 900, 2100],
	["Apollo Pharmacy", "Healthcare", 150, 1200], ["Cult.fit", "Personal Care", 1200, 1200],
];
const ACCOUNTS = ["HDFC ••4821", "ICICI ••1093", "SBI ••7720"];
const SAMPLE = (() => {
	let seed = 42;
	const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
	const out = [];
	const today = new Date();
	for (let back = 0; back < 182; back++) {
		const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - back);
		const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
		if (d.getDate() === 1) out.push({ date: `${iso}T09:30:00`, merchant: "Salary", category: "Salary", type: "INCOME", amount: "85000.00", currency: "INR", account: "HDFC ••4821" });
		const n = d.getDay() === 0 || d.getDay() === 6 ? 3 : 2;
		for (let i = 0; i < n; i++) {
			if (rnd() < 0.35) continue;
			const [merchant, category, lo, hi] = MERCHANTS[Math.floor(rnd() * MERCHANTS.length)];
			const amount = (lo + Math.round(rnd() * (hi - lo))).toFixed(2);
			const account = ACCOUNTS[Math.floor(rnd() * ACCOUNTS.length)];
			out.push({ date: `${iso}T${String(9 + Math.floor(rnd() * 12)).padStart(2, "0")}:${String(Math.floor(rnd() * 60)).padStart(2, "0")}:00`, merchant, category, type: account === "ICICI ••1093" ? "CREDIT" : "EXPENSE", amount, currency: "INR", account });
		}
		if (back % 23 === 5) out.push({ date: `${iso}T14:00:00`, merchant: "Amazon US", category: "Shopping", type: "CREDIT", amount: (12 + Math.round(rnd() * 60)).toFixed(2), currency: "USD", account: "ICICI ••1093" });
	}
	return out;
})();

const inRange = (t, a) => (!a.from || t.date.slice(0, 10) >= a.from) && (!a.to || t.date.slice(0, 10) <= a.to);
const money = (t) => ({ amount: t.amount, currency: t.currency });
const totals = (list) => {
	const by = {};
	for (const t of list) by[t.currency] = (by[t.currency] ?? 0) + Number(t.amount);
	return Object.entries(by).map(([currency, v]) => ({ amount: v.toFixed(2), currency }));
};
const tools = {
	search_transactions: (a) => {
		const q = (a.query ?? "").toLowerCase();
		const base = SAMPLE.filter((t) => inRange(t, a) && (!a.type || t.type === a.type) && (!q || t.merchant.toLowerCase().includes(q)));
		const facets = (f) => [...new Set(base.map(f).filter(Boolean))].sort();
		let list = base.filter((t) => (!a.category || t.category === a.category) && (!a.account || t.account === a.account));
		const sorts = { date_asc: (x, y) => x.date.localeCompare(y.date), amount_desc: (x, y) => x.currency.localeCompare(y.currency) || y.amount - x.amount, amount_asc: (x, y) => x.currency.localeCompare(y.currency) || x.amount - y.amount };
		list = list.sort(sorts[a.sort] ?? ((x, y) => y.date.localeCompare(x.date)));
		const offset = a.offset ?? 0;
		const shown = list.slice(offset, offset + Math.min(a.limit ?? 50, 200));
		return { total_matches: list.length, offset, shown: shown.length, categories: facets((t) => t.category), accounts: facets((t) => t.account), transactions: shown.map((t) => ({ date: t.date, merchant: t.merchant, category: t.category, type: t.type, amount: money(t), account: t.account })) };
	},
	spending_summary: (a) => {
		const types = a.type === "INCOME" ? ["INCOME"] : ["EXPENSE", "CREDIT"];
		const list = SAMPLE.filter((t) => inRange(t, a) && types.includes(t.type) && (!a.account || t.account === a.account));
		const key = {
			merchant: (t) => [t.merchant], month: (t) => [t.date.slice(0, 7)], day: (t) => [t.date.slice(0, 10)],
			account: (t) => [t.account], month_category: (t) => [t.date.slice(0, 7), t.category],
		}[a.group_by] ?? ((t) => [t.category]);
		const groups = {};
		for (const t of list) (groups[JSON.stringify(key(t))] ??= []).push(t);
		return {
			type: a.type === "INCOME" ? "INCOME" : "EXPENSE", grand_total: totals(list),
			groups: Object.entries(groups).sort((x, y) => y[1].length - x[1].length).map(([k, ts]) => {
				const [group, sub] = JSON.parse(k);
				return { group, ...(sub && { sub }), count: ts.length, totals: totals(ts) };
			}),
		};
	},
	list_accounts: () => ({ accounts: [
		{ account: "HDFC ••4821", kind: "savings", balance: { amount: "154210.40", currency: "INR" }, as_of: new Date().toISOString().slice(0, 19) },
		{ account: "SBI ••7720", kind: "savings", balance: { amount: "38990.00", currency: "INR" }, as_of: new Date().toISOString().slice(0, 19) },
		{ account: "ICICI ••1093", kind: "credit card", outstanding: { amount: "23914.00", currency: "INR" }, credit_limit: { amount: "150000.00", currency: "INR" }, as_of: new Date().toISOString().slice(0, 19) },
	] }),
	list_subscriptions: () => {
		const next = (day) => { const d = new Date(); d.setMonth(d.getDate() > day ? d.getMonth() + 1 : d.getMonth(), day); return d.toISOString().slice(0, 10); };
		return { subscriptions: [
			{ merchant: "Netflix", amount: { amount: "649.00", currency: "INR" }, billing_cycle: "Monthly", direction: "EXPENSE", next_payment: next(9), category: "Entertainment" },
			{ merchant: "Airtel", amount: { amount: "599.00", currency: "INR" }, billing_cycle: "Monthly", direction: "EXPENSE", next_payment: next(14), category: "Bills & Utilities" },
			{ merchant: "Cult.fit", amount: { amount: "1200.00", currency: "INR" }, billing_cycle: "Monthly", direction: "EXPENSE", next_payment: next(3), category: "Personal Care" },
			{ merchant: "iCloud+", amount: { amount: "2.99", currency: "USD" }, billing_cycle: "Monthly", direction: "EXPENSE", next_payment: next(21), category: "Bills & Utilities" },
		] };
	},
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
