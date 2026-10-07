# AA Experiments — Feature & Customization Catalog

This document is a practical catalog of Android Auto behaviors that **AA Experiments** can expose, tune, unlock, or redirect through internal experiment parameters.

It is based on reverse-engineering and consumer tracing performed against:

- **Android Auto 17.8.663814**
- **Android Auto 17.9.664004**

The goal is to document **what the app/module can meaningfully customize**, not to present every experiment key found in the APK. Only parameters whose behavior was sufficiently demonstrated were retained.

> [!WARNING]
> **AI-assisted reverse engineering:** ChatGPT was used extensively to analyze flag semantics, runtime consumers, dependencies, value mappings, and related code paths. Only part of the catalog has been manually validated on real Android Auto sessions; most entries have **not** been tested in practice. Treat the catalog as technically grounded research, not as a guarantee that every change will work on every phone, vehicle, head unit, Android Auto build, or configuration.

> [!TIP]
> **Change flags gradually.** Prefer enabling or modifying only **1–5 parameters at a time**, then test before continuing. This makes it much easier to identify and revert a flag that causes crashes, broken UI, failed projection, or other regressions.

> [!NOTE]
> Some changes are not picked up immediately. When a modification appears to have no effect, **force-stop Android Auto and start it again** before concluding that the flag is ineffective. A full reboot or reconnection may still be required for some lower-level paths.

> [!IMPORTANT]
> A flag being editable does **not** mean it works in isolation. Many features still depend on Android API level, the Google app/GSA version, Car App Library API level, projection protocol support, head-unit capabilities, display topology, app category, `CarInfo`, or another master gate.

> [!CAUTION]
> Low-level protocol, encoder, USB, thermal, and compatibility parameters can break projection, pairing, setup, or media behavior when misconfigured. Treat their defaults as part of the documented behavior, not as recommended tuning values.

> [!NOTE]
> This is a historical capability catalog. Google may rename, remove, hard-code, restructure, or later reintroduce these paths in subsequent Android Auto releases. Because of the catalog's size and the large number of flags that can be added, removed, changed, or reintroduced even between minor/point releases, the developer does **not** intend to keep this document continuously up to date.
>
> This document does **not** feed or control AA Experiments. An outdated catalog therefore does not make the app itself outdated: AA Experiments builds its working catalog from analysis of the DEX files in the installed Android Auto APK.

## How to read this catalog

### Relevance rating

- **★★★★★** — high-value customization, visible UI change, meaningful feature unlock, protocol/capability switch, or especially useful tuning point.
- **★★★★** — confirmed and useful, but narrower, more conditional, or more technical.

Lower-rated, dormant, dump-only, unresolved, or false-positive parameters are intentionally excluded.

### Version-state notation

The version columns intentionally show the **original build state only**. User-defined overrides applied through AA Experiments are excluded from this document.

- `false` / `true` — compiled Boolean default in that Android Auto build.
- `∅` — no formal compiled default was identified for that preference.
- `—` — the key was not present in that version.
- `17.8 & 17.9` — the parameter has the same original default/value in both analyzed builds.
- Numeric or string values — compiled/default value documented for that build, unless the row explicitly says that the value is an enum, recognized value, or configurable value whose default was not recorded.

### Kill-switch polarity

The `_kill_switch` suffix does **not** imply a universal TRUE/FALSE convention. For every retained kill switch, this catalog states the consumer-traced polarity when it was established. When the retained evidence does not safely establish both branches, the row is explicitly marked **polarity unknown** rather than inferring behavior from the parameter name.

### Scope

This catalog currently contains:

- **154 distinct customization capabilities**
- **262 approved parameters**
  - 172 Boolean
  - 8 String
  - 12 Double
  - 70 Long

## Contents

