#!/usr/bin/env bash
# Beta6 RC preflight: run ONLY the 3 isolated SQLite Android instrumentation
# tests. Bypass Gradle UTP host installer incompatibility with Kotlin stdlib.
set -euo pipefail

gradle --no-daemon --max-workers=1 :app:assembleDebug :app:assembleDebugAndroidTest
adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

adb shell am instrument -w \
  -e class io.github.highwindbr.aaxp.discovery.CatalogDatabaseInstrumentedTest \
  io.github.highwindbr.aaxp.test/androidx.test.runner.AndroidJUnitRunner \
  | tee beta6-sqlite-instrumentation.log

# adb shell may return zero even when Android instrumentation reports failures.
# Require the exact expected number of executed tests and no fatal markers.
grep -Eq '^OK \(3 tests\)' beta6-sqlite-instrumentation.log
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed' beta6-sqlite-instrumentation.log; then
  exit 1
fi
