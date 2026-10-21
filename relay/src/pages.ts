import type { ConsentDescription } from "@cloudflare/workers-oauth-provider";

// Everything shown here that came from the client (name, domain, redirect host) is attacker-chosen.
const escape = (value: string) => value.replace(/[&<>"']/g, (char) => `&#${char.charCodeAt(0)};`);

const STYLE = `
:root{--bg:#fbf9f6;--fg:#24201c;--muted:#6b635b;--line:#e4ded7;--accent:#c47a12;--on-accent:#fff;--err:#b42318;color-scheme:light dark}
@media (prefers-color-scheme:dark){:root{--bg:#161412;--fg:#f3eee8;--muted:#a8a097;--line:#2f2b27;--accent:#f0c66b;--on-accent:#2a1f08;--err:#ff8f84}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.5 system-ui,sans-serif;padding:32px max(16px,env(safe-area-inset-left))}
main{max-width:420px;margin:0 auto;display:flex;flex-direction:column;gap:16px}h1{font-size:22px;margin:0}
p{margin:0;color:var(--muted)}.sr{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0)}strong{color:var(--fg)}.warn{color:var(--err)}
input{font:600 28px/1 ui-monospace,monospace;letter-spacing:.3em;text-align:center;width:100%;padding:12px;border:1px solid var(--line);border-radius:10px;background:transparent;color:var(--fg)}
input:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
.row{display:flex;gap:8px}button{flex:1;font:600 16px system-ui;padding:12px;border-radius:10px;border:1px solid var(--line);background:transparent;color:var(--fg);cursor:pointer}
button.primary{background:var(--accent);border-color:var(--accent);color:var(--on-accent)}button:disabled{opacity:.6;cursor:wait}button:focus-visible{outline:2px solid var(--accent);outline-offset:2px}`;

/** `firstParty`: the request comes from this relay's own dashboard page, so there is nothing to verify. */
export function authorizePage(details: ConsentDescription, handle: string, firstParty = false, error?: string): string {
	const name = escape(details.clientName);
	const origin = firstParty
		? "This is PennyWise's own dashboard on this site."
		: details.clientDomain
			? `Published by <strong>${escape(details.clientDomain)}</strong>.`
			: "This app registered itself, so its name isn't verified.";
	return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Connect ${name} to PennyWise</title><style>${STYLE}</style></head>
<body><main>
<h1>Connect ${name} to PennyWise</h1>
<p>${origin} Access goes to <strong>${escape(details.redirectHost)}</strong>.</p>
${details.redirectIsLoopback && !firstParty ? '<p class="warn">This sends access to an app on your computer. Continue only if you just started connecting from it.</p>' : ""}
<p>It will be able to <strong>read</strong> your transactions, balances and subscriptions while an AI session is on in PennyWise. It can't change anything.</p>
<p>In PennyWise, open <strong>Settings → Connect Claude / ChatGPT</strong>, start an AI session, tap <strong>Get pairing code</strong> and enter it here.</p>
<form method="post" autocomplete="off">
  <input type="hidden" name="handle" value="${escape(handle)}">
  <label for="code" class="sr">Pairing code</label>
  <input id="code" name="code" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" required autofocus aria-describedby="err">
  <p id="err" class="warn" role="alert"${error ? "" : " hidden"}>${error ? escape(error) : ""}</p>
  <!-- Connect comes first so Enter submits it; row-reverse keeps Cancel on the left. -->
  <div class="row" style="margin-top:12px;flex-direction:row-reverse">
    <button class="primary" name="decision" value="approve">Connect</button>
    <button name="decision" value="deny" formnovalidate>Cancel</button>
  </div>
</form>
<script>
const form = document.querySelector("form"), err = document.getElementById("err");
form.addEventListener("submit", async (e) => {
	const decision = e.submitter && e.submitter.value;
	if (!decision) return;
	e.preventDefault();
	const body = new URLSearchParams(new FormData(form));
	body.set("decision", decision);
	form.querySelectorAll("button").forEach((b) => (b.disabled = true));
	try {
		const res = await fetch(location.href, { method: "POST", body, headers: { "X-PennyWise-Submit": "1" } });
		const out = await res.json();
		if (out.redirectTo) return location.replace(out.redirectTo);
		err.textContent = out.error || "Something went wrong. Try again.";
	} catch {
		err.textContent = "Couldn't reach PennyWise's relay. Check your connection and try again.";
	}
	err.hidden = false;
	form.querySelectorAll("button").forEach((b) => (b.disabled = false));
	document.getElementById("code").select();
});
</script>
</main></body></html>`;
}

export function messagePage(title: string, message: string, status = 400): Response {
	const html = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${escape(title)}</title><style>${STYLE}</style></head><body><main><h1>${escape(title)}</h1><p>${escape(message)}</p></main></body></html>`;
	return new Response(html, { status, headers: { "Content-Type": "text/html; charset=utf-8" } });
}
