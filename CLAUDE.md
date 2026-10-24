# tvssoca — project brief for Claude

The owner's own Android TV home screen (launcher), named **tvssoca** on the TV (package and code
still `dev.passoca.tvhome`). Its icon and banner use the passoca.dev logo (github.com/httpassoca/passoca
`static/logo.svg`) in signal green. It takes the behaviour and geometry of the
CleanYourTV launcher (github.com/Gathin23/cleanyourtv, MIT) and redraws it with **dssoca**, her
design system (github.com/httpassoca/dssoca, npm `dssoca`). Read this file first in every session.
**This repo is public.** Personal and device-specific notes live in two **gitignored** files at the repo root,
kept only on her machines: `important.md` (the live state of her TV: address, what is disabled and how to undo
it, usage and performance notes, removed apps, the build machine) and `config.json` (her real launcher config).
Read `important.md` if it exists; keep it current when anything on the TV changes. Never commit LAN addresses,
device serials, usage habits, installed-app lists, account details or her real `config.json`;
`config.example.json` is the public template.

## Goals (what she asked for, in her words where possible)

1. **Her TV home on dssoca.** The CleanYourTV launcher's interface and behaviour, restyled with dssoca:
   dark theme only, **md** size only, square corners, signal green on near-black, Electrolize + JetBrains Mono.
2. **Header:** the clock and date, with **weather and world clock built into the time heading** as a
   second line under the date. Plain text, not cards or labels. **No add-on app needed** for either.
   **No greeting** ("Good evening" was removed on purpose).
3. **Top bar:** Inputs, Wi-Fi, Launcher settings (sliders), Settings (gear) and Help as **icon-only** buttons.
   Help opens a card listing the launcher's keys (hold OK on an app or the gear, Back, Home, reorder, where
   `config.json` lives). Inputs lists only the TV's physical ports (HDMI in port order, then AV) and its
   built-in tuner, with a connected / standby badge; apps' channel services (Play Movies, VLC...) are left out.
4. **Dock:** show **only the app title above the dock and the apps**. Her dock lives in the local `config.json`.
   A dock entry can also be a TV input (`dock_apps` entry `"input:<TvInputInfo id>"`, named through
   `input_labels`), e.g. a game console on an HDMI port. No "dock 01/04" badge, no
   "last opened" line, no "DOCK" tab. The focus border is plain: **no extra corner marks**.
5. **Wallpapers from a GitHub folder:** `httpassoca/dotfiles` → `macOS/wallpapers` (branch `main`).
   Download when online, keep a cache, **use the cache when offline**. Pick a wallpaper in the launcher
   (Launcher settings → Picture), or rotate the folder in **shuffle** or **sequential** order at a chosen interval.
