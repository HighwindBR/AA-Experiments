# Release

Beta publication requires the regular suite, release lint, three isolated 256 MiB gates, frozen
full-corpus gate, physical smoke test and a tag. GitHub restores the configured experimental
release key, builds `assembleRelease`, verifies APK Signature Scheme v2, publishes the APK and a
SHA-256 sidecar.

The beta1 version remains frozen until all automated and physical gates pass. Public releases use
the repository's configured GitHub signing secret; loss or replacement of that key breaks normal
upgrade continuity and must be disclosed.
