# Compatibility

Catalog identity covers Android Auto versionCode, the complete base APK and every split APK. A
profile is rejected when any artifact changes. Compatible saved overrides remain active; missing,
ambiguous or type-changed mappings stay stored but are suspended and excluded from publication.

Rediscovery compares the same stable key against the previous build. Unique literal mappings are
preserved, structural candidates are globally matched one-to-one, ambiguity is suspended and
removed keys are never rebound to unrelated keys.

Imported APKM/APKS files are read-only. Their current synthetic split filenames can make equivalent
archives with different ZIP ordering receive different fingerprints; this conservative false
incompatibility does not affect the installed runtime profile.
