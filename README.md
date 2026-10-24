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

Licences: the CleanYourTV and dssoca MIT notices, and the OFL fonts, are in `licenses/`. The pictures in
`web/wall-dotfiles/` are a snapshot of `httpassoca/dotfiles/macOS/wallpapers`, kept for the prototype
only. They are third-party artwork, so don't redistribute them if this repo goes public.
