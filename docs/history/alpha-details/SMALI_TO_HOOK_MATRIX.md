# Legacy Smali-to-hook matrix — Android Auto 17.8.163744 daily

> Historical evidence from the fixed daily profile. These obfuscated names are not used as durable runtime targets by `alpha02`; the dynamic inventory resolves each stable key again for the analyzed APK hash.

Base SHA-256: `2390c782ff6024d092640fe9f2c1dfab669be56717e4647e38e04c04286d7a5a`.

| Stable control | Original evidence / getter(s) | API 101 result |
|---|---|---|
| `aax_light_dark_theme` | `Coolwalk__use_light_dark_theme` → `acqj.z()Z` | Boolean interceptor |
| `aax_phone_primary_color` | `Coolwalk__use_phone_primary_color` → `acqj.B()Z` | Boolean interceptor |
| `aax_hero_layout` / `aax_hero_vertical` / `aax_hero_media_ui` | `actg.j/k/s()Z` | Three independent controls |
| `aax_dynamic_font` | `acwi.e()Z` | Boolean interceptor |
| `aax_force_message_readout` | `acuu.j/k/l()Z` | Three getters; kill-switch result inverted |
| `aax_quick_settings` | `acyh.aE()Z` | Boolean interceptor |
| `aax_earth_widgets` | `acpu.g/h/i/j/k()Z` | Five getters grouped; UI effect unconfirmed |
| `aax_parked_video_apps` | `acqm.j()Z` | Local allow gate only; motion policy unchanged |
| notification/actions/suggestion signals | `acqj.q/n/u/v()Z` | Four boolean interceptors |
| `aax_gemini_personalization` | `acnk.u()Z` | Local getter only |
| `aax_template_miniplayer_overlay` | `acpi.G()Z` | Boolean interceptor |
| `aax_vertical_rail_widgets` | `acyh.bd/be()Z` | Two getters grouped |

Catalogued but deliberately disabled: alarm actions; Android-16/video head-unit decision gates; Gemini negotiated `kitt_enabled`/`kitt_eligible` protobuf fields; three Cielo decision returns; Extra Dim; settings defaults; typed miniplayer placement/alignment. Their old Smali behavior is documented, but no conservative API 101 adapter has been validated for this base.

The generated profile contains 519 unique literal-bearing boolean getter candidates. This count is static scanner output, not 519 safe hooks.
