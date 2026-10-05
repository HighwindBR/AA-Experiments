# Privacy

AA Experiments is designed to work locally on the user's Android device.

## Data collection and transmission

AA Experiments itself does **not**:

- operate a developer-controlled backend;
- collect analytics or usage telemetry;
- include advertising or tracking SDKs;
- include a crash-reporting service;
- upload imported APK/APKM/APKS files, Android Auto metadata, scan results, experiment values,
  LSPosed information, device identifiers, or diagnostics to the developer;
- include an OpenAI SDK or runtime integration; or
- send user data to OpenAI.

The current application manifest does not request the Android `INTERNET` permission. Scanning,
cataloguing, compatibility analysis, resolver work, profile generation, and override management are
performed locally by AA Experiments.

Android itself may handle application backups according to the user's device and backup settings.
That operating-system behavior is outside AA Experiments' own data collection and transmission
logic.

## AI-assisted development

OpenAI Codex was used during development as a programming and code-analysis assistant, including
implementation, refactoring, documentation, and automated-test work. Codex is **not** included in
the distributed application and has no runtime access to installations of AA Experiments.

AI-assisted changes were reviewed before inclusion. Physical-device testing, LSPosed/Android Auto
validation, and vehicle head-unit testing were performed manually by the developer. Automated
tests and regression gates supplement, rather than replace, that manual validation.

## Scope

This statement describes AA Experiments itself. Android, Android Auto, LSPosed, the device vendor,
and other software or services installed on the same device may have their own data practices and
privacy policies.
