package dev.passoca.tvhome.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import dev.passoca.tvhome.core.Config;
import dev.passoca.tvhome.core.WallpaperSync;

/**
 * The wallpaper cache: files/wallpapers/ plus a manifest of each file's git sha. {@link #sync} runs when
 * the TV is online and brings the cache in step with the GitHub folder; everything else reads only the
 * cache, so wallpapers keep rotating offline.
 */
public final class WallpaperRepository {

    public static final class Result {
        public final int added, removed;
        public final boolean ok;
        Result(boolean ok, int added, int removed) { this.ok = ok; this.added = added; this.removed = removed; }
    }

    private static final String PREFS = "wallpapers", KEY_MANIFEST = "manifest";

    private final Context ctx;
    private final File dir;

    public WallpaperRepository(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.dir = new File(ctx.getFilesDir(), "wallpapers");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    private SharedPreferences prefs() { return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    /** Cached pictures that finished downloading, in filename order. */
    public synchronized List<String> files() {
        List<String> out = new ArrayList<>();
        for (String name : WallpaperSync.manifestFromJson(prefs().getString(KEY_MANIFEST, null)).keySet()) {
            if (new File(dir, name).isFile()) out.add(name);
        }
        Collections.sort(out);
        return out;
    }

    /** Blocking; call off the main thread. Leaves the cache untouched when offline or on any error. */
    public synchronized Result sync(Config.WallpaperSource source) {
        if (!source.isSet() || !Net.online(ctx)) return new Result(false, 0, 0);
        try {
            List<WallpaperSync.Remote> remote = WallpaperSync.parseListing(Net.getText(WallpaperSync.apiUrl(source), 2_000_000));
            if (remote.isEmpty()) return new Result(false, 0, 0);   // rate limited or empty folder: keep what we have
            Map<String, String> cached = WallpaperSync.manifestFromJson(prefs().getString(KEY_MANIFEST, null));
            WallpaperSync.Plan plan = WallpaperSync.plan(remote, cached);
            int added = 0;
            for (WallpaperSync.Remote r : plan.download) {
                try {
                    Net.download(r.url, new File(dir, r.name), WallpaperSync.MAX_BYTES);
                    cached.put(r.name, r.sha);
                    added++;
                } catch (Exception e) {
                    cached.remove(r.name);   // retried on the next sync
                }
            }
            for (String name : plan.delete) {
                //noinspection ResultOfMethodCallIgnored
                new File(dir, name).delete();
                cached.remove(name);
            }
            prefs().edit().putString(KEY_MANIFEST, WallpaperSync.manifestToJson(cached)).apply();
            return new Result(true, added, plan.delete.size());
        } catch (Exception e) {
            return new Result(false, 0, 0);
        }
    }

    /** Decodes a cached picture at roughly screen size (centre-cropped later by the ImageView). */
    public Bitmap decode(String name, int targetW, int targetH) {
        File f = new File(dir, name);
        if (!f.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= targetW && o.outHeight / (sample * 2) >= targetH) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(f.getPath(), d);
    }
}
