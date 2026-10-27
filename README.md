# AA Experiments

AA Experiments is an independent Android manager and LSPosed API 101 module for researching
Android Auto configuration experiments. It scans installed or imported APK/APKM/APKS packages,
proves compatible scalar getters, and publishes only active overrides for the exact installed
base-and-split build fingerprint. It never patches, resigns, installs or writes into Android Auto.

Nothing is overridden by default. Ambiguous, sensitive, unsupported and schema-unknown entries
remain searchable but read-only. An editable experiment is technically hookable; it is not a
promise that every value is safe or that a server/head unit will enable the feature.

AA Experiments was designed to be resilient to Android Auto updates. Originally developed against
Android Auto 17.8, testing of the completed module later showed that its discovery mechanism can
identify flags in Android Auto versions dating back to 2.0 (2016). This is encouraging evidence
that the module may keep discovering flags in future releases, potentially remaining useful for
years without developer updates. Discovery alone does not guarantee that every flag can be safely
resolved or overridden as Android Auto internals evolve.

## Requirements

- Android 8.1 or newer
- LSPosed with modern API 101 and Android Auto in module scope
- JDK 17, Android SDK platform 36/build-tools 35.0.0 and Gradle 8.9 for local source builds

A normal source build does **not** require any proprietary Android Auto APKM fixture. Build and
forking instructions, including signing and optional fixture-dependent tests, are documented in
[Development](docs/DEVELOPMENT.md).

The regular local build command is:

```text
gradle --no-daemon --max-workers=1 :app:testDebugUnitTest :app:assembleDebug
```

## Privacy and development transparency

AA Experiments performs its scans and analysis locally. It has no developer-operated backend,
analytics, advertising, tracking, or OpenAI runtime integration, and the app itself does not send
scan results, experiment values, Android Auto metadata, LSPosed information, or device data to the
developer or to OpenAI.

OpenAI Codex was used as a programming and code-analysis assistant during development. Codex is not
part of the distributed application. AI-assisted changes were reviewed before inclusion, while
physical-device, LSPosed/Android Auto, and vehicle head-unit testing were performed manually by the
developer. See [Privacy](PRIVACY.md) for the precise scope of these statements.

## Beta4

Beta4 adds typed Advanced Overrides for eligible getters behind explicit
consent, optional read-only diagnostics, build-scoped profile recovery
and bounded JSON/CSV catalog exports. The separate full-recovery bundle
includes normal, Advanced and Experimental records and saved baselines;
it is unencrypted and restores only to the exact matching installed
Android Auto build. See [Known limitations](docs/KNOWN_LIMITATIONS.md).

The beta4 identity is `1.0.0-beta4` (versionCode 169).
The maintainer accepted the signed final candidate. Download
published versions only from the [official GitHub Releases](https://github.com/HighwindBR/AA-Experiments/releases);
development preflight APKs and Actions artifacts are not official releases. A valid getter override does not guarantee
a downstream vehicle, server or head-unit feature is enabled.

## Documentation

For a practical overview of the breadth of Android Auto behavior that can be customized by modifying experiment flags, see the [**Feature Catalog**](FEATURE_CATALOG.md).

- [Architecture](docs/ARCHITECTURE.md)
- [Development, building, testing and forking](docs/DEVELOPMENT.md)
- [Advanced Overrides mode](docs/ADVANCED_OVERRIDES_BETA4.md)
- [Release process](docs/RELEASE.md)
- [Private fixture runner handoff](docs/PRIVATE_RUNNER_CHECKLIST.md)
- [Known limitations](docs/KNOWN_LIMITATIONS.md)
- [Privacy](PRIVACY.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)
- [Alpha history](docs/history/ALPHA_HISTORY.md)

Use only while parked and disconnected from active projection. If Android Auto becomes unstable,
restore all overrides in the Manager or disable the module in LSPosed. This project is not
affiliated with Google, Android Auto or LSPosed.

## Roadmap

### Beta hardening

- Physical lifecycle, live-listener and rapid snapshot-swap validation.
- Compatibility reports across Android Auto, ROM, device and LSPosed versions.
- Explicit recovery documentation and release-key/fixture operations.

### Later

- Canonical logical identities for imported splits.
- Structurally proven safety decisions.
- Numeric enum domains and schema-backed Protobuf editing.
- Object/callback/IPC provenance and negotiated snapshots.
- Reverse observability or crash attribution only when a reliable channel exists.

## License

Copyright (C) 2026 HighwindBR and contributors.

AA Experiments is free software licensed under the **GNU General Public License, version 3 or (at
your option) any later version** (`GPL-3.0-or-later`). See [LICENSE](LICENSE).

Third-party components remain under their respective licenses; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

`SPDX-License-Identifier: GPL-3.0-or-later`
