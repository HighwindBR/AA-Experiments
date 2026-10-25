# Beta4 — signed device candidate, without publication

**Scope:** manually triggered, locally retained and non-publishing test of
`1.0.0-beta4-preflight21` (`versionCode=167`). This is **not RC1** and
does not prepare the official `1.0.0-beta4` release tag.

## Before building

1. Make and safely retain a **complete recovery bundle** exported from
   the current signed preflight18 installation; store a second copy off
   the phone. Do not select Restore or Restore All.
2. Do not uninstall the current manager. Its active normal, Advanced
   and Experimental records must remain untouched.
3. Start the existing trusted self-hosted WSL runner from its configured
   installation (`cd ~/actions-runner && ./run.sh`).

## Build and verification

In **Actions → Private release gates → Run workflow** select
`feature/beta4-getter-probe-preflight`, turn **build_signed_candidate**
on and keep **run_private_gates** off. The new optional input is defined
on this feature branch; do not select `main` or a release tag. If the
web UI does not expose the new input, use the GitHub CLI on an already
authenticated workstation:

```bash
gh workflow run private-release.yml --repo HighwindBR/AA-Experiments \
  --ref feature/beta4-getter-probe-preflight \
  -f run_private_gates=false -f build_signed_candidate=true
```

The job `signed-development-candidate` must run **on the trusted WSL
runner** after its ordinary unit and lint prerequisites pass. It:

- reconstructs the release keystore from existing GitHub Secrets in a
  temporary file, runs `assembleRelease` and deletes that file on exit;
- verifies the APK v2 signature, exact applicationId
  `io.github.highwindbr.aaxp`, `versionCode=167`, and
  `versionName=1.0.0-beta4-preflight21`;
- downloads the public official beta3 APK, checks its pinned SHA-256,
  and requires the **same signing certificate SHA-256 digest**;
- keeps the candidate **only on the local WSL filesystem** (no
  `upload-artifact`, GitHub Release, tag or merge).

Output (permissions: private to the WSL runner account):

```text
~/aaxp-private-candidates/AAExperiments-1.0.0-beta4-preflight21-signed.apk
~/aaxp-private-candidates/AAExperiments-1.0.0-beta4-preflight21-signed.apk.sha256
```

To verify manually in WSL:

```bash
cd ~/aaxp-private-candidates
sha256sum -c AAExperiments-1.0.0-beta4-preflight21-signed.apk.sha256
```

Copy the verified APK to the phone via a trusted local connection.
Do not share it as a public Actions artifact. Do not use the
`app-debug.apk` published by public CI: its signing key is disposable
and cannot upgrade the release-signed manager.

## Safe device smoke test (parked, no active projection)

1. Install the verified APK **as an update** over preflight18.
   Never uninstall, clear app data, or choose a reset option.
2. Open About: verify **preflight21**, application connected to LSPosed
   and the expected Android Auto build. Verify existing overrides remain
   present (last reference: 100 active normal, three suspended,
   one Advanced and one Experimental; actual counts may legitimately
   change if the user edited records later).
3. Confirm a dynamic profile is published and monitor LSPosed
   `PROFILE_ACCEPTED` and representative hook return events after
   starting a new Android Auto process.
4. Inspect a reviewed `parked_exp` flag under normal Experiments:
   it must show **ELEVATED RISK**, and a non-default edit must request
   typing `PARKED APPLY`. **Cancel without applying** any experimental
   media timing value. Verify Default remains a one-step recovery action
   without actually resetting a modified value.
5. Verify Advanced-tagged entries open the current Experiment details
   screen, can display state and baseline and do not take the obsolete
   editor route. Avoid editing active records during the smoke test.
6. Open **Restore full recovery bundle**, select the saved JSON and
   check only the preview. **Cancel**, never complete the destructive
   restore on live preferences.
7. If the updated manager behaves incorrectly, collect diagnostic logs
   and avoid repeated uninstall/reinstall. Do not attempt a versionCode
   downgrade without a planned, data-preserving recovery strategy.

## Still open for beta4 RC

A full **write-side** restore must be tested in an isolated disposable
environment. Final release identity must be frozen, and the official
signed `1.0.0-beta4` candidate must be tested for upgrade and signing
continuity. This development APK does not satisfy those gates alone.
