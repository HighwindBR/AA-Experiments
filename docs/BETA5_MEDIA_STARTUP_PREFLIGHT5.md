# Beta5 preflight5 — semantic Media Startup resolver, fail-closed

## Scope and status
PF4 is physically verified on the user's inspected Android Auto 18.1.164344-release.daily and remains unchanged. PF5 introduces a **bounded, read-only structural discovery** for future Android Auto builds; it does **not** yet enable automatic launching on unknown builds. The future PF6 cross-version fixture matrix and real HU checks are prerequisites to executing renamed internal methods.

## Why not use the flag resolver unchanged?
The existing `StructuralResolver` resolves getter methods for editable flags. Media Startup depends on multiple correlated methods, a nonpublic controller object, an options constructor and lifecycle callbacks. Matching one method's type or name cannot prove safe runtime semantics.

## Static proof roles
- Projection lifecycle readiness, end and teardown must each be unique `()->V` methods with corresponding **stable logger string literals**, and all three must be in the same class.
- Start-car-activity route must uniquely contain the logger anchor `startCarActivity(intent:`, be instance `(Intent, L<options>;)V`, with second argument an internal class reference.
- That route's owner must have exactly one static no-arg factory returning the same owner class.
- The options class must have a no-arg constructor.
- Media-selection callback must uniquely contain `onMediaAppChanged`, accept `ComponentName` and return `void`.
- Obfuscated names are neither searched nor trusted. Duplicates, inconsistent families, failures and missing anchors block candidate acceptance.

## Results
- `KNOWN_BUILD_VALIDATED`: all current structural anchors and the already field-tested exact 18.1 base SHA match. Runtime keeps its existing pinned hooks.
- `REVIEW_ONLY`: unique structural candidate for another build. **Never** enough to install active hooks. A reviewer must inspect candidate bytecode and exercise the AA-specific lifecycle on a head unit first.
- `AMBIGUOUS`, `UNSUPPORTED`, `ERROR`: do not select a controller.
- The `autoLaunchAllowed` field is permanently false for PF5 discovery results, including known builds. Discovery reports do not grant runtime execution permissions.

## Safety and resource bounds
The scanner opens only the installed base APK, processes one DEX entry at a time, caps each entry at 32 MiB and candidate records at 256, retains only anchor methods and relevant controller-class method ABI, never reads media/account data, and does not write dynamic profile fields or remote preferences.

Use `Diagnostics → Media Startup · preflight5 → PF5 · Inspect Media Startup compatibility`. Copy the result and compare with the previous AA 18.1. For a newer AA build, do not turn on PF4 until the cross-build compatibility gates are complete.

## Test gates
- [ ] Unit tests including deliberate aliasing, missing anchors, missing constructor, duplicate route, inconsistent lifecycle, parser error and known-build fallback.
- [ ] Android lint and debug build.
- [ ] Inspect the actual Android Auto 18.1 APK on device; expected `KNOWN_BUILD_VALIDATED` **only if** all anchors are identified. Otherwise improve proof rather than loosening constraints.
- [ ] For a different build, inspect its base APK independently and record `REVIEW_ONLY`/suspended diagnostics. No active remapping.
- [ ] PF6: run at least two independent Android Auto builds, compare class and method roles, check runtime behavior and false positives on real head unit before considering a gated dynamic launch map.

No public tag, release, merge or automatic cross-build enabling in this preflight.
