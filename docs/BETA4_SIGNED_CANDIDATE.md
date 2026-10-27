# Beta4 — signed device candidate, without publication

**Scope:** manually triggered, locally retained and non-publishing test of
`1.0.0-beta4-preflight22` (`versionCode=168`). This is **not RC1** and
does not prepare the official `1.0.0-beta4` release tag.
The APK remains locally on WSL; an **opt-in** delivery to a separate
private repository is also supported for downloading on the phone.

## Before building

1. Make and safely retain a **complete recovery bundle** exported from
   the installed signed preflight21; store a second copy off
   the phone. Do not clear the active profile.
2. Do not uninstall the current manager. Its active normal, Advanced
   and Experimental records must remain untouched.
3. Start the existing trusted self-hosted WSL runner from its configured
   installation (`cd ~/actions-runner && ./run.sh`).

## Build and verification

In **Actions → Private release gates → Run workflow** select
`feature/beta4-getter-probe-preflight`, turn **build_signed_candidate**
on **and** enable **run_private_gates**. The candidate job waits until
all private 256 MiB and frozen full-corpus gates pass. The new optional input is defined
on this feature branch; do not select `main` or a release tag. If the
web UI does not expose the new input, use the GitHub CLI on an already
authenticated workstation:

```bash
gh workflow run private-release.yml --repo HighwindBR/AA-Experiments \
  --ref feature/beta4-getter-probe-preflight \
  -f run_private_gates=true -f build_signed_candidate=true
```

The job `signed-development-candidate` must run **on the trusted WSL
runner** after unit, lint and private-fixture gates have all passed. It:

- reconstructs the release keystore from existing GitHub Secrets in a
  temporary file, runs `assembleRelease` and deletes that file on exit;
- verifies the APK v2 signature, exact applicationId
  `io.github.highwindbr.aaxp`, `versionCode=168`, and
  `versionName=1.0.0-beta4-preflight22`;
- downloads the public official beta3 APK, checks its pinned SHA-256,
  and requires the **same signing certificate SHA-256 digest**;
- keeps the candidate **on the local WSL filesystem** by default;
- optionally uploads the verified APK and SHA-256 to an **unpublished
  draft in a SEPARATE PRIVATE repo** only when specifically requested.
  There is no public artifact, official AA-Experiments release/tag,
  or merge.

Output (permissions: private to the WSL runner account):

```text
~/aaxp-private-candidates/AAExperiments-1.0.0-beta4-preflight22-signed.apk
~/aaxp-private-candidates/AAExperiments-1.0.0-beta4-preflight22-signed.apk.sha256
```

To verify manually in WSL:

```bash
cd ~/aaxp-private-candidates
sha256sum -c AAExperiments-1.0.0-beta4-preflight22-signed.apk.sha256
```

Copy the verified APK to the phone via a trusted local connection,
or configure **private delivery** below. Do not share the signed APK
as a public Actions artifact. Do not use the `app-debug.apk` from
public CI: it cannot upgrade the release-signed manager.

## Phone download through a separate private repository (one-time setup)

This option does **not** use the public repository's Actions artifacts
and does **not** create any draft, tag or release on `AA-Experiments`.

1. While signed into your GitHub account, create a new repository,
   for example `HighwindBR/aaxp-private-candidates`, with
   **Private** visibility. Initialize it with a README so it has a
   default branch. Do not repurpose unrelated private repositories.
2. Under GitHub Settings → Developer settings → Personal access tokens
   → Fine-grained tokens, create a **short-lived** token restricted to
   **only that private repository**. Set Repository permissions →
   **Contents: Read and write**; do not grant organization-wide or
   workflow access. Never paste this token in chat, issues, files,
   job output or code.
3. In the PUBLIC source repository `HighwindBR/AA-Experiments`,
   open Settings → Secrets and variables → Actions.
   Add **New repository secret**:
   `AAXP_PRIVATE_DELIVERY_TOKEN` = that token.
   Under **Variables**, create:
   `AAXP_PRIVATE_DELIVERY_REPO` =
   `HighwindBR/aaxp-private-candidates`.
4. On [Private release gates](https://github.com/HighwindBR/AA-Experiments/actions/workflows/private-release.yml),
   select `feature/beta4-getter-probe-preflight` and choose:
   `run_private_gates=true`,
   `build_signed_candidate=true`, and
   `deliver_private_candidate=true`.
   The WSL private runner must be **online**. All public checks,
   three 256 MiB gates, and the frozen 17.8 corpus test must pass
   before any signed APK is delivered.
5. In the **private** repository, open **Releases** while signed in
   with write access. Select the most recent **Draft** named
   `AA Experiments 1.0.0-beta4-preflight22 · private build ...`,
   then download the APK and the accompanying `.sha256` directly
   on Android. Never click **Publish release** on that draft.
   Its filename and digest must match the signed candidate.
   If the mobile app hides drafts, use the logged-in GitHub website
   in a browser.

The private-delivery script refuses a public or unexpected destination,
an APK version mismatch, or a checksum mismatch. It first checks the
private repository's actual GitHub visibility, then creates the
**unpublished** draft, uploads only the signed APK and checksum, and
rechecks draft status. An interrupted upload attempts to delete the
incomplete draft. No release signing key, fixture or private token is
included in the downloadable files.

If `deliver_private_candidate` is **false** (default), no network
delivery is attempted: the candidate remains only on WSL.

CLI alternative after the private destination is configured:

```bash
gh workflow run private-release.yml --repo HighwindBR/AA-Experiments \
  --ref feature/beta4-getter-probe-preflight \
  -f run_private_gates=true \
  -f build_signed_candidate=true \
  -f deliver_private_candidate=true
```


## Safe device smoke test (parked, no active projection)

1. Install the verified APK **as an update** over preflight21.
   Never uninstall, clear app data, or choose a reset option.
2. Open About: verify **preflight22**, application connected to LSPosed
   and the expected Android Auto build. Verify existing overrides remain
   present (last reference: 100 active normal, three suspended,
   one Advanced and one Experimental; actual counts may legitimately
   change if the user edited records later).
3. Confirm a dynamic profile is published and monitor LSPosed
   `PROFILE_ACCEPTED` and representative hook return events after
   starting a new Android Auto process.
4. Open `SynchronizedMediaFeature__enable_sync_parked_exp` in
   Experiments. It may remain tagged ADVANCED in an older saved catalog.
   Selecting True or False must enable **Review Advanced value**.
   Open the first confirmation and **Cancel**; never apply an unverified
   parked timing value. Final review still requires `PARKED APPLY`.
5. Verify Advanced-tagged entries open the current Experiment details
   screen, can display state and baseline and do not take the obsolete
   editor route. Avoid editing active records during the smoke test.
6. Inspect the **Analyzing Android Auto** dialog during analysis:
   the DEX filename and current/total share one compact row; Saving
   catalog has no empty detail slot; stage track has no detached dot.
   After completion, check Android Auto identity and preserved overrides.
7. Inspect **Settings → Backup & recovery**. Complete backup actions
   are prominent, normal-only legacy actions and Danger zone start
   collapsed, and full restore still requires the existing confirmation.
   Avoid repeating a destructive restore on the live profile.
7. If the updated manager behaves incorrectly, collect diagnostic logs
   and avoid repeated uninstall/reinstall. Do not attempt a versionCode
   downgrade without a planned, data-preserving recovery strategy.

## Still open for beta4 RC

Normal and complete write-side restore were reported successful by
the maintainer, but isolated disposable-storage verification was not
independently documented. Freeze final identity and verify the official
signed `1.0.0-beta4` candidate before publication. This preflight22
candidate is not an RC or public release.
