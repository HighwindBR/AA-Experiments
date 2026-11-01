# Advanced Overrides

This guide describes the source on the current maintenance branch. Historical
beta4 instructions and device evidence remain in
[the beta4 implementation record](ADVANCED_OVERRIDES_BETA4.md).

Advanced Overrides expose a restricted subset of additional typed getters.
An eligible getter is technically hookable; its safe values and downstream
effect may still be unknown. Configure while parked and outside active projection.

## Enable and edit

1. Open **Settings → Diagnostics → Advanced Overrides**.
2. Turn on **Show Advanced in Experiments** and accept the warning.
3. Open **Experiments** and choose an entry marked **Advanced**.
4. Inspect its type and evidence, enter a value accepted by that editor, and
   complete both confirmation steps. The typed acknowledgement is `APPLY`,
   or `PARKED APPLY` for elevated-risk entries.
5. Check whether the manager reports the change as published or merely saved.
   Resolve a publication failure before assuming Android Auto can use it.
6. End projection and restart Android Auto when hook installation changes.
   Use LSPosed logs to confirm getter invocation and override returns.

Turning off Advanced access only hides the extra editing controls. Use
**Pause**, **Pause all** or **Restore baseline** to change saved override state.
A restart may be needed for changed hook installations.

## Baselines and Android Auto updates

Each Advanced record retains the previous normal override and name, including
explicit absence, plus its exact build and getter identity. Pause retains that
baseline; Restore reinstates it. Resetting/deleting all overrides is different:
it discards saved records and baselines.

Records from another Android Auto fingerprint remain stored but suspended.
They are not automatically reactivated. Resume on the current build revalidates
the method before publication.

A complete recovery backup includes normal, Advanced and Experimental records
and baselines. It requires the same Android Auto build when restored. It does
not include Media Startup consent/routes, appearance, catalog cache or LSPosed
configuration. The ordinary normal-only backup does not carry all those
baselines. See [Architecture](ARCHITECTURE.md#catalog-exports-and-recovery).

## Diagnostics and limits

The former VTC and DOUBLE diagnostic panels were retired. Profile refresh
disables the old VTC preference and clears a persisted DOUBLE probe selection.
Other eligible read-only probes remain available in identifier details.
Runtime compatibility code still exists; UI removal does not mean every old
hook implementation has been deleted.

Ambiguous, sensitive, parameterized or unsupported getters remain blocked.
The reviewed parked-mode timing cohort still requires elevated acknowledgement;
the app does not establish safe numeric bounds or vehicle-state behavior.
See [Known limitations](KNOWN_LIMITATIONS.md) before experimenting.
