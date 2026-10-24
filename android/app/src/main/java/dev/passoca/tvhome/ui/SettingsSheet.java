package dev.passoca.tvhome.ui;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import dev.passoca.tvhome.R;
import dev.passoca.tvhome.core.Config;

/**
 * Launcher settings as a dssoca Card: two columns of rows (Segmented controls, Switches, the wallpaper
 * Picture row) and the action Buttons underneath. One schema ({@link #ROWS}) drives the view and the
 * D-pad, as in the web prototype.
 */
@SuppressLint("ViewConstructor")
final class SettingsSheet extends LinearLayout {

    interface Host {
        Config config();
        void setChoice(String key, Object value);
        void openWallpaperPicker(View anchor);
        void followConfig();
        void chooseDefaultHome();
        String wallpaperDescription();
        Drawable wallpaperThumb();
    }

    private enum Type { GROUP, BREAK, SEG, SWITCH, PICK, ACTS }

    private static final class Row {
        final Type type; final String key; final int label; final Object[] opts;
        Row(Type type, String key, int label, Object... opts) { this.type = type; this.key = key; this.label = label; this.opts = opts; }
    }

    private static final Row[] ROWS = {
            new Row(Type.GROUP, null, R.string.group_layout),
            new Row(Type.SEG, "columns", R.string.apps_per_row, 4, 5, 6),
            new Row(Type.GROUP, null, R.string.group_wallpaper),
            new Row(Type.PICK, "wallpaper", R.string.picture),
            new Row(Type.SEG, "wallpaper_order", R.string.order, Config.ORDER_SHUFFLE, Config.ORDER_SEQUENTIAL),
            new Row(Type.SEG, "wallpaper_rotation_minutes", R.string.change_every, 0, 1, 30, 60),
            new Row(Type.BREAK, null, 0),
            new Row(Type.GROUP, null, R.string.group_motion),
            new Row(Type.SWITCH, "animations", R.string.animations),
            new Row(Type.SWITCH, "advanced_effects", R.string.advanced_effects),
            new Row(Type.SWITCH, "focus_overshoot", R.string.focus_overshoot),
            new Row(Type.SWITCH, "ambient_glow", R.string.app_glow),
            new Row(Type.ACTS, null, 0),
    };

    private final Dss dss;
    private final Host host;
    private final List<Row> focusable = new ArrayList<>();
    private final List<View> controls = new ArrayList<>();   // one per focusable row
    int row = 0, col = 0;

    SettingsSheet(Dss dss, Host host) {
        super(dss.ctx);
        this.dss = dss;
        this.host = host;
        setOrientation(VERTICAL);
        setBackground(dss.box(dss.bgElev, dss.line));
        setElevation(dss.dpf(16));
        build();
        col = colOf(focusable.get(0));
        paint();
    }

