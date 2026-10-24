package dev.passoca.tvhome.ui;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import dev.passoca.tvhome.R;
import dev.passoca.tvhome.core.Config;

/**
 * The Help button's card: a dssoca Card listing the launcher's keys, each as a Kbd chip with its meaning.
 * Ends with the apps removed from the TV (config.json removed_apps). Up and down scroll; OK or Back closes it.
 */
@SuppressLint("ViewConstructor")
final class HelpCard extends LinearLayout {

    private static final int[][] ROWS = {
            {R.string.help_k_move, R.string.help_move},
            {R.string.help_k_ok, R.string.help_ok},
            {R.string.help_k_hold_app, R.string.help_hold_app},
            {R.string.help_k_hold_gear, R.string.help_hold_gear},
            {R.string.help_k_back, R.string.help_back},
            {R.string.help_k_home, R.string.help_home},
            {R.string.help_k_reorder, R.string.help_reorder},
            {R.string.help_k_config, R.string.help_config},
    };

    private final Dss dss;
    private final ScrollView scroll;

    HelpCard(Dss dss, Config cfg) {
        super(dss.ctx);
        this.dss = dss;
        setOrientation(VERTICAL);
        setBackground(dss.box(dss.bgElev, dss.line));
        setElevation(dss.dpf(16));

        // head: uppercase mono title, faint meta, rule below (ss-card .head)
        LinearLayout head = new LinearLayout(dss.ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dss.dp(18), dss.dp(12), dss.dp(18), dss.dp(12));
        TextView title = dss.caps(12f, dss.fg, 0.06f);
        title.setText(R.string.help_title);
        head.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView meta = dss.text(12f, dss.fgFaint, dss.body);
        meta.setText(R.string.esc_to_close);
        head.addView(meta);
        addView(head);
        View rule = new View(dss.ctx);
        rule.setBackgroundColor(dss.line);
        addView(rule, new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, dss.dp(1))));

        LinearLayout body = new LinearLayout(dss.ctx);
        body.setOrientation(VERTICAL);
        body.setPadding(dss.dp(18), dss.dp(10), dss.dp(18), dss.dp(14));
        for (int[] r : ROWS) body.addView(row(dss.ctx.getString(r[0]), dss.ctx.getString(r[1])));

        if (!cfg.removedApps.isEmpty()) {
            TextView group = dss.caps(10f, dss.fgFaint, 0.14f);
            group.setText(R.string.help_removed);
            group.setPadding(0, dss.dp(14), 0, dss.dp(4));
            body.addView(group);
            List<String> play = new ArrayList<>(), system = new ArrayList<>();
            for (Config.RemovedApp a : cfg.removedApps) (a.system ? system : play).add(a.label);
            if (!play.isEmpty()) body.addView(row(dss.ctx.getString(R.string.help_k_play), String.join(" · ", play)));
            if (!system.isEmpty()) {
                body.addView(row(dss.ctx.getString(R.string.help_k_system), String.join(" · ", system)));
                body.addView(row("", dss.ctx.getString(R.string.help_system_how)));
            }
        }
        scroll = new ScrollView(dss.ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(body);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** One line: a dssoca Kbd chip (square, 1 dp strong line, mono) and what it does. */
    private LinearLayout row(String keyText, String whatText) {
        LinearLayout line = new LinearLayout(dss.ctx);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, dss.dp(5), 0, dss.dp(5));
        LinearLayout keyCol = new LinearLayout(dss.ctx);
        if (!keyText.isEmpty()) {
            TextView key = dss.text(11f, dss.fg, dss.bodyMedium);
            key.setText(keyText);
            key.setPadding(dss.dp(7), dss.dp(3), dss.dp(7), dss.dp(3));
            key.setBackground(dss.box(dss.bg, dss.lineStrong));
            keyCol.addView(key);
        }
        line.addView(keyCol, new LayoutParams(dss.dp(130), LayoutParams.WRAP_CONTENT));
        TextView what = dss.text(12f, dss.fgMuted, dss.body);
        what.setSingleLine(false);
        what.setText(whatText);
        line.addView(what, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        return line;
    }

    /** D-pad up / down: scroll the card a few lines. */
    void scrollBy(int direction) { scroll.smoothScrollBy(0, direction * dss.dp(72)); }

    /** Never taller than the screen below the top bar. */
    @Override protected void onMeasure(int w, int h) {
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(dss.dp(440), MeasureSpec.AT_MOST));
    }
}
