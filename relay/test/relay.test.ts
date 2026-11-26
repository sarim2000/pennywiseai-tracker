import { runInDurableObject } from "cloudflare:test";
import { env, exports } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import { hideNoteConditions, refuseDryRun, refuseHiddenConditions, requireAppliedToPast, requireFilterEcho } from "../src/tools";

const BASE = "https://relay.test";
const REDIRECT = "https://client.test/callback";
const worker = () => (exports as unknown as { default: { fetch: (r: Request) => Promise<Response> } }).default;
const call = (path: string, init: RequestInit = {}) => worker().fetch(new Request(BASE + path, { redirect: "manual", ...init }));

async function registerDevice() {
	// A fresh IP per phone keeps the per-IP registration limit out of the way.
	const res = await call("/device", { method: "POST", headers: { "CF-Connecting-IP": randomIp() } });
	expect(res.status).toBe(201);
	const { deviceId, secret } = (await res.json()) as { deviceId: string; secret: string };
	return { deviceId, auth: { Authorization: `Device ${deviceId}.${secret}` } };
}

/** A fake phone: answers every tool call with the tool name it was asked for. */
async function connectPhone(auth: Record<string, string>) {
	const res = await call("/device/connect", { headers: { ...auth, Upgrade: "websocket" } });
	expect(res.status).toBe(101);
	const ws = res.webSocket!;
	ws.accept();
	const calls: { name: string; arguments: unknown }[] = [];
	ws.addEventListener("message", (e) => {
		const msg = JSON.parse(e.data as string);
		calls.push(msg);
		ws.send(JSON.stringify({ type: "result", id: msg.id, result: { content: [{ type: "text", text: `answered ${msg.name}` }] } }));
	});
	return { ws, calls };
}

const b64url = (bytes: ArrayBuffer | Uint8Array) =>
	btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

/** Runs the full OAuth sign-in an AI app does, typing `code` on our page. Returns the access token. */
async function registerClient(clientName: string) {
	const reg = await call("/oauth/register", {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ redirect_uris: [REDIRECT], token_endpoint_auth_method: "none", client_name: clientName }),
	});
	expect(reg.status).toBe(201);
	return ((await reg.json()) as { client_id: string }).client_id;
}

// Each request from its own address, so the per-IP rate limits don't trip across tests.
const randomIp = () => `10.0.${Math.floor(Math.random() * 250)}.${Math.floor(Math.random() * 250)}`;

async function signIn(code: string, clientName = "Test AI", existingClientId?: string) {
	const client_id = existingClientId ?? (await registerClient(clientName));

	const verifier = b64url(crypto.getRandomValues(new Uint8Array(32)));
	const challenge = b64url(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
	const q = new URLSearchParams({
		response_type: "code", client_id, redirect_uri: REDIRECT, state: "s1", scope: "read",
		code_challenge: challenge, code_challenge_method: "S256", resource: `${BASE}/mcp`,
	});
	const page = await call(`/authorize?${q}`);
	expect(page.status).toBe(200);
	const html = await page.text();
	expect(html).toContain(clientName);
	const handle = html.match(/name="handle" value="([^"]+)"/)![1];
	const cookie = page.headers.getSetCookie().map((c) => c.split(";")[0]).join("; ");

	const submit = await call("/authorize", {
		method: "POST",
		headers: { Cookie: cookie, Origin: BASE, "Content-Type": "application/x-www-form-urlencoded", "CF-Connecting-IP": randomIp() },
		body: new URLSearchParams({ handle, code, decision: "approve" }),
	});
	if (submit.status !== 302) return { status: submit.status, token: undefined };
	const back = new URL(submit.headers.get("Location")!);
	expect(back.origin + back.pathname).toBe(REDIRECT);
	expect(back.searchParams.get("state")).toBe("s1");

	const tok = await call("/oauth/token", {
		method: "POST",
		headers: { "Content-Type": "application/x-www-form-urlencoded" },
		body: new URLSearchParams({
			grant_type: "authorization_code", code: back.searchParams.get("code")!, redirect_uri: REDIRECT,
			client_id, code_verifier: verifier, resource: `${BASE}/mcp`,
		}),
	});
	expect(tok.status).toBe(200);
	const { access_token } = (await tok.json()) as { access_token: string };
	return { status: 302, token: access_token };
}

