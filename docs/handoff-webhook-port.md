# Webhook port status

The webhook implementation is on `feat/webhook-sync`, with the upstream contribution
on `feat/webhook-sync-upstream`. It uses Cashiro as a
reference, with PennyWise's current budget and transaction semantics.

See [webhooks.md](webhooks.md) for setup, payloads, scheduling, receiver
idempotency, and limitations.

## Completed

- Room schema 64, registered 62→63 and 63→64 migrations, DAOs, converters, and Hilt wiring.
- Create/edit/delete profiles, enable/disable, selected types and currency,
  predefined/custom ranges, masked custom headers, and delivery history.
- Profile-specific synthetic tests, manual sync, and network-constrained
  WorkManager interval scheduling from 1 to 24 hours. No-profile scheduling
  is cancelled. Both navigation roots are wired.
- Incremental transaction updates and soft-delete tombstones, bounded by the
  captured sync time. Millisecond, inclusive cursors match SQL mutation timestamps.
- Successful batches track delivered IDs for currency-change removals. Backward
  clock changes replay retained records before resetting the cursor.
- Cursor commits only after all batches succeed, and only if the profile's
  configuration has not changed during delivery. Profile edits preserve history.
- Expense summary splits and analytics/credit-card preferences. Budgets use
  resolved budget windows and the existing budget spending calculation.
- Bounded retries, cancellation propagation, request timeouts, safe redirect
  handling, credential-free error messages, and explicit schema/default JSON fields.
- HTTPS for remote endpoints; loopback HTTP for local tests.
- Count and UTF-8 byte limits split large batches before delivery without dropping records.
- Validation errors beside Save and independent editor/list/history scroll state.

## Verification

`./init.sh` with JDK 21 and `:app:assembleDebug` passed. The follow-up `./init.sh app` gate passed with 45 webhook unit tests.
Set `JAVA_HOME` to JDK 21 when the shell default uses an older JDK.

Nine instrumentation tests passed on the `Slim_Pixel` emulator, covering
62→64 and 63→64 migration/data preservation, profile-edit history preservation and delete
cascades, incremental queries, transaction/profile currency removals, stale-config cursor protection, concurrent profile-toggle preservation, and Android
client delivery of synthetic JSON/custom headers to a loopback receiver.
Earlier migration/query tests also passed on the connected physical device.

Emulator UI checks passed for Settings navigation, visible save validation,
profile creation/editing/deletion, disabled automatic delivery, type/range
selection, masked and retained custom headers, synthetic HTTP 200 delivery,
HTTP 503 retries/failure history, history retention across edits, and light/dark
rendering. The temporary emulator profile was deleted after verification.


The APK is
`app/build/outputs/apk/standard/debug/app-standard-universal-debug.apk`.
Its application ID is **com.pennywiseai.tracker.debug**, with activity
**com.pennywiseai.tracker.MainActivity**. It is separate from the regular app.

Use an emulator for further UI verification without clearing existing app data
or altering real webhook profiles.

## Scope limits

Scheduling uses intervals rather than Cashiro's daily clock-time alarms.
Webhook profiles/credentials are excluded from JSON backups. Hard deletion,
delete-all, and SMS reparsing do not retain remote tombstones; receivers need
separate reconciliation after those operations. These limits are documented in
`webhooks.md`.
