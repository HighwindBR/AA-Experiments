# Runtime observability — alpha19

## Purpose

The manager previously displayed `Not observed` for every runtime situation. That wording did not distinguish a disconnected framework, an invalid scope, an unpublished profile or a valid profile waiting for Android Auto to start.

Alpha19 adds an honest readiness model without claiming reverse telemetry that modern LSPosed API 101 does not provide.

## Manager states

- `NO_TARGET`: an imported archive is being inspected offline.
- `DISCONNECTED`: the LSPosed service is unavailable.
- `API_UNSUPPORTED`: the connected framework API is older than 101.
- `OUT_OF_SCOPE`: Android Auto is not in the module scope.
- `NO_OVERRIDES`: the runtime is intentionally unchanged.
- `PROFILE_UNPUBLISHED`: overrides exist but their dynamic profile was not published.
- `READY_AWAITING_PROCESS`: the profile is ready; execution must be confirmed in LSPosed logs.

## Structured LSPosed events

Runtime events use the `AAExperiments` tag and an `AAX_EVENT` JSON payload. The important events are:

- `MODULE_LOADED`
- `PROFILE_ACCEPTED` / `PROFILE_REJECTED` / `PROFILE_FAILED`
- `HOOK_INSTALLED` / `HOOK_SUSPENDED` / `HOOK_FAILED`
- `OVERRIDE_RETURNED` / `ORIGINAL_RETURNED` / `OVERRIDE_REJECTED`

Return events are emitted once for each key and value generation. Repeated getter calls therefore do not flood the framework log.

## API boundary

Inside a hooked process, API 101 exposes remote preferences and shared files as read-only. It does not provide a writable reverse channel to the manager. Consequently, the UI reports readiness and directs the user to the structured LSPosed evidence instead of presenting an unverified `observed` value.

A mapping newly added while Android Auto is already running is published safely but cannot install its hook retroactively. The Android Auto process must start again for `onPackageReady` to evaluate the new mapping. The app does not force-stop Android Auto or reboot the device.

## UI polish included

- The tri-state Boolean label was shortened from `System default` to `default`.
- The complete-catalog export scope was shortened from `ALL DISCOVERIES` to `ALL`.
- Internal enum and export semantics are unchanged.

## Validation

- All 53 declared unit and fixture tests passed.
- The complete installed 17.8.663814 scan passed in a separately constrained 256 MiB test worker.
- `assembleDebug` passed for version 118 / `1.0.0-alpha19`.
- APK Signature Scheme v2 verified.
- APK SHA-256: `3b73887b16ec7a44ecf36b24c1c6fa9e6166a1063071dc9d178429530c495a0a`.
