#!/usr/bin/env bash
# Beta6 RC preflight: run ONLY the 3 isolated SQLite Android instrumentation
# tests. Bypass Gradle UTP host installer incompatibility with Kotlin stdlib.
set -euo pipefail

gradle --no-daemon --max-workers=1 :app:assembleDebug :app:assembleDebugAndroidTest
adb wait-for-device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# Capture startup exceptions even when the instrumentation process crashes
# before JUnit can produce XML. Collect only the isolated emulator logs.
adb logcat -c || true
set +e
adb shell am instrument -w \
  -e class io.github.highwindbr.aaxp.discovery.CatalogDatabaseInstrumentedTest \
  io.github.highwindbr.aaxp.test/androidx.test.runner.AndroidJUnitRunner \
  | tee beta6-sqlite-instrumentation.log
instrument_status=${PIPESTATUS[0]}
set -e

# Record emulator crash diagnostics for the CI artifact and log tail.
adb logcat -d -b crash -t 250 > beta6-emulator-crash.log 2>&1 || true
adb logcat -d -t 700 > beta6-emulator-logcat.log 2>&1 || true
if ! grep -Eq '^OK \(3 tests\)' beta6-sqlite-instrumentation.log ||
   grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed' beta6-sqlite-instrumentation.log ||
   [ "$instrument_status" -ne 0 ]; then
  echo "Android instrumentation failed, see emulator crash log below."
  tail -n 120 beta6-emulator-crash.log || true
  grep -E -A 14 'FATAL EXCEPTION|AndroidRuntime|Process: io.github.highwindbr.aaxp|ClassNotFoundException|SQLiteException|AssertionError' beta6-emulator-logcat.log | tail -n 100 || true
  exit 1
fi
