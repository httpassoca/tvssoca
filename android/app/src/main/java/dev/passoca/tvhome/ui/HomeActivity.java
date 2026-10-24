package dev.passoca.tvhome.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.passoca.tvhome.R;
import dev.passoca.tvhome.core.Config;
import dev.passoca.tvhome.core.HomeModel;
import dev.passoca.tvhome.core.Layout;
import dev.passoca.tvhome.core.Spring;
import dev.passoca.tvhome.core.WallpaperRotation;
import dev.passoca.tvhome.core.WeatherReading;
import dev.passoca.tvhome.core.WorldClock;
import dev.passoca.tvhome.data.AppsRepository;
import dev.passoca.tvhome.data.InputsRepository;
import dev.passoca.tvhome.data.Prefs;
import dev.passoca.tvhome.data.WallpaperRepository;
import dev.passoca.tvhome.data.WeatherRepository;

/**
 * Home. The 960 x 540 dp stage of the web prototype, drawn with dssoca views and driven by the core
 * model. Keys are routed by zone (tiles, top bar, menu, settings sheet) exactly like web/index.html's
 * focus engine; nothing here relies on Android view focus.
 */
public final class HomeActivity extends Activity implements SettingsSheet.Host {

    private static final int TOP_INPUTS = 0, TOP_WIFI = 1, TOP_SETTINGS = 2, TOP_COUNT = 3;
    private static final long LONG_PRESS_MS = 500, TICK_MS = 15_000, SYNC_EVERY_MS = 3 * 3_600_000L;

    private Dss dss;
    private Prefs prefs;
    private Config cfg;
    private HomeModel model;
    private final Map<String, AppsRepository.App> apps = new HashMap<>();
    private final Map<String, Views.TileView> tiles = new HashMap<>();

    private FrameLayout stage, content, overlay;
    private ImageView wallA, wallB;
    private View scrim, band;
    private Views.GlowView glowA, glowB;
    private Views.FocusFrameView frame;
    private Views.HeadingView hero;
    private TextView clock, ampm, date, caption;
    private LinearLayout info;
    private final Views.IconButton[] top = new Views.IconButton[TOP_COUNT];
    private MenuPanel menu;
    private SettingsSheet sheet;
    private View toast;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private WallpaperRepository walls;
    private WeatherRepository weatherRepo;
    private WeatherReading weather;
    private List<String> wallFiles = new ArrayList<>();
    private final Map<String, Drawable> wallThumbs = new HashMap<>();
    private int wallIndex;
    private long wallSince;
    private String wallShown;
    private boolean wallFront = true;
    private long lastSync;
    private final Random random = new Random();

    private boolean glowFront = true, launching;
    private Integer glowColor;
    private final Spring.Value sx = new Spring.Value(), sy = new Spring.Value(), sw = new Spring.Value(), sh = new Spring.Value();
    private boolean springRunning, frameShown;
    private long lastFrameNanos;

