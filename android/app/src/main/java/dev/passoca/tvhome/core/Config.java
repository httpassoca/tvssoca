package dev.passoca.tvhome.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Launcher configuration. Key names follow the CleanYourTV launcher's config.xml / theme config.json
 * (themes/README.md) so a config written for one reads the same in the other, plus this project's own keys
 * (weather, world_clocks, wallpaper_source, input_labels). A dock_apps entry may also be a TV input:
 * {@code "input:<TvInputInfo id>"} puts that HDMI or AV port on the dock as a tile of its own.
 *
 * <p>Same rule as CleanYourTV: every key is optional, a missing, unknown or wrongly typed key keeps the
 * default, and a file that does not parse is ignored as a whole, so a config can never break Home.
 */
public final class Config {

    public static final String ORDER_SHUFFLE = "shuffle";
    public static final String ORDER_SEQUENTIAL = "sequential";

    /** Prefix of a dock_apps entry that names a TV input instead of a package. */
    public static final String INPUT_PREFIX = "input:";

    public List<String> dockApps = List.of();
    public List<String> hiddenApps = List.of();
    public int columns = 4;
    public boolean animations = true;
    public boolean advancedEffects = false;
    public boolean focusOvershoot = true;
    public boolean ambientGlow = true;
    public boolean dockTitle = true;
    public boolean reducedMotion = false;

    public boolean wallpaper = true;
    public int wallpaperRotationMinutes = 60;
    public String wallpaperOrder = ORDER_SHUFFLE;
    public boolean wallpaperCaptions = false;
    public int wallpaperDim = 45;
    public WallpaperSource wallpaperSource = new WallpaperSource("httpassoca/dotfiles", "main", "macOS/wallpapers");

    public Weather weather = new Weather(Double.NaN, Double.NaN, "celsius", 30);
    public List<WorldClockEntry> worldClocks = List.of(new WorldClockEntry("Tokyo", "Asia/Tokyo"));
    /** Tile names for input entries on the dock, keyed by the full "input:..." entry. */
    public Map<String, String> inputLabels = Map.of();

    public static final class WallpaperSource {
        public final String repo, branch, path;
        public WallpaperSource(String repo, String branch, String path) { this.repo = repo; this.branch = branch; this.path = path; }
        public boolean isSet() { return repo != null && !repo.isEmpty() && path != null; }
    }

    public static final class Weather {
        public final double latitude, longitude;
        public final String units;
        public final int refreshMinutes;
        public Weather(double latitude, double longitude, String units, int refreshMinutes) {
            this.latitude = latitude; this.longitude = longitude; this.units = units; this.refreshMinutes = refreshMinutes;
        }
        /** Weather is shown only once a place is configured; there is no location permission. */
        public boolean isSet() { return !Double.isNaN(latitude) && !Double.isNaN(longitude); }
    }

    public static final class WorldClockEntry {
        public final String label, zone;
        public WorldClockEntry(String label, String zone) { this.label = label; this.zone = zone; }
    }

    public Config copy() {
        Config c = new Config();
        c.dockApps = dockApps; c.hiddenApps = hiddenApps; c.columns = columns; c.animations = animations;
        c.advancedEffects = advancedEffects; c.focusOvershoot = focusOvershoot; c.ambientGlow = ambientGlow;
        c.dockTitle = dockTitle; c.reducedMotion = reducedMotion; c.wallpaper = wallpaper;
        c.wallpaperRotationMinutes = wallpaperRotationMinutes; c.wallpaperOrder = wallpaperOrder;
        c.wallpaperCaptions = wallpaperCaptions; c.wallpaperDim = wallpaperDim; c.wallpaperSource = wallpaperSource;
        c.weather = weather; c.worldClocks = worldClocks; c.inputLabels = inputLabels;
        return c;
    }

