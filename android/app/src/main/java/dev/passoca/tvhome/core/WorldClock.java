package dev.passoca.tvhome.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** Header text: the clock, the date and the world clocks. Pure time-zone maths, offline and DST-aware. */
public final class WorldClock {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private WorldClock() {}

    public static boolean isValidZone(String id) {
        try { ZoneId.of(id); return true; } catch (Exception e) { return false; }
    }

    /** "04:46" in the given zone. */
    public static String timeIn(Instant now, String zone) {
        return HHMM.format(now.atZone(ZoneId.of(zone)));
    }

    /** Main clock: "7:46" in 12-hour mode, "19:46" in 24-hour mode. */
    public static String clock(ZonedDateTime t, boolean is24Hour) {
        if (is24Hour) return String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
        int h = t.getHour() % 12;
        return String.format(Locale.ROOT, "%d:%02d", h == 0 ? 12 : h, t.getMinute());
    }

    public static String ampm(ZonedDateTime t, boolean is24Hour) {
        return is24Hour ? "" : (t.getHour() < 12 ? "AM" : "PM");
    }

    /** CleanYourTV's date_format "EEEE  d MMMM": "Saturday  24 October" (shown upper-cased). */
    public static String date(ZonedDateTime t, Locale locale) {
        return t.getDayOfWeek().getDisplayName(TextStyle.FULL, locale) + "  " + t.getDayOfMonth() + " "
                + t.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale);
    }
}
