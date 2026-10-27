# LSPosed official catalog submission

The AA Experiments **public source repository** stays
`HighwindBR/AA-Experiments`. The LSPosed catalog is a separate
distribution repository, created and administered by the
`Xposed-Modules-Repo` organization.

## 1. Request the catalog repository

Open https://modules.lsposed.org/submission/ (official submission form)
and choose **submission**. Submit the package
`io.github.highwindbr.aaxp` while signed in as **HighwindBR**.

The upstream issue title must be exactly:

```text
[submission] io.github.highwindbr.aaxp
```

Suggested brief description:

```text
AA Experiments is an open-source modern libxposed API 101 module and
Android manager for inspecting and configuring Android Auto experiment
flags. Source: https://github.com/HighwindBR/AA-Experiments
Official signed beta4: https://github.com/HighwindBR/AA-Experiments/releases/tag/v1.0.0-beta4
Application ID: io.github.highwindbr.aaxp
```

The bot validates the `io.github.highwindbr.` application ID prefix
against the GitHub account, creates
`Xposed-Modules-Repo/io.github.highwindbr.aaxp`, and sends the
maintainer an invitation. If approval/invitation needs manual attention,
use the upstream issue thread.

The GitHub connector used during preparation cannot create issues in
the external LSPosed organization (HTTP 403). A signed-in maintainer
must submit via the GitHub website; source repository permissions alone
do not grant rights in `Xposed-Modules-Repo/submission`.

## 2. Populate the newly created catalog repository

After accepting the GitHub invitation, in the new **catalog**
repository (not the main source repository):

- Set its **Description** to `AA Experiments` using the GitHub web
  interface's **About** panel. Upstream warns this cannot reliably
  be changed through their API permissions.
- Set the **Website** to
  `https://github.com/HighwindBR/AA-Experiments`.
- Copy `distribution/lsposed/SUMMARY` as the root `SUMMARY` and
  `distribution/lsposed/README.md` as the root `README.md`.
- Do not copy the Android application source into the catalog repo.
  Source is maintained at the main GitHub repository.

## 3. Create the catalog's first release

Use GitHub Releases **in the new catalog repository** with:

- Release tag: `169-1.0.0-beta4`
- Release title: `1.0.0-beta4`
- Release body: copy
  `distribution/lsposed/RELEASE_NOTES_169-1.0.0-beta4.md`
- APK asset: the **unmodified official signed**
  `AAExperiments-1.0.0-beta4.apk` copied from
  https://github.com/HighwindBR/AA-Experiments/releases/tag/v1.0.0-beta4

Verify the original signed APK SHA-256 before uploading:
`c8e5566ab50c681bbb88f216be48f8725a6d03bdce3abe845535faf58f56bf43`.

**Do not reuse the original source repo's tag name `v1.0.0-beta4`
in the catalog repo**: the catalog specifies
`[versionCode]-[versionName]` = `169-1.0.0-beta4`.

## 4. Verify listing

Wait for the upstream catalog index refresh (upstream documentation:
approximately 5 to 60 minutes after a complete module repository
and release exist). Check
https://modules.lsposed.org/module/io.github.highwindbr.aaxp/.

If it has not appeared after the normal refresh window, inspect
description, root SUMMARY/README, release and APK asset first,
then contact upstream via the issue. An approved issue alone does
**not** mean the module is already visible to users.

LSPosed upstream instructions:
https://github.com/Xposed-Modules-Repo/submission.
