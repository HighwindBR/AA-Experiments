# Beta4 — Visual QA from user screenshots

**Test build:** `1.0.0-beta4-preflight15`
**Capture time:** 2026-10-23 23:36–23:42 (device local)
**Evidence:** 16 JPEG images within
`Screenshot_20261023_AA Experiments.zip` supplied in the project
conversation. Screenshots themselves are not uploaded to GitHub.

## Screen coverage

| Screen / state | Dark mode | Light mode | Observation |
| --- | --- | --- | --- |
| Status | Yes | Yes | AA 18.0, LSPosed API 102, 100 normal overrides, 3 suspended; later 1 Advanced active |
| Experiments list | Yes | Yes | 900 shown; heading and counts no longer overlap |
| Boolean detail (TRUE) | Yes | Yes | Value and selected TRUE visible; configured vs runtime wording correct |
| Boolean detail (Default/FALSE) | Yes | Yes | Three-state selector and configured values readable |
| Advanced LONG detail | Yes | Yes | `AceFeature__mode` 0 active, single detail/editor layout |
| Settings | Yes | Yes | Lab Green / Material You selector; backup exclusions explicit |
| Diagnostics | Yes | Yes | Advanced section prominent; VTC armed and DOUBLE read-only cards below Developer tools |

## Confirmed defects

### QA-01 — Dark header back arrow lacks contrast

Screenshots 03–06: the back arrow in Experiment details is almost
black over dark `#101510`, though the title is light.
Root cause: ExperimentDetailScreen renders outside Scaffold and
Icon used the inherited content color. The next branch commit sets
`tint = MaterialTheme.colorScheme.onBackground`.

### QA-02 — Armed Developer tools cannot be collapsed

Screenshots 08 and 16: button shows
`Developer tools · diagnostic active` while VTC card is expanded.
Source previously rendered children whenever a trace was armed,
regardless of the button's Boolean state; tapping the button
could not hide them. A new state effect reveals armed diagnostics
by default, but child rendering now follows the user's toggle.

## Verified and not verified

**Passed visually:** responsive count placement at the captured
device width; both theme palettes; selected Boolean value colors;
Advanced integrated details; honest Configured/Runtime labels;
reorganized Diagnostics; explicit backup scope.

**Still needs focused interaction testing:** invalid INT overflow,
DOUBLE NaN, empty STRING and signed LONG feedback; Snackbar duration;
Advanced double confirmation; Developer tools hide/reopen while
VTC remains armed; back-arrow contrast after installing the newer
build; font scaling 1.3x/1.5x; TalkBack.

**No changes** to the 100 normal overrides, suspended mappings,
Advanced values or the AA runtime profile were requested or performed
by this screenshot QA.

## Follow-up accessibility and editor screenshots — 2026-10-24

The user supplied another private archive, `Screenshot_AAXP.zip`,
containing **13 screenshots**. Two explicitly vary the device's Samsung
font/zoom settings against the usual **font 3/9, zoom 1/5**:

- `Screenshot_biggerfont.jpg`: font **6 or 7/9**, zoom **1/5**.
  Experiments heading, counts, search, flag names, state chips and
  bottom navigation remain legible. Long identifiers wrap within
  their cards without visible overlap/clipping.
- `Screenshot_with_zoom.jpg`: font **3/9**, zoom **4/5**.
  The same Experiments components fit the viewport, and the summary
  wraps to a second line without intersecting cards. Fewer rows are
  visible by design at this scale.

An external floating picture-in-picture window partially obscures
the lower-right viewport in these screenshots; that overlap should
not be attributed to the app.

The other screenshots provide additional **visual** acceptance evidence:

- Normal DOUBLE editor: blank input, `abc`, `NaN`, and `Infinity`
  all display inline error feedback with Apply disabled.
- A valid `1.67` leaves Apply enabled, with no error feedback.
- Advanced LONG `AceFeature__mode`: first review dialog, second
  typed-`APPLY` dialog, and the enabled final control after typing
  `APPLY` are visible.

**Scope limitation:** screenshot evidence does not prove the result of
tapping final Apply, restoring the baseline, Snackbar expiration,
TalkBack, or expanded-font rendering on the Advanced/Settings screens.
Nor do these screenshots alone independently establish the installed
package version, because no About/version screen was captured.

**Result:** visual scaling for the Experiments list **passed at both
tested alternative settings**; normal DOUBLE inline validation and
Advanced double-confirmation appearance **passed**. No source change
was necessary based on this follow-up.
