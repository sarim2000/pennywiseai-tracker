import { DurableObject } from "cloudflare:workers";
import { NOT_CONNECTED, textResult, type ToolResult } from "./tools";

const CALL_TIMEOUT_MS = 15_000;

type Pending = { resolve: (r: ToolResult) => void; timer: ReturnType<typeof setTimeout> };

/**
 * One per phone. Holds the phone's outbound WebSocket during an AI session and
 * forwards tool calls over it. Stores only the device secret's hash.
 */
export class Phone extends DurableObject<Env> {
	private pending = new Map<string, Pending>();

	constructor(ctx: DurableObjectState, env: Env) {
		super(ctx, env);
		ctx.blockConcurrencyWhile(async () => {
			ctx.storage.sql.exec("CREATE TABLE IF NOT EXISTS device (secret_hash TEXT NOT NULL)");
			ctx.storage.sql.exec("CREATE TABLE IF NOT EXISTS pair (code TEXT NOT NULL, expires_at INTEGER NOT NULL)");
		});
		// Keepalives from the phone are answered without waking the object.
		ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
	}

	init(secretHash: string): void {
		this.ctx.storage.sql.exec("DELETE FROM device");
		this.ctx.storage.sql.exec("INSERT INTO device (secret_hash) VALUES (?)", secretHash);
	}

	verify(secretHash: string): boolean {
		const row = this.ctx.storage.sql.exec<{ secret_hash: string }>("SELECT secret_hash FROM device").toArray()[0];
		return row !== undefined && timingSafeEqual(row.secret_hash, secretHash);
	}

	async wipe(): Promise<void> {
		for (const ws of this.ctx.getWebSockets()) ws.close(4001, "reset");
		await this.ctx.storage.deleteAll();
	}

	/** One live pairing code per phone; a new one replaces the old. */
	issuePairCode(code: string, expiresAt: number): void {
		this.ctx.storage.sql.exec("DELETE FROM pair");
		this.ctx.storage.sql.exec("INSERT INTO pair (code, expires_at) VALUES (?, ?)", code, expiresAt);
	}

	/** True for exactly one caller: the object runs one call at a time and the delete is the check. */
	consumePairCode(code: string): boolean {
		return this.ctx.storage.sql.exec("DELETE FROM pair WHERE code = ? AND expires_at > ? RETURNING code", code, Date.now()).toArray().length > 0;
	}

	isOnline(): boolean {
		return this.ctx.getWebSockets().length > 0;
	}

	/** WebSocket upgrade from the phone; the Worker has already checked the device secret. */
	async fetch(request: Request): Promise<Response> {
		if (request.headers.get("Upgrade") !== "websocket") return new Response("Expected WebSocket", { status: 426 });
		for (const old of this.ctx.getWebSockets()) old.close(4000, "replaced by a newer connection");
		const { 0: client, 1: server } = new WebSocketPair();
		this.ctx.acceptWebSocket(server);
		return new Response(null, { status: 101, webSocket: client });
	}

	async callTool(name: string, args: unknown): Promise<ToolResult> {
		const ws = this.ctx.getWebSockets()[0];
		if (!ws) return textResult(NOT_CONNECTED, true);
		const id = crypto.randomUUID();
		return new Promise<ToolResult>((resolve) => {
			const timer = setTimeout(() => {
				this.pending.delete(id);
				resolve(textResult("PennyWise didn't answer in time. Check the phone is online and the AI session is still on, then ask again.", true));
			}, CALL_TIMEOUT_MS);
			this.pending.set(id, { resolve, timer });
			try {
				ws.send(JSON.stringify({ type: "call", id, name, arguments: args ?? {} }));
			} catch {
				clearTimeout(timer);
				this.pending.delete(id);
				resolve(textResult(NOT_CONNECTED, true));
			}
		});
	}

	async webSocketMessage(_ws: WebSocket, message: string | ArrayBuffer): Promise<void> {
		if (typeof message !== "string") return;
		let msg: { type?: string; id?: string; result?: ToolResult };
		try {
			msg = JSON.parse(message);
		} catch {
			return;
		}
		if (msg.type !== "result" || !msg.id) return;
		const p = this.pending.get(msg.id);
		if (!p) return;
		clearTimeout(p.timer);
		this.pending.delete(msg.id);
		p.resolve(isToolResult(msg.result) ? msg.result : textResult("PennyWise sent an answer the relay couldn't read.", true));
	}

	async webSocketClose(ws: WebSocket, code: number): Promise<void> {
		ws.close(code === 1005 ? 1000 : code, "closing");
	}
}

function isToolResult(r: unknown): r is ToolResult {
	return typeof r === "object" && r !== null && Array.isArray((r as ToolResult).content);
}

function timingSafeEqual(a: string, b: string): boolean {
	if (a.length !== b.length) return false;
	let diff = 0;
	for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
	return diff === 0;
}
