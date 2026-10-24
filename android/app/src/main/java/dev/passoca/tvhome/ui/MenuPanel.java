package dev.passoca.tvhome.ui;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import dev.passoca.tvhome.R;
import dev.passoca.tvhome.core.HomeModel;

/**
 * dssoca Menu ({@code .ss-menu > .panel > .item}): elevated square panel, rows with a lead icon or
 * swatch, label, optional badge and a check marker for selection groups. D-pad focus draws the same
 * state as dssoca's :focus-visible item (soft accent wash, 3 dp accent bar on the left).
 */
@SuppressLint("ViewConstructor")
final class MenuPanel extends FrameLayout {

    static final class Item {
        final String label;
        final int icon;
        final Drawable thumb;
        final String badge;
        final boolean badgePositive, sep;
        final Boolean selected;     // null: plain action row
        final Runnable act;

        private Item(String label, int icon, Drawable thumb, String badge, boolean badgePositive, Boolean selected, boolean sep, Runnable act) {
            this.label = label; this.icon = icon; this.thumb = thumb; this.badge = badge; this.badgePositive = badgePositive;
            this.selected = selected; this.sep = sep; this.act = act;
        }

        static Item action(String label, int icon, Runnable act) { return new Item(label, icon, null, null, false, null, false, act); }
        static Item badged(String label, int icon, String badge, boolean positive, Runnable act) { return new Item(label, icon, null, badge, positive, null, false, act); }
        static Item choice(String label, int icon, Drawable thumb, boolean selected, Runnable act) { return new Item(label, icon, thumb, null, false, selected, false, act); }
        static Item separator() { return new Item(null, 0, null, null, false, null, true, null); }
    }

    final String kind;
    final List<Item> items;
    /** Zone to return to when the menu closes. */
    final HomeModel.Zone back;
    int sel;

    private final Dss dss;
    private final List<View> rows = new ArrayList<>();
    private final ScrollView scroll;

    MenuPanel(Dss dss, String kind, List<Item> items, HomeModel.Zone back) {
        super(dss.ctx);
        this.dss = dss;
        this.kind = kind;
        this.items = items;
        this.back = back;
        setBackground(dss.box(dss.bgElev, dss.line));
        setElevation(dss.dpf(24));   // above the settings sheet (16) it can open from
        int pad = dss.dp(4);
        setPadding(pad, pad, pad, pad);

        LinearLayout list = new LinearLayout(dss.ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        boolean selectable = false;
        for (Item it : items) if (it.selected != null) selectable = true;
        for (Item it : items) {
            View row = it.sep ? separator() : row(it, selectable);
            rows.add(row);
            list.addView(row);
        }
        scroll = new ScrollView(dss.ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(list);
        addView(scroll, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        sel = firstSelectable();
        for (int i = 0; i < items.size(); i++) if (Boolean.TRUE.equals(items.get(i).selected)) sel = i;
        paint();
    }

    private int firstSelectable() {
        for (int i = 0; i < items.size(); i++) if (!items.get(i).sep) return i;
        return 0;
    }

    private View separator() {
        View v = new View(dss.ctx);
        v.setBackgroundColor(dss.line);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dss.dp(1)));
        lp.setMargins(dss.dp(8), dss.dp(4), dss.dp(8), dss.dp(4));
        v.setLayoutParams(lp);
        return v;
    }

    private View row(Item it, boolean selectable) {
        LinearLayout r = new LinearLayout(dss.ctx);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dss.dp(12), dss.dp(8), dss.dp(12), dss.dp(8));
        r.setMinimumWidth(dss.dp(220));
        if (it.thumb != null) r.addView(Views.thumb(dss, it.thumb, 44, 25));
        else r.addView(Views.icon(dss, it.icon, dss.fgMuted, 20));
        TextView label = dss.text(13f, dss.fgMuted, dss.bodyMedium);
        label.setText(it.label);
        label.setMaxWidth(dss.dp(300));
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dss.dp(10);
        r.addView(label, lp);
        if (it.badge != null) {
            TextView b = Views.badge(dss, it.badge, it.badgePositive);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bl.leftMargin = dss.dp(14);
            r.addView(b, bl);
        }
        if (selectable) {
            ImageView m = Views.icon(dss, R.drawable.ic_check, dss.primary, 20);
            m.setVisibility(Boolean.TRUE.equals(it.selected) ? VISIBLE : INVISIBLE);
            ((LinearLayout.LayoutParams) m.getLayoutParams()).leftMargin = dss.dp(10);
            r.addView(m);
        }
        return r;
    }

    boolean[] separators() {
        boolean[] s = new boolean[items.size()];
        for (int i = 0; i < s.length; i++) s[i] = items.get(i).sep;
        return s;
    }

    void move(int delta) {
        sel = HomeModel.menuMove(separators(), sel, delta);
        paint();
    }

    void activate() {
        Item it = items.get(sel);
        if (it.act != null) it.act.run();
    }

    void paint() {
        for (int i = 0; i < rows.size(); i++) {
            Item it = items.get(i);
            if (it.sep) continue;
            View r = rows.get(i);
            boolean f = i == sel;
            if (f) {
                LayerDrawable d = new LayerDrawable(new Drawable[]{dss.box(dss.primarySoft, 0), dss.box(dss.primary, 0)});
                d.setLayerWidth(1, dss.dp(3));
                d.setLayerGravity(1, Gravity.START | Gravity.FILL_VERTICAL);
                r.setBackground(d);
            } else {
                r.setBackground(Boolean.TRUE.equals(it.selected) ? dss.box(Dss.withAlpha(dss.primary, 0.06f), 0) : null);
            }
            LinearLayout row = (LinearLayout) r;
            View lead = row.getChildAt(0);
            if (lead instanceof ImageView && it.thumb == null) ((ImageView) lead).setColorFilter(f ? dss.fg : dss.fgMuted);
            ((TextView) row.getChildAt(1)).setTextColor(f || Boolean.TRUE.equals(it.selected) ? dss.fg : dss.fgMuted);
        }
        // keep the focused row inside the scrolling panel (the wallpaper picker is taller than the screen allows)
        View r = rows.get(sel);
        post(() -> {
            int top = r.getTop(), bottom = r.getBottom(), y = scroll.getScrollY(), h = scroll.getHeight();
            if (top < y) scroll.scrollTo(0, top - dss.dp(4));
            else if (bottom > y + h) scroll.scrollTo(0, bottom - h + dss.dp(4));
        });
    }

    /** Caps the panel's height so long lists scroll inside it. */
    @Override protected void onMeasure(int w, int h) {
        int max = dss.dp(360);
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
    }
}