    private void build() {
        removeAllViews(); focusable.clear(); controls.clear();
        // head: uppercase mono title, faint meta, rule below (ss-card .head)
        LinearLayout head = new LinearLayout(dss.ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dss.dp(18), dss.dp(12), dss.dp(18), dss.dp(12));
        TextView title = dss.caps(12f, dss.fg, 0.06f);
        title.setText(R.string.launcher_settings);
        head.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView meta = dss.text(12f, dss.fgFaint, dss.body);
        meta.setText(R.string.esc_to_close);
        head.addView(meta);
        addView(head);
        addView(rule(0));

        LinearLayout body = new LinearLayout(dss.ctx);
        body.setOrientation(VERTICAL);
        body.setPadding(dss.dp(18), dss.dp(14), dss.dp(18), dss.dp(16));
        LinearLayout cols = new LinearLayout(dss.ctx);
        LinearLayout column = column();
        cols.addView(column, colParams(false));
        for (Row r : ROWS) {
            switch (r.type) {
                case GROUP: {
                    TextView g = dss.caps(10f, dss.fgFaint, 0.14f);
                    g.setText(r.label);
                    g.setPadding(0, column.getChildCount() == 0 ? 0 : dss.dp(10), 0, dss.dp(6));
                    column.addView(g);
                    break;
                }
                case BREAK:
                    column = column();
                    cols.addView(column, colParams(true));
                    break;
                case ACTS: {
                    body.addView(cols);
                    View sep = rule(14);
                    body.addView(sep);
                    LinearLayout acts = new LinearLayout(dss.ctx);
                    acts.addView(button(R.string.follow_config, false));
                    View gap = new View(dss.ctx);
                    acts.addView(gap, new LayoutParams(dss.dp(10), 1));
                    acts.addView(button(R.string.default_home, true));
                    body.addView(acts);
                    focusable.add(r);
                    controls.add(acts);
                    break;
                }
                default: {
                    LinearLayout line = new LinearLayout(dss.ctx);
                    line.setGravity(Gravity.CENTER_VERTICAL);
                    line.setMinimumHeight(dss.dp(40));
                    line.setPadding(dss.dp(6), 0, dss.dp(6), 0);
                    LinearLayout lab = new LinearLayout(dss.ctx);
                    lab.setOrientation(VERTICAL);
                    TextView t = dss.text(13f, dss.fg, dss.body);
                    t.setText(r.label);
                    TextView k = dss.text(10f, dss.fgFaint, dss.body);
                    k.setText(r.key);
                    k.setPadding(0, dss.dp(3), 0, 0);
                    lab.addView(t);
                    lab.addView(k);
                    line.addView(lab, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
                    View ctrl = r.type == Type.SEG ? segmented(r) : r.type == Type.SWITCH ? new Views.SwitchView(dss) : pick();
                    line.addView(ctrl);
                    column.addView(line, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                    focusable.add(r);
                    controls.add(r.type == Type.PICK ? line : ctrl);
                }
            }
        }
        addView(body);
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(dss.ctx);
        c.setOrientation(VERTICAL);
        return c;
    }

    private LayoutParams colParams(boolean second) {
        LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        if (second) lp.leftMargin = dss.dp(32);
        return lp;
    }

    private View rule(int topMarginDp) {
        View v = new View(dss.ctx);
        v.setBackgroundColor(dss.line);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, dss.dp(1)));
        lp.topMargin = dss.dp(topMarginDp);
        lp.bottomMargin = dss.dp(topMarginDp);
        v.setLayoutParams(lp);
        return v;
    }

    private String optLabel(Row r, Object o) {
        if ("wallpaper_rotation_minutes".equals(r.key)) {
            int m = (Integer) o;
            return m == 0 ? dss.ctx.getString(R.string.never) : dss.ctx.getString(R.string.minutes, m);
        }
        return String.valueOf(o);
    }

    private LinearLayout segmented(Row r) {
        LinearLayout s = new LinearLayout(dss.ctx);
        s.setBackground(dss.box(dss.bg, dss.line));
        for (Object o : r.opts) {
            TextView t = dss.text(12f, dss.fgMuted, dss.bodyMedium);
            t.setText(optLabel(r, o));
            t.setGravity(Gravity.CENTER);
            t.setPadding(dss.dp(12), dss.dp(7), dss.dp(12), dss.dp(7));
            s.addView(t);
        }
        return s;
    }

    private LinearLayout pick() {
        LinearLayout p = new LinearLayout(dss.ctx);
        p.setGravity(Gravity.CENTER_VERTICAL);
        ImageView th = Views.thumb(dss, host.wallpaperThumb(), 48, 27);
        p.addView(th);
        TextView t = dss.text(12f, dss.fgMuted, dss.body);
        t.setText(host.wallpaperDescription() + " ›");
        t.setMaxWidth(dss.dp(190));   // a long picture name must not push the row's label out
        t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        t.setPadding(dss.dp(10), 0, 0, 0);
        p.addView(t);
        return p;
    }

    private TextView button(int label, boolean ghost) {
        TextView b = dss.text(14f, ghost ? dss.fgMuted : dss.fg, dss.bodyMedium);
        b.setText(label);
        b.setPadding(dss.dp(16), dss.dp(9), dss.dp(16), dss.dp(9));
        b.setBackground(ghost ? null : dss.box(0, dss.line));
        return b;
    }

