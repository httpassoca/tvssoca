package dev.passoca.tvhome.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Installed apps Home can open: every leanback launcher entry, then phone-style launcher entries. */
public final class AppsRepository {

    public static final class App {
        public final String pkg, label;
        public final Drawable banner, icon;
        /** A vivid colour from the artwork, for the ambient glow and the tinted focus. */
        public final int color;
        final Intent launch;

        App(String pkg, String label, Drawable banner, Drawable icon, int color, Intent launch) {
            this.pkg = pkg; this.label = label; this.banner = banner; this.icon = icon; this.color = color; this.launch = launch;
        }

        public Intent launchIntent() { return new Intent(launch); }
    }

    private AppsRepository() {}

    public static List<App> load(Context ctx, List<String> hidden) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, App> apps = new LinkedHashMap<>();
        collect(ctx, pm, Intent.CATEGORY_LEANBACK_LAUNCHER, hidden, apps);
        collect(ctx, pm, Intent.CATEGORY_LAUNCHER, hidden, apps);
        List<App> out = new ArrayList<>(apps.values());
        out.sort(Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        return out;
    }

    private static void collect(Context ctx, PackageManager pm, String category, List<String> hidden, Map<String, App> into) {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || into.containsKey(ai.packageName) || ai.packageName.equals(ctx.getPackageName())
                    || hidden.contains(ai.packageName)) continue;
            Drawable banner = ai.loadBanner(pm);
            if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            Drawable icon = ai.loadIcon(pm);
            Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(category)
                    .setClassName(ai.packageName, ai.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            into.put(ai.packageName, new App(ai.packageName, String.valueOf(ri.loadLabel(pm)), banner, icon,
                    vivid(banner != null ? banner : icon), launch));
        }
    }

    /** The most saturated, reasonably bright pixel of a 24 x 14 sample; grey when the art has no colour. */
    static int vivid(Drawable d) {
        if (d == null) return Color.GRAY;
        try {
            Bitmap b = Bitmap.createBitmap(24, 14, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, 24, 14);
            d.draw(c);
            float[] hsv = new float[3];
            int best = Color.GRAY;
            float bestScore = 0.15f;
            for (int y = 0; y < 14; y++) for (int x = 0; x < 24; x++) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 200) continue;
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);
                if (score > bestScore) { bestScore = score; best = p; }
            }
            b.recycle();
            return best | 0xFF000000;
        } catch (RuntimeException e) {
            return Color.GRAY;
        }
    }
}
