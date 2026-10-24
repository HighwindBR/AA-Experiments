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
