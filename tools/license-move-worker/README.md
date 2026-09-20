# License move Worker

Frees a Dodo license key's activation so PennyWise Pro can move to a new phone
(activation limit is 1 per key). See `worker.js` header for the contract.

```bash
cd tools/license-move-worker
wrangler secret put DODO_API_KEY     # Dodo dashboard → Developer → API keys
wrangler deploy
```

Then put the deployed URL in `local.properties` as `LICENSE_MOVE_URL=https://…/`
so release builds show the "Move to this device" button. Builds without it
still activate keys; they just can't self-serve a move.
