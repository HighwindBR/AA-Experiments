# Building

Requirements: JDK 17, Android SDK platform 36/build-tools 35.0.0 and Gradle 8.9.

```text
gradle --no-daemon --max-workers=1 :app:testDebugUnitTest :app:assembleDebug
```

Public source bundles omit caches, toolchains, keystores, build outputs and proprietary APKMs.
Debug uses AGP's disposable key or optional `AAXP_DEBUG_*` variables. Release uses
`AAXP_RELEASE_STORE_FILE`, `AAXP_RELEASE_STORE_PASSWORD`, `AAXP_RELEASE_KEY_ALIAS` and
`AAXP_RELEASE_KEY_PASSWORD`; GitHub restores those values from repository secrets.
