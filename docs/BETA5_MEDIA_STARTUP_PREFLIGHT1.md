# Beta5 Media Startup: preflight1 read-only investigation

This preflight records two observed internal Android Auto 18.1 methods. It does not launch Spotify or other apps, force the head unit to display Android Auto, alter navigation, play audio, or change vehicle settings.

## Inspected build

- Android Auto 18.1.164344-release.daily base APK SHA-256: f3e90ea898469991785dbf91500e1d50db4c637e0e67483065ee0813e3b59c73
- classes.dex: Lnzf;->m(Landroid/content/ComponentName;Lnzb;Z)V — navigation/startup routing invocation.
- classes2.dex: Lmad;->b(Landroid/content/Intent;)Lqz; — media intent target resolution invocation.
- Both hooks are opt-in, check the exact base APK SHA and method signatures, and call the original code without changing parameters or results.
- Events do not expose package names, intent contents, account identifiers or media metadata. Logging is capped at 48 events per method per process.
- Neither event proves UI visibility, a media application launch, or the physical head-unit screen being ready.

## Test procedure — only after signed preflight passes gates

1. Disconnect projection and park. Open Settings > Diagnostics > Developer tools > Media Startup observation and arm the trace.
2. Restart Android Auto to install the opt-in hooks, then inspect the LSPosed module logs.
3. In the Volkswagen, connect and allow the head unit to open Android Auto automatically. Note approximately when audio, splash and the visible UI appear.
4. In the Chevrolet, connect while the native HU screen remains visible; note Spotify playback. Then tap the Android Auto icon and note splash and visible UI.
5. Compare timing for MEDIA_STARTUP_TRACE_INSTALLED, MEDIA_STARTUP_NAV_ROUTE_INVOKED and MEDIA_STARTUP_MEDIA_ROUTE_INVOKED. The script records no personal data or media content.
6. Disable the trace and restart Android Auto afterward.

## Preflight2 gate

Observe manual Spotify opening in each vehicle, find a verifiable actual projected UI readiness transition and safe media launch controller. Do not conflate playback, connection, startup nav callback, and host-screen readiness. A missing or incompatible trace fails open.

Keep the beta4 public release and signing certificate unchanged. This diagnostic is intentionally excluded from full recovery backups, consistent with other developer probes.
