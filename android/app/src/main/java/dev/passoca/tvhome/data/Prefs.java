package dev.passoca.tvhome.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.passoca.tvhome.core.Config;

/**
 * Where settings come from: the built-in defaults, overlaid by an optional config.json in the app's
 * external files folder (pushed over ADB, or later synced from the repo), overlaid by choices made on
 * the TV in Launcher settings. "Follow config" clears the on-TV choices.
 */
public final class Prefs {

    private final Context ctx;

    public Prefs(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    private SharedPreferences p() { return ctx.getSharedPreferences("choices", Context.MODE_PRIVATE); }

    public Config load() {
        Config c = Config.parse(readConfigFile());
        SharedPreferences p = p();
        if (p.contains("columns")) c.columns = p.getInt("columns", c.columns);
        if (p.contains("animations")) c.animations = p.getBoolean("animations", c.animations);
        if (p.contains("advanced_effects")) c.advancedEffects = p.getBoolean("advanced_effects", c.advancedEffects);
        if (p.contains("focus_overshoot")) c.focusOvershoot = p.getBoolean("focus_overshoot", c.focusOvershoot);
        if (p.contains("ambient_glow")) c.ambientGlow = p.getBoolean("ambient_glow", c.ambientGlow);
        if (p.contains("wallpaper_order")) c.wallpaperOrder = p.getString("wallpaper_order", c.wallpaperOrder);
        if (p.contains("wallpaper_rotation_minutes")) c.wallpaperRotationMinutes = p.getInt("wallpaper_rotation_minutes", c.wallpaperRotationMinutes);
        return c;
    }

    private String readConfigFile() {
        File dir = ctx.getExternalFilesDir(null);
        File f = dir == null ? null : new File(dir, "config.json");
        if (f == null || !f.isFile() || f.length() > 64 * 1024) return null;
        try { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); } catch (Exception e) { return null; }
    }

    public void putInt(String k, int v) { p().edit().putInt(k, v).apply(); }
    public void putBool(String k, boolean v) { p().edit().putBoolean(k, v).apply(); }
    public void putString(String k, String v) { p().edit().putString(k, v).apply(); }

    /** Clears every on-TV choice, the saved order and the wallpaper pick. */
    public void followConfig() { p().edit().clear().apply(); }

    public List<String> order() {
        String s = p().getString("order", "");
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split("\n")));
    }

    public void saveOrder(List<String> order) { p().edit().putString("order", String.join("\n", order)).apply(); }

    /** Wallpaper pick: null rotates, "none", or a file name. */
    public String wallpaperChoice() { return p().getString("wallpaper_choice", null); }
    public void setWallpaperChoice(String v) {
        if (v == null) p().edit().remove("wallpaper_choice").apply(); else p().edit().putString("wallpaper_choice", v).apply();
    }

    public int wallIndex() { return p().getInt("wall_index", -1); }
    public long wallSince() { return p().getLong("wall_since", 0L); }
    public void setWall(int index, long since) { p().edit().putInt("wall_index", index).putLong("wall_since", since).apply(); }
}
