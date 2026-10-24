package dev.passoca.tvhome.core;

import static org.junit.Assert.*;
import org.junit.Test;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

public class WeatherAndClockTest {
    @Test public void parsesOpenMeteo() {
        String json = "{\"current\":{\"time\":\"2026-10-24T19:45\",\"temperature_2m\":16.6,\"weather_code\":2}}";
        WeatherReading r = WeatherReading.parse(json, 1000L);
        assertNotNull(r);
        assertEquals(17, r.temperature);
        assertEquals("partly cloudy", WeatherReading.describe(r.code));
        assertNull(WeatherReading.parse("{\"current\":{}}", 0L));
        assertNull(WeatherReading.parse("nope", 0L));
    }

    @Test public void cacheRoundTripsAndAges() {
        WeatherReading r = new WeatherReading(17, 2, 1_000_000L);
        WeatherReading back = WeatherReading.fromJson(r.toJson());
        assertEquals(17, back.temperature);
        assertEquals(1_000_000L, back.fetchedAt);
        assertFalse(r.shouldRefresh(1_000_000L + 29 * 60_000L, 30));
        assertTrue(r.shouldRefresh(1_000_000L + 30 * 60_000L, 30));
        assertTrue("last reading still shows offline", r.showable(1_000_000L + 5 * 3_600_000L));
        assertFalse(r.showable(1_000_000L + 7 * 3_600_000L));
        assertNull(WeatherReading.fromJson("{}"));
    }

    @Test public void urlNeedsNoKey() {
        Config c = Config.parse("{\"weather\":{\"latitude\":48.85,\"longitude\":2.35}}");
        String u = WeatherReading.url(c.weather);
        assertTrue(u.startsWith("https://api.open-meteo.com/v1/forecast?latitude=48.85&longitude=2.35"));
        assertFalse(u.contains("key"));
    }

    @Test public void describesCodeFamilies() {
        assertEquals("clear", WeatherReading.describe(0));
        assertEquals("rain", WeatherReading.describe(63));
        assertEquals("thunderstorm", WeatherReading.describe(95));
        assertEquals("—", WeatherReading.describe(42));
    }

    @Test public void worldClockIsDstAware() {
        // 2026-03-29 01:30 UTC: London has just moved to summer time (UTC+1), Tokyo stays UTC+9
        Instant t = Instant.parse("2026-03-29T01:30:00Z");
        assertEquals("02:30", WorldClock.timeIn(t, "Europe/London"));
        assertEquals("10:30", WorldClock.timeIn(t, "Asia/Tokyo"));
        assertFalse(WorldClock.isValidZone("Mars/Base"));
    }

    @Test public void clockAndDateText() {
        ZonedDateTime t = ZonedDateTime.of(2026, 10, 24, 19, 46, 0, 0, ZoneId.of("Europe/London"));
        assertEquals("7:46", WorldClock.clock(t, false));
        assertEquals("PM", WorldClock.ampm(t, false));
        assertEquals("19:46", WorldClock.clock(t, true));
        assertEquals("", WorldClock.ampm(t, true));
        assertEquals("12:05", WorldClock.clock(t.withHour(0).withMinute(5), false));
        assertEquals("Saturday  24 October", WorldClock.date(t, Locale.ENGLISH));
    }
}