const mcp = (token: string, body: unknown) =>
	call("/mcp", {
		method: "POST",
		headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json", Accept: "application/json, text/event-stream" },
		body: JSON.stringify(body),
	});

const pair = async (auth: Record<string, string>) =>
	((await (await call("/device/pair", { method: "POST", headers: auth })).json()) as { code: string }).code;

describe("AI relay", () => {
	it("passes a tool call from an AI app to the phone and back", async () => {
		const { auth } = await registerDevice();
		const { token } = await signIn(await pair(auth));

		const init = (await (await mcp(token!, { jsonrpc: "2.0", id: 1, method: "initialize", params: { protocolVersion: "2025-06-18" } })).json()) as any;
		expect(init.result.protocolVersion).toBe("2025-06-18");
		// The connector's picture: named in serverInfo and served from this host.
		const icon = init.result.serverInfo.icons[0].src as string;
		expect(new URL(icon).pathname).toBe("/icon.png");
		for (const path of ["/icon.png", "/favicon.ico"]) {
			const res = await call(path);
			expect(res.status).toBe(200);
			expect(res.headers.get("Content-Type")).toMatch(/^image\//);
		}
		const list = (await (await mcp(token!, { jsonrpc: "2.0", id: 2, method: "tools/list" })).json()) as any;
		expect(list.result.tools.map((t: { name: string }) => t.name)).toContain("spending_summary");

		// No AI session yet: the AI is told how to connect.
		const offline = (await (await mcp(token!, { jsonrpc: "2.0", id: 3, method: "tools/call", params: { name: "list_accounts" } })).json()) as any;
		expect(offline.result.isError).toBe(true);
		expect(offline.result.content[0].text).toContain("start an AI session");

		const phone = await connectPhone(auth);
		const res = (await (await mcp(token!, { jsonrpc: "2.0", id: 4, method: "tools/call", params: { name: "spending_summary", arguments: { from: "2026-09-01", to: "2026-09-30" } } })).json()) as any;
		expect(res.result.content[0].text).toBe("answered spending_summary");
		expect(phone.calls[0]).toMatchObject({ type: "call", name: "spending_summary", arguments: { from: "2026-09-01", to: "2026-09-30" } });
		phone.ws.close();
	});

	it("needs a valid token, a known tool, and accepts notifications", async () => {
		expect((await mcp("not-a-token", { jsonrpc: "2.0", id: 1, method: "tools/list" })).status).toBe(401);
		const { auth } = await registerDevice();
		const { token } = await signIn(await pair(auth));
		const bad = (await (await mcp(token!, { jsonrpc: "2.0", id: 1, method: "tools/call", params: { name: "delete_everything" } })).json()) as any;
		expect(bad.error.code).toBe(-32602);
		expect((await mcp(token!, { jsonrpc: "2.0", method: "notifications/initialized" })).status).toBe(202);
	});

	it("rejects a wrong pairing code and a used one", async () => {
		const { auth } = await registerDevice();
		const code = await pair(auth);
		const wrong = code === "000000" ? "111111" : "000000";
		expect((await signIn(wrong)).status).toBe(400);
		expect((await signIn(code)).status).toBe(302);
		expect((await signIn(code)).status).toBe(400); // single use
	});

	it("lets only one of two simultaneous sign-ins use a code", async () => {
		const { auth } = await registerDevice();
		const code = await pair(auth);
		const statuses = (await Promise.all([signIn(code), signIn(code)])).map((r) => r.status).sort();
		expect(statuses).toEqual([302, 400]);
	});

	it("lists AI connections and revoking one cuts its token off", async () => {
		const { auth } = await registerDevice();
		const { token } = await signIn(await pair(auth), "Claude");
		const { connections } = (await (await call("/device/connections", { headers: auth })).json()) as any;
		expect(connections).toHaveLength(1);
		expect(connections[0].clientName).toBe("Claude");
		expect((await call(`/device/connections/${connections[0].id}`, { method: "DELETE", headers: auth })).status).toBe(204);
		expect((await mcp(token!, { jsonrpc: "2.0", id: 1, method: "tools/list" })).status).toBe(401);
	});

	it("asks the AI app to sign in again once its phone has been silent for 30 days", async () => {
		const { deviceId, auth } = await registerDevice();
		const { token } = await signIn(await pair(auth), "Claude");
		expect((await mcp(token!, { jsonrpc: "2.0", id: 1, method: "tools/list" })).status).toBe(200); // offline but recent: fine

		await runInDurableObject(env.PHONE.getByName(deviceId), (_phone, state) => {
			state.storage.sql.exec("UPDATE device SET last_seen = ?", Date.now() - 31 * 24 * 60 * 60 * 1000);
		});
		const res = await mcp(token!, { jsonrpc: "2.0", id: 2, method: "tools/list" });
		expect(res.status).toBe(401);
		expect(res.headers.get("WWW-Authenticate")).toContain("resource_metadata=");
		const { connections } = (await (await call("/device/connections", { headers: auth })).json()) as any;
		expect(connections).toHaveLength(0);

		// Pairing again proves the phone is alive, so the new connection works before any AI session.
		await runInDurableObject(env.PHONE.getByName(deviceId), (_phone, state) => {
			state.storage.sql.exec("UPDATE device SET last_seen = ?", Date.now() - 31 * 24 * 60 * 60 * 1000);
		});
		const again = await signIn(await pair(auth), "Claude");
		expect((await mcp(again.token!, { jsonrpc: "2.0", id: 3, method: "tools/list" })).status).toBe(200);
	});

	it("refuses device routes without the device secret", async () => {
		const { deviceId } = await registerDevice();
		const forged = { Authorization: `Device ${deviceId}.${"x".repeat(43)}` };
		expect((await call("/device/pair", { method: "POST", headers: forged })).status).toBe(401);
		expect((await call("/device/connect", { headers: { ...forged, Upgrade: "websocket" } })).status).toBe(401);
	});
});

describe("dashboard", () => {
	it("is served with a strict content security policy", async () => {
		const res = await call("/dashboard/");
		expect(res.status).toBe(200);
		expect(await res.text()).toContain("PennyWise Dashboard");
		expect(res.headers.get("Content-Security-Policy")).toContain("frame-ancestors 'none'");
	});

	it("replaces an earlier dashboard sign-in instead of adding another connection", async () => {
		const { auth } = await registerDevice();
		const dashboard = await registerClient("PennyWise dashboard"); // the page keeps its client id in localStorage
		await signIn(await pair(auth), "PennyWise dashboard", dashboard);
		await signIn(await pair(auth), "PennyWise dashboard", dashboard);
		await signIn(await pair(auth), "Claude");
		const { connections } = (await (await call("/device/connections", { headers: auth })).json()) as any;
		expect(connections.map((c: any) => c.clientName).sort()).toEqual(["Claude", "PennyWise dashboard"]);
	});
});

describe("older apps behind the relay", () => {
	const answer = (body: object) => ({ content: [{ type: "text" as const, text: JSON.stringify(body) }] });

	it("refuses create_rule and update_rule with a condition on SMS text or a note, and passes the rest", () => {
		for (const name of ["create_rule", "update_rule"]) {
			for (const field of ["SMS_TEXT", "NARRATION"]) {
				const out = refuseHiddenConditions(name, { conditions: [{ field: "MERCHANT" }, { field, operator: "CONTAINS", value: "x" }] });
				expect(out?.isError).toBe(true);
				expect(out?.content[0].text).toContain("hidden from AI apps");
			}
			expect(refuseHiddenConditions(name, { conditions: [{ field: "MERCHANT", operator: "CONTAINS", value: "x" }] })).toBeNull();
			for (const conditions of ["SMS_TEXT", { field: "SMS_TEXT" }, [null, 3, "NARRATION"], []]) expect(refuseHiddenConditions(name, { conditions })).toBeNull();
			expect(refuseHiddenConditions(name, null)).toBeNull();
			expect(refuseHiddenConditions(name, undefined)).toBeNull();
		}
		expect(refuseHiddenConditions("preview_rule", { conditions: [{ field: "SMS_TEXT" }] })).toBeNull();
	});

	it("refuses a rule that writes a note, and hides note condition text an older app lists", () => {
		const out = refuseHiddenConditions("create_rule", { conditions: [{ field: "MERCHANT" }], actions: [{ field: "NARRATION", actionType: "SET", value: "x" }] });
		expect(out?.content[0].text).toContain("write a transaction's note");
		expect(refuseHiddenConditions("update_rule", { actions: [{ field: "CATEGORY", actionType: "SET", value: "Food" }] })).toBeNull();
		const listed = (value: string) => ({ content: [{ type: "text" as const, text: JSON.stringify({ rules: [{ name: "N", conditions: [{ field: "NARRATION", value }, { field: "MERCHANT", value: "cafe" }] }] }) }] });
		const shown = hideNoteConditions("list_rules", listed("my private note"));
		expect(shown.content[0].text).not.toContain("my private note");
		expect(shown.content[0].text).toContain("(note hidden)");
		expect(shown.content[0].text).toContain("cafe");
		const already = listed("(note hidden)");
		expect(hideNoteConditions("list_rules", already)).toBe(already);
		const other = listed("my private note");
		expect(hideNoteConditions("search_transactions", other)).toBe(other);
	});

	it("refuses dry_run on create_rule and update_rule, and nothing else", () => {
		for (const name of ["create_rule", "update_rule"]) {
			const out = refuseDryRun(name, { dry_run: false });
			expect(out?.isError).toBe(true);
			expect(out?.content[0].text).toContain("preview_rule");
		}
		expect(refuseDryRun("create_rule", { name: "x" })).toBeNull();
		expect(refuseDryRun("preview_rule", { dry_run: true })).toBeNull();
		expect(refuseDryRun("create_rule", undefined)).toBeNull();
	});

	it("says so when an older app created the rule but applied nothing", () => {
		const out = requireAppliedToPast("create_rule", { apply_to_past: "all" }, answer({ created: "Cafe" }));
		expect(out.isError).toBeFalsy();
		expect(out.content[0].text).toContain('"created":"Cafe"');
		expect(out.content[0].text).toContain("needs a newer version of the PennyWise app");
	});

	it("passes results that are counted, errors, or not asked to apply", () => {
		const counted = answer({ created: "Cafe", past_transactions_updated: 0 });
		expect(requireAppliedToPast("create_rule", { apply_to_past: "all" }, counted)).toBe(counted);
		const created = answer({ created: "Cafe" });
		expect(requireAppliedToPast("create_rule", { apply_to_past: "none" }, created)).toBe(created);
		expect(requireAppliedToPast("create_rule", {}, created)).toBe(created);
		const failed = { ...created, isError: true };
		expect(requireAppliedToPast("create_rule", { apply_to_past: "all" }, failed)).toBe(failed);
		expect(requireAppliedToPast("update_rule", { apply_to_past: "all" }, created)).toBe(created);
		const pastFailed = answer({ created: "Cafe", past_error: "failed" });
		expect(requireAppliedToPast("create_rule", { apply_to_past: "all" }, pastFailed)).toBe(pastFailed);
	});
});

describe("requireFilterEcho", () => {
	const answer = (body: object) => ({ content: [{ type: "text" as const, text: JSON.stringify(body) }] });
	const args = { from: "2026-09-01", to: "2026-09-30", exclude_categories: ["Shopping"] };

	it("refuses an unfiltered answer to a filtered request", () => {
		const out = requireFilterEcho("spending_summary", args, answer({ grand_total: [] }));
		expect(out.isError).toBe(true);
		expect(out.content[0].text).toContain("needs a newer version of the PennyWise app");
	});

	it("passes an answer that echoes the filters", () => {
		const result = answer({ grand_total: [], filters: { tag: [], exclude_categories: ["Shopping"], exclude_tags: [] } });
		expect(requireFilterEcho("search_transactions", args, result)).toBe(result);
	});

	it("passes an answer when no filter was asked for", () => {
		const result = answer({ grand_total: [] });
		expect(requireFilterEcho("spending_summary", { from: "2026-09-01", to: "2026-09-30", exclude_tags: [] }, result)).toBe(result);
		expect(requireFilterEcho("list_accounts", args, result)).toBe(result);
		// Not names to the app either: numbers, nested lists, blanks.
		for (const tag of [5, true, [["Trip"]], [1, null, ""], "  ", "\ufeff", "\u001f", "\u0085", "\u180e"]) {
			expect(requireFilterEcho("spending_summary", { tag }, result)).toBe(result);
		}
	});
});
