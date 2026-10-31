# Beta5 final — evidence and publication checklist

## Fixed source and metadata

- Frozen validated RC1 source: `e8286335b8261d4e8e249049b27324b27a989578`.
- Public RC1 CI: [unit/lint/debug workflow](https://github.com/HighwindBR/AA-Experiments/actions/runs/41063913701)
  and [independent debug build](https://github.com/HighwindBR/AA-Experiments/actions/runs/41065632029),
  both reported successful at the time of release preparation.
- Maintainer confirmed RC1 worked in a parked vehicle on 2026-10-30.
  This does **not** establish individual A/B, alternate-player or
  cross-build acceptance beyond what was explicitly reported.
- Final source: `release/1.0.0-beta5`; application ID unchanged,
  `versionName=1.0.0-beta5`, `versionCode=178`.
- Compared with RC1, the final branch must change only app version
  metadata, signing/release workflow guardrails, documentation and
  offline delivery tests. Do not alter the already-validated media hooks.

## Required before tagging

- [ ] Final-branch public unit tests, `:app:lintRelease`,
  `:app:assembleDebug` and offline Python guard tests pass.
- [ ] Exact SHA-256-verified fixture staging, three 256 MiB regression
  gates, and frozen 17.8 full corpus gate run and pass, not silently skip.
- [ ] Private signed `1.0.0-beta5` candidate built from the final source,
  with APK Signature Scheme v2, exact code/name and certificate continuity
  against the SHA-256-pinned official beta4.
- [ ] Final candidate upgrade (beta4 / RC1 → beta5) and preservation
  of configured data checked on the user's device. The final
  versionCode is larger than the RC1's to permit the upgrade.
- [ ] Final notes and known limitations reviewed; any reduced
  per-app/version coverage is explicitly documented.
- [ ] Maintainer expressly authorizes the merge and final
  `v1.0.0-beta5` tag/public GitHub Release.

## Publication

Only after sign-off, merge/approve the exact final source commit to
`main`, then create the exact `v1.0.0-beta5` tag. The tag workflow
must independently pass tests, private corpus gates and signing checks
before publishing `AAExperiments-1.0.0-beta5.apk` and its SHA-256
sidecar. Do not tag merely to start testing; the tag is a publication
trigger.

Do not make a public Actions artifact from a release-signed candidate.
If private signed delivery is required, use the manual guarded workflow,
which permits an unpublished draft only in a separate PRIVATE repo.
Keep release signing secrets outside the source repository. There is no
need to include LICENSE or THIRD_PARTY_NOTICES as separate release
assets.

Historical RC1 matrix: [BETA5_RC1.md](BETA5_RC1.md).
