# Beta6 — PF2: Media Startup UX and legacy diagnostics cleanup

Development-only branch: `feature/beta6-reliability`. Do not merge, tag or
publish this work while UI, build and device gates are pending.

## Media Startup screen

- One short functional description instead of duplicated onboarding paragraphs.
- Header status reflects ON, route selected, ready-but-off or unsupported state.
- Primary setup button explicitly says "Check compatibility & enable opening".
- **One-step opt-in for a known previously validated Android Auto build only:**
  after the button is clicked, require unique launch routes, a verified
  projected-activity trigger, current exact base fingerprint and successful
  readback/activation of a new build-scoped profile. Keep auto OFF before and
  during the profile write. Then persist and enable the per-build consent.
- New or unreviewed builds get a compatibility proof but no silent automatic
  activation. They may only use the separate explicit confirm/opt-in if a
  unique tracker was proven and publication succeeded.
- Automatic opening NEVER carries over to another AA fingerprint. After an
  update, the installed build must be rescanned and the user must opt in again.
  The runtime still checks its pinned profile/fingerprint.
- The resolver now attempts **independent structural proofs** for A
  (delegated auxiliary) and B (direct Intent+options). Either proven route is
  sufficient for a build-scoped profile; no route always fails closed.
- When both are proven, retain the user's preferred route. When only A or
  only B is proven, select it automatically, persist that choice for the exact
  build, hide the missing route from selection and never reflect/invoke the
  missing route in the Android Auto runtime.
- A-only recovery can use a uniquely proven auxiliary delegate and separate
  navigation observer even if the direct route's log anchor is gone. If the
  underlying delegate itself vanishes and no safe replacement is provable,
  neither the route nor automatic opening is invented.
- Automatic opening still needs a unique projected-activity trigger, verified
  navigation observer and explicit build-scoped consent. A proven route alone
  permits manual operation but does not bypass those independent safety checks.
- This is **build-level fallback**, not a second runtime attempt after a
  launch fails. Each connection still has a single launch budget.
- Route A/B choice, recheck, disarm and manual compatibility tools are now
  grouped in a collapsed "Advanced options and tests" section.
- Legacy labels in Media Startup screens, confirmations and offline inspection
  summaries no longer mention individual preflights.

## Diagnostics

- Remove the obsolete VTC trace panel, DOUBLE getter activity panel, and their
  Show/Hide developer tools container from Diagnostics.
- Suppress the retired DOUBLE probe card inside experiment details as well.
- On manager profile refresh, disable an obsolete persisted VTC trace and clear
  a prior DOUBLE probe selection before publication, so hidden controls cannot
  leave unwanted hooks armed. Other getter-probe states remain untouched.
- This removes the UI and disarms the named retired diagnostics; it does NOT
  delete unrelated runtime or experiment flags.

## Verification

- New pure JVM tests gate the automatic enablement policy: known build with
  unique tracker, one proven route and a navigation observer succeeds;
  unreviewed/unsupported/ambiguous, blank or missing roles fail closed.
- Route selection tests cover A+B, A-only, B-only, stale preferences and
  absence of both routes. Resolver tests cover loss of the B anchor while
  the typed auxiliary A survives; parser tests cover separately proven A/B
  profiles and reject zero-route profiles.
- Existing resolver regression tests confirm absence or ambiguity of route A
  does not silently fall back to route B.
- Public gates: `:app:testDebugUnitTest`, `:app:lintRelease`,
  `:app:assembleDebug` and compile-only `:app:assembleDebugAndroidTest`.
- Not yet tested physically: legacy VTC/DOUBLE cleanup after upgrading from a
  build with probes armed, single-tap automatic setup, disconnected LSPosed,
  failed profile publication, unchanged build recheck, and Android Auto update.
- Check Media Startup on a parked head unit before considering beta6 RC.

## Second PF2 slice: Status, backups, navigation and accessibility

- The Status runtime banner no longer says "no active overrides" without
  qualification when a separate Media Startup runtime feature is configured.
  It reports the media profile independently from flag override counts.
- A single pure presentation helper provides installed-route summaries
  (A+B, A-only, B-only), selected route and published/unpublished states.
  The Settings summary no longer claims both A/B routes are available for
  single-route profiles.
- The complete recovery JSON remains **override-only**: normal, Advanced,
  Experimental and stored baselines. Clarify explicitly that it does not
  export Media Startup consent/routes, the catalog cache, theme, LSPosed setup,
  or Android Auto preferences. No backup schema changes were made.
- Tabs reset the previously open Settings subpage on tab switches; tapping
  the selected Settings tab preserves its current screen. Nested Back remains
  unchanged. Pure navigation tests cover both directions.
- Expanding Backup legacy options and Danger zone now provides an explicit
  accessible Expanded/Collapsed state.
- JVM regression tests cover single-route status, unpublished media profiles,
  legacy-only state and Settings tab reentry.

## Remaining PF2 checks before RC

- Confirm UI behavior with the signed beta6 debug/release candidate on
  phone: status with zero flag overrides + armed media, A-only/B-only
  Settings summary, theme/light-dark, large text, screen rotation and
  TalkBack on expandable Backup/Danger cards.
- Run instrumented SQLite tests and beta6 private 256 MiB fixtures; public
  CI only compiles instrumented tests.
- Validate Media Startup consent and selected player on a **parked** head unit.
  A successful invocation alone is not evidence the player opened visually.
