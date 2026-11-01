# LSPosed catalog maintenance

The source repository is [HighwindBR/AA-Experiments](https://github.com/HighwindBR/AA-Experiments).
Distribution uses the separate
[Xposed-Modules-Repo/io.github.highwindbr.aaxp](https://github.com/Xposed-Modules-Repo/io.github.highwindbr.aaxp)
repository, which already exists. Do not submit a duplicate repository request.

As checked on 2026-10-31, the catalog includes
[178-1.0.0-beta5](https://github.com/Xposed-Modules-Repo/io.github.highwindbr.aaxp/releases/tag/178-1.0.0-beta5).
That existing release is historical evidence, not authorization to publish a new candidate.

## Update descriptive files

After maintainer approval, copy the source templates
[SUMMARY](../distribution/lsposed/SUMMARY) and
[README](../distribution/lsposed/README.md) to the catalog repository.
They must describe the version actually being distributed; do not advertise
unreleased branch behavior as published functionality.

The catalog repository's description should be `AA Experiments`, and its
website should point to the source repository. Follow upstream instructions
for metadata changes. Application source stays in the source repository.

Editing templates here does not synchronize the external catalog.

## Publish an approved version

1. Start with an already approved, official signed APK and its source release.
2. Verify the APK against the source release's SHA-256 checksum. Do not rebuild
   or resign it for catalog distribution.
3. Read versionName and versionCode from the verified APK.
4. Use catalog tag `[versionCode]-[versionName]` and release title
   `[versionName]`. For example, the existing beta5 uses
   `178-1.0.0-beta5`; the source tag is `v1.0.0-beta5`.
5. Use that version's own release notes and attach the verified APK when
   creating the release. Do not reuse the stored beta4 notes for a newer version.
6. Verify the catalog listing after its index refresh and check the downloadable
   APK digest against the source artifact.

The retained [beta4 catalog notes](../distribution/lsposed/RELEASE_NOTES_169-1.0.0-beta4.md)
document the first catalog release. They are not the current release template.

## Upstream requirements

See [LSPosed submission and repository format](https://github.com/Xposed-Modules-Repo/submission).
Upstream documents the tag format, SUMMARY/README metadata and indexing delay.
It also warns that replacing only an APK asset does not necessarily update the
indexed tag; submit a properly tagged release with its APK.

Check the [module listing](https://modules.lsposed.org/module/io.github.highwindbr.aaxp/)
after updating. A repository or release existing on GitHub does not by itself
prove the catalog index has refreshed.
