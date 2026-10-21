# PennyWise dashboard

Big-screen analytics for PennyWise, served by the relay at `/dashboard/`. It signs in like any AI app (pairing code + OAuth) and reads the phone live through the same read-only tools; nothing is stored server-side.

React + TypeScript + Tailwind + shadcn/ui + Recharts. `npm run build` writes to `../public/dashboard` (the relay's `npm run deploy` and `npm test` build it first).

Local run: from the repo root, start the `relay-local` launch config (`wrangler dev` on :8787), then `node relay/scripts/fake-phone.mjs http://localhost:8787` for sample data and a pairing code.
