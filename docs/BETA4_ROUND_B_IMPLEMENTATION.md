# Beta4 — Round B UI and accessibility implementation

Development version: `1.0.0-beta4-preflight15` (`versionCode 161`)
Branch: `feature/beta4-getter-probe-preflight`.
**No merge, RC tag, release or change to the runtime override engine.**

## Changed screens and flows

### Experiments list

- Title and item counts are arranged vertically to avoid clipping on
  narrower displays and at larger font scales.
- Modifiers, state colors and selected-state labels use the active
  Material3 scheme, respecting dark Lab Green and Material You.
- Global success/failure messages are shown through one Scaffold
  SnackbarHost when a *new* feedback message arrives. Existing success
  messages no longer occupy permanent space above the flag list.
  Advanced editor feedback is shown temporarily for five seconds instead
  of replaying earlier status strings when revisiting a flag.
- FilterToggle now uses one TalkBack switch row and a 48dp minimum
  touch target rather than competing Row/Switch click nodes.

### Experiment details

- `Configured value` describes the selected manager-side override
  or compiled fallback; it is not presented as runtime telemetry.
- `Runtime verification: Check LSPosed logs` accurately reflects
  the lack of an API 101/102 reverse observability channel.
- Shared `OverrideInputFeedback` validates INT/LONG/FLOAT/DOUBLE,
  disallows NaN/Infinity/overflow and provides inline error text.
- Number/decimal software keyboards are suggested for scalar numeric
  values; pasting signed values remains possible.
- Empty `STRING` remains valid and a deliberate override, distinct
  from selecting Default.
- Normal editor still uses the existing Apply success animation,
  focus release and keyboard dismissal.

### Advanced editor and Diagnostics

- Advanced BOOLEAN with unknown compiled default starts without a
  FALSE selection and cannot reach Review until True/False is chosen.
- LONG/DOUBLE Advanced input validation is inline, but still does not
  claim knowledge of a safe application-specific domain. All Advanced
  saves still require both review and typed confirmation.
- Diagnostics still exposes the Advanced opt-in card directly.
  Read-only Video Timing traces and historical DOUBLE probe cards
  are grouped in optional `Developer tools`, automatically
  revealed when a diagnostic is armed.
- Existing active probes and VTC settings remain unchanged.
  Preflight labels are moved out of the user-facing probe title.

## Regression tests added

`OverrideInputFeedbackTest`: signed numeric boundaries,
non-finite values, explicit BOOLEAN selection, and empty STRING.

## QA checklist before accepting Round B

1. Run `:app:testDebugUnitTest`, `:app:lintRelease`, and a signed
   preflight artifact build with the repository workflow.
2. Verify the Settings/About version shows preflight15.
3. Test Experiments list at 320dp, 360dp and 440dp widths and
   font scaling 1.0x, 1.3x and 1.5x; ensure count text wraps below
   heading without overlapping cards.
4. Inspect dark Lab Green and Material You palettes for readability.
   Check status badges and selected values in the BOOLEAN editor.
5. Try blank LONG, overflowing INT, DOUBLE `NaN`/`Infinity`,
   valid `1.67` and empty STRING; invalid inputs must disable Apply.
   Confirm the Advanced BOOLEAN with no known default cannot Review
   without a selection.
6. Check TalkBack reads the filter toggle as a single actionable switch
   and that all major controls have practical touch targets.
7. Open Diagnostics: Advanced is visible, Developer tools collapsed
   when inactive, and armed VTC/probes remain discoverable.
8. A routine Open Headunit session is optional for Round B; no
   hook implementation was modified. No per-flag functional A/B
   exercise is needed.

## Remains beyond Round B

- Streaming all-discoveries export and broader backup/recovery decisions
  belong to Round C.
- Reducing MainActivity/ViewModel size and running device-level
  UI/Compose tests can be addressed incrementally.
- Release workflow/notes and RC gating remain a separate step.

## Visual inspection follow-up — preflight16

Sixteen user-provided screenshots taken 23:36–23:42 on 2026-10-23
were reviewed from the preflight15 build. Both Lab Green
light and dark appearances were exercised with real installed
Android Auto catalog data, 100 normal overrides and an existing
Advanced `AceFeature__mode` record. The screenshots are private
test evidence, not shipped repository assets.

**Confirmed visually:**
- Experiments count is below the heading rather than colliding with it:
  `900 shown · 100 normal modified · 30 Advanced · 3 suspended`.
- `Configured value`, the LSPosed runtime-verification note,
  TRUE/FALSE/Default selection and Advanced inline editor display.
- Light/dark Lab Green cards, buttons and modified-state labels.
- Advanced opt-in is a primary Diagnostics card; VTC trace and DOUBLE
  probes are grouped below Developer tools when the trace is armed.
- Recovery semantics in Settings remain explicit about normal-only backup.

**Two defects identified and fixed in the next development revision:**
- Dark Experiment details back-arrow inherited an effectively black
  content color because this page is rendered outside the root
  Scaffold. The icon now explicitly uses onBackground.
- The Developer tools button could not collapse the tool cards while
  a VTC/probe was armed because the visibility condition always
  overrode the toggle. An armed diagnostic now auto-expands when
  armed, but the user can collapse or reopen it.

**Not proven by screenshots:** inline invalid-number errors,
Snackbar life-cycle, Advanced two-stage typed confirmation, 320dp
small-device layout, nondefault font scaling, TalkBack traversal
and absence of overlap in landscape. These remain appropriate
small UX acceptance tests, not requirements for per-flag runtime
functional verification.