1. [UI, Layout & Visual Customization](#1-ui-layout-visual-customization)
2. [Media, Hero & Audio](#2-media-hero-audio)
3. [Apps, Cradle & Car App Library](#3-apps-cradle-car-app-library)
4. [Assistant, Gemini & Proactive Features](#4-assistant-gemini-proactive-features)
5. [Wireless Android Auto & Connectivity](#5-wireless-android-auto-connectivity)
6. [Vehicle, Head Unit & Multi-Display](#6-vehicle-head-unit-multi-display)
7. [Messaging & Calls](#7-messaging-calls)
8. [Safety, Input & Driving Restrictions](#8-safety-input-driving-restrictions)
9. [Projection, Video & Rendering](#9-projection-video-rendering)
10. [Setup, FRX, Bluetooth & USB](#10-setup-frx-bluetooth-usb)
11. [System, Performance & Low-Level Internals](#11-system-performance-low-level-internals)

---

<a id="1-ui-layout-visual-customization"></a>

<details>
<summary><strong>1. UI, Layout & Visual Customization</strong></summary>

The highest-priority area in the catalog: visible interface modes, layout classification, theming, rail behavior, wallpapers, display geometry, maps, fonts, and notification presentation.

## Earth / CAR.SYS — ★★★★★

Unlocks the Earth path, its dashboard/widget compositions, mini dashboard card, focus behavior, and widget limit.

**Requirements / hierarchy:** `earth_status == CAR.SYS`, Android API 37+, and compatible head-unit/display capabilities. The mini dashboard card depends on the Dashboard + Widget combination.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `CieloFeature__earth_enabled` | `false` | Master gate for Earth. |
| BOOLEAN | `CieloFeature__earth_dashboard_widget_combo_enabled` | `false` | Enables Earth Dashboard + Widget composition. |
| BOOLEAN | `CieloFeature__earth_mini_dashboard_card_enabled` | `false` | Enables the Earth mini dashboard card; depends on the combo above. |
| BOOLEAN | `CieloFeature__earth_focus_enabled` | `false` | Earth-specific focus/transition handling. |
| STRING | `CieloFeature__earth_status` | `default not recorded` | Selects the Earth/CAR.SYS path. Recognized value: `CAR.SYS`. |
| LONG | `CieloFeature__earth_tilt` | `6` | Despite the name, sets the maximum number of Earth widgets that can be added. |

**17.8 → 17.9:** stable.

## SystemUI display classification & layout thresholds — ★★★★★

Exposes the gates and thresholds Android Auto uses to classify the projected display and choose widescreen, semi-widescreen, portrait, narrow-portrait, rail, and map+dashboard layouts.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `SystemUi__narrow_portrait_enabled_kill_switch` | `true` | Gate for `PORTRAIT_NARROW`. Kill-switch polarity: **TRUE** enables/allows the `PORTRAIT_NARROW` category; **FALSE** does not enable that path. |
| BOOLEAN | `SystemUi__wide_portrait_as_widescreen_enabled_kill_switch` | `true` | Allows wide portrait displays to enter widescreen paths. Kill-switch polarity: **TRUE** allows wide-portrait displays to be treated as widescreen when the other layout criteria match; **FALSE** prevents this gate from allowing that path. |
| DOUBLE | `SystemUi__widescreen_aspect_ratio_breakpoint` | `1.67` | Widescreen aspect-ratio breakpoint. |
| DOUBLE | `SystemUi__widescreen_aspect_ratio_breakpoint_for_portrait` | `1.2` | Portrait-path aspect-ratio breakpoint. |
| LONG | `SystemUi__widescreen_breakpoint_dp` | `1240 dp` | Widescreen width breakpoint. |
| LONG | `SystemUi__semi_widescreen_breakpoint_dp` | `880 dp` | Semi-widescreen breakpoint. |
| LONG | `SystemUi__portrait_breakpoint_dp` | `900 dp` | Portrait-layout breakpoint. |
| LONG | `SystemUi__short_portrait_breakpoint_dp` | `680 dp` | Short-portrait breakpoint. |
| LONG | `SystemUi__narrow_portrait_width_threshold_dp` | `730 dp` | Narrow-portrait width threshold. |
| LONG | `SystemUi__horizontal_rail_canonical_breakpoint_dp` | `450 dp` | Canonical horizontal-rail breakpoint. |
| LONG | `SystemUi__full_maps_and_dashboard_width_threshold_dp` | `710 dp` | Threshold for full map + dashboard composition. |
| LONG | `SystemUi__wide_dashboard_min_map_height_dp` | `400 dp` | Minimum map height for wide-dashboard composition. |

**17.8 → 17.9:** stable.

## Coolwalk palette & wallpaper-derived theming — ★★★★★

Controls where the Coolwalk palette comes from and allows the base palette color to be overridden.

<table>
  <thead>
    <tr>
      <th>Type</th>
      <th>Parameter</th>
      <th>17.8</th>
      <th>17.9</th>
      <th>Role</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>BOOLEAN</td>
      <td><code>Coolwalk__neutralized_theme_kill_switch</code></td>
      <td colspan="2" align="center"><code>true</code></td>
      <td><code>true</code> keeps legacy primary-color theming; releasing it opens the wallpaper/seed-derived path. Kill-switch polarity: <strong>TRUE</strong> keeps the legacy primary-color theming path; <strong>FALSE</strong> releases the wallpaper/seed-derived palette path.</td>
    </tr>
    <tr>
      <td>BOOLEAN</td>
      <td><code>Coolwalk__use_phone_primary_color</code></td>
      <td><code>false</code></td>
      <td><code>—</code></td>
      <td>Historical 17.8 control that explicitly used the phone primary color.</td>
    </tr>
    <tr>
      <td>STRING</td>
      <td><code>Coolwalk__palette_base_color</code></td>
      <td colspan="2" align="center"><code>configurable; default not recorded</code></td>
      <td>Overrides the base color used to derive the Coolwalk palette. Recognized examples include <code>#4285F4</code>, <code>#FF4285F4</code>, <code>0x4285F4</code>, and <code>0xFF4285F4</code>.</td>
    </tr>
  </tbody>
</table>

**17.8 → 17.9:** restructured. `Coolwalk__use_phone_primary_color` disappears, while palette control remains through the other paths.

## Wallpaper backdrop — ★★★★★

Controls the wallpaper/backdrop behind the UI, the inset threshold used for eligibility, and historically a `CarInfo`-based denylist.

<table>
  <thead>
    <tr>
      <th>Type</th>
      <th>Parameter</th>
      <th>17.8</th>
      <th>17.9</th>
      <th>Role</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>BOOLEAN</td>
      <td><code>SystemUi__wallpaper_backdrop_enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Master gate.</td>
    </tr>
    <tr>
      <td>LONG</td>
      <td><code>SystemUi__wallpaper_backdrop_threshold</code></td>
      <td colspan="2" align="center"><code>20</code></td>
      <td>Inset/stable-area threshold; the raw unit is not proven by the inspected consumer.</td>
    </tr>
    <tr>
      <td>BOOLEAN</td>
      <td><code>SystemUi__use_denylist_to_prevent_wallpaper_backdrop_from_showing_kill_switch</code></td>
      <td><code>true</code></td>
      <td><code>—</code></td>
      <td>Historical <code>CarInfo</code>-based denylist gate. Kill-switch polarity: <strong>TRUE</strong> consults/applies the <code>CarInfo</code> denylist; <strong>FALSE</strong> ignores that denylist gate.</td>
    </tr>
  </tbody>
</table>

**17.8 → 17.9:** restructured; the configurable denylist disappears.

## Vertical rail widget — ★★★★★

Enables the vertical rail/taskbar widget and can broadly force-enable it on the main display.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `SystemUi__vertical_rail_widget_enabled` | `false` | Enables the rail widget in additional layouts, especially widescreen. |
| BOOLEAN | `SystemUi__vertical_rail_widget_enabled_all` | `false` | Broad force-enable for the main display. |

## Irregular-display Cielo scrim — ★★★★★

Controls Cielo scrim handling for stable-area cutouts and asymmetrical/irregular displays.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `SystemUi__cielo_scrim_with_stable_area_cutout_enabled` | `false` | Enables Cielo scrim handling for stable-area cutouts. |
| BOOLEAN | `SystemUi__cielo_scrim_with_stable_area_cutout_variable_scrim_enabled` | `false` | Dynamically varies the scrim according to content/dashboard geometry. |
| LONG | `SystemUi__irregular_display_scrim_inset_diff_threshold_dp` | `40 dp` | Per-side threshold for irregular-display scrim compensation. |

## Contextual map styling modes — ★★★★

Enables custom map styling modes exposed by the contextual styling path.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `ContextualStyling__enable_custom_hybrid` | `true` | Enables `ROADMAP_SATELLITE`. |
| BOOLEAN | `ContextualStyling__enable_custom_terrain_vector_client` | `true` | Enables `TERRAIN_VECTOR_CLIENT`. |
| BOOLEAN | `ContextualStyling__enable_custom_terrain_vector_client_dark` | `true` | Enables `TERRAIN_VECTOR_CLIENT_DARK`. |

## Hero cutout handling — ★★★★

Controls support and force-enable behavior for cutouts/display geometry in the Hero path. Requires Hero.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `HeroFeature__cutouts_enabled` | `true` | Master support for Hero cutout/display-geometry handling. |
| BOOLEAN | `HeroFeature__force_cutouts` | `false` | Forces cutout handling even when the HU would not normally trigger it. |

## Other UI & visual capabilities

<table>
  <thead>
    <tr>
      <th>Capability</th>
      <th>Parameter</th>
      <th>17.8</th>
      <th>17.9</th>
      <th>What it changes</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Quick Settings in Notification Center</td>
      <td>BOOLEAN — <code>SystemUi__quick_settings_enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Adds Theme, Battery Saver, DND, and Settings controls to Notification Center.</td>
    </tr>
    <tr>
      <td>Compose rail implementation</td>
      <td>BOOLEAN — <code>SystemUi__use_compose_rail</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Replaces the legacy rail/facet bar with the Compose implementation.</td>
    </tr>
    <tr>
      <td>Cielo system-theme mode</td>
      <td>STRING — <code>CieloFeature__cielo_status</code></td>
      <td colspan="2" align="center"><code>default not recorded</code></td>
      <td>Selects the Cielo visual path tied to the system theme. Recognized value: <code>system_theme</code>.</td>
    </tr>
    <tr>
      <td>Twin Smarts / Energy Shader notifications</td>
      <td>LONG — <code>SystemUi__twin_smarts_mode</code></td>
      <td colspan="2" align="center"><code>0</code></td>
      <td>Magic-number gate for the animated Energy Shader on <code>ProjectionNotification</code> cards. <code>13007</code> enables Twin Smarts; any other value keeps normal behavior.</td>
    </tr>
    <tr>
      <td>Edge-to-edge maps/apps</td>
      <td>BOOLEAN — <code>SystemUi__edge_to_edge_maps_enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Negotiates/renders eligible maps and apps edge-to-edge.</td>
    </tr>
    <tr>
      <td>Dynamic phone/manufacturer font family</td>
      <td>BOOLEAN — <code>PhoneThemeFeature__enable_dynamic_font_family</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Selects a manufacturer-specific font-family overlay; Samsung is present in the inspected default mapping.</td>
    </tr>
    <tr>
      <td>Core Maps shared labeler</td>
      <td>BOOLEAN — <code>CoreMaps__enable_shared_labeler</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Replaces the Legacy Labeler with the Shared Labeler in the Maps renderer.</td>
    </tr>
    <tr>
      <td>Hero theming API</td>
      <td>BOOLEAN — <code>HeroFeature__theming_enabled</code></td>
      <td colspan="2" align="center"><code>true</code></td>
      <td>Enables <code>HERO_THEMING</code> and the Hero theming API. Requires Hero.</td>
    </tr>
    <tr>
      <td>Hero punch-through / integrated overlay</td>
      <td>BOOLEAN — <code>HeroFeature__punch_through_enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Enables <code>HERO_PUNCH_THROUGH</code> and <code>IntegratedOverlayManager</code>. Requires Hero.</td>
    </tr>
    <tr>
      <td>Dashboard third-party notifications with actions</td>
      <td>BOOLEAN — <code>Coolwalk__dashboard_show_3p_notifications_with_actions_enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Allows third-party dashboard/HUN notifications with actions, subject to other filters.</td>
    </tr>
    <tr>
      <td>DND-driven notification suppression</td>
      <td>BOOLEAN — <code>SystemUi__dnd_suppress_notifications_enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Connects phone DND state to HUN/audio notification suppression.</td>
    </tr>
    <tr>
      <td>Navigation-window border</td>
      <td>LONG — <code>Coolwalk__nav_app_border_width</code></td>
      <td colspan="2" align="center"><code>0</code></td>
      <td>Controls the navigation projection-window border width; the raw unit is not proven to be dp.</td>
    </tr>
    <tr>
      <td>Notification Center bell visibility</td>
      <td>BOOLEAN — <code>SystemUi__display_bell_when_status_elements_hidden</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Keeps the Notification Center bell visible when other status elements are hidden.</td>
    </tr>
    <tr>
      <td>HUN action-button stacking</td>
      <td>BOOLEAN — <code>SystemUi__stack_hun_buttons_if_actions_contain_text_enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Stacks HUN actions when action labels contain text.</td>
    </tr>
    <tr>
      <td>Rail status-bar theme overlays</td>
      <td>BOOLEAN — <code>SystemUi__status_bar_theme_overlay_kill_switch</code></td>
      <td colspan="2" align="center"><code>true</code></td>
      <td>Controls Coolwalk light/dark overlays on the Rail Status Bar. Kill-switch polarity: <strong>TRUE</strong> applies the Coolwalk light/dark Rail Status Bar overlay path; <strong>FALSE</strong> uses the normal/non-overlay path.</td>
    </tr>
    <tr>
      <td>Independent night mode</td>
      <td>BOOLEAN — <code>IndependentNightModeFeature__enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Enables independent night mode for CarWindow/Projected Presentation.</td>
    </tr>
    <tr>
      <td>Satellite-aware network status</td>
      <td>BOOLEAN — <code>SystemUi__satellite_network_status</code></td>
      <td><code>false</code></td>
      <td><code>—</code></td>
      <td>Historical satellite-aware network-status provider for API 36+ and compatible hardware.</td>
    </tr>
  </tbody>
</table>

---

</details>

<a id="2-media-hero-audio"></a>

<details>
<summary><strong>2. Media, Hero & Audio</strong></summary>

High-impact media UI and playback capabilities come first; low-level buffering and synchronization controls are listed later in the section.

## Hero new media UI / Project Accordion — ★★★★★

The new Hero media UI is the base path; Project Accordion is the responsive/compact variant that reorganizes artwork, controls, and palette behavior.

**Hierarchy:** Project Accordion depends on the new media UI.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `HeroFeature__use_new_media_ui` | `false` | Master gate for the new media UI. |
| BOOLEAN | `SystemUi__project_accordion_enable` | `false` | Enables the Project Accordion responsive/compact media layout. |

## Hero core & layout selection — ★★★★★

Controls the Hero feature set and can force Hero layout selection/orientation, including Hero Large in the `CANONICAL` layout.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `HeroFeature__enabled` | `true` | Master gate for Hero capabilities. |
| BOOLEAN | `HeroFeature__force_hero_layout` | `false` | Forces Hero layout selection. May be incompatible with low-resolution HUDs (poor scaling / frequent crashes). |
| BOOLEAN | `HeroFeature__force_hero_vertical` | `false` | Forces the vertical Hero variant/orientation. May be incompatible with low-resolution HUDs (poor scaling / frequent crashes). |
| BOOLEAN | `SystemUi__hero_large_canonical_layout_enabled_kill_switch` | `true` | Allows Hero Large in `CANONICAL`. Kill-switch polarity: **TRUE** allows Hero Large in `CANONICAL`; **FALSE** does not allow it through this gate. |

## Apollo Expressive media experience — ★★★★★

Enables the Expressive Compose media experience and controls automatic pager movement toward suggestion cards.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `ApolloFeature__expressive_enabled` | `false` | Enables the Expressive variant. |
| BOOLEAN | `ApolloFeature__no_auto_swipe_to_suggestions_kill_switch` | `true` | `true` prevents automatic swiping to suggestion cards. Kill-switch polarity: **TRUE** blocks automatic swiping to suggestion cards; **FALSE** allows the previous auto-swipe behavior. |

## Media suggestions directly from apps — ★★★★★

Lets Android Auto query a media app's `MediaBrowserService` directly for suggested content and optionally display album art.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `Media__query_media_app_directly_for_suggestions_enabled` | `false` | Queries the media app directly for suggestions. |
| BOOLEAN | `Media__show_album_art_for_suggestion` | `false` | Fetches/displays album art for suggestions. |

## Disable-autoplay recommendation HUN — ★★★★★

Controls the HUN that suggests disabling autoplay after repeated manual pauses, including the quantitative history window and trigger count.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `Media__display_disable_autoplay_notification_enabled` | `false` | Enables the recommendation HUN. |
| LONG | `Media__disable_autoplay_hun_pause_history_length` | `5` | Pause-history window length. |
| LONG | `Media__disable_autoplay_hun_required_pause_count` | `3` | Number of qualifying pauses required. |

## Audio minimum-buffer calculation — ★★★★★

Selects the adaptive minimum-buffer calculation path and exposes the default minimum buffer count for Wi-Fi audio.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `AudioBufferingFeature__use_min_audio_buffer_calculator_kill_switch` | `true` | Selects adaptive minimum-buffer calculation. Kill-switch polarity: **TRUE** selects the adaptive minimum-audio-buffer calculator; **FALSE** does not select that calculator. |
| LONG | `AudioBufferingFeature__default_minimum_audio_buffers_for_wifi` | `8` | Minimum audio-buffer count for the Wi-Fi path. |

## Other media & audio capabilities

<table>
  <thead>
    <tr>
      <th>Capability</th>
      <th>Parameter</th>
      <th>17.8</th>
      <th>17.9</th>
      <th>What it changes</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Multiple dashboard media cards</td>
      <td>BOOLEAN — <code>Media__support_multiple_dashboard_media_cards</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Allows multiple media cards on the dashboard.</td>
    </tr>
    <tr>
      <td>Dynamic media playback background</td>
      <td>BOOLEAN — <code>Media__update_playback_background</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Dynamically updates player colors/background.</td>
    </tr>
    <tr>
      <td>Synthetic ±10-second media actions</td>
      <td>BOOLEAN — <code>Media__force_skip_10s_media_actions_enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Synthesizes ±10 s controls when seek is available.</td>
    </tr>
    <tr>
      <td>Neoplan buffered media source</td>
      <td>LONG — <code>NeoplanFeature__enabled</code></td>
      <td colspan="2" align="center"><code>0</code></td>
      <td>Magic-number gate for an experimental buffered-media service using <code>CarBufferedMediaSourceService</code> and a Cronet-backed data path.</td>
    </tr>
    <tr>
      <td>Synchronized Media master gate</td>
      <td>BOOLEAN — <code>SynchronizedMediaFeature__enable_sync</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Enables the configurable protocol-level A/V synchronization mechanism.</td>
    </tr>
    <tr>
      <td>Audio flow-control queue</td>
      <td>LONG — <code>AudioFlowControl__transmission_queue_max_capacity</code></td>
      <td colspan="2" align="center"><code>240</code></td>
      <td>Sets the maximum audio transmission-queue capacity in frames; <code>0</code> means no configured capacity limit.</td>
    </tr>
    <tr>
      <td>Playback-only media sessions</td>
      <td>BOOLEAN — <code>Media__support_playback_only_media</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Supports playback-only sessions without a complete browse tree.</td>
    </tr>
    <tr>
      <td>ASR audio-channel hold</td>
      <td>BOOLEAN — <code>AudioFocus__hold_channels_for_asr</code></td>
      <td colspan="2" align="center"><code>true</code></td>
      <td>Keeps channels held/reserved during ASR.</td>
    </tr>
    <tr>
      <td>Radio DTS station-name preference</td>
      <td>BOOLEAN — <code>HeroFeature__radio_dts_prefer_station_name_kill_switch</code></td>
      <td><code>—</code></td>
      <td><code>true</code></td>
      <td>Prefers station name in enriched DTS radio metadata. Kill-switch polarity: <strong>TRUE</strong> prefers station name in enriched DTS radio metadata; <strong>FALSE</strong> does not apply that preference.</td>
    </tr>
  </tbody>
</table>

> **ℹ️ Note:** `SynchronizedMediaFeature__average_lag_weight` is real and active, but remains intentionally excluded from the confirmed catalog because the receiving-side mathematical interpretation of its value could not be proven.

---

</details>

<a id="3-apps-cradle-car-app-library"></a>

<details>
<summary><strong>3. Apps, Cradle & Car App Library</strong></summary>

This area contains some of the most interesting feature-unlock paths in the catalog: projected Android apps, video-app eligibility, immersive mode, DPI control, Compose templates, CAL media overlays, and search/template behavior.

## Cradle video-app eligibility — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `CradleFeature__allow_video_apps` | `false` | Opens Cradle eligibility to video apps. |

**Requirements:** Android API 37+ and an eligible head unit in the traced path.

## Cradle Phone Apps launcher — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `CradleFeature__all_app_launcher_enabled` | `false` | Adds the **Phone Apps** tab to the Cradle launcher. |

## Cradle immersive mode — ★★★★★

Enables immersive mode for projected native Android apps and allows apps to control system bars/overlay when supported.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `CradleFeature__immersive_mode_enabled` | `true` | Master gate for Cradle immersive mode. |
| BOOLEAN | `CradleFeature__app_controlled_immersive_mode_enabled` | `false` | Lets the projected app control system bars/overlay. |

**Requirement for app-controlled mode:** Android API 37+.

## Cradle native DPI override — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `CradleFeature__show_dpi_picker` | `false` | Exposes Native DPI selection and allows density override for projected apps. |

## CAL miniplayer overlay — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `CarAppLibrary__miniplayer_overlay_enabled` | `false` | Enables the expandable `MediaOverlayUiModel` miniplayer overlay. |

**Requirement:** CAL MEDIA API 9+ in the traced path.

## Watevra Foreground Search — ★★★★★

Adds browser-aware foreground/search-results behavior and can feed host transcription into the CAL search field.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `Watevra__foreground_search_fab_enabled` | `false` | Enables foreground/browser-aware search flow. |
| BOOLEAN | `Watevra__transcription_enabled` | `false` | Integrates host transcription into the CAL search field. |

> **ℹ️ Note:** `Watevra__foreground_search_fab_component_denylist` is intentionally not cataloged as a behavior switch: in these builds it only reaches dump/config plumbing and does not control Foreground Search behavior.

## CAL ConversationItem driving line limit — ★★★★

Controls message-line limits while driving; 17.9 adds a dedicated gate around the dynamic limit path.

<table>
  <thead>
    <tr>
      <th>Type</th>
      <th>Parameter</th>
      <th>17.8</th>
      <th>17.9</th>
      <th>Role</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>LONG</td>
      <td><code>CarAppLibrary__conversation_item_max_message_lines_while_driving</code></td>
      <td colspan="2" align="center"><code>2</code></td>
      <td>Maximum message lines in a <code>ConversationItem</code> while driving.</td>
    </tr>
    <tr>
      <td>BOOLEAN</td>
      <td><code>CarAppLibrary__conversation_item_max_message_lines_kill_switch</code></td>
      <td><code>—</code></td>
      <td><code>true</code></td>
      <td>New 17.9 gate around the dynamic line-limit behavior. Kill-switch polarity: <strong>unknown</strong> — the retained evidence confirms the gate and the dynamic line-limit path, but does not safely establish both TRUE/FALSE branches.</td>
    </tr>
  </tbody>
</table>

**17.8 → 17.9:** restructured.

## Other Cradle / CAL capabilities

<table>
  <thead>
    <tr>
      <th>Capability</th>
      <th>Parameter</th>
      <th>17.8</th>
      <th>17.9</th>
      <th>What it changes</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>CAL templates in Compose</td>
      <td>BOOLEAN — <code>CarAppLibrary__all_templates_in_compose_enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Master gate for migrating CAL templates to Compose, with alternate sub-gates.</td>
    </tr>
    <tr>
      <td>CAL tab layout orientation</td>
      <td>LONG — <code>CarAppLibrary__tab_layout_orientation</code></td>
      <td colspan="2" align="center"><code>0</code></td>
      <td><code>0</code> = <code>FORCE_HORIZONTAL</code>; <code>1</code> = <code>FORCE_VERTICAL</code>; <code>2</code> = <code>AUTO</code>; any other value falls back to <code>AUTO</code>. The default <code>0</code> therefore explicitly forces horizontal layout.</td>
    </tr>
    <tr>
      <td>CAL Map Presentation Service architecture</td>
      <td>BOOLEAN — <code>CarAppLibrary__use_map_presentation_service_kill_switch</code></td>
      <td colspan="2" align="center"><code>true</code></td>
      <td>Selects between the inline architecture and the alternate Map Presentation Service path. Kill-switch polarity: <strong>TRUE</strong> selects the alternate Map Presentation Service path; <strong>FALSE</strong> uses the inline architecture.</td>
    </tr>
    <tr>
      <td>ProjectedApps vendor extension</td>
      <td>BOOLEAN — <code>ProjectedAppsFeature__enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Enables the <code>projectedapps</code> vendor extension on compatible HUs.</td>
    </tr>
    <tr>
      <td>Cradle day/night propagation</td>
      <td>BOOLEAN — <code>CradleFeature__day_night_enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Propagates vehicle day/night state to Cradle apps; API 37+ in the traced path.</td>
    </tr>
    <tr>
      <td>CAL dashboard progress actions</td>
      <td>BOOLEAN — <code>CarAppLibrary__enable_progress_buttons_on_dashboard_mode</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Renders dashboard actions with real progress/duration.</td>
    </tr>
    <tr>
      <td>CAL tall/narrow navigation templates</td>
      <td>BOOLEAN — <code>CarAppLibrary__allow_tall_narrow_for_nav_apps</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Expands <code>NavigationTemplate</code> eligibility/layout for tall/narrow geometry.</td>
    </tr>
    <tr>
      <td>CAL SectionedItemTemplate search header</td>
      <td>BOOLEAN — <code>CarAppLibrary__search_with_sectioned_item_template_kill_switch</code></td>
      <td colspan="2" align="center"><code>true</code></td>
      <td>When released, allows a full <code>SearchHeader</code> in fullscreen <code>SectionedItemTemplate</code>. Kill-switch polarity: <strong>TRUE</strong> keeps the restricted/kill-switch path; <strong>FALSE</strong> releases the full <code>SearchHeader</code> in fullscreen <code>SectionedItemTemplate</code>.</td>
    </tr>
    <tr>
      <td>CAL SectionedItemTemplate sticky actions</td>
      <td>BOOLEAN — <code>CarAppLibrary__sectioned_item_template_sticky_actions_enabled</code></td>
      <td><code>—</code></td>
      <td><code>false</code></td>
      <td>Enables persistent/sticky actions in <code>SectionedItemTemplate</code>; API 9+ in the traced path.</td>
    </tr>
    <tr>
      <td>CAL Speedbump Compose renderer</td>
      <td>BOOLEAN — <code>CarAppLibrary__speedbump_compose_enabled_kill_switch</code></td>
      <td colspan="2" align="center"><code>true</code></td>
      <td>Controls the dedicated Compose renderer for speedbump UI. Kill-switch polarity: <strong>TRUE</strong> disables/kills the dedicated Compose speedbump renderer; <strong>FALSE</strong> allows that Compose renderer path.</td>
    </tr>
    <tr>
      <td>CAL primary action on Coolwalk</td>
      <td>BOOLEAN — <code>Watevra__primary_actions_on_coolwalk</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Promotes the <code>NavigationTemplate</code> primary action next to the travel estimate.</td>
    </tr>
    <tr>
      <td>Watevra host CAL API level</td>
      <td>LONG — <code>Watevra__host_car_app_library_latest_api_level</code></td>
      <td colspan="2" align="center"><code>9</code></td>
      <td>Sets the maximum CAL API level advertised by the host. Raising it does not implement missing host APIs.</td>
    </tr>
    <tr>
      <td>CAL icon color analysis/tint strategy</td>
      <td>BOOLEAN — <code>CarAppLibrary__icon_color_analyzer_enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Classifies icons as single-color/multi-color for tint strategy.</td>
    </tr>
  </tbody>
</table>

---

</details>

<a id="4-assistant-gemini-proactive-features"></a>

<details>
<summary><strong>4. Assistant, Gemini & Proactive Features</strong></summary>

Gemini UI, voice plates, Magic Cue, Barge-In, personalization, Assistant suggestions, capability negotiation, and user-education experiments.

## Gemini Luminous UI — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `Assistant__gemini_luminous_ui_updates` | `false` | Enables the experimental Gemini Luminous UI / Compose Voice Plate path. |

## CAL Voice Plate for Assistant & Gemini — ★★★★★

Controls the Assistant and Gemini CAL Voice Plate paths independently, including separate minimum GSA version thresholds.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `SystemUi__use_cal_voice_plate_for_assistant` | `false` | Master gate for the classic Assistant CAL Voice Plate. |
| BOOLEAN | `SystemUi__use_cal_voice_plate_for_gemini` | `false` | Master gate for Gemini CAL Voice Plate. |
| LONG | `SystemUi__use_cal_voice_plate_for_assistant_min_gsa_version` | `301700000` | Minimum Google app/GSA `longVersionCode` for the Assistant path. |
| LONG | `SystemUi__use_cal_voice_plate_for_gemini_min_gsa_version` | `301649918` | Independent minimum GSA version for Gemini. |

## Magic Cue / Actionable Insights — ★★★★★

Combines the Magic Cue / Proactive Assistance / `ContextInsight` setting with the gate that converts insights into actionable suggestions.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `key_settings_magic_cue_enabled` | `false` | Master setting for Magic Cue / Proactive Assistance. |
| BOOLEAN | `AceFeature__actionable_insight_enabled` | `false` | Converts `ActionableInsight` / `ContextInsight` into actionable suggestions. |

## Gemini Barge-In — ★★★★★

Combines the feature gate with the effective preference serialized into `VoiceSessionConfig`.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `Assistant__gemini_barge_in_enabled_kill_switch` | `true` | Master gate. Kill-switch polarity: **TRUE** permits Gemini Barge-In; **FALSE** does not permit it through this gate. |
| BOOLEAN | `key_settings_gemini_barge_in` | `∅` | Effective Barge-In preference sent in `VoiceSessionConfig`. |

## Gemini personalization — ★★★★★

Controls Gemini personalization with chats and Connected Apps.

<table>
  <thead>
    <tr>
      <th>Type</th>
      <th>Parameter</th>
      <th>17.8</th>
      <th>17.9</th>
      <th>Role</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>BOOLEAN</td>
      <td><code>key_settings_gemini_personalization</code></td>
      <td><code>∅</code></td>
      <td><code>false</code></td>
      <td>Effective Gemini personalization preference.</td>
    </tr>
    <tr>
      <td>BOOLEAN</td>
      <td><code>Assistant__enable_gemini_personalization</code></td>
      <td><code>false</code></td>
      <td><code>—</code></td>
      <td>Historical separate gate, absorbed into the normal settings path in 17.9.</td>
    </tr>
  </tbody>
</table>

**17.8 → 17.9:** restructured; the dedicated gate disappears while the settings-based path remains.

## Coolwalk Assistant Suggestions — ★★★★★

Controls Assistant Suggestions in Coolwalk, preference over traditional app suggestions, and contextual media/navigation signals sent into the Assistant suggestion pipeline.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `Coolwalk__assistant_suggestions_enabled` | `false` | Master gate. |
| BOOLEAN | `Coolwalk__choose_assistant_suggestion_over_app_suggestion` | `false` | Prefers Assistant suggestions over traditional app/media-browser suggestions. |
| BOOLEAN | `Coolwalk__media_rec_request_signal_to_assistant_enabled` | `false` | Sends media-recommendation request context to Assistant. |
| BOOLEAN | `Coolwalk__navigation_signal_to_assistant_enabled` | `false` | Sends navigation state/context into the suggestion pipeline. |

## Dodgeboost / Jumpboost Assistant handshake — ★★★★★

Enables an alternate early Assistant capability-discovery handshake and its GSA version gate.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `Assistant__dodgeboost_initial_handshake_enabled` | `true` | Master gate. |
| LONG | `Assistant__dodgeboost_initial_handshake_min_gsa_version` | `301397393` | Minimum GSA `longVersionCode` for the Jumpboost/Dodgeboost handshake. |

The traced protocol identifies the service as `CarAssistantService` and requests `GetAssistantFeatureFlags`.

## Assistant user-education / tooltip experiments — ★★★★★

Controls text variants, artwork, repetition limits, and free-form tooltip copy for Assistant education surfaces.

<table>
  <thead>
    <tr>
      <th>Type</th>
      <th>Parameter</th>
      <th>17.8</th>
      <th>17.9</th>
      <th>Role</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>STRING</td>
      <td><code>UserEducation__assistant_tooltip_launcher_open_iteration_string</code></td>
      <td colspan="2" align="center"><code>free text; default not recorded</code></td>
      <td>Free-form displayed tooltip text.</td>
    </tr>
    <tr>
      <td>LONG</td>
      <td><code>UserEducation__assistant_tooltip_first_run_copy</code></td>
      <td><code>0</code></td>
      <td><code>15</code></td>
      <td>First-run copy selector. Known range: <code>0</code>–<code>17</code>; invalid values fall back to the base copy.</td>
    </tr>
    <tr>
      <td>LONG</td>
      <td><code>UserEducation__assistant_tooltip_nth_run_copy</code></td>
      <td colspan="2" align="center"><code>0</code></td>
      <td>Subsequent-run copy selector.</td>
    </tr>
    <tr>
      <td>LONG</td>
      <td><code>UserEducation__assistant_tooltip_nth_run_button_copy</code></td>
      <td colspan="2" align="center"><code>0</code></td>
      <td>Button-oriented nth-run copy selector.</td>
    </tr>
    <tr>
      <td>LONG</td>
      <td><code>UserEducation__assistant_tooltip_start_of_navigation_text_copy</code></td>
      <td colspan="2" align="center"><code>0</code></td>
      <td>Start-of-navigation education copy selector.</td>
    </tr>
    <tr>
      <td>LONG</td>
      <td><code>UserEducation__assistant_tooltip_first_run_image</code></td>
      <td colspan="2" align="center"><code>0</code></td>
      <td>First-run artwork selector (<code>0</code>–<code>6</code> known).</td>
    </tr>
    <tr>
      <td>LONG</td>
      <td><code>UserEducation__assistant_tooltip_nth_run_image</code></td>
      <td colspan="2" align="center"><code>0</code></td>
      <td>Nth-run artwork selector.</td>
    </tr>
    <tr>
      <td>LONG</td>
      <td><code>UserEducation__assistant_tooltip_nth_run_button_image</code></td>
      <td colspan="2" align="center"><code>0</code></td>
      <td>Button-oriented nth-run artwork selector.</td>
    </tr>
    <tr>
      <td>LONG</td>
      <td><code>UserEducation__assistant_tooltip_nth_run_count</code></td>
      <td colspan="2" align="center"><code>3</code></td>
      <td>Eligibility/display-count limit.</td>
    </tr>
    <tr>
      <td>LONG</td>
      <td><code>UserEducation__assistant_tooltip_nth_run_button_count</code></td>
      <td colspan="2" align="center"><code>3</code></td>
      <td>Eligibility/display-count limit for the button variant.</td>
    </tr>
  </tbody>
</table>

**17.8 → 17.9:** maturation: `assistant_tooltip_first_run_copy` changes from `0` to `15`; the rest of the documented set remains stable.

## Assistant transcription presentation — ★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| LONG | `Assistant__transcription_character_limit` | `45` | Maximum character count used by the Assistant transcription UI. |
| BOOLEAN | `HeroFeature__assistant_transcription_aligned_by_rail_kill_switch` | `true` | Aligns the Assistant transcription window with the rail in the Hero path. Kill-switch polarity: **TRUE** aligns Assistant transcription to the rail; **FALSE** does not apply that alignment. |

## Other Assistant / Gemini capabilities

| Capability | Parameter | 17.8 & 17.9 | What it changes |
| --- | --- | --- | --- |
| Gemini-in-AAP local master gate | BOOLEAN — `SystemUi__enable_gemini_in_aap_kill_switch` | `true` | Local gate for Gemini integration inside projected Android Auto. Kill-switch polarity: **TRUE** includes/enforces this local gate in the `GEMINI_ENABLED_KEY` decision; **FALSE** bypasses/ignores this local gate. This is not equivalent to saying FALSE globally disables Gemini. |
| Assistant API/protocol version override | LONG — `Assistant__api_version_override` | `-1` | Overrides the Assistant API/protocol version, constrained by versions advertised through capability negotiation. |
| Assistant/Gemini icon branding variant | LONG — `Assistant__icon_experiment_variant` | `0` | Selects the Assistant/Gemini iconography: `0` = classic Google Assistant microphone; `1` = `gemini_branded_mic`; `2` = `cielo_gemini_spark`; `3` = `product_logo_gemini_2026_color_36`; any other value falls back to the classic microphone. |
| Gemini precise-location sharing | BOOLEAN — `key_settings_gemini_share_precise_location` | `true` | Controls precise-location sharing with Gemini/Assistant. |
| Assistant checks across car regions/displays | BOOLEAN — `Assistant__check_all_assistant_regions_enabled` | `false` | Extends Assistant checks to additional car regions/displays. |
| Assistant Live short-search interruption | BOOLEAN — `Assistant__interrupt_live_on_short_search_key_press_kill_switch` | `true` | Makes a short Search-key press interrupt an active Live session. Kill-switch polarity: **TRUE** makes a short Search-key press interrupt an active Live session; **FALSE** keeps the normal/non-interrupting branch. |

---

</details>

<a id="5-wireless-android-auto-connectivity"></a>

<details>
<summary><strong>5. Wireless Android Auto & Connectivity</strong></summary>

Wireless feature negotiation and connection behavior: WPA3, Dual STA, Wi-Fi lock strategy, learned-frequency hints, adapter recognition, and connection-request details.

## Start Wireless Android Auto from a notification — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `WirelessProjectionInGearhead__start_from_notification_enabled` | `false` | Creates an actionable notification that can start Wireless Android Auto, including from a USB-connected state. |

## Wireless AA WPA3 negotiation — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `WirelessProjectionInGearhead__permit_wpa3_security_type` | `false` | Allows WPA3 as a negotiable HU security type. |
| BOOLEAN | `WirelessProjectionInGearhead__use_wpa3_when_available` | `false` | Prefers WPA3 in WPA2/WPA3 transition mode. |

## Wireless AA Dual STA — ★★★★★

Requests a secondary Wi-Fi STA for the head-unit network when supported, with an optional hotspot conflict gate.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `WirelessProjectionInGearhead__enable_dual_sta` | `false` | Requests a secondary Wi-Fi STA for the HU network. |
| BOOLEAN | `WirelessProjectionInGearhead__disable_dual_sta_when_hotspot` | `false` | Blocks Dual STA while hotspot is active. |

## Wireless Network Request Manager Wi-Fi lock strategy — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| STRING | `WirelessProjectionInGearhead__wifi_lock_selected_option_in_network_request_manager` | `high_performance` | Recognized values: `no_lock`, `low_latency`, and `high_performance`. Unknown values enter an invalid-value path that can throw `IllegalArgumentException`. |

## Wireless AA learned-frequency selection — ★★★★★

Learns Wi-Fi frequencies previously associated with the vehicle, decides when the history is concentrated enough to trust, and feeds the resulting channel list to `WifiNetworkSpecifier`.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `WirelessProjectionInGearhead__specify_wifi_frequency_if_available` | `true` | Supplies learned HU frequencies to the network specifier. |
| LONG | `WirelessProjectionInGearhead__frequency_list_max_size` | `25` | Maximum retained history length. |
| LONG | `WirelessProjectionInGearhead__wifi_frequency_saturation_coefficient` | `5` | Controls when the learned history is considered concentrated enough to use. |

## Wireless adapter/dongle name recognition — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| STRING | `WirelessProjectionInGearhead__dongle_device_name_matches` | `Intercooler,AndroidAuto-` | Comma-separated Bluetooth-name substrings used to recognize wireless adapters/dongles. |

## Wireless network-request identity options — ★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `WirelessProjectionInGearhead__omit_bssid_in_network_request` | `false` | Allows the Wi-Fi request to omit a fixed BSSID. |
| BOOLEAN | `WirelessProjectionInGearhead__add_is_hidden_ssid_to_network_request` | `false` | Marks the HU SSID as hidden in the network request. |

## Other wireless connectivity capabilities

| Capability | Parameter | 17.8 & 17.9 | What it changes |
| --- | --- | --- | --- |
| Spark RFCOMM transport | BOOLEAN — `WirelessProjectionInGearhead__use_rfcomm_transport_for_spark` | `false` | Uses Bluetooth RFCOMM in the Spark flow for configured HUs. |
| Companion Device Manager wireless trigger | BOOLEAN — `WirelessProjectionInGearhead__associate_bt_devices_and_trigger_wireless_from_cdm` | `false` | Uses Companion Device Manager to associate Bluetooth devices and trigger wireless projection. |

---

</details>

<a id="6-vehicle-head-unit-multi-display"></a>

<details>
<summary><strong>6. Vehicle, Head Unit & Multi-Display</strong></summary>

Vehicle-facing capabilities, projected clusters, auxiliary displays, Hero Car Controls, and head-unit-specific compatibility behavior.

## Hero Car Controls — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `HeroFeature__car_controls_enabled` | `true` | Enables `HERO_CAR_CONTROLS` and the associated car-control APIs/service. |

**Requirement:** Hero core.

## Projected cluster shell & default policy — ★★★★★

Controls the cluster's default policy, projected Cluster Shell/launcher, and rotary focus/navigation between cluster windows.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| LONG | `MultiDisplay__cluster_display_default_configuration` | `2` | Cluster-display policy: `0` = `OFF`; `1` = `BATTERY_OPTIMIZED`; `2` = `ON`. |
| BOOLEAN | `MultiDisplay__cluster_launcher_enabled` | `false` | Enables Cluster Shell UI/launcher on a projectable cluster. |
| BOOLEAN | `MultiDisplay__cluster_rotary_window_navigation` | `true` | Enables rotary focus/navigation between cluster windows. |

## Auxiliary projected display default policy — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| LONG | `MultiDisplay__aux_display_default_configuration` | `2` | Auxiliary-display policy: `0` = `OFF`; `1` = `BATTERY_OPTIMIZED`; `2` = `ON`. |

## Vehicle/HU audio-focus exceptions — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| STRING | `AudioFocus__should_stop_media_without_interrupting_guidance_deny_list` | `SYNC` | Structured vehicle/HU matching rules that alter media-stop behavior without interrupting guidance. |

## Head-unit software update notifications — ★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `HeadUnitFeature__show_update_notifications_enabled` | `false` | Enables notifications for known HU software updates. |
| LONG | `HeadUnitFeature__max_notifications_for_head_unit` | `5` | Limits notifications in the relevant HU update/notification path. |

## Other vehicle / display capabilities

| Capability | Parameter | 17.8 & 17.9 | What it changes |
| --- | --- | --- | --- |
| Third-party navigation turn icon in cluster | BOOLEAN — `Watevra__use_3p_turn_icon_in_cluster_but_not_hud_enabled` | `false` | Serializes a third-party turn icon for the cluster but not the HUD. |

---

</details>

<a id="7-messaging-calls"></a>

<details>
<summary><strong>7. Messaging & Calls</strong></summary>

Messaging discovery/readout, Smart Actions, templated dialers, call architecture, VoIP association, and historical messaging UI behavior.

## CallsManager / InCallServiceCompat architecture — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `Dialer__calls_manager_ics_enabled` | `false` | Migrates calling to the `CallsManager` / `InCallServiceCompat` architecture. |

## External templated dialer — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `Dialer__external_dialer_enabled` | `false` | Allows an external templated app to act as the default dialer path. |

## Notification-powered calls — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `Dialer__notification_powered_calls_enabled` | `true` | Uses `CallStyle` / `OngoingCallNotificationManager` as the central source for call state/presentation. |

## Messaging geographic text gate — ★★★★★

<table>
  <thead>
    <tr>
      <th>Type</th>
      <th>Parameter</th>
      <th>17.8</th>
      <th>17.9</th>
      <th>Role</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>BOOLEAN</td>
      <td><code>Messaging__is_messaging_text_allowed</code></td>
      <td><code>—</code></td>
      <td><code>true</code></td>
      <td>New 17.9 geographic gate for messaging text, including country denylist and fail-conservative behavior.</td>
    </tr>
  </tbody>
</table>

## Messaging Smart Actions / Smart Replies — ★★★★

Controls platform Smart Replies/Actions, Navigate-action expansion, confidence filtering, and displayed text length.

<table>
  <thead>
    <tr>
      <th>Type</th>
      <th>Parameter</th>
      <th>17.8</th>
      <th>17.9</th>
      <th>Role</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>BOOLEAN</td>
      <td><code>Messaging__platform_smart_replies_kill_switch</code></td>
      <td><code>false</code></td>
      <td><code>true</code></td>
      <td>Integrates Smart Replies/Actions from Notification Ranking. Kill-switch polarity: <strong>TRUE</strong> initializes/uses the platform Smart Replies/Actions path; <strong>FALSE</strong> does not use that path.</td>
    </tr>
    <tr>
      <td>BOOLEAN</td>
      <td><code>Messaging__smart_action_navigate_enabled_for_all_nav_apps</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Expands the Navigate Smart Action to more navigation apps.</td>
    </tr>
    <tr>
      <td>DOUBLE</td>
      <td><code>Messaging__minimum_smart_action_confidence_score</code></td>
      <td colspan="2" align="center"><code>0.1</code></td>
      <td>Minimum confidence score for a Smart Action to survive filtering.</td>
    </tr>
    <tr>
      <td>LONG</td>
      <td><code>Messaging__maximum_smart_action_display_text_length</code></td>
      <td colspan="2" align="center"><code>10</code></td>
      <td>Maximum display-text length used by message-row Smart Actions.</td>
    </tr>
  </tbody>
</table>

**17.8 → 17.9:** maturation: `Messaging__platform_smart_replies_kill_switch` changes its compiled default from `false` in 17.8 to `true` in 17.9. This is a genuine build-default promotion, independent of any user override.

## Other messaging & call capabilities

<table>
  <thead>
    <tr>
      <th>Capability</th>
      <th>Parameter</th>
      <th>17.8</th>
      <th>17.9</th>
      <th>What it changes</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Messaging autoplay/readout</td>
      <td>BOOLEAN — <code>Messaging__autoplay_messages_kill_switch</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Master enable for message autoplay/readout, still subject to other gates. Kill-switch polarity: <strong>TRUE</strong> enables message autoplay/readout; <strong>FALSE</strong> keeps it disabled through this gate.</td>
    </tr>
    <tr>
      <td>Messaging app discovery</td>
      <td>BOOLEAN — <code>Messaging__app_identification_intent_service_enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Discovers Messaging apps through <code>HANDLE_CAR_MESSAGING</code>.</td>
    </tr>
    <tr>
      <td>Messaging ConversationItem mute action</td>
      <td>BOOLEAN — <code>Messaging__cal_mute_button_enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Adds Mute/Unmute actions to <code>ConversationItem</code>.</td>
    </tr>
    <tr>
      <td>Legacy <code>Notification.CarExtender</code> messaging parser</td>
      <td>BOOLEAN — <code>Messaging__carextender_enabled</code></td>
      <td colspan="2" align="center"><code>true</code></td>
      <td>Keeps the legacy <code>Notification.CarExtender</code> parser path.</td>
    </tr>
    <tr>
      <td>Messaging image-description readout</td>
      <td>BOOLEAN — <code>Messaging__image_description_readout_enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Allows Assistant to receive an image for description/readout.</td>
    </tr>
    <tr>
      <td>Messaging launcher deduplication</td>
      <td>BOOLEAN — <code>Messaging__launcher_priority_messaging_suppression_kill_switch</code></td>
      <td colspan="2" align="center"><code>true</code></td>
      <td>Suppresses duplicate Messaging launcher presence when the same package has richer CAL/Media integration. Kill-switch polarity: <strong>TRUE</strong> suppresses the duplicate Messaging launcher entry when richer CAL/Media integration is present; <strong>FALSE</strong> does not apply that suppression.</td>
    </tr>
    <tr>
      <td>Unified SMS <code>MessagingInfo</code></td>
      <td>BOOLEAN — <code>UnifiedSmsMessagingInfo__enabled</code></td>
      <td colspan="2" align="center"><code>true</code></td>
      <td>Unifies SMS in <code>MessagingInfo</code>, including <code>RemoteInput</code> replies.</td>
    </tr>
    <tr>
      <td>Call HUN third action</td>
      <td>BOOLEAN — <code>Dialer__enable_third_action_on_call_huns</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Allows a third action on call HUNs.</td>
    </tr>
    <tr>
      <td>Unified calling Car Apps routing</td>
      <td>BOOLEAN — <code>Dialer__unified_iccaras_enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Enables session-aware/unified routing for calling Car Apps.</td>
    </tr>
    <tr>
      <td>VoIP call ↔ templated-app association</td>
      <td>BOOLEAN — <code>Voip__voip_call_notification_template_app_kill_switch</code></td>
      <td><code>—</code></td>
      <td><code>true</code></td>
      <td>Associates a VoIP call notification with a templated/Messaging Car App. Kill-switch polarity: <strong>TRUE</strong> enables/attempts templated/Messaging Car App association for the VoIP call; <strong>FALSE</strong> skips that provider/path.</td>
    </tr>
    <tr>
      <td>Work-profile calling apps</td>
      <td>BOOLEAN — <code>WorkAppsFeature__calling_apps_enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Includes calling apps from the work profile.</td>
    </tr>
    <tr>
      <td>Legacy Messaging FAB accent color</td>
      <td>BOOLEAN — <code>Messaging__use_app_accent_color_as_fab_background</code></td>
      <td><code>false</code></td>
      <td><code>—</code></td>
      <td>Used the app accent color for the FAB in the legacy Messaging UI.</td>
    </tr>
    <tr>
      <td>Contacts standard template/provider</td>
      <td>BOOLEAN — <code>PhoneFeature__use_standard_template_for_contacts</code></td>
      <td><code>false</code></td>
      <td><code>—</code></td>
      <td>Selected the standard template/provider path for Contacts.</td>
    </tr>
  </tbody>
</table>

---

</details>

<a id="8-safety-input-driving-restrictions"></a>

<details>
<summary><strong>8. Safety, Input & Driving Restrictions</strong></summary>

Driving-moderation engines, map interactivity under restrictions, task limits, keyboard restrictions, and touchpad thresholds.

## Watevra Speedbump moderation & map interactivity — ★★★★★

Controls migration to the Watevra moderation architecture and its map tap/pan/double-tap policy.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `Watevra__speedbump_enabled` | `false` | Master migration switch for Watevra speedbump/moderation. |
| BOOLEAN | `Watevra__speedbump_map_interactivity_enabled` | `false` | Changes the restriction policy for map tap/pan/double-tap interaction. |

## ContentBrowse SixTap ↔ SpeedBump moderation engine — ★★★★★

Controls migration from the global `GH.SixTap` moderator to the `GH.SpeedBump` permits/token-bucket model, plus force-enable behavior.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `ContentBrowse__enable_speed_bump_projected` | `false` | Switches the global moderator from `GH.SixTap` to `GH.SpeedBump`. |
| BOOLEAN | `ContentBrowse__sixtap_force_enabled` | `false` | Keeps SixTap active even while parked. |
| BOOLEAN | `ContentBrowse__speedbump_force_enabled` | `false` | Keeps SpeedBump active even while parked. |

## CAL moderation-exemption policy — ★★★★★

<table>
  <thead>
    <tr>
      <th>Type</th>
      <th>Parameter</th>
      <th>17.8</th>
      <th>17.9</th>
      <th>Role</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>BOOLEAN</td>
      <td><code>CarAppLibrary__moderation_exempt_kill_switch</code></td>
      <td><code>true</code></td>
      <td><code>—</code></td>
      <td>Historical gate selecting a broader set of moderation-exempt templates. Kill-switch polarity: <strong>TRUE</strong> selected the broader moderation-exempt template set; <strong>FALSE</strong> used the narrower Navigation-only exemption behavior.</td>
    </tr>
  </tbody>
</table>

**17.8 → 17.9:** restructured rather than removed: the broad exemption policy is applied directly in 17.9, so the configurable gate disappears.

## Touchpad navigation thresholds — ★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| LONG | `TouchpadUiNavigation__base_fraction` | `6` | Base fraction used to derive the touchpad movement threshold. |
| LONG | `TouchpadUiNavigation__min_size_mm` | `4 mm` | Minimum physical movement size. |
| LONG | `TouchpadUiNavigation__multimove_penalty_mm` | `4 mm` | Physical-distance penalty used by multi-move recognition. |

## Other safety & input capabilities

| Capability | Parameter | 17.8 & 17.9 | What it changes |
| --- | --- | --- | --- |
| Driving keyboard restriction | BOOLEAN — `ContentBrowse__keyboard_force_disabled` | `false` | Forces the keyboard/input restriction bit in driving status. |
| CAL task-limit overflow restriction | BOOLEAN — `CarAppLibrary__task_limit_restrictions_allows_overflow` | `true` | Integrates overflow state into driving restriction policy. |
| Strato input-restriction bypass | BOOLEAN — `StratoFeature__ignore_input_restrictions` | `false` | Bypasses part of the input/`CarUiInfo` restriction gates in the Strato path. |
| Touchpad drawer interaction limit | LONG — `ContentBrowse__drawer_default_allowed_taps_touchpad` | `6` | Sets the allowed tap/interaction count for Content Browse drawers in touchpad operation. |

---

</details>

<a id="9-projection-video-rendering"></a>

<details>
<summary><strong>9. Projection, Video & Rendering</strong></summary>

This section is intentionally later in the document because it contains lower-level projection, encoder, frame-timing, scaling, and transport controls. These are powerful, but more technical than the UI and feature-unlock sections above.

## Video encoder adaptive bitrate model — ★★★★★

Exposes the codec-specific baselines, floor, gradients, exponent, and 60 FPS multipliers used by the H.264/H.265 adaptive bitrate models.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| LONG | `VideoEncoderParams__adaptive_bitrate_baseline_h264` | `1,500,000` | H.264 adaptive-bitrate baseline. |
| LONG | `VideoEncoderParams__adaptive_bitrate_baseline_h265` | `3,500,000` | H.265/HEVC adaptive-bitrate baseline. |
| LONG | `VideoEncoderParams__adaptive_bitrate_minimal_h264` | `2,000,000` | H.264 minimum/pedestal term. |
| DOUBLE | `VideoEncoderParams__adaptive_bitrate_gradient_h264` | `2.75` | H.264 adaptive-bitrate gradient. |
| DOUBLE | `VideoEncoderParams__adaptive_bitrate_gradient_h265` | `0.46` | H.265 adaptive-bitrate gradient. |
| DOUBLE | `VideoEncoderParams__bitrate_adjustment_exponent` | `0.5` | Exponent that changes the shape of the bitrate adaptation curve. |
| DOUBLE | `VideoEncoderParams__sixty_fps_bitrate_multiplier` | `2.0` | H.264 60 FPS bitrate multiplier. |
| DOUBLE | `VideoEncoderParams__sixty_fps_bitrate_multiplier_h265` | `2.0` | H.265 60 FPS bitrate multiplier. |

## Wireless encoder QP window — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| LONG | `VideoEncoderParams__min_qp_wireless` | `15` | Minimum allowed QP on the wireless encoder path. |
| LONG | `VideoEncoderParams__max_qp_wireless` | `30` | Maximum allowed QP on the wireless encoder path. |

## Video encoder ROI QP tuning — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| LONG | `VideoEncoderParams__roi_qp_offset` | `0` | QP offset for Region of Interest encoding. |
| LONG | `VideoEncoderParams__roi_qp_offset_margin` | `127` | Companion ROI/QP parameter passed into encoder configuration. |

## Video codec capability validation — ★★★★★

Controls codec-level validation, codec-reported maximum dimensions, and a protocol-5+ multi-codec rule.

<table>
  <thead>
    <tr>
      <th>Type</th>
      <th>Parameter</th>
      <th>17.8</th>
      <th>17.9</th>
      <th>Role</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>BOOLEAN</td>
      <td><code>VideoEncoderParams__use_spec_codec_level_limits</code></td>
      <td><code>—</code></td>
      <td><code>false</code></td>
      <td>New in 17.9: validates resolution/FPS against required AVC/HEVC levels.</td>
    </tr>
    <tr>
      <td>BOOLEAN</td>
      <td><code>VideoEncoderParams__enforce_supported_codec_dimensions</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Restricts modes to the maximum dimensions reported by the codec.</td>
    </tr>
    <tr>
      <td>BOOLEAN</td>
      <td><code>VideoEncoderParams__extend_avc_support</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>In protocol 5+, rejects a display advertising multiple codec types in the traced branch.</td>
    </tr>
  </tbody>
</table>

**17.8 → 17.9:** restructured by adding `use_spec_codec_level_limits`.

## Video Timing Controller — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `VideoTimingControllerFeature__enable_video_timing_controller` | `true` | Negotiates Video Timing Controller behavior and a timing window with the HU. |

## Low-tier device 30 Hz projection policy — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| LONG | `ProjectionWindowManager__max_sdk_version_for_low_tier_devices` | `33` | Participates in low-tier device classification that can force 30 Hz instead of 60 Hz under the documented resolution/device conditions. |

## Projection geometry & scaling policy — ★★★★★

Controls the legacy pixel-aspect-ratio workaround path and upper/lower tolerances for non-uniform scaling.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `ProjectionWindowManager__deprecate_pixel_aspect_ratio_scaling_kill_switch` | `true` | Avoids legacy pixel-aspect-ratio scaling workarounds for older HUs. Kill-switch polarity: **TRUE** selects the deprecation/modern path that avoids legacy pixel-aspect-ratio scaling workarounds; **FALSE** falls back to the legacy path. |
| DOUBLE | `ProjectionWindowManager__non_square_scale_limit` | `1.35` | Upper tolerance for non-uniform X/Y scaling. |
| DOUBLE | `ProjectionWindowManager__non_square_scale_min_limit` | `1.0125` | Lower tolerance for the same scaling logic. |

## Projection non-drawable insets — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `ProjectionWindowManager__send_non_drawable_insets_enabled` | `false` | Sends non-drawable insets to compatible projected clients. |

## Frame-rate restrictions & thermal policy — ★★★★★

Controls HIGH/MEDIUM FPS limits, thermal eligibility, display/activity-count criteria, power-save behavior, predictive thermal headroom, and cooling hysteresis.

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `FrameRateRestrictions__disable_interactivity_tracking` | `false` | Disables interactive↔idle tracking used by adaptive FPS control. |
| BOOLEAN | `FrameRateRestrictions__thermal_headroom_throttling_enabled` | `false` | Adds predictive throttling based on thermal headroom; API 36+ in the traced path. |
| BOOLEAN | `FrameRateRestrictions__use_power_save_mode_for_battery_level` | `false` | Uses Battery Saver instead of percentage-based battery thresholds. |
| LONG | `FrameRateRestrictions__fps_limit_for_restriction_level_high` | `15 FPS` | FPS ceiling for HIGH restriction level. |
| LONG | `FrameRateRestrictions__fps_limit_for_restriction_level_medium` | `30 FPS` | FPS ceiling for MEDIUM restriction level. |
| LONG | `FrameRateRestrictions__min_number_of_displays_to_trigger_restrictions` | `2` | Minimum active-display count used by one restriction decision. |
| LONG | `FrameRateRestrictions__min_thermal_status_for_frame_rate_restrictions` | `3` | Thermal-status threshold for restriction eligibility. |
| LONG | `FrameRateRestrictions__min_number_of_impactful_activities_to_trigger_restrictions` | `-1` | Threshold for active “impactful activities”; `-1` disables this criterion. |
| DOUBLE | `FrameRateRestrictions__thermal_headroom_cooling_hysteresis_factor` | `0.85` | Cooling hysteresis factor used when deciding whether restrictions can be relaxed. |

## H.264 compatibility profile — ★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `VideoEncoderParams__enable_max_h264_encoder_compatibility` | `false` | Switches H.264 Baseline to Constrained Baseline in the checker and `MediaFormat`. |
| BOOLEAN | `VideoEncoderParams__enable_max_h264_encoder_compatibility_fast_track` | `false` | Requests Constrained Baseline only in `MediaFormat`. |

## Projected Presentation configuration validation — ★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| LONG | `ProjectedPresentation__configuration_check_tolerance_dp` | `2 dp` | Tolerance inserted into the `DrawingSpec` for configuration checks. |
| BOOLEAN | `ProjectedPresentation__crash_projected_presentation_if_unable_to_get_correct_configuration` | `false` | Chooses fail-open vs crash behavior when projected configuration cannot converge. |

## GAL transport framing / buffering — ★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| LONG | `FrameworkGalFeature__fragment_size` | `16128` | Populates `FramerConnectionSettings.fragmentSize`. |
| LONG | `FrameworkGalFeature__framer_send_buffer_size` | `16384` | Populates `FramerConnectionSettings.bufferedStreamSize`. |

## Other projection / rendering capabilities

<table>
  <thead>
    <tr>
      <th>Capability</th>
      <th>Parameter</th>
      <th>17.8</th>
      <th>17.9</th>
      <th>What it changes</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Projection frame-timing tolerance</td>
      <td>DOUBLE — <code>ProjectionWindowManager__expected_frame_time_deviation</code></td>
      <td><code>0.5</code></td>
      <td><code>0.2</code></td>
      <td>Tightens the allowed deviation around expected frame timing from 0.5 to 0.2 in 17.9.</td>
    </tr>
    <tr>
      <td>Projection pending-frame queue</td>
      <td>LONG — <code>ProjectionWindowManager__max_pending_frames_to_send</code></td>
      <td colspan="2" align="center"><code>3</code></td>
      <td>Limits how many projection frames may remain pending for transmission.</td>
    </tr>
    <tr>
      <td>Projection content-window startup animation scheduling</td>
      <td>BOOLEAN — <code>ProjectionWindowManager__content_window_startup_animation_kill_switch</code></td>
      <td colspan="2" align="center"><code>true</code></td>
      <td>Preserves specialized scheduling/batching for content-window startup animations. Kill-switch polarity: <strong>unknown</strong> — the retained catalog confirms its relationship to specialized startup-animation scheduling/batching, but the available retained evidence is not consistent enough to state both TRUE/FALSE branches safely.</td>
    </tr>
  </tbody>
</table>

---

</details>

<a id="10-setup-frx-bluetooth-usb"></a>

<details>
<summary><strong>10. Setup, FRX, Bluetooth & USB</strong></summary>

Onboarding/FRX control, setup bypasses, country eligibility, Bluetooth pairing behavior, A2DP handling, USB recovery, and related diagnostics.

## Simplified pre-setup — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `Preflight__simplified_pre_setup_enabled` | `false` | Moves checks into an earlier pre-setup phase and can bypass the legacy FRX path. |

## FRX restore / rewind lifecycle — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `Preflight__restore_phone_frx` | `false` | Allows phone FRX to be resumed from a notification after leaving the activity. |
| BOOLEAN | `Preflight__frx_rewind_enabled` | `false` | Clears/rewinds FRX/ToS state so onboarding can be presented again. |

## Assistant notification data-sharing FRX consent — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `Messaging__assistant_notification_data_sharing_frx_enabled` | `false` | Enables the newer FRX consent that gates notification/data sharing with Assistant. |
| BOOLEAN | `Messaging__assistant_notification_data_sharing_leave_behind_enabled` | `false` | Bridges the new FRX consent to the legacy preference/runtime state. |

## Phase 1.75 setup bypasses — ★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `Preflight__phase_1_75_skip_agsa` | `false` | Skips Google Assistant sign-in/opt-in in the setup flow. |
| BOOLEAN | `Preflight__phase_1_75_skip_gmm` | `false` | Skips Google Maps projected first-run/sign-in. |

## Preflight country eligibility — ★★★★

<table>
  <thead>
    <tr>
      <th>Type</th>
      <th>Parameter</th>
      <th>17.8</th>
      <th>17.9</th>
      <th>Role</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>BOOLEAN</td>
      <td><code>Preflight__is_device_country_blocked</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Forces the country-blocked gate; can lead to teardown except for the vehicle allowlist path.</td>
    </tr>
    <tr>
      <td>BOOLEAN</td>
      <td><code>Preflight__enable_gps_country_check</code></td>
      <td><code>false</code></td>
      <td><code>—</code></td>
      <td>Historical 17.8 key; despite its name, <code>true</code> bypassed the physical/GPS fallback in the traced logic.</td>
    </tr>
  </tbody>
</table>

**17.8 → 17.9:** restructured; the explicit GPS key disappears while the main country gate remains.

## Wireless FRX location prerequisites — ★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `WirelessFrxFeature__show_if_missing_location_permission` | `true` | Shows Wireless FRX when location permission is missing. |
| BOOLEAN | `WirelessFrxFeature__show_if_location_services_disabled` | `true` | Shows Wireless FRX when Location Services are disabled. |

## Bluetooth rebond prevention — ★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| BOOLEAN | `BluetoothPairing__prevent_rebonding_during_wired_projection` | `false` | Prevents automatic rebonding during wired projection. |
| BOOLEAN | `BluetoothPairing__prevent_rebonding_during_wireless_projection` | `false` | Prevents automatic rebonding during wireless projection. |

## USB function recovery / UsbBabysitter — ★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| LONG | `UsbBabysitter__default_usb_function` | `-1` | Android USB-function bitmask applied/restored by UsbBabysitter; negative values mean no override. |
| BOOLEAN | `UsbBabysitter__switch_usb_function_on_bluetooth_connect` | `false` | Reapplies/switches the USB function when car Bluetooth connects. |
| BOOLEAN | `UsbBabysitter__switch_usb_function_on_usb_disconnect` | `false` | Reapplies/switches the USB function after USB disconnect. |

## Other setup / Bluetooth / USB capabilities

<table>
  <thead>
    <tr>
      <th>Capability</th>
      <th>Parameter</th>
      <th>17.8</th>
      <th>17.9</th>
      <th>What it changes</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Preflight phone denylist state</td>
      <td>BOOLEAN — <code>Preflight__is_phone_denylisted</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Forces the phone-denylisted state in pre-setup/wireless logic.</td>
    </tr>
    <tr>
      <td>FRX Maps requirement bypass</td>
      <td>BOOLEAN — <code>LegacyCarSetupFlags__frx_maps_bypass_enabled</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Allows configured navigation apps to bypass the Maps requirement in FRX.</td>
    </tr>
    <tr>
      <td>Deferred non-essential startup during preflight</td>
      <td>BOOLEAN — <code>GearheadCarService__delay_non_essential_startup_during_preflight</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Defers non-essential services until preflight is finished.</td>
    </tr>
    <tr>
      <td>Bluetooth CarInfo metadata</td>
      <td>BOOLEAN — <code>BluetoothPairing__add_car_info_to_bluetooth_metadata</code></td>
      <td><code>—</code></td>
      <td><code>false</code></td>
      <td>Writes manufacturer/model/year/HU data into Bluetooth-device RDI metadata.</td>
    </tr>
    <tr>
      <td>CarBluetoothService master kill switch</td>
      <td>BOOLEAN — <code>BluetoothPairing__car_bluetooth_service_disable</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Disables <code>CarBluetoothService</code>.</td>
    </tr>
    <tr>
      <td>A2DP during projection</td>
      <td>BOOLEAN — <code>BluetoothPairing__disable_a2dp</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Disables the car A2DP route during projection.</td>
    </tr>
    <tr>
      <td>Flaky USB detection</td>
      <td>BOOLEAN — <code>UsbBabysitterFeature__enable_flakey_usb_detector</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Enables detection of unstable USB connections.</td>
    </tr>
    <tr>
      <td>Projection-end A2DP recovery</td>
      <td>BOOLEAN — <code>UsbBabysitter__enable_a2dp_at_projection_end</code></td>
      <td colspan="2" align="center"><code>false</code></td>
      <td>Attempts to restore HU A2DP when projection ends.</td>
    </tr>
  </tbody>
</table>

---

</details>

<a id="11-system-performance-low-level-internals"></a>

<details>
<summary><strong>11. System, Performance & Low-Level Internals</strong></summary>

The most technical section: release-channel identity, thermal compatibility policy, startup/performance tuning, launcher limits, and internal testing surfaces.

## Local release-channel identity — ★★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| LONG | `ReleaseChannel__gearhead_channel` | `0` | Selects local `UNKNOWN` / `ALPHA` / `BETA` / `PROD` / `INTERNAL_BETA` identity used by local runtime branches. |

This does **not** change the APK signature, server-side entitlement, or Google-side release status.

## SystemUI compatibility mode — ★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| LONG | `SystemUi__compat_mode_min_android_version` | `30` | Minimum Android SDK used by the SystemUI compatibility-mode decision. |
| LONG | `SystemUi__compat_mode_min_trigger_thermal_status` | `4` | Thermal-status threshold associated with triggering compatibility mode. |

## Feature-specific thermal thresholds — ★★★★

| Type | Parameter | 17.8 & 17.9 | Role |
| --- | --- | --- | --- |
| LONG | `SystemUi__thermal_battery_temperature_severe_threshold` | `420` | Severe battery-temperature threshold in SystemUI thermal policy. |
| LONG | `SystemUi__thermal_camera_threshold` | `3` | Camera-specific thermal threshold. |

The inspected code also contains related thermal paths for other resources, but only confirmed promoted parameters are listed here.

## Other system / performance capabilities

| Capability | Parameter | 17.8 & 17.9 | What it changes |
| --- | --- | --- | --- |
| NavigationClientManager delayed startup | BOOLEAN — `Performance__navigation_client_manager_delayed_start_enabled` | `false` | Moves `NavigationClientManager` out of the critical startup path. |
| Activity pre-warming | BOOLEAN — `Performance__pre_warm_activities_enabled` | `false` | Pre-warms activities for configured regions. |
| Launcher shortcut limit | LONG — `LauncherShortcuts__max_shortcuts` | `32` | Sets the maximum number of launcher shortcuts considered by the corresponding path. |
| CSAT proof-mode survey selector | STRING — `Csat__proof_mode_survey` | `enum; default not recorded` | Forces an internal CSAT survey variant in proof/testing mode. |

---

</details>

# Historical notes & exclusions

This catalog intentionally keeps historically useful controls when they were approved in 17.8 but disappeared in 17.9. A missing key does **not** automatically mean the feature disappeared; some behaviors were absorbed into a different gate or became fixed policy.

Examples include:

- `Assistant__enable_gemini_personalization` — separate 17.8 gate absorbed into the normal settings path in 17.9.
- `CarAppLibrary__moderation_exempt_kill_switch` — configurable gate removed after the broader policy became direct behavior.
- `Coolwalk__use_phone_primary_color` — removed while palette control remained through other theming paths.

The catalog also intentionally excludes parameters that were:

- dormant or dump-only in the analyzed builds;
- unresolved after consumer tracing;
- false positives from the resolver;
- rated ★★★ or below in the final review.

## Known kill-switch polarity gaps

Two retained kill switches still have confirmed relevance but do not have a sufficiently safe TRUE/FALSE branch interpretation in the retained evidence:

- `CarAppLibrary__conversation_item_max_message_lines_kill_switch` — the dynamic `ConversationItem` line-limit capability is confirmed, but the exact polarity of the gate is not.
- `ProjectionWindowManager__content_window_startup_animation_kill_switch` — its relationship to specialized content-window startup-animation scheduling is confirmed, but the retained evidence is not consistent enough to assign both TRUE/FALSE branches safely.

These are intentionally marked **polarity unknown** in the catalog rather than inferred from their names.

# Catalog integrity

The public-facing organization above is a reordering of the approved consolidated catalog, not a new round of reverse engineering. The target is to preserve every approved parameter exactly once while ranking capabilities by practical impact rather than by parameter type or source-file order.

