# Contributing

Use JDK 17, Android SDK 36 and Gradle 8.9. Changes must keep production resolution generic: never
hardcode obfuscated Android Auto names or promote a test oracle into runtime mapping logic.

Resolver changes require positive and negative regressions, the regular suite, relevant 256 MiB
gate and full-corpus gate when distributions can change. Precision is preferred over recall;
ambiguous evidence must remain suspended. Do not commit proprietary Android Auto APKs to a public
fork. Pull requests should explain the evidence, memory impact and compatibility behavior.

## Licensing of contributions

Unless explicitly agreed otherwise in writing, contributions submitted for inclusion in AA
Experiments are accepted under the project's `GPL-3.0-or-later` license. Contributors retain
copyright in their own contributions.

Do not remove applicable third-party copyright or license notices. New dependencies or copied or
adapted third-party code must have a GPLv3-compatible license and must be documented in
`THIRD_PARTY_NOTICES.md` when applicable.

AI-assisted contributions are permitted, but the contributor remains responsible for reviewing,
testing, and licensing the submitted material and for avoiding incorporation of code whose
provenance or license cannot be established.