    private Object current(Row r) {
        Config c = host.config();
        switch (r.key) {
            case "columns": return c.columns;
            case "wallpaper_order": return c.wallpaperOrder;
            case "wallpaper_rotation_minutes": return c.wallpaperRotationMinutes;
            case "animations": return c.animations;
            case "advanced_effects": return c.advancedEffects;
            case "focus_overshoot": return c.focusOvershoot;
            case "ambient_glow": return c.ambientGlow;
            default: return null;
        }
    }

    private int colOf(Row r) {
        if (r.type != Type.SEG) return 0;
        Object cur = current(r);
        for (int i = 0; i < r.opts.length; i++) if (r.opts[i].equals(cur)) return i;
        return 0;
    }

    /** Rebuilds after a change (wallpaper description, selection states), keeping focus where it was. */
    void refresh() {
        build();
        paint();
    }

    void paint() {
        for (int i = 0; i < focusable.size(); i++) {
            Row r = focusable.get(i);
            View v = controls.get(i);
            boolean f = i == row;
            switch (r.type) {
                case SEG: {
                    LinearLayout s = (LinearLayout) v;
                    Object cur = current(r);
                    for (int j = 0; j < s.getChildCount(); j++) {
                        TextView t = (TextView) s.getChildAt(j);
                        boolean on = r.opts[j].equals(cur), focus = f && j == col;
                        t.setTextColor(on ? dss.fgOnPrimary : focus ? dss.fg : dss.fgMuted);
                        if (focus) t.setBackground(dss.box(on ? dss.primary : 0, dss.fgShine, 2));
                        else t.setBackground(on ? dss.box(dss.primary, 0) : null);
                    }
                    break;
                }
                case SWITCH: {
                    Views.SwitchView sw = (Views.SwitchView) v;
                    sw.on = Boolean.TRUE.equals(current(r));
                    sw.focused = f;
                    sw.invalidate();
                    break;
                }
                case PICK: {
                    if (f) {
                        LayerDrawable d = new LayerDrawable(new Drawable[]{dss.box(dss.primarySoft, 0), dss.box(dss.primary, 0)});
                        d.setLayerWidth(1, dss.dp(3));
                        d.setLayerGravity(1, Gravity.START | Gravity.FILL_VERTICAL);
                        v.setBackground(d);
                    } else v.setBackground(null);
                    break;
                }
                case ACTS: {
                    LinearLayout acts = (LinearLayout) v;
                    TextView a = (TextView) acts.getChildAt(0), b = (TextView) acts.getChildAt(2);
                    a.setBackground(f && col == 0 ? dss.box(0, dss.primary, 2) : dss.box(0, dss.line));
                    b.setBackground(f && col == 1 ? dss.box(0, dss.primary, 2) : null);
                    b.setTextColor(f && col == 1 ? dss.fg : dss.fgMuted);
                    break;
                }
                default:
            }
        }
    }

    /** D-pad inside the sheet; returns false for Back so the activity closes it. */
    boolean onKey(int keyCode) {
        Row r = focusable.get(row);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
                row = Math.max(0, Math.min(focusable.size() - 1, row + (keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1)));
                col = colOf(focusable.get(row));
                paint();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int max = r.type == Type.SEG ? r.opts.length - 1 : r.type == Type.ACTS ? 1 : 0;
                col = Math.max(0, Math.min(max, col + (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1)));
                paint();
                return true;
            }
            default:
                return false;
        }
    }

    void activate() {
        Row r = focusable.get(row);
        switch (r.type) {
            case SEG: host.setChoice(r.key, r.opts[col]); break;
            case SWITCH: host.setChoice(r.key, !Boolean.TRUE.equals(current(r))); break;
            case PICK: host.openWallpaperPicker(controls.get(row)); break;
            case ACTS: if (col == 0) host.followConfig(); else host.chooseDefaultHome(); break;
            default:
        }
        refresh();
    }
}