    // ─── lifecycle ──────────────────────────────────────────────────────────

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dss = new Dss(this);
        prefs = new Prefs(this);
        cfg = loadConfig();
        walls = new WallpaperRepository(this);
        weatherRepo = new WeatherRepository(this);
        weather = weatherRepo.cached();
        model = new HomeModel(new ArrayList<>(), cfg.columns, TOP_COUNT);
        buildStage();
        wallFiles = walls.files();
        wallIndex = prefs.wallIndex() >= 0 ? prefs.wallIndex() : WallpaperRotation.next(-1, wallFiles.size(), cfg.wallpaperOrder, random);
        wallSince = prefs.wallSince() > 0 ? prefs.wallSince() : System.currentTimeMillis();
        loadApps();
        loadThumbs();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
    }

    @Override protected void onResume() {
        super.onResume();
        if (launching) {
            launching = false;
            stage.setAlpha(0.65f);
            stage.setScaleX(0.97f * stageScale());
            stage.setScaleY(0.97f * stageScale());
            stage.animate().alpha(1f).scaleX(stageScale()).scaleY(stageScale()).setDuration(motion() ? 220 : 0)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        tick();
        main.removeCallbacks(ticker);
        main.postDelayed(ticker, TICK_MS);
        if (System.currentTimeMillis() - lastSync > SYNC_EVERY_MS) syncWallpapers();
        refreshWeather();
    }

    @Override protected void onPause() {
        super.onPause();
        main.removeCallbacks(ticker);
    }

    @Override protected void onDestroy() {
        unregisterReceiver(packages);
        io.shutdownNow();
        super.onDestroy();
    }

    /** The Home button while Home is already up: back to the first dock tile, everything closed. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction()) && intent.hasCategory(Intent.CATEGORY_HOME)) {
            closeOverlays();
            if (model.reorder) endReorder(false);
            model.home();
            render(true);
        }
    }

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadApps(); }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() { tick(); main.postDelayed(this, TICK_MS); }
    };

    private Config loadConfig() {
        Config c = prefs.load();
        float scale = Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        if (scale == 0f) c.reducedMotion = true;
        return c;
    }

    private boolean motion() { return cfg.animations && !cfg.reducedMotion; }

    // ─── stage ──────────────────────────────────────────────────────────────

    private float stageScale() {
        View root = (View) stage.getParent();
        if (root == null || root.getWidth() == 0) return 1f;
        return Math.min(root.getWidth() / dss.dpf(Layout.STAGE_W), root.getHeight() / dss.dpf(Layout.STAGE_H));
    }

    private FrameLayout.LayoutParams at(float x, float y, float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : dss.dp(w),
                h < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : dss.dp(h));
        lp.leftMargin = dss.dp(x);
        lp.topMargin = dss.dp(y);
        lp.gravity = Gravity.TOP | Gravity.START;
        return lp;
    }

    private FrameLayout.LayoutParams fill() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void buildStage() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(dss.bg);
        root.setFocusable(true);
        stage = new FrameLayout(this);
        stage.setClipChildren(true);
        stage.setPivotX(0f);
        stage.setPivotY(0f);
        root.addView(stage, new FrameLayout.LayoutParams(dss.dp(Layout.STAGE_W), dss.dp(Layout.STAGE_H)));
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            float s = stageScale();
            stage.setScaleX(s);
            stage.setScaleY(s);
            stage.setTranslationX((v.getWidth() - dss.dpf(Layout.STAGE_W) * s) / 2f);
            stage.setTranslationY((v.getHeight() - dss.dpf(Layout.STAGE_H) * s) / 2f);
        });
        setContentView(root);
        root.requestFocus();

        wallA = wallImage();
        wallB = wallImage();
        stage.addView(wallA, fill());
        stage.addView(wallB, fill());
        scrim = new View(this);
        scrim.setAlpha(0f);
        stage.addView(scrim, fill());
        stage.addView(new Views.GridBackgroundView(dss), fill());
        glowA = new Views.GlowView(this);
        glowB = new Views.GlowView(this);
        glowA.setAlpha(0f);
        glowB.setAlpha(0f);
        stage.addView(glowA, fill());
        stage.addView(glowB, fill());

        hero = new Views.HeadingView(dss, 56f);
        hero.setAlpha(0f);
        stage.addView(hero, heroParams());

        buildHeader();

        FrameLayout clip = new FrameLayout(this);
        clip.setClipChildren(true);
        stage.addView(clip, at(0, Layout.CLIP_TOP, Layout.STAGE_W, Layout.STAGE_H - Layout.CLIP_TOP));
        content = new FrameLayout(this);
        content.setClipChildren(false);
        clip.addView(content, new FrameLayout.LayoutParams(dss.dp(Layout.STAGE_W), dss.dp(2400)));
        content.setTranslationY(-dss.dpf(Layout.CLIP_TOP));
        band = new View(this);
        band.setBackground(dss.box(Dss.withAlpha(dss.bgElev, 0.88f), dss.line));
        content.addView(band);

        frame = new Views.FocusFrameView(dss);
        stage.addView(frame, fill());
        overlay = new FrameLayout(this);
        stage.addView(overlay, fill());
    }

    private ImageView wallImage() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setAlpha(0f);
        return v;
    }

    private FrameLayout.LayoutParams heroParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.leftMargin = dss.dp(62);
        lp.bottomMargin = dss.dp(Layout.STAGE_H - Layout.heroBottom(cfg.columns));
        return lp;
    }

    private void buildHeader() {
        LinearLayout left = new LinearLayout(this);
        left.setGravity(Gravity.BOTTOM);
        clock = dss.text(54f, dss.fgShine, dss.display);
        clock.setLetterSpacing(-0.01f);
        left.addView(clock);
        ampm = dss.text(12f, dss.fgMuted, dss.body);
        ampm.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.leftMargin = dss.dp(8);
        ap.bottomMargin = dss.dp(4);
        left.addView(ampm, ap);
        View rule = new View(this);
        rule.setBackgroundColor(dss.lineStrong);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(Math.max(1, dss.dp(1)), dss.dp(40));
        rp.setMargins(dss.dp(18), 0, dss.dp(18), dss.dp(2));
        left.addView(rule, rp);
        LinearLayout dateCol = new LinearLayout(this);
        dateCol.setOrientation(LinearLayout.VERTICAL);
        date = dss.caps(12f, dss.fgMuted, 0.14f);
        dateCol.addView(date);
        info = new LinearLayout(this);
        info.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.topMargin = dss.dp(7);
        dateCol.addView(info, ip);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dp.bottomMargin = dss.dp(2);
        left.addView(dateCol, dp);
        stage.addView(left, at(56, 28, -1, -1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        LinearLayout buttons = new LinearLayout(this);
        int[] icons = {R.drawable.ic_input, R.drawable.ic_wifi, R.drawable.ic_settings};
        int[] labels = {R.string.inputs, R.string.wifi, R.string.settings};
        for (int i = 0; i < TOP_COUNT; i++) {
            top[i] = new Views.IconButton(dss, icons[i], getString(labels[i]));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dss.dp(38), dss.dp(38));
            if (i > 0) bp.leftMargin = dss.dp(10);
            buttons.addView(top[i], bp);
        }
        right.addView(buttons);
        caption = dss.caps(10f, dss.fgFaint, 0.12f);
        caption.setPadding(0, dss.dp(6), 0, 0);
        right.addView(caption);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.gravity = Gravity.TOP | Gravity.END;
        rl.topMargin = dss.dp(36);
        rl.rightMargin = dss.dp(56);
        stage.addView(right, rl);
    }

    // ─── apps and tiles ─────────────────────────────────────────────────────

    private void loadApps() {
        io.execute(() -> {
            List<AppsRepository.App> list = AppsRepository.load(this, cfg.hiddenApps);
            main.post(() -> {
                apps.clear();
                List<String> installed = new ArrayList<>();
                for (AppsRepository.App a : list) { apps.put(a.pkg, a); installed.add(a.pkg); }
                List<String> order = HomeModel.arrange(installed, prefs.order(), cfg.dockApps);
                String keep = model.focusedApp();
                model.order.clear();
                model.order.addAll(order);
                int idx = keep == null ? 0 : Math.max(0, order.indexOf(keep));
                buildTiles(true);
                model.focusTile(idx);
                render(false);
            });
        });
    }

    private void buildTiles(boolean enter) {
        for (Views.TileView t : tiles.values()) content.removeView(t);
        tiles.clear();
        int i = 0;
        for (String pkg : model.order) {
            AppsRepository.App a = apps.get(pkg);
            if (a == null) continue;
            Views.TileView t = new Views.TileView(dss, a);
            tiles.put(pkg, t);
            content.addView(t);
            if (enter && motion()) {
                t.setAlpha(0f);
                t.animate().alpha(1f).setStartDelay(i * 25L).setDuration(420).setInterpolator(new DecelerateInterpolator()).start();
            }
            i++;
        }
        placeTiles(false);
    }

    private void placeTiles(boolean animate) {
        int cols = model.columns;
        for (int i = 0; i < model.order.size(); i++) {
            Views.TileView t = tiles.get(model.order.get(i));
            if (t == null) continue;
            Layout.Cell c = Layout.cell(i, cols);
            t.size(c.w, c.h);
            FrameLayout.LayoutParams lp = at(0, 0, c.w, c.h + 30);
            t.setLayoutParams(lp);
            if (animate && motion()) t.animate().x(dss.dpf(c.x)).y(dss.dpf(c.y)).setDuration(220).start();
            else { t.setX(dss.dpf(c.x)); t.setY(dss.dpf(c.y)); }
        }
        Layout.Rect b = Layout.band(cols, model.dockCount());
        band.setLayoutParams(at(b.x, b.y, b.w, b.h));
        band.setVisibility(model.order.isEmpty() ? View.GONE : View.VISIBLE);
        hero.setLayoutParams(heroParams());
    }

    // ─── render ─────────────────────────────────────────────────────────────

    /** Applies the model to the views: focus, scroll, frame, glow, title, top bar. */
    private void render(boolean animate) {
        String focused = model.focusedApp();
        Layout.Cell q = Layout.cell(model.tile, model.columns);
        boolean inTiles = model.zone == HomeModel.Zone.TILES && focused != null;
        float scale = Layout.focusScale(cfg);
        for (Map.Entry<String, Views.TileView> e : tiles.entrySet()) {
            int idx = model.order.indexOf(e.getKey());
            boolean f = inTiles && e.getKey().equals(focused);
            e.getValue().setFocus(f, model.picked && f ? 1.16f : scale, cfg.focusOvershoot && !cfg.reducedMotion,
                    animate && motion(), Layout.cell(idx, model.columns).row > 0);
            e.getValue().setElevation(f ? dss.dpf(4) : 0f);
        }
        float ty = -dss.dpf(Layout.CLIP_TOP + model.scroll);
        content.animate().cancel();
        if (animate && motion()) content.animate().translationY(ty).setDuration(350).setInterpolator(new DecelerateInterpolator()).start();
        else content.setTranslationY(ty);

        for (int i = 0; i < TOP_COUNT; i++) {
            boolean open = menu != null && menu.back != HomeModel.Zone.SHEET
                    && (("inputs".equals(menu.kind) && i == TOP_INPUTS) || (!"inputs".equals(menu.kind) && i == TOP_SETTINGS));
            top[i].setFocused((model.zone == HomeModel.Zone.TOP && model.top == i) || open, model.reorder && i == TOP_SETTINGS);
        }

        if (inTiles) {
            float s = model.picked ? 1.16f : scale, w = q.w * s, h = q.h * s;
            moveFrame(q.x + q.w / 2f - w / 2f, q.y - model.scroll + q.h / 2f - h / 2f, w, h, animate);
            frame.setColor(model.reorder ? dss.blue : dss.primary);
            showFrame(true);
            AppsRepository.App a = apps.get(focused);
            setGlow(cfg.ambientGlow && a != null ? a.color : null);
        } else {
            showFrame(false);
        }

        boolean showHero = inTiles && q.row == 0 && !model.reorder && cfg.dockTitle;
        if (showHero) {
            AppsRepository.App a = apps.get(focused);
            String name = a == null ? "" : a.label;
            if (!name.contentEquals(hero.getText())) {
                hero.setText(name);
                if (motion()) { hero.setAlpha(0f); hero.setTranslationY(dss.dpf(4)); }
            }
        }
        hero.animate().alpha(showHero ? 1f : 0f).translationY(0f).setDuration(motion() ? 180 : 0).start();
    }

    private void showFrame(boolean on) {
        frameShown = on;
        frame.shown = on;
        frame.invalidate();
    }

    private void moveFrame(float x, float y, float w, float h, boolean animate) {
        if (!frame.shown || !animate || !cfg.animations) {
            sx.jump(x); sy.jump(y); sw.jump(w); sh.jump(h);
            frame.setRect(x, y, w, h);
            return;
        }
        sx.target = x; sy.target = y; sw.target = w; sh.target = h;
        if (!springRunning) {
            springRunning = true;
            lastFrameNanos = 0;
            Choreographer.getInstance().postFrameCallback(springStep);
        }
    }

    private final Choreographer.FrameCallback springStep = new Choreographer.FrameCallback() {
        @Override public void doFrame(long now) {
            float dt = lastFrameNanos == 0 ? 1f / 60f : (now - lastFrameNanos) / 1e9f;
            lastFrameNanos = now;
            Spring s = Spring.forConfig(cfg);
            boolean moving = sx.step(s, dt) | sy.step(s, dt) | sw.step(s, dt) | sh.step(s, dt);
            frame.setRect(sx.position, sy.position, sw.position, sh.position);
            if (moving && frameShown) Choreographer.getInstance().postFrameCallback(this);
            else springRunning = false;
        }
    };

    /** Ambient glow: crossfade with advanced effects, otherwise dip to 35%, swap colour and come back. */
    private void setGlow(Integer color) {
        if (color == null) {
            glowA.animate().alpha(0f).setDuration(200).start();
            glowB.animate().alpha(0f).setDuration(200).start();
            glowColor = null;
            return;
        }
        if (color.equals(glowColor)) return;
        boolean first = glowColor == null;
        glowColor = color;
        Views.GlowView a = glowFront ? glowA : glowB, b = glowFront ? glowB : glowA;
        if (!motion()) { a.setColor(color); a.setAlpha(1f); b.setAlpha(0f); return; }
        if (first) { a.setColor(color); a.animate().alpha(1f).setDuration(600).start(); return; }
        if (cfg.advancedEffects) {
            b.setColor(color);
            b.animate().alpha(1f).setDuration(500).start();
            a.animate().alpha(0f).setDuration(500).start();
            glowFront = !glowFront;
            return;
        }
        a.animate().alpha(0.35f).setDuration(200).withEndAction(() -> {
            if (glowColor != null) a.setColor(glowColor);
            a.animate().alpha(1f).setDuration(500).start();
        }).start();
    }

    // ─── header: clock, date, weather, world clocks ─────────────────────────

    private void tick() {
        boolean h24 = DateFormat.is24HourFormat(this);
        ZonedDateTime now = ZonedDateTime.now();
        clock.setText(WorldClock.clock(now, h24));
        ampm.setText(WorldClock.ampm(now, h24));
        ampm.setVisibility(h24 ? View.GONE : View.VISIBLE);
        date.setText(WorldClock.date(now, Locale.getDefault()));
        renderInfo();
        if (WallpaperRotation.rotating(prefs.wallpaperChoice(), cfg.wallpaper, cfg.wallpaperRotationMinutes)
                && WallpaperRotation.due(System.currentTimeMillis(), wallSince, cfg.wallpaperRotationMinutes)) {
            nextWallpaper();
        }
        if (weather != null && cfg.weather.isSet() && weather.shouldRefresh(System.currentTimeMillis(), cfg.weather.refreshMinutes)) refreshWeather();
    }

    private void renderInfo() {
        info.removeAllViews();
        long now = System.currentTimeMillis();
        boolean first = true;
        if (weather != null && cfg.weather.isSet() && weather.showable(now)) {
            info.addView(Views.icon(dss, R.drawable.ic_cloud, dss.fgMuted, 16));
            TextView t = dss.text(12f, dss.fgShine, dss.bodyMedium);
            t.setText(String.format(Locale.ROOT, "%d°", weather.temperature));
            t.setPadding(dss.dp(6), 0, dss.dp(6), 0);
            info.addView(t);
            TextView d = dss.text(12f, dss.fgMuted, dss.body);
            d.setText(WeatherReading.describe(weather.code));
            info.addView(d);
            first = false;
        }
        for (Config.WorldClockEntry c : cfg.worldClocks) {
            if (!first) info.addView(separator());
            TextView city = dss.caps(11f, dss.fgFaint, 0.14f);
            city.setText(c.label);
            info.addView(city);
            TextView t = dss.text(12f, dss.fgShine, dss.bodyMedium);
            t.setText(WorldClock.timeIn(Instant.now(), c.zone));
            t.setPadding(dss.dp(6), 0, 0, 0);
            info.addView(t);
            first = false;
        }
    }

    private View separator() {
        View v = new View(this);
        v.setBackgroundColor(dss.lineStrong);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(1, dss.dp(1)), dss.dp(12));
        lp.setMargins(dss.dp(10), 0, dss.dp(10), 0);
        v.setLayoutParams(lp);
        return v;
    }

    private void refreshWeather() {
        if (!cfg.weather.isSet()) return;
        io.execute(() -> {
            WeatherReading r = weatherRepo.refresh(cfg.weather, System.currentTimeMillis());
            main.post(() -> { weather = r; renderInfo(); });
        });
    }

    // ─── wallpapers ─────────────────────────────────────────────────────────

    private void syncWallpapers() {
        lastSync = System.currentTimeMillis();
        io.execute(() -> {
            WallpaperRepository.Result r = walls.sync(cfg.wallpaperSource);
            main.post(() -> {
                wallFiles = walls.files();
                if (r.ok && (r.added > 0 || r.removed > 0)) {
                    loadThumbs();
                    if (wallShown != null && !wallFiles.contains(wallShown)) wallShown = null;
                }
                showWallpaper();
            });
        });
    }

    private void loadThumbs() {
        List<String> files = new ArrayList<>(wallFiles);
        io.execute(() -> {
            Map<String, Drawable> out = new HashMap<>();
            for (String f : files) {
                Bitmap b = walls.decode(f, 96, 54);
                if (b != null) out.put(f, new BitmapDrawable(getResources(), b));
            }
            main.post(() -> { wallThumbs.clear(); wallThumbs.putAll(out); showWallpaper(); });
        });
    }

    private void nextWallpaper() {
        wallIndex = WallpaperRotation.next(wallIndex, wallFiles.size(), cfg.wallpaperOrder, random);
        wallSince = System.currentTimeMillis();
        prefs.setWall(wallIndex, wallSince);
        showWallpaper();
    }

    private void showWallpaper() {
        int i = WallpaperRotation.current(wallFiles, prefs.wallpaperChoice(), wallIndex, cfg.wallpaper);
        String file = i < 0 ? null : wallFiles.get(i);
        caption.setText(file != null && cfg.wallpaperCaptions ? WallpaperRotation.label(file) : "");
        applyScrim(file != null);
        if (file == null) {
            wallShown = null;
            wallA.animate().alpha(0f).setDuration(400).start();
            wallB.animate().alpha(0f).setDuration(400).start();
            return;
        }
        if (file.equals(wallShown)) return;
        wallShown = file;
        int w = getResources().getDisplayMetrics().widthPixels, h = getResources().getDisplayMetrics().heightPixels;
        io.execute(() -> {
            Bitmap b = walls.decode(file, w, h);
            main.post(() -> {
                if (b == null || !file.equals(wallShown)) return;
                ImageView in = wallFront ? wallB : wallA, out = wallFront ? wallA : wallB;
                in.setImageBitmap(b);
                in.animate().alpha(1f).setDuration(motion() ? 650 : 0).start();
                out.animate().alpha(0f).setDuration(motion() ? 650 : 0).start();
                wallFront = !wallFront;
            });
        });
    }

    /** wallpaper_dim as a vertical scrim: lighter at the top, darkest behind the dock. */
    private void applyScrim(boolean on) {
        float d = cfg.wallpaperDim / 100f;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
                Dss.withAlpha(dss.bg, d * 0.78f), Dss.withAlpha(dss.bg, Math.min(0.92f, d * 1.22f)), Dss.withAlpha(dss.bg, Math.min(0.92f, d * 1.9f))});
        scrim.setBackground(g);
        scrim.animate().alpha(on ? 1f : 0f).setDuration(350).start();
    }

    // ─── keys ───────────────────────────────────────────────────────────────

    private boolean okDown, okLongFired;
    private final Runnable longPress = () -> { okLongFired = true; onOk(true); };

    private static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
    }

    private static boolean isBack(int code) {
        return code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_BUTTON_B;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (isOk(code)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                okDown = true;
                okLongFired = false;
                main.postDelayed(longPress, LONG_PRESS_MS);
            } else if (e.getAction() == KeyEvent.ACTION_UP && okDown) {
                okDown = false;
                main.removeCallbacks(longPress);
                if (!okLongFired) onOk(false);
            }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return isBack(code) || isDpad(code) || super.dispatchKeyEvent(e);
        if (isBack(code)) { onBack(); return true; }
        if (code == KeyEvent.KEYCODE_SETTINGS) { quickPanel(); return true; }
        if (isDpad(code)) { onDpad(code); return true; }
        return super.dispatchKeyEvent(e);
    }

    private static boolean isDpad(int code) {
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private static HomeModel.Dir dir(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return HomeModel.Dir.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return HomeModel.Dir.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return HomeModel.Dir.LEFT;
            default: return HomeModel.Dir.RIGHT;
        }
    }

    private void onDpad(int code) {
        switch (model.zone) {
            case MENU:
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) menu.move(1);
                else if (code == KeyEvent.KEYCODE_DPAD_UP) menu.move(-1);
                else if (code == KeyEvent.KEYCODE_DPAD_LEFT) closeMenu();
                return;
            case SHEET:
                sheet.onKey(code);
                return;
            case TOP:
                if (model.moveTop(dir(code))) render(true);
                return;
            default:
                boolean swapped = model.picked;
                if (model.move(dir(code))) {
                    if (swapped) placeTiles(true);
                    render(true);
                }
        }
    }

    private void onOk(boolean longPress) {
        switch (model.zone) {
            case MENU: menu.activate(); return;
            case SHEET: sheet.activate(); return;
            case TOP:
                if (model.top == TOP_INPUTS) openInputs();
                else if (model.top == TOP_WIFI) open(new Intent(Settings.ACTION_WIFI_SETTINGS), getString(R.string.wifi));
                else if (longPress) launcherMenu();
                else open(new Intent(Settings.ACTION_SETTINGS), getString(R.string.settings));
                return;
            default:
                String pkg = model.focusedApp();
                if (pkg == null) return;
                if (model.reorder) {
                    model.picked = !model.picked;
                    render(true);
                    return;
                }
                if (longPress) {
                    open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)), pkg);
                    return;
                }
                AppsRepository.App a = apps.get(pkg);
                if (a != null) launch(a.launchIntent(), a.label);
        }
    }

    private void onBack() {
        switch (model.zone) {
            case MENU: closeMenu(); return;
            case SHEET: closeSheet(); return;
            default:
                if (model.reorder) {
                    if (model.picked) { model.picked = false; render(true); }
                    else endReorder(true);
                    return;
                }
                if (model.back()) render(true);
        }
    }

    // ─── actions ────────────────────────────────────────────────────────────

    /** Opens another app with the launcher's exit motion (fade to 25%, a 2.5% push in). */
    private void launch(Intent intent, String what) {
        if (motion()) {
            float s = stageScale();
            stage.animate().alpha(0.25f).scaleX(1.025f * s).scaleY(1.025f * s).setDuration(200)
                    .withEndAction(() -> {
                        if (open(intent, what)) launching = true;
                        else { stage.setAlpha(1f); stage.setScaleX(s); stage.setScaleY(s); }
                    }).start();
        } else {
            open(intent, what);
        }
    }

    private boolean open(Intent intent, String what) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            toast(getString(R.string.cant_open, what), false);
            return false;
        }
    }

    private void openInputs() {
        List<InputsRepository.Input> inputs = InputsRepository.load(this);
        List<MenuPanel.Item> items = new ArrayList<>();
        boolean tunerSep = false;
        for (InputsRepository.Input in : inputs) {
            if (in.tuner && !tunerSep && !items.isEmpty()) { items.add(MenuPanel.Item.separator()); tunerSep = true; }
            int icon = in.tuner ? R.drawable.ic_livetv : in.hdmi ? R.drawable.ic_input : R.drawable.ic_av;
            Runnable act = () -> { closeMenu(); launch(in.intent(), in.label); };
            items.add(in.connected ? MenuPanel.Item.badged(in.label, icon, getString(R.string.connected), true, act)
                    : MenuPanel.Item.action(in.label, icon, act));
        }
        if (items.isEmpty()) { toast(getString(R.string.no_inputs), false); return; }
        showMenu(new MenuPanel(dss, "inputs", items, HomeModel.Zone.TOP), top[TOP_INPUTS]);
    }

    private void launcherMenu() {
        List<MenuPanel.Item> items = new ArrayList<>();
        items.add(MenuPanel.Item.action(getString(R.string.launcher_settings), R.drawable.ic_home, this::openSheet));
        items.add(MenuPanel.Item.action(getString(R.string.reorder), R.drawable.ic_swap, this::startReorder));
        showMenu(new MenuPanel(dss, "launcher", items, HomeModel.Zone.TOP), top[TOP_SETTINGS]);
    }

    private void quickPanel() {
        closeOverlays();
        HomeModel.Zone back = model.zone == HomeModel.Zone.TOP ? HomeModel.Zone.TOP : HomeModel.Zone.TILES;
        List<MenuPanel.Item> items = new ArrayList<>();
        items.add(MenuPanel.Item.action(getString(R.string.tv_settings), R.drawable.ic_settings,
                () -> { closeMenu(); open(new Intent(Settings.ACTION_SETTINGS), getString(R.string.tv_settings)); }));
        items.add(MenuPanel.Item.action(getString(R.string.display), R.drawable.ic_picture,
                () -> { closeMenu(); open(new Intent(Settings.ACTION_DISPLAY_SETTINGS), getString(R.string.display)); }));
        items.add(MenuPanel.Item.action(getString(R.string.network), R.drawable.ic_wifi,
                () -> { closeMenu(); open(new Intent(Settings.ACTION_WIFI_SETTINGS), getString(R.string.network)); }));
        items.add(MenuPanel.Item.badged(getString(R.string.apps), R.drawable.ic_apps, getString(R.string.reenable), false,
                () -> { closeMenu(); open(new Intent(Settings.ACTION_APPLICATION_SETTINGS), getString(R.string.apps)); }));
        items.add(MenuPanel.Item.action(getString(R.string.launcher_settings), R.drawable.ic_home, this::openSheet));
        showMenu(new MenuPanel(dss, "quick", items, back), top[TOP_SETTINGS]);
    }

    private void startReorder() {
        closeMenu();
        model.reorder = true;
        model.picked = false;
        model.focusTile(model.dockReturn);
        toast(getString(R.string.reorder_hint), false);
        render(true);
    }

    private void endReorder(boolean announce) {
        model.reorder = false;
        model.picked = false;
        prefs.saveOrder(model.order);
        if (announce) toast(getString(R.string.order_saved), true);
        render(true);
    }

    // ─── overlays: menu, sheet, toast ───────────────────────────────────────

    private float[] stageRect(View v) {
        int[] a = new int[2], s = new int[2];
        v.getLocationInWindow(a);
        stage.getLocationInWindow(s);
        float k = stageScale();
        float l = (a[0] - s[0]) / k, t = (a[1] - s[1]) / k;
        return new float[]{l, t, l + v.getWidth(), t + v.getHeight()};
    }

    /** Drops a menu under its anchor, right edges aligned (dssoca Menu data-align="end"). */
    private void showMenu(MenuPanel m, View anchor) {
        if (menu != null) overlay.removeView(menu);
        menu = m;
        float[] r = stageRect(anchor);
        m.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = Math.max(dss.dp(16), Math.round(r[2] - m.getMeasuredWidth()));
        lp.topMargin = Math.round(r[3] + dss.dpf(8));
        overlay.addView(m, lp);
        if (motion()) { m.setAlpha(0f); m.setTranslationY(-dss.dpf(6)); m.animate().alpha(1f).translationY(0f).setDuration(250).start(); }
        model.zone = HomeModel.Zone.MENU;
        render(true);
    }

    private void closeMenu() {
        if (menu == null) return;
        HomeModel.Zone back = menu.back;
        overlay.removeView(menu);
        menu = null;
        model.zone = back;
        render(true);
    }

    private void openSheet() {
        if (menu != null) { overlay.removeView(menu); menu = null; }
        if (sheet == null) {
            sheet = new SettingsSheet(dss, this);
            overlay.addView(sheet, at(70, 44, 820, -1));
            if (motion()) { sheet.setAlpha(0f); sheet.animate().alpha(1f).setDuration(250).start(); }
        }
        model.zone = HomeModel.Zone.SHEET;
        render(true);
    }

    private void closeSheet() {
        if (sheet != null) overlay.removeView(sheet);
        sheet = null;
        model.zone = HomeModel.Zone.TOP;
        render(true);
    }

    private void closeOverlays() {
        if (menu != null) overlay.removeView(menu);
        if (sheet != null) overlay.removeView(sheet);
        menu = null;
        sheet = null;
    }

    /** dssoca Toast: elevated square card, 3 dp left border (accent for success), glyph and message. */
    private void toast(String msg, boolean success) {
        if (toast != null) overlay.removeView(toast);
        LinearLayout t = new LinearLayout(this);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dss.dp(16), dss.dp(12), dss.dp(16), dss.dp(12));
        GradientDrawable bg = dss.box(dss.bgElev, dss.line);
        android.graphics.drawable.LayerDrawable d = new android.graphics.drawable.LayerDrawable(new Drawable[]{bg, new ColorDrawable(success ? dss.primary : dss.fgFaint)});
        d.setLayerWidth(1, dss.dp(3));
        d.setLayerGravity(1, Gravity.START | Gravity.FILL_VERTICAL);
        t.setBackground(d);
        t.setElevation(dss.dpf(12));
        TextView ic = dss.text(13f, success ? dss.primary : dss.fgMuted, dss.bodyMedium);
        ic.setText(success ? "✓" : "i");
        t.addView(ic);
        TextView m = dss.text(13f, dss.fg, dss.body);
        m.setSingleLine(false);
        m.setMaxWidth(dss.dp(560));
        m.setText(msg);
        m.setPadding(dss.dp(12), 0, 0, 0);
        t.addView(m);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dss.dp(16);
        overlay.addView(t, lp);
        toast = t;
        if (motion()) { t.setAlpha(0f); t.setTranslationY(dss.dpf(16)); t.animate().alpha(1f).translationY(0f).setDuration(250).start(); }
        main.postDelayed(() -> { if (toast == t) { overlay.removeView(t); toast = null; } }, 3200);
    }

    // ─── SettingsSheet.Host ─────────────────────────────────────────────────

    @Override public Config config() { return cfg; }

    @Override public void setChoice(String key, Object value) {
        switch (key) {
            case "columns":
                cfg.columns = (Integer) value;
                prefs.putInt(key, cfg.columns);
                model.setColumns(cfg.columns);
                buildTiles(true);
                break;
            case "wallpaper_order":
                cfg.wallpaperOrder = (String) value;
                prefs.putString(key, cfg.wallpaperOrder);
                break;
            case "wallpaper_rotation_minutes":
                cfg.wallpaperRotationMinutes = (Integer) value;
                prefs.putInt(key, cfg.wallpaperRotationMinutes);
                wallSince = System.currentTimeMillis();
                prefs.setWall(wallIndex, wallSince);
                int m = cfg.wallpaperRotationMinutes;
                toast(m > 0 ? getString(R.string.rotation_every, m) : getString(R.string.rotation_off), false);
                break;
            default: {
                boolean v = (Boolean) value;
                prefs.putBool(key, v);
                if ("animations".equals(key)) cfg.animations = v;
                else if ("advanced_effects".equals(key)) cfg.advancedEffects = v;
                else if ("focus_overshoot".equals(key)) cfg.focusOvershoot = v;
                else if ("ambient_glow".equals(key)) cfg.ambientGlow = v;
            }
        }
        render(false);
    }

    @Override public void openWallpaperPicker(View anchor) {
        String choice = prefs.wallpaperChoice();
        List<MenuPanel.Item> items = new ArrayList<>();
        items.add(MenuPanel.Item.choice(getString(R.string.rotate_folder, wallFiles.size()), R.drawable.ic_rotate, null, choice == null,
                () -> pickWallpaper(null, getString(R.string.wallpaper_rotating))));
        items.add(MenuPanel.Item.separator());
        for (String f : wallFiles) {
            items.add(MenuPanel.Item.choice(WallpaperRotation.label(f), R.drawable.ic_picture, wallThumbs.get(f), f.equals(choice),
                    () -> pickWallpaper(f, getString(R.string.wallpaper_changed))));
        }
        items.add(MenuPanel.Item.separator());
        items.add(MenuPanel.Item.choice(getString(R.string.wallpaper_none), R.drawable.ic_none, null, WallpaperRotation.NONE.equals(choice),
                () -> pickWallpaper(WallpaperRotation.NONE, getString(R.string.wallpaper_none))));
        showMenu(new MenuPanel(dss, "wallpaper", items, HomeModel.Zone.SHEET), anchor);
    }

    private void pickWallpaper(String choice, String msg) {
        prefs.setWallpaperChoice(choice);
        if (choice != null && !WallpaperRotation.NONE.equals(choice)) wallIndex = Math.max(0, wallFiles.indexOf(choice));
        wallSince = System.currentTimeMillis();
        prefs.setWall(wallIndex, wallSince);
        closeMenu();
        showWallpaper();
        if (sheet != null) sheet.refresh();
        toast(msg, true);
    }

    @Override public void followConfig() {
        prefs.followConfig();
        cfg = loadConfig();
        model.setColumns(cfg.columns);
        List<String> order = HomeModel.arrange(new ArrayList<>(apps.keySet()), new ArrayList<>(), cfg.dockApps);
        order.sort((a, b) -> {
            int ia = cfg.dockApps.indexOf(a), ib = cfg.dockApps.indexOf(b);
            if (ia >= 0 || ib >= 0) return ia < 0 ? 1 : ib < 0 ? -1 : Integer.compare(ia, ib);
            return apps.get(a).label.compareToIgnoreCase(apps.get(b).label);
        });
        model.order.clear();
        model.order.addAll(order);
        buildTiles(true);
        showWallpaper();
        toast(getString(R.string.back_to_config), true);
    }

    @Override public void chooseDefaultHome() {
        if (!open(new Intent(Settings.ACTION_HOME_SETTINGS), getString(R.string.default_home))) {
            open(new Intent(Settings.ACTION_SETTINGS), getString(R.string.settings));
        }
    }

    @Override public String wallpaperDescription() {
        String choice = prefs.wallpaperChoice();
        if (!cfg.wallpaper || WallpaperRotation.NONE.equals(choice)) return getString(R.string.wallpaper_none);
        if (wallFiles.isEmpty()) return getString(R.string.wallpaper_empty);
        if (choice != null && wallFiles.contains(choice)) return WallpaperRotation.label(choice);
        String repo = cfg.wallpaperSource.repo;
        return getString(R.string.wallpaper_folder, repo.substring(repo.indexOf('/') + 1), wallFiles.size());
    }

    @Override public Drawable wallpaperThumb() {
        return wallShown == null ? null : wallThumbs.get(wallShown);
    }
}