6. **Web first, then the TV.** `web/index.html` is the design reference. `android/` is the TV port
   (this repo's main product). Both must stay in step (see "Keeping web and Android in step").
7. **It is a repo.** Continue work from any machine using this file.

### Planned next (she asked to keep note)

- **Web remote ("phone remote").** A page any browser on the LAN can open to drive the TV: D-pad, OK
  with long press, Back, Home, Settings, and text entry. **Styled with dssoca.** The launcher (or a small
  companion service) needs a LAN listener for this, so it needs some form of pairing or auth.
- **Idle lock screen.** After N minutes untouched on Home: big Electrolize clock, date and the weather
  line, drifting slowly to protect the panel. CleanYourTV has the same idea (`home_idle_clock_minutes`).

### Ideas discussed, not decided

Race-weekend panel under the clock (CleanYourTV has one for F1 via `race_calendar`), wallpaper sets by time
of day (`wallpaper_moods`), sync `config.json` from a private repo like the wallpapers, home-server status
dots, a "continue watching" row from a home media server, profiles, and a dssoca SearchPalette on a remote
button.

## Layout of the repo

```
web/                     design reference: one HTML page + vendored dssoca 0.19 CSS + a wallpaper snapshot
  index.html             sections: 1 config, 2 catalog, 3 store, 4 layout, 5 views, 6 focus, 7 motion, 8 input, 9 boot
  dssoca/                theme.css + vanilla.css from npm dssoca@0.19.0 (Caskaydia @font-face removed)
  wall-dotfiles/         gitignored: run web/fetch-wallpapers.sh to download the pictures (third-party art)
android/                 the TV launcher, Java, no runtime dependencies
  app/src/main/java/dev/passoca/tvhome/
    core/                pure Java, unit-tested; shared logic with web/ (see table below)
    data/                Android I/O: installed apps, TV inputs, wallpaper sync + cache, weather cache, prefs
    ui/                  HomeActivity (stage, render, key routing), Dss (tokens), Views, MenuPanel, SettingsSheet
  app/src/main/res/      dssoca tokens as colors.xml / dimens.xml, fonts, vector icons, banner
  app/src/test/...       JUnit tests for core/
licenses/                MIT (CleanYourTV, dssoca), OFL (Electrolize, JetBrains Mono)
```

| web/index.html section | Android |
|---|---|
| 1 CONFIG | `core/Config` (+ `data/Prefs` for on-TV choices and `config.json`) |
| 3 STORE / 6 FOCUS ENGINE | `core/HomeModel` (tiles, top bar, reorder, scroll, menu stepping); zones in `ui/HomeActivity` |
| 4 LAYOUT + wallpaper rules | `core/Layout`, `core/WallpaperRotation` |
| 7 MOTION spring | `core/Spring` (driven by Choreographer in `HomeActivity`) |
| Info (weather, world clock) | `core/WeatherReading`, `core/WorldClock`, `data/WeatherRepository` |
| (TV only) GitHub folder sync | `core/WallpaperSync`, `data/WallpaperRepository` |
| 5 VIEWS (dssoca markup) | `ui/*` drawn from `ui/Dss` tokens |

## How it works (Android)

- **Stage:** everything is placed in dp on a 960 x 540 canvas (what a 1080p TV reports at density 2),
  scaled to fit the real screen. Positions come from `core/Layout` only.
- **Keys:** `HomeActivity.dispatchKeyEvent` routes every key by zone (TILES, TOP, MENU, SHEET), the same
  way the web prototype's focus engine does. Android view focus is not used. OK held for 500 ms is a long
  press: on a tile it opens App info; on Settings it opens the launcher menu (Launcher settings, Reorder apps).
  The remote's Settings key (`KEYCODE_SETTINGS`) opens the quick panel, except on TVs whose system takes
  that key first (her TCL Google TV does), so Launcher settings also has its own top-bar button.
  Home while on Home resets focus.
- **Focus:** the tile zooms to 1.10 (1.12 with advanced effects), overshooting when `focus_overshoot` is on.
  A square 2 dp accent frame (border only, **no glow**) follows on a spring (260/0.58, or 320/0.76 advanced, or
  critically damped without overshoot). The ambient glow takes the app's colour, sampled from its banner.
- **Settings:** defaults ← `config.json` ← choices made on the TV. "Follow config" clears the TV choices.
  `config.json` lives in `/sdcard/Android/data/dev.passoca.tvhome/files/config.json` and uses
  CleanYourTV's key names plus `weather`, `world_clocks`, `wallpaper_source`, `input_labels` and
  `removed_apps` (listed at the end of the Help card), for example:
  ```json
  { "dock_apps": ["com.netflix.ninja", "com.google.android.youtube.tv"],
    "columns": 4,
    "weather": { "latitude": 0.0, "longitude": 0.0, "units": "celsius", "refresh_minutes": 30 },
    "world_clocks": [{ "label": "Tokyo", "tz": "Asia/Tokyo" }],
    "wallpaper_source": { "repo": "httpassoca/dotfiles", "branch": "main", "path": "macOS/wallpapers" },
    "wallpaper_order": "shuffle", "wallpaper_rotation_minutes": 60, "wallpaper_dim": 45 }
  ```
  Weather stays hidden until a latitude and longitude are set. There is deliberately no location permission.
- **Wallpaper sync:** when online, at start and then every 3 hours, one call to
  `api.github.com/repos/<repo>/contents/<path>?ref=<branch>` lists the folder. Each file's git `sha` is
  compared with the cached manifest, new or changed pictures are downloaded through a `.part` file, and
  removed ones are deleted. Rotation reads only the cache. Limits: images only (jpg, png, webp), 10 MB
  each, 100 files. Anonymous GitHub API calls are limited to about 60 an hour, which is far above this
  usage.
- **Weather:** Open-Meteo (`api.open-meteo.com`, no key). It is cached with its fetch time, refreshed
  every `refresh_minutes`, still shown offline, and hidden after 6 hours.
- **Panels:** menus draw above the settings sheet (elevation 24 vs 16) and are lifted to stay on screen.
  Leaving Home (onStop) closes every panel and ends reorder, so coming back is always controllable.
- **Retries:** wallpaper sync every 3 h after a good sync, 10 min after a failed or offline one (checked
  on each 15 s tick, not only on resume); weather is asked for at most every 10 min while it is due;
  a picture that fails to decode, or a download that is not an image, is retried later.
- **Permissions:** INTERNET and ACCESS_NETWORK_STATE, for those two calls only. This differs from stock
  CleanYourTV, which has no internet permission.

## Commands

```bash
cd android
./gradlew testDebugUnitTest            # 47 JVM tests for core/
./gradlew lintDebug                    # 0 errors expected
./gradlew assembleDebug                # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease              # minified, signed with this machine's debug key (personal build)

# install on the TV (ADB over network), make it Home, keep the stock launcher as fallback
adb connect <tv-ip>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd package set-home-activity --user 0 dev.passoca.tvhome/.ui.HomeActivity
adb push ../config.json /sdcard/Android/data/dev.passoca.tvhome/files/config.json
# undo: pick the original home in Settings, or
adb shell cmd package set-home-activity --user 0 <stock launcher component>
```

Requirements: JDK 17+ and Android SDK platform 35 with build-tools 35. Set `sdk.dir` in
`android/local.properties`. AGP 8.7.3 and the Gradle 8.14.3 wrapper are included.
Do **not** disable the stock launcher until this one has been tried on the TV.

**Her TV (TCL C655, Google TV, Android 12):** `set-home-activity` and `cmd role add-role-holder
android.app.role.HOME dev.passoca.tvhome` are accepted, but Google TV's launcherx declares its HOME filter
at system priority 2, so the Home key still opens it. **Done on 2026-10-24:** tvssoca is Home after
```bash
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx      # Google TV home
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith   # its HOME RecoveryActivity (priority 1) shows a black screen otherwise
```
Undo (restores Google TV home): `adb shell pm enable com.google.android.tungsten.setupwraith` and
`adb shell pm enable com.google.android.apps.tv.launcherx`. The remote's Home key needs no change: it sends
the HOME intent, which now only tvssoca answers. A TCL OTA or a factory reset may re-enable both.
The remote's Settings key is still taken by the system.

## Keeping web and Android in step

- A behaviour change goes into `core/` first, with a test, and then into `web/index.html`'s matching section.
- Colours, type and spacing come only from dssoca tokens (`--ss-*` on the web, `res/values/colors.xml`
  and `ui/Dss` on Android). On Android the tokens are resolved hex values of dssoca's dark theme.
  Recompute them if dssoca changes: elevation mixes are done in OKLab, as CSS `color-mix(in oklab, …)` does.
- Never reintroduce: the greeting, dock labels or badges, corner brackets or a glow on the focus frame,
  boxed weather/clock cards, labelled top buttons, the light theme, or other sizes.

## Status (2026-10-24)

Verified in the build sandbox:
- 47 unit tests pass (config parsing, layout geometry, spring, focus model, wallpaper rotation and
  sync plan, Open-Meteo parsing and cache ageing, world clock across a DST change).
- `assembleDebug`, `assembleRelease` and `lintDebug` succeed: 0 errors, 31 warnings. Most warnings are
  dssoca size tokens in `dimens.xml` that the code does not reference yet (it uses dp literals that
  match them), plus ViewConstructor on code-only views.

**Verified on her TV (2026-10-24):** header, dock and grid, input tile, Inputs menu, Help card, Launcher
settings and the wallpaper picker over it, wallpaper sync from GitHub, panels closed after leaving Home.
Idle on Home: 1 frame in 20 s, about 0% CPU.
Performance notes (details in the local important.md): the TV GPU-upscales every app to 4K, so frame count is what matters; keep
animations short. App list loading is parallel; wallpapers are decoded to screen size; launch never waits on motion.

**Not yet verified:**
- Long press OK with the real IR remote (adb's `--longpress` releases at 400 ms, before the 500 ms timer).
- The web page's wallpapers are a fixed snapshot. Only the Android build syncs from GitHub.

### Next steps, in order

1. Check long press OK with the real IR remote now that tvssoca is Home.
2. Set the weather coordinates in `config.json`.
3. Idle lock screen.
4. Web remote.
5. Wire `dimens.xml` tokens into `ui/` (or drop them) so lint is quiet.
6. CI: `.github/workflows/android.yml` already runs tests, lint and the build on push.

## Notes for the build sandbox

The sandbox this was built in hit Maven Central rate limits. Its `~/.gradle/init.d` pointed Gradle at
Google's mirror of Central. That is not part of the repo; on a normal machine the plain
`google()` / `mavenCentral()` repositories work.
