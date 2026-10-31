# Private fixture runner handoff

Use this guide for maintainer-controlled regression runs. Proprietary Android
Auto archives and signing secrets stay on trusted infrastructure.

## Preconditions

- A trusted Linux/WSL runner has all labels: `self-hosted`, `linux`, `x64`,
  `aa-private-fixtures`.
- JDK 17, Android SDK platform 36/build-tools 35.0.0 and Gradle 8.9 are available.
- The repository Actions variable `AAXP_PRIVATE_FIXTURES_DIR` names an existing
  private directory on that host containing the frozen APKMs.
- [SHA256SUMS](../fixtures/SHA256SUMS) defines the required fixture names and
  hashes. The workflow stages them under `fixtures/apks/` and verifies the
  complete manifest before testing.
- The host has enough resources for Gradle and the test workers. The 256 MiB
  budget applies to each selected test JVM, not the entire host.
- Only reviewed, maintainer-controlled source may run with private fixtures or
  signing material. Do not enable this runner for untrusted PR code.

## Select the exact source

First check the ref allowlist in
[private-release.yml](../.github/workflows/private-release.yml).
Its current branches are listed in [Release process](RELEASE.md).

**The Beta6 and Group A branches are not currently allowed.** Selecting
`feature/beta6-reliability` or `maintenance/group-a-stable` in a manual
dispatch will not execute the private-fixture job. A reviewed workflow update
is required to test those candidates through this workflow. Testing `main`
or an older beta branch does not establish candidate coverage.

## Run an allowed candidate's gates

1. Confirm the runner is online and has the labels above.
2. Open **Actions → Private release gates → Run workflow**.
3. Select the reviewed candidate ref and enable `run_private_gates`.
   Leave `build_signed_candidate` and `deliver_private_candidate` off
   when only regression checks are intended.
4. Confirm `private-release-gates` actually ran. Queued, skipped and cancelled
   jobs are not passing evidence.
5. Review fixture hash verification, the three 256 MiB tests and the frozen
   full-corpus result individually. Confirm the tests were not skipped.
6. Record the run URL, source SHA and outcomes. Do not upload fixtures,
   signing keys or private device logs.

These checks do not create a signed candidate, certify a phone upgrade,
exercise real LSPosed recovery, or prove head-unit behavior. Signed candidate
delivery and release approval are separate steps in [Release process](RELEASE.md).
