package dev.passoca.tvhome.core;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class WallpaperTest {
    private static final List<String> FILES = List.of("a.jpg", "b.jpg", "c.jpg");

    @Test public void shuffleNeverRepeats() {
        Random r = new Random(1);
        for (int i = 0; i < 200; i++) assertNotEquals(1, WallpaperRotation.next(1, 3, Config.ORDER_SHUFFLE, r));
    }

    @Test public void sequentialWraps() {
        assertEquals(0, WallpaperRotation.next(2, 3, Config.ORDER_SEQUENTIAL, new Random()));
        assertEquals(0, WallpaperRotation.next(-1, 3, Config.ORDER_SEQUENTIAL, new Random()));
        assertEquals(0, WallpaperRotation.next(0, 1, Config.ORDER_SHUFFLE, new Random()));
    }

    @Test public void choiceRules() {
        assertEquals(-1, WallpaperRotation.current(FILES, WallpaperRotation.NONE, 0, true));
        assertEquals(-1, WallpaperRotation.current(FILES, null, 0, false));
        assertEquals(-1, WallpaperRotation.current(List.of(), null, 0, true));
        assertEquals(2, WallpaperRotation.current(FILES, "c.jpg", 0, true));
        assertEquals("chosen file left the folder: rotate", 1, WallpaperRotation.current(FILES, "gone.jpg", 1, true));
        assertEquals(2, WallpaperRotation.current(FILES, null, 9, true));
    }

    @Test public void rotationOnlyWithoutAChoice() {
        assertTrue(WallpaperRotation.rotating(null, true, 60));
        assertFalse(WallpaperRotation.rotating("a.jpg", true, 60));
        assertFalse(WallpaperRotation.rotating(null, true, 0));
        assertTrue(WallpaperRotation.due(3_600_000L, 0L, 60));
        assertFalse(WallpaperRotation.due(3_599_999L, 0L, 60));
    }

    @Test public void labelsFromFileNames() {
        assertEquals("Japan background digital art", WallpaperRotation.label("japan-background-digital-art.jpg"));
        assertEquals("4k anime scenery wallpapers", WallpaperRotation.label("wp5683360-4k-anime-scenery-wallpapers.jpg"));
        assertEquals("A house with a bike and a chair", WallpaperRotation.label("a_house_with_a_bike_and_a_chair.jpg"));
    }

    @Test public void apiUrlForTheDotfilesFolder() {
        assertEquals("https://api.github.com/repos/httpassoca/dotfiles/contents/macOS/wallpapers?ref=main",
                WallpaperSync.apiUrl(Config.parse(null).wallpaperSource));
    }

    @Test public void listingKeepsImagesWithinLimits() {
        String json = "[{\"type\":\"file\",\"name\":\"b.png\",\"sha\":\"2\",\"size\":10,\"download_url\":\"https://raw/b.png\"},"
                + "{\"type\":\"file\",\"name\":\"a.JPG\",\"sha\":\"1\",\"size\":10,\"download_url\":\"https://raw/a.JPG\"},"
                + "{\"type\":\"dir\",\"name\":\"sub\",\"sha\":\"3\",\"size\":0},"
                + "{\"type\":\"file\",\"name\":\"notes.md\",\"sha\":\"4\",\"size\":10,\"download_url\":\"https://raw/n\"},"
                + "{\"type\":\"file\",\"name\":\"huge.jpg\",\"sha\":\"5\",\"size\":20000000,\"download_url\":\"https://raw/h\"},"
                + "{\"type\":\"file\",\"name\":\"http.jpg\",\"sha\":\"6\",\"size\":10,\"download_url\":\"http://raw/x\"}]";
        List<WallpaperSync.Remote> l = WallpaperSync.parseListing(json);
        assertEquals(2, l.size());
        assertEquals("a.JPG", l.get(0).name);
        assertEquals("b.png", l.get(1).name);
        assertTrue(WallpaperSync.parseListing("{\"message\":\"rate limited\"}").isEmpty());
    }

    @Test public void planDownloadsChangesAndDeletesRemovals() {
        List<WallpaperSync.Remote> remote = List.of(
                new WallpaperSync.Remote("a.jpg", "1", 10, "https://x/a"),
                new WallpaperSync.Remote("b.jpg", "2new", 10, "https://x/b"),
                new WallpaperSync.Remote("c.jpg", "3", 10, "https://x/c"));
        Map<String, String> cached = new HashMap<>();
        cached.put("a.jpg", "1");
        cached.put("b.jpg", "2");
        cached.put("old.jpg", "9");
        WallpaperSync.Plan p = WallpaperSync.plan(remote, cached);
        assertEquals(2, p.download.size());
        assertEquals("b.jpg", p.download.get(0).name);
        assertEquals("c.jpg", p.download.get(1).name);
        assertEquals(List.of("old.jpg"), p.delete);
        assertEquals(3, p.manifest.size());
        assertTrue(WallpaperSync.plan(remote, p.manifest).isEmpty());
    }

    @Test public void manifestRoundTrips() {
        Map<String, String> m = new HashMap<>();
        m.put("a.jpg", "1");
        assertEquals(m, WallpaperSync.manifestFromJson(WallpaperSync.manifestToJson(m)));
        assertTrue(WallpaperSync.manifestFromJson("garbage").isEmpty());
        assertTrue(WallpaperSync.manifestFromJson(null).isEmpty());
    }
}
