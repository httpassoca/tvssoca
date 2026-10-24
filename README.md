# tvssoca

An Android TV home screen drawn with the [dssoca](https://github.com/httpassoca/dssoca) design system,
built on the behaviour of the [CleanYourTV](https://github.com/Gathin23/cleanyourtv) launcher.

- **Header:** a clock and date, with the weather and world clocks on a line underneath. Both are built
  into the launcher, so no add-on apps are needed.
- **Top bar:** icon-only Inputs, Wi-Fi and Settings buttons.
- **Dock and grid:** the dock and the grid of installed apps, with a springy square focus frame and a
  glow in the focused app's colour.
- **Wallpapers:** synced from a GitHub folder and cached for offline use. You can rotate them in
  shuffle or sequential order, or pick one in Launcher settings.

`web/` is the interactive design reference; open `web/index.html` in a browser. `android/` is the launcher.
See [CLAUDE.md](CLAUDE.md) for the full brief, the architecture and the build commands.

```bash
cd android && ./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Files that are not in git

This repo is public, so three things stay on the owner's machines only and are listed in `.gitignore`:

| Local file | What it holds | How to get or recreate it |
|---|---|---|
| `important.md` | The live state of the owner's TV: its LAN address and ADB access, what was disabled to make tvssoca Home and how to undo it, removed apps, usage and performance notes, the build machine | Private notes; keep a copy outside the repo |
| `config.json` | The real launcher config pushed to the TV (dock, inputs, removed apps) | Start from `config.example.json` |
| `web/wall-dotfiles/` | The prototype's wallpapers, from `httpassoca/dotfiles/macOS/wallpapers` (third-party artwork, so it is not redistributed here) | `web/fetch-wallpapers.sh` |

Licences: the CleanYourTV and dssoca MIT notices, and the OFL fonts, are in `licenses/`.
