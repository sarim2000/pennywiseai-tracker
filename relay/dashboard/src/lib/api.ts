// OAuth public client (PKCE) of this relay plus a typed caller for the read-only MCP tools.
// Tokens stay in sessionStorage: this tab only, gone when it closes.

export type Money = { amount: string; currency: string };
export type Group = { group: string; sub?: string; count: number; totals: Money[] };
export type Summary = { type: "EXPENSE" | "INCOME"; grand_total: Money[]; groups: Group[] };
export type Txn = { date: string; merchant: string; category: string; type: string; amount: Money; account?: string };
/** `categories`/`accounts`: every value among the matches before those two filters (older apps omit them). */
export type Search = { total_matches: number; offset: number; shown: number; categories?: string[]; accounts?: string[]; transactions: Txn[] };
export type Account = { account: string; kind: string; balance?: Money; outstanding?: Money; credit_limit?: Money; as_of: string };
export type Subscription = { merchant: string; amount: Money; billing_cycle: string; direction: string; next_payment?: string; category?: string };

export type GroupBy = "category" | "merchant" | "month" | "day" | "account" | "month_category";
export type Range = { from: string; to: string };

type Tools = {
	spending_summary: [Range & { group_by: GroupBy; type?: "EXPENSE" | "INCOME"; account?: string }, Summary];
	search_transactions: [Range & { query?: string; category?: string; type?: string; account?: string; sort?: string; limit?: number; offset?: number }, Search];
	list_accounts: [Record<string, never>, { accounts: Account[] }];
	list_subscriptions: [Record<string, never>, { subscriptions: Subscription[] }];
};

const BASE = location.origin;
const REDIRECT = `${BASE}/dashboard/`;
const RESOURCE = `${BASE}/mcp`;
const ss = sessionStorage;

/** The phone answered with a message for the user (not connected, Pro ended, timed out). */
export class PhoneError extends Error {}
export class SignedOut extends Error {}

const b64url = (b: ArrayBuffer | Uint8Array) =>
	btoa(String.fromCharCode(...new Uint8Array(b))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const random = (n = 32) => b64url(crypto.getRandomValues(new Uint8Array(n)));

async function clientId(): Promise<string> {
	const saved = localStorage.getItem("pw_client_id");
	if (saved) return saved;
	const res = await fetch("/oauth/register", {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({
			client_name: "PennyWise dashboard", redirect_uris: [REDIRECT], token_endpoint_auth_method: "none",
			grant_types: ["authorization_code", "refresh_token"], response_types: ["code"],
		}),
	});
	if (!res.ok) throw new Error("Couldn't register this browser with PennyWise's relay.");
	const id = ((await res.json()) as { client_id: string }).client_id;
	localStorage.setItem("pw_client_id", id);
	return id;
}

export const isSignedIn = () => ss.getItem("pw_access") !== null;

export async function signIn() {
	const verifier = random();
	const challenge = b64url(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier)));
	const state = random(16);
	ss.setItem("pw_verifier", verifier);
	ss.setItem("pw_state", state);
	const q = new URLSearchParams({
		response_type: "code", client_id: await clientId(), redirect_uri: REDIRECT, state, scope: "read",
		code_challenge: challenge, code_challenge_method: "S256", resource: RESOURCE,
	});
	location.replace(`/authorize?${q}`); // replace: Back from the dashboard shouldn't land on the sign-in page
}

/** Completes the redirect back from /authorize, if this page load is one. */
export async function finishSignInIfReturning(): Promise<void> {
	const p = new URLSearchParams(location.search);
	if (!p.has("code") && !p.has("error")) return;
	history.replaceState(null, "", REDIRECT);
	if (p.has("error")) throw new Error("Connecting was cancelled.");
	if (p.get("state") !== ss.getItem("pw_state")) throw new Error("This sign-in link isn't valid. Connect again.");
	await token({ grant_type: "authorization_code", code: p.get("code")!, redirect_uri: REDIRECT, code_verifier: ss.getItem("pw_verifier") ?? "" });
	ss.removeItem("pw_verifier");
	ss.removeItem("pw_state");
}

async function token(fields: Record<string, string>) {
	const res = await fetch("/oauth/token", {
		method: "POST",
		headers: { "Content-Type": "application/x-www-form-urlencoded" },
		body: new URLSearchParams({ client_id: await clientId(), resource: RESOURCE, ...fields }),
	});
	if (!res.ok) {
		localStorage.removeItem("pw_client_id"); // an expired registration is the usual cause
		throw new Error("Couldn't sign in. Connect again with a new pairing code.");
	}
	const t = (await res.json()) as { access_token: string; refresh_token?: string };
	ss.setItem("pw_access", t.access_token);
	if (t.refresh_token) ss.setItem("pw_refresh", t.refresh_token);
}

export function signOut() {
	ss.removeItem("pw_access");
	ss.removeItem("pw_refresh");
}

let refreshing: Promise<boolean> | null = null;
function refresh(): Promise<boolean> {
	const rt = ss.getItem("pw_refresh");
	if (!rt) return Promise.resolve(false);
	refreshing ??= token({ grant_type: "refresh_token", refresh_token: rt }).then(() => true, () => false).finally(() => (refreshing = null));
	return refreshing;
}

export async function tool<K extends keyof Tools>(name: K, args: Tools[K][0], retried = false): Promise<Tools[K][1]> {
	const res = await fetch("/mcp", {
		method: "POST",
		headers: { Authorization: `Bearer ${ss.getItem("pw_access")}`, "Content-Type": "application/json", Accept: "application/json, text/event-stream" },
		body: JSON.stringify({ jsonrpc: "2.0", id: crypto.randomUUID(), method: "tools/call", params: { name, arguments: args } }),
	});
	if (res.status === 401) {
		if (!retried && (await refresh())) return tool(name, args, true);
		signOut();
		throw new SignedOut("You've been signed out. Connect again with a new pairing code.");
	}
	if (res.status === 429) throw new PhoneError("Too many requests. Wait a minute, then refresh.");
	const body = (await res.json()) as { result?: { content: { text: string }[]; isError?: boolean }; error?: { message: string } };
	if (!body.result) throw new PhoneError(body.error?.message ?? "PennyWise's relay couldn't answer.");
	const text = body.result.content[0]?.text ?? "";
	if (body.result.isError) throw new PhoneError(text);
	return JSON.parse(text) as Tools[K][1];
}
