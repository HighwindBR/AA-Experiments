# Beta4 — Round A reliability implementation

Development version: `1.0.0-beta4-preflight14` (`versionCode 160`)
Scope: reliability only. No RC, merge, signed release or public publishing.

## Design

### Active profile durability (P1-01)

**Before:** `active_profile_v2.json` was truncated in place, so an
interrupted write could destroy the last usable profile.

**Now:** two remote files, `active_profile_a_v3.json` and
`active_profile_b_v3.json`, are selected using the SharedPreferences
`profile.active_slot.v1` key, stored in the existing
`dynamic_overrides_v2` preference group.

Publication order:

1. Serialize schema-3 profile for the complete installed AA fingerprint.
2. Choose the *inactive* slot; never truncate the selected slot.
3. Write, flush, fsync and close the inactive slot.
4. Reopen and read it back; compare bytes and validate schema/build.
5. Atomically commit the slot pointer through RemotePreferences.
6. Only then report publication success.

The runtime first reads the selected slot; if invalid/unavailable it
tries the other slot and then the legacy profile. Before the first
successful new publication, the legacy file is the preferred reader
source. No existing normal/Advanced/Experimental preferences are reset.
Every successful loaded profile is still subject to normal AA package
fingerprint verification before any hooks are installed.

Events: `PROFILE_SOURCE` identifies the successfully parsed source;
`PROFILE_FALLBACK` signals that the selected slot was skipped.
`PROFILE_ACCEPTED` remains the actual build-fingerprint and
hook-installation eligibility signal. Even an accepted profile can
have individual `HOOK_SUSPENDED` entries; do not conflate them.

### Profile refresh concurrency (P1-02)

`ProfileRefreshGate` is a suspend-safe exclusive lock on profile
recomputation, file publication and installed-vs-imported build-state
handoffs. Queued updates evaluate the current state only after
acquiring the gate. The LSPosed connection state flow now uses
sequential `collect` to avoid canceling writes on rapid bind/death
events. Stale service death callbacks are ignored. A missing remote
preference snapshot rejects publication and preserves the old slot
instead of submitting an empty profile.

### Foreground service ownership (P1-03)

`AnalysisOperationGate` is an atomic single-owner gate, covering
installed-AA scan, archive import, directed deep-resolve and advanced
consumer trace. Rejected overlapping operations leave the ongoing
analysis untouched. Every successful acquisition releases via
`finally` even after coroutine cancellation or Android API errors.
Background DEX progress uses atomic StateFlow updates, while
unexpected exceptions reset loading/scan UI state.

## Tests added — not yet marked as CI-pass

- `ProfileSlotPolicyTest`: legacy order, A/B rotation, malformed pointer.
- `VerifiedProfilePublisherTest`: successful first and next publish,
  interrupted half-write, corrupted read-back, rejected pointer commit,
  rejected wrong-build input.
- `ProfileRefreshGateTest`: later refresh waits until earlier completes,
  release after exception.
- `AnalysisOperationGateTest`: overlapping analysis rejected,
  unmatched release rejected.

All tests operate on the real production decision classes. The fault
injection uses in-memory storage; on-device remote-file semantics must
be verified separately.

## Required sign-off before Round B / beta4 RC

1. In Actions, manually run `debug-apk.yml` on
   `feature/beta4-getter-probe-preflight`. Its signed-preflight job
   runs `:app:testDebugUnitTest :app:lintRelease` before signing.
2. Confirm the job/compilation and APK signature succeeded, then
   install preflight14 over preflight13 without clearing any data.
3. Check Settings → About shows `1.0.0-beta4-preflight14`.
4. Without changing any of the existing normal, suspended or special
   override records, restart AA in Open Headunit and capture LSPosed
   events. Require `PROFILE_SOURCE` = a v3 slot,
   `PROFILE_ACCEPTED` and representative `HOOK_INSTALLED` /
   `OVERRIDE_RETURNED`. No unexpected
   `PROFILE_FAILED`, `PROFILE_REJECTED`, `HOOK_FAILED`.
5. Stop an analysis using the existing explicit cancel control,
   verify the service exits and the UI returns to a non-loading
   state. Start a new analysis afterward; do not force-stop mid-write.
6. Reconnect LSPosed (only when parked/disconnected from active AA).
   Check that the last known profile and normal mappings remain.
7. Review the new unit tests and device logs before accepting the
   P1 mitigations as validated. Testing deliberate destructive
   corruption on the user's active phone is **not required**.

## Deferred

The complete backup schema, streaming 50k-record export, dark theme
contrast, numeric input validation, advanced UX and release workflow
for beta4 RC are tracked in the separate pre-RC review. No normal
profile schema bump was needed.
