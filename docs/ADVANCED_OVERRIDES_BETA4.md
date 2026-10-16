# Advanced Overrides Mode (beta4 preflight5)

This beta4 work is an opt-in capability **inside Open Full Catalog**, not
an upgrade of the default Experiments editor. It does not treat all catalog
identifiers as writable flags.

## Unlock and use

1. Open **Settings → Diagnostics → Full Catalog**.
2. Tap the **Full Catalog** heading seven times. Read the explicit danger
   warning before unlocking. The extra controls are hidden until unlocked.
3. Search for a Fenotype flag and open **View evidence**. Under **Advanced
   Overrides**, inspect the eligibility diagnosis, then **Open advanced
   editor**. For a blocked symbol, the UI instead explains why it is read-only.
4. Edit a **BOOLEAN**, **LONG**, **DOUBLE**, or **STRING** value; no type
   changes are permitted. LONG is signed 64-bit, DOUBLE must be finite.
   Unknown numeric enum domains may accept raw LONG *only* if a unique typed
   getter has been proven, and the value remains explicitly unverified.
5. Confirm twice: review the key/default/type/confidence and finally type
   `APPLY`. The manager atomically saves the original override or original
   absence before writing any experimental value.
6. Fully restart Android Auto after publishing. Observe **LSPosed module
   logs** for `ADVANCED_HOOK_INSTALLED`, `ADVANCED_GETTER_INVOKED`,
   `ADVANCED_OVERRIDE_RETURNED` or `ADVANCED_FALLBACK`. Hook success
   does not guarantee a visible feature change.

The opt-in editor does **not** support unknown getter types, ambiguous
mappings, standalone enum literals, route states, protobuf objects, methods
with parameters, or safety-policy blocks. Known normally editable flags use
the ordinary Experiments screen.

## Persistence, recovery and build changes

Records reside separately under `advanced.v1.*` in LSPosed remote
preferences, scoped to an exact build fingerprint, method/ABI, and opcode
fingerprint. Records include the original serialized normal override and
name (or explicit absence), value, and timestamp.

- **Pause / Resume:** toggles one current-build record. Resume revalidates
  the method before publication.
- **Pause all Advanced:** pauses all current-build records atomically.
- **Restore baseline / Restore all baselines:** restores previous normal
  override bytes or removes the override when it had been absent.
- A different Android Auto build **suspends all old records**. They stay
  visible in the saved count, but are never automatically published or
  converted into a default. Reapplying across builds requires a dedicated
  reviewed migration path; this preflight intentionally fails closed.
- Existing beta4-preflight PhoneTheme BOOLEAN snapshots remain in their
  legacy storage location and retain their original restoration action.
  This experimental legacy path is deliberately not migrated automatically.

Regular backup/import does not silently include or reactivate unsafe records.
The manager rejects normal backup import while advanced baselines are retained,
to avoid overwriting their recorded previous state. A module reinstall,
data wipe, or framework change may make experimental backups unrecoverable.

## Technical boundaries

The runtime only hooks class/method/descriptors emitted by the scanner for
the exact installed AA build, requires a matching typed record, and calls the
original getter if validation fails. Logged STRING values are redacted.
The legacy normal profiles and the original beta3 runtime path remain
unchanged. This preflight is not a tagged release; install only an APK signed
with the existing release key after public CI succeeds and the manually
triggered signed-build workflow completes.
