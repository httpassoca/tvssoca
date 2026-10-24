package dev.passoca.tvhome.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import dev.passoca.tvhome.R;

/**
 * dssoca on Android: the token colours and faces (res/values/colors.xml, res/font) plus the few drawables
 * every component shares. Zero radius everywhere, 1 dp lines, signal green for focus and selection.
 */
final class Dss {

    final Context ctx;
    final float density;
    final Typeface display, body, bodyMedium;

    final int bg, bgElev, bgElevHover, fg, fgShine, fgMuted, fgFaint, fgOnPrimary,
            primary, primarySoft, primaryGlow, line, lineStrong, hover, blue, yellow, red,
            badgePositiveBg, badgePositiveBorder, badgeNeutralBg, chromeBg;

    Dss(Context c) {
        ctx = c;
        density = c.getResources().getDisplayMetrics().density;
        display = c.getResources().getFont(R.font.electrolize_regular);
        body = c.getResources().getFont(R.font.jetbrains_mono_regular);
        bodyMedium = c.getResources().getFont(R.font.jetbrains_mono_medium);
        bg = c.getColor(R.color.ss_bg);
        bgElev = c.getColor(R.color.ss_bg_elev);
        bgElevHover = c.getColor(R.color.ss_bg_elev_hover);
        fg = c.getColor(R.color.ss_fg);
        fgShine = c.getColor(R.color.ss_fg_shine);
        fgMuted = c.getColor(R.color.ss_fg_muted);
        fgFaint = c.getColor(R.color.ss_fg_faint);
        fgOnPrimary = c.getColor(R.color.ss_fg_on_primary);
        primary = c.getColor(R.color.ss_primary);
        primarySoft = c.getColor(R.color.ss_primary_soft);
        primaryGlow = c.getColor(R.color.ss_primary_glow);
        line = c.getColor(R.color.ss_line);
        lineStrong = c.getColor(R.color.ss_line_strong);
        hover = c.getColor(R.color.ss_hover);
        blue = c.getColor(R.color.ss_blue);
        yellow = c.getColor(R.color.ss_yellow);
        red = c.getColor(R.color.ss_red);
        badgePositiveBg = c.getColor(R.color.ss_badge_positive_bg);
        badgePositiveBorder = c.getColor(R.color.ss_badge_positive_border);
        badgeNeutralBg = c.getColor(R.color.ss_badge_neutral_bg);
        chromeBg = c.getColor(R.color.tv_chrome_bg);
    }

    int dp(float v) { return Math.round(v * density); }
    float dpf(float v) { return v * density; }

    /** Square fill with a 1 dp border: dssoca's card, panel and control surface. */
    GradientDrawable box(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(0f);
        d.setColor(fill);
        if (stroke != 0) d.setStroke(Math.max(1, dp(1)), stroke);
        return d;
    }

    GradientDrawable box(int fill, int stroke, float strokeDp) {
        GradientDrawable d = box(fill, 0);
        d.setStroke(dp(strokeDp), stroke);
        return d;
    }

    TextView text(float sp, int color, Typeface face) {
        TextView t = new TextView(ctx);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(face);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    /** Small uppercase label: dssoca's card-head / caption voice. */
    TextView caps(float sp, int color, float tracking) {
        TextView t = text(sp, color, bodyMedium);
        t.setAllCaps(true);
        t.setLetterSpacing(tracking);
        return t;
    }

    static int withAlpha(int color, float alpha) {
        return Color.argb(Math.round(Math.max(0f, Math.min(1f, alpha)) * 255), Color.red(color), Color.green(color), Color.blue(color));
    }

    /** Mix toward white by {@code t} (0..1), for the rim of a tinted focus. */
    static int lift(int color, float t) {
        return Color.rgb(Math.round(Color.red(color) + (255 - Color.red(color)) * t),
                Math.round(Color.green(color) + (255 - Color.green(color)) * t),
                Math.round(Color.blue(color) + (255 - Color.blue(color)) * t));
    }
}
