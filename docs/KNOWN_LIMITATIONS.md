# Known limitations

- **Beta6:** long DEX scans now run alongside a foreground data-sync service holding a **partial CPU wakelock** (bounded to two hours) to help analyses continue when the display turns off or the user opens another app. The screen itself stays off; the ongoing notification returns to AA Experiments. This can increase battery usage during heavy analysis. A wake lock does **not** make the ViewModel-owned coroutine durable: task removal, user force-stop, reboot, OS process termination, foreground-service time limits or vendor battery restrictions can still interrupt work. Results already committed, including streamed deep-resolve records, remain preserved. If the two-hour cap expires, CPU wakefulness is no longer guaranteed.
- Static proof does not guarantee that a visible feature will activate. Server eligibility, account state, negotiated capabilities, SDK/hardware gates, head-unit support and remote services can still prevent an effect.
- LSPosed API 101 has no reverse telemetry channel. Runtime confirmation still requires `PROFILE_ACCEPTED`, `HOOK_INSTALLED` and `OVERRIDE_RETURNED` in LSPosed logs.
- Automatic crash-loop attribution is not implemented. Hooks fail open, profiles are tied to the complete installed-build fingerprint, and incompatible overrides are suspended before publication.
- Deep value-flow analysis intentionally suspends ambiguous, shared-infrastructure and unsupported object/callback paths instead of guessing.
- Numeric enums and Protobuf messages remain read-only until their complete accepted domain or schema is proven.
- Resources are catalogued for diagnostics but are not directly editable through the modern API 101 runtime path.
- A force-stop, reboot or OS process termination interrupts active work; catalog writes and directed results already committed remain preserved.
- Release signing requires the CI secrets documented in the workflow. Debug builds use an automatically generated disposable debug key.

- The two-slot publication and shared refresh coordinator received signed-device LSPosed smoke testing in the beta4 preflight14 sequence. Deliberate destructive interruption between remote file writes, read-back and pointer commit has **not** been reproduced on the user's configured device; tests cover injected failures at the storage interface.

- Preflight15–16 visual checks covered light and dark themes, enlarged text and display zoom on the Galaxy S24+. The dark-theme navigation arrow and Diagnostics developer-tools collapse were checked again on the device. **Landscape, TalkBack and other screen-reader behavior** have not been fully certified.

- A complete recovery bundle contains raw normal/Advanced/Experimental values (including STRING content); it is not encrypted or authenticated. Store it securely and do not import untrusted JSON. Full recovery requires an exact matching Android Auto build; it is not a cross-version migration tool.
- Streaming full-catalog export is bounded in memory but may still take time on slower devices or document providers. If the provider fails, incomplete destination files must be discarded. A cancel/progress UI for very long exports remains future work.

- The reviewed parked-mode media timing flags are not parking-state
  detectors, but their safe numeric domains and downstream vehicle
  behavior remain unproven. Preflight20 requires typed elevated
  acknowledgement in the normal editor, not just Advanced.
  Do not experiment with these values during active projection or
  while driving.

- **Beta5 automatic Media startup** is opt-in and tied to the exact
  Android Auto build. The RC1 was reported functional during a parked
  vehicle test on 2026-10-30, but every A/B route, alternate media app,
  Android Auto revision and head unit has not been independently certified.
  Android Auto's own controller can reject the opening request even when
  hooks and selected-component checks succeed. The feature never starts
  audio playback or bypasses vehicle restrictions. If a build-specific
  first-activity role cannot be proven, automatic opening is disabled.
  Legacy PF4 remains Spotify-only and pinned to a previously reviewed
  build; it is separate from the dynamically resolved route.

- The About screen states **LSPosed runtime API 101** (the module's
  supported API), while Status may report **API 102** from the
  connected framework service. These describe different sides of the
  integration, not necessarily a service-version mismatch. Verify
  actual `PROFILE_ACCEPTED` and hook events to assess compatibility.
