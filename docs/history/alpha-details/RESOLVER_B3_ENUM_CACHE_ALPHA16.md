# Resolver B3 — enums, cache and activation, alpha16

## Enum safety

- Java enum literals, Protobuf-backed values and numeric storage with enum semantics remain separate concepts.
- Numeric `__mode` settings retain their physical `INT`/`LONG` ABI and are marked `ENUM_BACKED_INT` or `ENUM_BACKED_LONG`.
- A resolved getter does not imply a resolved enum domain. Such entries expose `enumMapping=RESOLVED` and `enumDomain=UNRESOLVED`, remain read-only and report `ENUM_DOMAIN_UNRESOLVED`.
- The real `AceFeature__mode` oracle verifies this behavior. The manager never offers an unrestricted long field as a substitute for an unknown enum domain.

## Cache data flow

- Added compact invoke-result provenance while DEX instructions are available.
- Provenance follows register moves, wrapper/interface calls, selected scalar combining operations and transformations before `iput`/`sput`.
- Instruction objects are discarded after each method. Only compact cache evidence is retained.
- Cache lookup runs after interface/wrapper resolution and uses the bounded consumer worklist rather than fixed obfuscated names.

The catalog now reports:

- `LIVE`
- `INSTANCE_CACHE`
- `STATIC_CACHE`
- `MIXED`
- `UNKNOWN`

## Activation scope

Detected paths are translated conservatively:

- live read -> `LIVE`
- instance cache -> `NEXT_INSTANCE`
- static cache -> `NEXT_CLASS_LOAD` and `NEXT_PROCESS`
- multiple paths -> `MIXED`

The details dialog shows consumption paths, activation scopes, cache fields and intermediate transformations. These are diagnostic statements, not automatic process restarts.

## Real 17.8.663814 oracles

- `Media__autoplay_paused_after_buffering_retry_delay_ms`: static cache path recovered through generated wrappers.
- `FrameRateRestrictions__thermal_headroom_throttling_enabled`: instance cache path recovered.
- `Messaging__remove_sms_stream_item_path` and `Messaging__remove_im_stream_item_path`: live plus instance-derived cache paths, classified `MIXED`.
- `AceFeature__mode`: numeric mapping resolved, enum domain unresolved, editing disabled.

Obfuscated class, method and field names are outcomes asserted by reference tests only; they are not the production resolution mechanism.

## Deliberate boundary

This version does not yet follow a cached field into a second downstream static startup cache or runtime latch. Consequently, the Messaging regression proves the live and instance-derived portions, while the later `STATIC_STARTUP` and lifecycle latch lineage remain for the next graph layer. It also does not infer valid enum members without concrete comparison/schema evidence.

## Icon

- Replaced the white-disc asset with the user-supplied transparent PNG.
- The source asset has transparent outer pixels and is packaged directly as the launcher icon.

## Validation

- All 45 unit and fixture tests passed.
- The complete installed 17.8.663814 scan passed with a separately constrained 256 MiB test heap.
- `assembleDebug` passed for version 115 / `1.0.0-alpha16`.
- APK Signature Scheme v2 verified.
- APK SHA-256: `11250cc295f2fd8bb9d51d563216219970a53c6257017732bca511ae7119f60e`.
