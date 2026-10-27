# Maintainer handoff — private fixture runner (WSL/Linux)

**Preparation guide only; do not use this procedure to publish a
release.** The repository is public and proprietary Android Auto APKMs
and signing secrets must never be uploaded or committed.

## Preconditions

- GitHub self-hosted runner for this repository is online on a trusted
  Linux/WSL host and bears **all** labels:
  `self-hosted`, `linux`, `x64`, `aa-private-fixtures`.
- JDK 17, Android SDK platform 36/build-tools 35.0.0 and Gradle
  8.9 are supported by the release workflow setup.
- Repository Actions variable `AAXP_PRIVATE_FIXTURES_DIR` points to an
  existing private directory on that host containing the expected
  APKM fixture files. This is a *path*, not an APK upload.
- Source repository supplies
  `fixtures/compatibility-17.8.663814.json`; frozen APKMs remain
  outside Git. `fixtures/SHA256SUMS` defines exact expected paths
  and hashes. The workflow copies the private fixtures into
  `fixtures/apks/` and checks the whole manifest with `sha256sum -c`.
- The runner must have enough local resources for the constrained
  256 MiB test workers; the memory limit refers to JVM test-worker
  heaps, not the complete Linux/WSL memory allocation.

## Safe invocation (no tag, merge or publication)

1. On the computer, confirm the runner is online in the GitHub
   repository's **Settings → Actions → Runners** and the labels match.
   If the runner is normally launched manually, start it through its
   already-configured local runner installation; do not re-register
   or expose registration tokens unnecessarily.
2. In the repository open
   **Actions → Private release gates → Run workflow**.
3. Select `feature/beta4-getter-probe-preflight`; enable the boolean
   `run_private_gates` option.
4. Confirm the workflow's `private-release-gates` job was **run**
   and completed successfully, not skipped or stuck in the queue.
5. Review the fixture integrity verification, **three 256 MiB gates**
   and **frozen 17.8 full-corpus gate** individually.
6. Report the workflow run URL and pass/fail job outcomes. Never
   attach or upload the private APKM fixtures, signing keys or
   unredacted diagnostic logs to the public repository.

## What this does not prove

- A successful run on the feature branch **does not create a GitHub
  Release or a signed beta4 artifact**. It does not test the final
  tagged release job or its upgrade-certificate comparison.
- A successful private run does not exercise a destructive full
  recovery restore on isolated app preferences.
- A valid signed preflight18 device upgrade does not establish the
  signing digest of a *future* final beta4 APK. The tag workflow has
  a separate certificate comparison against the official beta3 APK.
- A runner that is offline or missing the required label will leave
  the private job **queued**; it does not mean the tests passed.
- This handoff is independent of the user-facing Android app. Avoid
  installing a new preflight solely to test a GitHub release workflow.

The final decision to freeze RC, merge or push `v1.0.0-beta4`
remains with the maintainer after reviewing the outstanding gates.
