package dev.passoca.tvhome.data;

import android.content.Context;
import android.content.SharedPreferences;

import dev.passoca.tvhome.core.Config;
import dev.passoca.tvhome.core.WeatherReading;

/** Header weather: refresh from Open-Meteo when due and online, otherwise the cached reading. */
public final class WeatherRepository {

    private final Context ctx;

    public WeatherRepository(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    private SharedPreferences prefs() { return ctx.getSharedPreferences("weather", Context.MODE_PRIVATE); }

    public WeatherReading cached() { return WeatherReading.fromJson(prefs().getString("last", null)); }

    /** Blocking; call off the main thread. Returns the reading to show, or null. */
    public WeatherReading refresh(Config.Weather w, long now) {
        WeatherReading last = cached();
        if (!w.isSet()) return null;
        if (last != null && !last.shouldRefresh(now, w.refreshMinutes)) return last;
        if (!Net.online(ctx)) return last;
        try {
            WeatherReading fresh = WeatherReading.parse(Net.getText(WeatherReading.url(w), 200_000), now);
            if (fresh == null) return last;
            prefs().edit().putString("last", fresh.toJson()).apply();
            return fresh;
        } catch (Exception e) {
            return last;
        }
    }
}
