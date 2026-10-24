package dev.passoca.tvhome.ui;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.passoca.tvhome.R;

/**
 * The Help button's card: a dssoca Card listing the launcher's keys, each as a Kbd chip with its meaning.
 * Read-only; OK or Back closes it.
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

    HelpCard(Dss dss) {
        super(dss.ctx);
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
        for (int[] r : ROWS) {
            LinearLayout line = new LinearLayout(dss.ctx);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(0, dss.dp(5), 0, dss.dp(5));
            // dssoca Kbd: square key chip, 1 dp strong line, mono
            TextView key = dss.text(11f, dss.fg, dss.bodyMedium);
            key.setText(r[0]);
            key.setPadding(dss.dp(7), dss.dp(3), dss.dp(7), dss.dp(3));
            key.setBackground(dss.box(dss.bg, dss.lineStrong));
            LinearLayout keyCol = new LinearLayout(dss.ctx);
            keyCol.addView(key);
            line.addView(keyCol, new LayoutParams(dss.dp(130), LayoutParams.WRAP_CONTENT));
            TextView what = dss.text(12f, dss.fgMuted, dss.body);
            what.setSingleLine(false);
            what.setText(r[1]);
            line.addView(what, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            body.addView(line);
        }
        addView(body);
    }
}
