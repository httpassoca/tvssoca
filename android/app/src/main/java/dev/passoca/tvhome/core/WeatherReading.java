package dev.passoca.tvhome.core;

import org.json.JSONObject;

import java.util.Locale;

/**
 * The header's weather: one Open-Meteo call (no API key) made by the launcher itself, cached with the
 * time it was fetched so the last reading still shows offline, and hidden once it is too old to trust.
 */
public final class WeatherReading {

    /** Older than this, the reading is not shown at all. */
    public static final long MAX_AGE_MS = 6 * 60 * 60 * 1000L;

    public final int temperature;
    public final int code;
    public final long fetchedAt;

    public WeatherReading(int temperature, int code, long fetchedAt) {
        this.temperature = temperature; this.code = code; this.fetchedAt = fetchedAt;
    }

    public static String url(Config.Weather w) {
        return String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code&temperature_unit=%s&timezone=auto",
                w.latitude, w.longitude, w.units);
    }

    /** Parses an Open-Meteo forecast answer; null when the fields are missing. */
    public static WeatherReading parse(String json, long now) {
        try {
            JSONObject cur = new JSONObject(json).getJSONObject("current");
            if (!cur.has("temperature_2m") || !cur.has("weather_code")) return null;
            return new WeatherReading((int) Math.round(cur.getDouble("temperature_2m")), cur.getInt("weather_code"), now);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean shouldRefresh(long now, int refreshMinutes) { return now - fetchedAt >= refreshMinutes * 60_000L; }

    public boolean showable(long now) { return now - fetchedAt <= MAX_AGE_MS && now >= fetchedAt - 60_000L; }

    public String toJson() {
        JSONObject o = new JSONObject();
        try { o.put("t", temperature); o.put("c", code); o.put("at", fetchedAt); } catch (Exception ignored) { }
        return o.toString();
    }

    public static WeatherReading fromJson(String json) {
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            return new WeatherReading(o.getInt("t"), o.getInt("c"), o.getLong("at"));
        } catch (Exception e) {
            return null;
        }
    }

    /** WMO weather interpretation codes, as Open-Meteo documents them, in the header's lowercase voice. */
    public static String describe(int code) {
        if (code == 0) return "clear";
        if (code == 1) return "mostly clear";
        if (code == 2) return "partly cloudy";
        if (code == 3) return "overcast";
        if (code == 45 || code == 48) return "fog";
        if (code >= 51 && code <= 57) return "drizzle";
        if (code >= 61 && code <= 67) return "rain";
        if (code >= 71 && code <= 77) return "snow";
        if (code >= 80 && code <= 82) return "showers";
        if (code == 85 || code == 86) return "snow showers";
        if (code >= 95) return "thunderstorm";
        return "—";
    }
}
