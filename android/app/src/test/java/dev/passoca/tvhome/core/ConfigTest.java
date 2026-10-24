package dev.passoca.tvhome.core;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;

public class ConfigTest {
    @Test public void defaultsMatchTheWebPrototype() {
        Config c = Config.parse(null);
        assertEquals(4, c.columns);
        assertTrue(c.animations);
        assertFalse(c.advancedEffects);
        assertTrue(c.focusOvershoot);
        assertTrue(c.wallpaper);
        assertEquals(60, c.wallpaperRotationMinutes);
        assertEquals(Config.ORDER_SHUFFLE, c.wallpaperOrder);
        assertEquals("httpassoca/dotfiles", c.wallpaperSource.repo);
        assertEquals("macOS/wallpapers", c.wallpaperSource.path);
        assertFalse("no place configured, so no weather", c.weather.isSet());
        assertEquals("Asia/Tokyo", c.worldClocks.get(0).zone);
    }

    @Test public void unparsableTextKeepsDefaults() {
        Config c = Config.parse("{ not json");
        assertEquals(4, c.columns);
    }

    @Test public void wrongTypesAndOutOfRangeValuesAreIgnored() {
        Config c = Config.parse("{\"columns\":\"6\",\"animations\":\"no\",\"wallpaper_dim\":120,\"wallpaper_rotation_minutes\":2.5}");
        assertEquals(4, c.columns);
        assertTrue(c.animations);
        assertEquals(45, c.wallpaperDim);
        assertEquals(60, c.wallpaperRotationMinutes);
    }

    @Test public void readsKnownKeys() {
        Config c = Config.parse("{\"columns\":6,\"dock_apps\":[\"a\",\"\",\"b\",3],\"wallpaper_order\":\"sequential\","
                + "\"weather\":{\"latitude\":48.85,\"longitude\":2.35,\"units\":\"fahrenheit\"},"
                + "\"world_clocks\":[{\"label\":\"Rio\",\"tz\":\"America/Sao_Paulo\"},{\"label\":\"Bad\",\"tz\":\"Mars/Base\"}],"
                + "\"wallpaper_source\":{\"repo\":\"me/walls\",\"path\":\"tv\"}}");
        assertEquals(6, c.columns);
        assertEquals(List.of("a", "b"), c.dockApps);
        assertEquals(Config.ORDER_SEQUENTIAL, c.wallpaperOrder);
        assertTrue(c.weather.isSet());
        assertEquals("fahrenheit", c.weather.units);
        assertEquals(1, c.worldClocks.size());
        assertEquals("Rio", c.worldClocks.get(0).label);
        assertEquals("me/walls", c.wallpaperSource.repo);
        assertEquals("main", c.wallpaperSource.branch);
        assertEquals("tv", c.wallpaperSource.path);
    }

    @Test public void unknownOrderFallsBackToShuffle() {
        assertEquals(Config.ORDER_SHUFFLE, Config.parse("{\"wallpaper_order\":\"random\"}").wallpaperOrder);
    }

    @Test public void copyIsIndependent() {
        Config a = Config.parse(null);
        Config b = a.copy();
        b.columns = 6;
        assertEquals(4, a.columns);
    }
}