    /** Defaults overlaid with a config.json text. Null or unparsable text returns plain defaults. */
    public static Config parse(String json) {
        Config c = new Config();
        if (json == null || json.trim().isEmpty()) return c;
        JSONObject o;
        try { o = new JSONObject(json); } catch (Exception e) { return c; }
        c.dockApps = strings(o, "dock_apps", c.dockApps);
        c.hiddenApps = strings(o, "hidden_apps", c.hiddenApps);
        int cols = integer(o, "columns", c.columns, 4, 6);
        c.columns = cols;
        c.animations = bool(o, "animations", c.animations);
        c.advancedEffects = bool(o, "advanced_effects", c.advancedEffects);
        c.focusOvershoot = bool(o, "focus_overshoot", c.focusOvershoot);
        c.ambientGlow = bool(o, "ambient_glow", c.ambientGlow);
        c.dockTitle = bool(o, "dock_title", c.dockTitle);
        c.reducedMotion = bool(o, "reduced_motion", c.reducedMotion);
        c.wallpaper = bool(o, "wallpaper", c.wallpaper);
        c.wallpaperRotationMinutes = integer(o, "wallpaper_rotation_minutes", c.wallpaperRotationMinutes, 0, 1440);
        String order = o.optString("wallpaper_order", c.wallpaperOrder);
        c.wallpaperOrder = ORDER_SEQUENTIAL.equals(order) ? ORDER_SEQUENTIAL : ORDER_SHUFFLE;
        c.wallpaperCaptions = bool(o, "wallpaper_captions", c.wallpaperCaptions);
        c.wallpaperDim = integer(o, "wallpaper_dim", c.wallpaperDim, 0, 90);
        JSONObject src = o.optJSONObject("wallpaper_source");
        if (src != null) {
            c.wallpaperSource = new WallpaperSource(src.optString("repo", c.wallpaperSource.repo),
                    src.optString("branch", c.wallpaperSource.branch), src.optString("path", c.wallpaperSource.path));
        }
        JSONObject w = o.optJSONObject("weather");
        if (w != null) {
            c.weather = new Weather(number(w, "latitude", -90, 90), number(w, "longitude", -180, 180),
                    "fahrenheit".equals(w.optString("units")) ? "fahrenheit" : "celsius",
                    integer(w, "refresh_minutes", c.weather.refreshMinutes, 10, 1440));
        }
        JSONArray clocks = o.optJSONArray("world_clocks");
        if (clocks != null) {
            List<WorldClockEntry> list = new ArrayList<>();
            for (int i = 0; i < clocks.length() && list.size() < 3; i++) {
                JSONObject e = clocks.optJSONObject(i);
                if (e == null) continue;
                String label = e.optString("label", ""), tz = e.optString("tz", "");
                if (!label.isEmpty() && WorldClock.isValidZone(tz)) list.add(new WorldClockEntry(label, tz));
            }
            c.worldClocks = Collections.unmodifiableList(list);
        }
        JSONObject labels = o.optJSONObject("input_labels");
        if (labels != null) {
            Map<String, String> m = new LinkedHashMap<>();
            for (java.util.Iterator<String> it = labels.keys(); it.hasNext(); ) {
                String k = it.next();
                Object v = labels.opt(k);
                if (k.startsWith(INPUT_PREFIX) && v instanceof String && !((String) v).isEmpty()) m.put(k, (String) v);
            }
            c.inputLabels = Collections.unmodifiableMap(m);
        }
        return c;
    }

    public static boolean isInput(String dockEntry) { return dockEntry.startsWith(INPUT_PREFIX); }

    public static String inputId(String dockEntry) { return dockEntry.substring(INPUT_PREFIX.length()); }

    private static boolean bool(JSONObject o, String key, boolean def) {
        Object v = o.opt(key);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    private static int integer(JSONObject o, String key, int def, int min, int max) {
        Object v = o.opt(key);
        if (!(v instanceof Number)) return def;
        double d = ((Number) v).doubleValue();
        if (d != Math.rint(d) || d < min || d > max) return def;
        return (int) d;
    }

    private static double number(JSONObject o, String key, double min, double max) {
        Object v = o.opt(key);
        if (!(v instanceof Number)) return Double.NaN;
        double d = ((Number) v).doubleValue();
        return d < min || d > max ? Double.NaN : d;
    }

    private static List<String> strings(JSONObject o, String key, List<String> def) {
        JSONArray a = o.optJSONArray(key);
        if (a == null) return def;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            Object v = a.opt(i);
            if (v instanceof String && !((String) v).isEmpty()) out.add((String) v);
        }
        return Collections.unmodifiableList(out);
    }
}
