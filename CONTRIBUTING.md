# Contributing

Use JDK 17, Android SDK 36 and Gradle 8.9. Changes must keep production resolution generic: never
hardcode obfuscated Android Auto names or promote a test oracle into runtime mapping logic.

Resolver changes require positive and negative regressions, the regular suite, relevant 256 MiB
gate and full-corpus gate when distributions can change. Precision is preferred over recall;
ambiguous evidence must remain suspended. Do not commit proprietary Android Auto APKs to a public
fork. Pull requests should explain the evidence, memory impact and compatibility behavior.
