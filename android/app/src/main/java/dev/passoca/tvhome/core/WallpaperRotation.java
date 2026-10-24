package dev.passoca.tvhome.core;

import java.util.List;
import java.util.Random;

/**
 * Which wallpaper shows. Mirrors the CleanYourTV launcher's WallpaperRotation and the Launcher settings
 * Wallpaper row: rotate the collection (the default), one chosen picture (rotation stops), or none.
 */
public final class WallpaperRotation {

    /** Saved choice: null rotates the collection, {@link #NONE} draws no picture, any other value is a file name. */
    public static final String NONE = "none";

    private WallpaperRotation() {}

    /** Shuffle never repeats the current picture; sequential walks filename order and wraps. */
    public static int next(int current, int count, String order, Random random) {
        if (count <= 1) return 0;
        if (Config.ORDER_SEQUENTIAL.equals(order)) return (current + 1 + count) % count;
        int j;
        do { j = random.nextInt(count); } while (j == current);
        return j;
    }

    /** Index to show, or -1 for no picture. A chosen file that left the collection falls back to rotating. */
    public static int current(List<String> files, String choice, int rotatingIndex, boolean wallpaperOn) {
        if (!wallpaperOn || files.isEmpty() || NONE.equals(choice)) return -1;
        if (choice != null) {
            int i = files.indexOf(choice);
            if (i >= 0) return i;
        }
        return Math.max(0, Math.min(rotatingIndex, files.size() - 1));
    }

    public static boolean rotating(String choice, boolean wallpaperOn, int rotationMinutes) {
        return wallpaperOn && choice == null && rotationMinutes > 0;
    }

    public static boolean due(long nowMs, long shownSinceMs, int rotationMinutes) {
        return rotationMinutes > 0 && nowMs - shownSinceMs >= rotationMinutes * 60_000L;
    }

    /** Picker label from a file name: "wp5683360-4k-anime-scenery-wallpapers.jpg" → "4k anime scenery wallpapers". */
    public static String label(String file) {
        String n = file.replaceAll("\\.[A-Za-z0-9]+$", "").replaceAll("^(wp\\d+|wallhaven)-", "").replaceAll("[-_]+", " ").trim();
        return n.isEmpty() ? file : Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
