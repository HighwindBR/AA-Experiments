# AA Experiments beta4 — Override mechanism coverage audit

Audit date: 2026-10-23
Scope: `feature/beta4-getter-probe-preflight`, API 101 module.
This is a **mechanism audit**, not a claim that all 900+ individual flags
have independently verified downstream effects.

## Evidence levels

- **S**: Code and source-level policy reviewed (not proof of running behavior).
- **U**: Unit tests exist in the repository (the new Gradle suite needs execution).
- **R**: Getter invocation / typed return confirmed by an Android Auto LSPosed log.
- **F**: Downstream function visibly changed in a controlled A/B test.
- **—**: Not demonstrated in this audit.

Only a finite set of representative **R** validations is needed per
mechanism; **F** is never a mandatory test for all flags.

## Normal scalar overrides

| Type / Java ABI | Storage and validation | Code-level runtime | Tests in repo | On-device evidence |
| --- | --- | --- | --- | --- |
| BOOLEAN `()Z` | `BOOLEAN:true/false` (strict) | Supported (S) | U | No new R check in this round |
| INT `()I/()B/()S/()C` | `INT:` (signed 32-bit; narrowed values range-checked) | Supported (S) | U | — |
| LONG `()J` | `LONG:` (signed 64-bit) | Supported (S) | U; prior handoff diagnostics | — in this round |
| FLOAT `()F` | `FLOAT:` (finite) | Supported (S) | U | — |
| DOUBLE `()D` | `DOUBLE:` (finite) | Supported (S) | U | **R**: SystemUI 1.67 returned in `:projection` |
| STRING `()Ljava/lang/String;` | `STRING:` including empty; not absent | Supported (S); log contents redacted | U | — |

Codec unit tests cover normal serialization, cross-type rejection, and
finite-range validation; `OverrideCompatibilityTest` covers ABI and
narrowed INT range. The normal Java codec independently rejects malformed
BOOLEAN, non-finite FLOAT/DOUBLE and out-of-range char values even if
the manager's validation is bypassed.

## Other mechanisms

| Mechanism | Supported types | Publication and runtime behavior | Validation |
| --- | --- | --- | --- |
| Advanced Overrides | BOOLEAN, LONG, DOUBLE, STRING | Exact getter, signature, fingerprint, current AA build, active record; fail-open on invalid snapshot | S/U; no universal R claim |
| Experimental BOOLEAN preflight | A single allowlisted BOOLEAN key | Separate snapshot and profile array; pause/restore preserves original presence/absence | S/U |
| LONG 3→3 handoff | One reviewed LONG getter | Switches hook ownership, preserves normal `LONG:3` preference | S/U; previous targeted logs |
| Read-only getter probes | Exact allowlisted BOOLEAN/LONG/DOUBLE targets | Logs installation and invocation without changing return | **R** for all three AA 18.0 DOUBLE targets |
| VideoTiming protocol diagnostic | Reviewed protocol classes on matching AA base | Read-only constructor/method trace, no overrides | S/U |

Advanced mode deliberately does not admit INT/FLOAT today. Enabling the
Advanced card controls presentation/access; it does not itself pause
previously saved Advanced hooks.

## Publication, restore, build compatibility

- Each active normal override has a typed remote preference. The manager
  recomputes mappings against the **installed** AA catalog, then publishes
  the `schemaVersion=3` runtime profile.
- The loader validates the canonical SHA-256 package fingerprint, covering
  package version, base APK and split APK hashes, before installing hooks.
- A normal override with missing, incompatible, noneditable or ambiguous
  getter is retained as a **suspended** preference, not published.
- Multiple normal identifiers resolving to one class/method/descriptor now
  arbitrate deterministic single-target ownership; losing aliases are
  suspended rather than hooked simultaneously.
- Advanced snapshots retain prior normal override/name or their absence.
  Pause/Resume/Restore revalidate the recorded build and getter. The LONG
  handoff has stricter baseline-integrity checks.
- Ordinary Save/Default changes the stored preference. Already-installed
  normal hooks can observe a new remote preference snapshot; **adding or
  removing a method hook still requires restarting AA**. A failed profile
  publication must not be described as an applied new mapping.
- Backups currently export/import **normal active and suspended overrides**.
  Advanced and experimental baseline records are not included in that
  normal backup schema. Import is build-scoped and now rejects noneditable
  and ABI-incompatible records.
- A global reset intentionally removes normal, experimental and Advanced
  records rather than retaining Advanced baseline history. Use each
  Advanced Restore baseline operation if preserving an older normal value
  is desired.

## Audit changes

- `31b7274`: strict runtime scalar decoding, privacy redaction of STRING
  return logs, range and descriptor checks, build-current edit validation,
  import gate, alias collision suspension, regression tests.
- `d39e083`: all Advanced saves require both a review dialog and typed
  second acknowledgement (`APPLY` or `PARKED APPLY`), not only
  the higher-risk subset.
- Previous beta4 commits include DOUBLE compiled-default normalization
  and the on-device `HOOK_INSTALLED`/`OVERRIDE_RETURNED` proof.

## Tests performed versus pending

**Performed in isolated local JVM smoke harnesses (outside Android Gradle):**
21 Java scalar decoding assertions and 17 Kotlin compatibility/collision
assertions. Both passed. These tests exercise the corresponding algorithms,
but are **not equivalent to building or running the full app**.

**Still required before declaring the beta4 release ready:**

1. Execute `:app:testDebugUnitTest`, `:app:lintRelease`, and
   `:app:assembleRelease` with the configured signed-preflight CI path.
2. Regression-check imported normal backups, including noneditable keys,
   changed getter ABI, and the single-getter collision suspension display.
3. Check installed-app publication and recovery after LSPosed service
   reconnection, as well as profile rejection after an AA build change.
4. Run a small **representative** runtime test for each *unverified
   mechanism/type*, not for each flag; no functional A/B requirement.
5. Review the profile file's truncate-and-write publication behavior:
   interruption mid-write can leave an incomplete JSON file; the loader
   rejects an invalid profile, but publication is not yet an atomic
   replace/rollback operation.
6. Recheck stale-build edits and race conditions during simultaneous
   installation scans/profile refreshes; no reproduction was established
   in this audit.

**No `main` merge, signed release publication, or Android package
version bump was performed by this audit.**
