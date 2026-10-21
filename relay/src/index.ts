import { AuthorizationError, CimdFetchError, OAuthProvider, type OAuthHelpers } from "@cloudflare/workers-oauth-provider";
import { authorizePage, messagePage } from "./pages";
import { TOOLS, TOOL_NAMES } from "./tools";

export { Phone } from "./phone";

type Props = { deviceId: string };
type Ctx = ExecutionContext & { props: Props };
type HelperEnv = Env & { OAUTH_PROVIDER: OAuthHelpers };

const PROTOCOL_VERSIONS = ["2025-06-18", "2025-03-26"];
const PAIR_TTL_SECONDS = 300;
const DASHBOARD_CLIENT = "PennyWise dashboard";

// The dashboard shows phone data, so it gets a strict policy: own scripts only, no framing, no referrer.
async function dashboardAsset(request: Request, env: Env): Promise<Response> {
	const res = await env.ASSETS.fetch(request);
	const out = new Response(res.body, res);
	out.headers.set("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self'; connect-src 'self'; img-src 'self' data:; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
	out.headers.set("X-Frame-Options", "DENY");
	out.headers.set("Referrer-Policy", "no-referrer");
	out.headers.set("X-Content-Type-Options", "nosniff");
	return out;
}

const INSTRUCTIONS =
	"Read-only access to the user's PennyWise expense data, answered live by their phone. " +
	"Amounts always come with a currency code: never add or compare amounts in different currencies as one number. " +
	"Dates are YYYY-MM-DD in the user's local time. If a tool says PennyWise isn't connected, tell the user how to connect and stop.";

// ── MCP (Streamable HTTP, stateless JSON). Every request here carries a valid OAuth token. ──
const mcpHandler = {
	async fetch(request: Request, env: Env, ctx: Ctx): Promise<Response> {
		if (request.method !== "POST") return new Response(null, { status: 405, headers: { Allow: "POST" } });
		const { deviceId } = ctx.props;
		if (!(await env.MCP_LIMIT.limit({ key: deviceId })).success) return rpcError(null, -32000, "Too many requests, slow down.", 429);

		let msg: { jsonrpc?: string; id?: string | number | null; method?: string; params?: Record<string, unknown> };
		try {
			msg = await request.json();
		} catch {
			return rpcError(null, -32700, "Parse error");
		}
		if (Array.isArray(msg) || typeof msg !== "object" || msg === null) return rpcError(null, -32600, "Invalid request");
		const { id, method, params = {} } = msg;
		if (id === undefined) return new Response(null, { status: 202 }); // notifications need no answer

		switch (method) {
			case "initialize": {
				const asked = String(params.protocolVersion ?? "");
				return rpcResult(id, {
					protocolVersion: PROTOCOL_VERSIONS.includes(asked) ? asked : PROTOCOL_VERSIONS[0],
					capabilities: { tools: { listChanged: false } },
					serverInfo: { name: "pennywise", title: "PennyWise", version: "1.0.0" },
					instructions: INSTRUCTIONS,
				});
			}
			case "ping":
				return rpcResult(id, {});
			case "tools/list":
				return rpcResult(id, { tools: TOOLS });
			case "tools/call": {
				const name = String(params.name ?? "");
				if (!TOOL_NAMES.has(name)) return rpcError(id, -32602, `Unknown tool: ${name}`);
				const result = await env.PHONE.getByName(deviceId).callTool(name, params.arguments ?? {});
				return rpcResult(id, result);
			}
			default:
				return rpcError(id, -32601, `Method not found: ${method}`);
		}
	},
};

// ── Everything else: sign-in page, phone registration, pairing, the phone's line. ──
const defaultHandler = {
	async fetch(request: Request, env: HelperEnv): Promise<Response> {
		const url = new URL(request.url);
		const path = url.pathname;
		const oauth = env.OAUTH_PROVIDER;

		if (path === "/authorize") return authorize(request, env, oauth);
		if (path === "/dashboard") return Response.redirect(`${url.origin}/dashboard/`, 301);
		if (path.startsWith("/dashboard/")) return dashboardAsset(request, env);

		if (path === "/device" && request.method === "POST") {
			if (!(await env.REGISTER_LIMIT.limit({ key: clientIp(request) })).success) return json({ error: "rate_limited" }, 429);
			const deviceId = randomHex(16);
			const secret = randomBase64Url(32);
			await env.PHONE.getByName(deviceId).init(await sha256(secret));
			return json({ deviceId, secret }, 201);
		}

		if (path.startsWith("/device/") || (path === "/device" && request.method === "DELETE")) {
			const deviceId = await authenticateDevice(request, env);
			if (!deviceId) return json({ error: "unauthorized" }, 401);
			const phone = env.PHONE.getByName(deviceId);

			if (path === "/device" && request.method === "DELETE") {
				for (const grant of await allGrants(oauth, deviceId)) await oauth.revokeGrant(grant.id, deviceId);
				await phone.wipe();
				return new Response(null, { status: 204 });
			}
			if (path === "/device/connect") return phone.fetch(request);
			if (path === "/device/pair" && request.method === "POST") {
				const code = await newPairingCode(env, deviceId);
				return json({ code, expiresIn: PAIR_TTL_SECONDS });
			}
			if (path === "/device/connections" && request.method === "GET") {
				const items = (await allGrants(oauth, deviceId)).map((g) => ({
					id: g.id,
					clientName: (g.metadata as { clientName?: string } | null)?.clientName ?? "AI app",
					createdAt: g.createdAt,
				}));
				return json({ connections: items });
			}
			const revoke = path.match(/^\/device\/connections\/([A-Za-z0-9_-]+)$/);
			if (revoke && request.method === "DELETE") {
				await oauth.revokeGrant(revoke[1], deviceId);
				return new Response(null, { status: 204 });
			}
		}

		if (path === "/") return new Response("PennyWise AI relay. Passes questions to your phone; stores none of them.\n");
		return new Response("Not found", { status: 404 });
	},
};

async function authorize(request: Request, env: Env, oauth: OAuthHelpers): Promise<Response> {
	try {
		if (request.method === "GET") {
			const authRequest = await oauth.parseAuthRequest(request);
			const details = await oauth.describeConsent(authRequest);
			const consent = await oauth.beginConsent(authRequest);
			consent.headers.set("Content-Type", "text/html; charset=utf-8");
			const firstParty = details.clientName === DASHBOARD_CLIENT && details.redirectUri === `${new URL(request.url).origin}/dashboard/`;
			return new Response(authorizePage(details, consent.handle, firstParty), { headers: consent.headers });
		}
		if (request.method !== "POST") return new Response(null, { status: 405 });

		// The page submits in the background (X-PennyWise-Submit) and then replaces itself with the
		// redirect, so the sign-in page doesn't stay in the browser's history. A plain form post still works.
		const inPage = request.headers.get("X-PennyWise-Submit") === "1";
		const go = (url: string, headers: Headers) => {
			if (inPage) { headers.set("Content-Type", "application/json"); return new Response(JSON.stringify({ redirectTo: url }), { headers }); }
			headers.set("Location", url);
			return new Response(null, { status: 302, headers });
		};
		const fail = (title: string, message: string, status = 400) =>
			inPage ? json({ error: message }, status) : messagePage(title, message, status);

		const badCode = () => fail("That code didn't work", inPage
			? "That code didn't work. Codes last 5 minutes and work once; get a new one in PennyWise."
			: "Codes last 5 minutes and work once. Go back, get a new code in PennyWise and try again.");

		const form = await request.formData();
		const handle = String(form.get("handle") ?? "");
		if (form.get("decision") !== "approve") {
			const denied = await oauth.denyConsent(request, handle);
			return go(denied.headers.get("Location") ?? "/", denied.headers);
		}
		if (!(await env.PAIR_LIMIT.limit({ key: clientIp(request) })).success) {
			return fail("Too many attempts", "Too many attempts. Wait a minute, then try again.", 429);
		}
		const code = String(form.get("code") ?? "").trim();
		const deviceId = /^\d{6}$/.test(code) ? await env.OAUTH_KV.get(`pair:${code}`) : null;
		if (!deviceId) {
			// The handle is still unused, so the same page can try again.
			return badCode();
		}
		// KV can't do check-and-delete, so the phone's object decides which request gets the code.
		const consumed = await env.PHONE.getByName(deviceId).consumePairCode(code);
		await env.OAUTH_KV.delete(`pair:${code}`);
		if (!consumed) return badCode();
		const approved = await oauth.approveConsent(request, handle);
		const client = await oauth.lookupClient(approved.request.clientId);
		const { redirectTo } = await oauth.completeAuthorization({
			request: approved.request,
			userId: deviceId,
			metadata: { clientName: client?.clientName ?? "AI app" },
			scope: approved.request.scope,
			props: { deviceId } satisfies Props,
			// The same AI app on web and phone are separate connections; a new dashboard sign-in replaces the old one.
			revokeExistingGrants: client?.clientName === DASHBOARD_CLIENT,
		});
		return go(redirectTo, approved.headers);
	} catch (error) {
		if (error instanceof AuthorizationError && error.redirectTo) return Response.redirect(error.redirectTo, 302);
		if (error instanceof AuthorizationError) return messagePage("Can't connect", error.description ?? "This sign-in request isn't valid.");
		if (error instanceof CimdFetchError) return messagePage("Can't connect", "This AI app could not be verified.");
		throw error;
	}
}

async function newPairingCode(env: Env, deviceId: string): Promise<string> {
	for (;;) {
		const n = crypto.getRandomValues(new Uint32Array(1))[0] % 1_000_000;
		const code = n.toString().padStart(6, "0");
		if ((await env.OAUTH_KV.get(`pair:${code}`)) === null) {
			await env.OAUTH_KV.put(`pair:${code}`, deviceId, { expirationTtl: PAIR_TTL_SECONDS });
			await env.PHONE.getByName(deviceId).issuePairCode(code, Date.now() + PAIR_TTL_SECONDS * 1000);
			return code;
		}
	}
}

/** `Authorization: Device <deviceId>.<secret>` */
async function authenticateDevice(request: Request, env: Env): Promise<string | null> {
	const m = (request.headers.get("Authorization") ?? "").match(/^Device ([0-9a-f]{32})\.([A-Za-z0-9_-]{43})$/);
	if (!m) return null;
	return (await env.PHONE.getByName(m[1]).verify(await sha256(m[2]))) ? m[1] : null;
}

async function allGrants(oauth: OAuthHelpers, userId: string) {
	const out = [];
	let cursor: string | undefined;
	do {
		const page = await oauth.listUserGrants(userId, { cursor });
		out.push(...page.items);
		cursor = page.cursor;
	} while (cursor);
	return out;
}

const rpcResult = (id: string | number | null, result: unknown) => json({ jsonrpc: "2.0", id, result });
const rpcError = (id: string | number | null, code: number, message: string, status = 200) =>
	json({ jsonrpc: "2.0", id, error: { code, message } }, status);
const json = (body: unknown, status = 200) => Response.json(body, { status });
const clientIp = (r: Request) => r.headers.get("CF-Connecting-IP") ?? "unknown";

async function sha256(s: string): Promise<string> {
	const d = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s));
	return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
const randomHex = (n: number) => [...crypto.getRandomValues(new Uint8Array(n))].map((b) => b.toString(16).padStart(2, "0")).join("");
const randomBase64Url = (n: number) =>
	btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(n)))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

// The provider is built on first request because the public origin comes from env.
let provider: OAuthProvider<Env> | undefined;
export default {
	fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
		provider ??= new OAuthProvider<Env>({
			apiRoute: "/mcp",
			apiHandler: mcpHandler as never,
			defaultHandler: defaultHandler as never,
			authorizeEndpoint: "/authorize",
			tokenEndpoint: "/oauth/token",
			clientRegistrationEndpoint: "/oauth/register",
			clientIdMetadataDocumentEnabled: true,
			scopesSupported: ["read", "offline_access"],
			requiredScopes: ["read"],
			resourceMetadata: { resource: `${env.BASE_URL}/mcp`, authorization_servers: [env.BASE_URL] },
			accessTokenTTL: 60 * 60,
			refreshTokenTTL: 365 * 24 * 60 * 60,
			refreshTokenIdleTTL: 90 * 24 * 60 * 60,
		});
		return provider.fetch(request, env, ctx);
	},
} satisfies ExportedHandler<Env>;
