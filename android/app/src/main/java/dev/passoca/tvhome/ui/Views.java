package dev.passoca.tvhome.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.passoca.tvhome.data.AppsRepository;

/** The small custom views Home is drawn with. Every colour comes from {@link Dss}. */
final class Views {

    private Views() {}

    /** One app tile: the app's banner (or its icon on a dssoca card), the name under it on grid rows. */
    @SuppressLint("ViewConstructor")
    static final class TileView extends FrameLayout {
        final AppsRepository.App app;
        final FrameLayout card;
        final ImageView banner;
        final TextView name;
        private final Dss dss;

        TileView(Dss dss, AppsRepository.App app) {
            super(dss.ctx);
            this.dss = dss;
            this.app = app;
            setClipChildren(false);
            card = new FrameLayout(dss.ctx);
            card.setBackground(dss.box(dss.bgElev, dss.line));
            card.setClipToOutline(true);
            banner = new ImageView(dss.ctx);
            banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(banner, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            if (app.banner != null) {
                banner.setImageDrawable(app.banner);
            } else {
                // No banner: the icon on the elevated surface, label underneath, like a dssoca card.
                LinearLayout col = new LinearLayout(dss.ctx);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER);
                ImageView icon = new ImageView(dss.ctx);
                icon.setImageDrawable(app.icon);
                col.addView(icon, new LinearLayout.LayoutParams(dss.dp(40), dss.dp(40)));
                TextView label = dss.text(12f, dss.fg, dss.body);
                label.setText(app.label);
                label.setPadding(dss.dp(8), dss.dp(8), dss.dp(8), 0);
                col.addView(label);
                card.addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            }
            addView(card);
            name = dss.text(13f, dss.fg, dss.body);
            name.setText(app.label);
            name.setGravity(Gravity.CENTER);
            name.setAlpha(0f);
            addView(name);
        }

        void size(float wDp, float hDp) {
            card.setLayoutParams(new LayoutParams(dss.dp(wDp), dss.dp(hDp)));
            LayoutParams lp = new LayoutParams(dss.dp(wDp + 20), dss.dp(20));
            lp.leftMargin = -dss.dp(10);
            lp.topMargin = dss.dp(hDp + 9);
            name.setLayoutParams(lp);
        }

        /** Focus zoom (1.10, overshooting when focus_overshoot is on); the name shows on grid rows only. */
        void setFocus(boolean focused, float scale, boolean overshoot, boolean animate, boolean showName) {
            float s = focused ? scale : 1f;
            card.animate().cancel();
            if (animate) {
                card.animate().scaleX(s).scaleY(s).setDuration(focused && overshoot ? 340 : 220)
                        .setInterpolator(focused && overshoot ? new OvershootInterpolator(1.6f) : new DecelerateInterpolator()).start();
            } else {
                card.setScaleX(s);
                card.setScaleY(s);
            }
            float a = focused && showName ? 1f : 0f;
            name.animate().cancel();
            if (animate) name.animate().alpha(a).translationY(a > 0 ? 0 : -dss.dpf(6)).setDuration(150).start();
            else { name.setAlpha(a); name.setTranslationY(0); }
        }
    }

    /** The focus frame: GlassView redrawn as a square dssoca ring (2 dp accent, no glow), placed by a spring. */
    static final class FocusFrameView extends View {
        private final Dss dss;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private int color;
        boolean shown;

        FocusFrameView(Dss dss) {
            super(dss.ctx);
            this.dss = dss;
            color = dss.primary;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dss.dpf(2));
            inner.setStyle(Paint.Style.STROKE);
            inner.setStrokeWidth(dss.dpf(1));
            inner.setColor(Dss.withAlpha(dss.bg, 0.6f));
            setWillNotDraw(false);
        }

        void setRect(float xDp, float yDp, float wDp, float hDp) {
            rect.set(dss.dpf(xDp), dss.dpf(yDp), dss.dpf(xDp + wDp), dss.dpf(yDp + hDp));
            invalidate();
        }

        void setColor(int c) { color = c; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            if (!shown || rect.isEmpty()) return;
            stroke.setColor(color);
            float h = stroke.getStrokeWidth() / 2f;
            c.drawRect(rect.left + h, rect.top + h, rect.right - h, rect.bottom - h, stroke);
            float i2 = stroke.getStrokeWidth() + inner.getStrokeWidth() / 2f;
            c.drawRect(rect.left + i2, rect.top + i2, rect.right - i2, rect.bottom - i2, inner);
        }
    }

    /** Ambient glow in the focused app's colour: radial, centred at 32% / 42%, radius 62% of the width. */
    static final class GlowView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color;

        GlowView(Context c) { super(c); setWillNotDraw(false); }

        void setColor(int c) { color = c; rebuild(); invalidate(); }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { rebuild(); }

        private void rebuild() {
            if (getWidth() == 0) return;
            float w = getWidth(), h = getHeight();
            paint.setShader(new RadialGradient(w * 0.32f, h * 0.42f, w * 0.62f,
                    new int[]{Dss.withAlpha(color, 0.38f), Dss.withAlpha(color, 0.14f), Dss.withAlpha(color, 0f)},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override protected void onDraw(Canvas c) { c.drawRect(0, 0, getWidth(), getHeight(), paint); }
    }

    /** dssoca's faint 48 dp line grid, fading out towards the edges. */
    static final class GridBackgroundView extends View {
        private final Dss dss;
        private final Paint p = new Paint();

        GridBackgroundView(Dss dss) { super(dss.ctx); this.dss = dss; setWillNotDraw(false); }

        @Override protected void onDraw(Canvas c) {
            float step = dss.dpf(48), w = getWidth(), h = getHeight(), cx = w / 2f, cy = h * 0.4f;
            for (float x = 0; x <= w; x += step) {
                for (float y = 0; y <= h; y += step) {
                    float dx = (x - cx) / (w * 0.4f), dy = (y - cy) / (h * 0.35f);
                    float fade = Math.max(0f, 1f - (float) Math.sqrt(dx * dx + dy * dy) / 1.5f);
                    if (fade <= 0) continue;
                    p.setColor(Dss.withAlpha(dss.fgShine, 0.12f * 0.35f * fade));
                    c.drawRect(x, y, x + step, y + 1, p);
                    c.drawRect(x, y, x + 1, y + step, p);
                }
            }
        }
    }

    /** dssoca accent Heading ({@code .ss-heading.accent}): display face, accent bar behind the lower part of the text. */
    @SuppressLint({"ViewConstructor", "AppCompatCustomView"})
    static final class HeadingView extends TextView {
        private final Paint bar = new Paint();

        HeadingView(Dss dss, float sp) {
            super(dss.ctx);
            setPadding(dss.dp(4), dss.dp(2), dss.dp(28), dss.dp(4));   // room for the bar's overhang
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sp);
            setTypeface(dss.display);
            setTextColor(dss.fg);
            setIncludeFontPadding(false);
            setSingleLine(true);
            setLetterSpacing(-0.015f);
            setShadowLayer(0.01f, dss.dpf(3), dss.dpf(3), dss.bg);
            bar.setColor(Dss.withAlpha(dss.primary, 0.9f));
        }

        @Override protected void onDraw(Canvas c) {
            float tw = getPaint().measureText(getText().toString()), h = getHeight();
            float left = getPaddingLeft() - tw * 0.02f, right = getPaddingLeft() + tw * 1.08f;
            c.drawRect(left, h * 0.70f, right, h * 0.90f, bar);
            super.onDraw(c);
        }
    }

    /** Icon-only dssoca Button ({@code .ss-btn.icon-only}); focused it takes the primary variant. */
    static final class IconButton extends FrameLayout {
        private final Dss dss;
        private final ImageView icon;

        IconButton(Dss dss, int iconRes, String label) {
            super(dss.ctx);
            this.dss = dss;
            setContentDescription(label);
            icon = new ImageView(dss.ctx);
            icon.setImageResource(iconRes);
            LayoutParams lp = new LayoutParams(dss.dp(20), dss.dp(20), Gravity.CENTER);
            addView(icon, lp);
            setFocused(false, false);
        }

        void setFocused(boolean focused, boolean armed) {
            setBackground(focused ? dss.box(dss.primary, dss.primary) : dss.box(dss.chromeBg, armed ? dss.blue : dss.line));
            icon.setColorFilter(focused ? dss.fgOnPrimary : armed ? dss.blue : dss.fg);
            setTranslationY(focused ? -dss.dpf(1) : 0f);
        }
    }

    /** dssoca Switch: square track and thumb, primary when on. */
    static final class SwitchView extends View {
        private final Dss dss;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean on, focused;

        SwitchView(Dss dss) { super(dss.ctx); this.dss = dss; setWillNotDraw(false); }

        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(dss.dp(44), dss.dp(28)); }

        @Override protected void onDraw(Canvas c) {
            float l = dss.dpf(4), t = dss.dpf(4), r = l + dss.dpf(36), b = t + dss.dpf(20), pad = dss.dpf(2);
            if (focused) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dss.dpf(2));
                p.setColor(dss.primary);
                c.drawRect(l - dss.dpf(3), t - dss.dpf(3), r + dss.dpf(3), b + dss.dpf(3), p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(on ? dss.primary : dss.bgElev);
            c.drawRect(l, t, r, b, p);
            if (!on) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dss.dpf(1));
                p.setColor(dss.line);
                c.drawRect(l, t, r, b, p);
                p.setStyle(Paint.Style.FILL);
            }
            float th = b - t - 2 * pad;
            float tl = on ? r - pad - th : l + pad;
            p.setColor(on ? dss.bg : dss.fg);
            c.drawRect(tl, t + pad, tl + th, t + pad + th, p);
        }
    }

    /** dssoca Badge: mono, lowercase, square dot, tinted wash. */
    static TextView badge(Dss dss, String text, boolean positive) {
        TextView t = dss.text(10f, positive ? dss.primary : dss.fgMuted, dss.body);
        t.setText("■ " + text.toLowerCase(java.util.Locale.ROOT));
        t.setLetterSpacing(0.05f);
        t.setPadding(dss.dp(8), dss.dp(2), dss.dp(8), dss.dp(2));
        t.setBackground(positive ? dss.box(dss.badgePositiveBg, dss.badgePositiveBorder) : dss.box(dss.badgeNeutralBg, dss.line));
        return t;
    }

    static ImageView icon(Dss dss, int res, int tint, float sizeDp) {
        ImageView v = new ImageView(dss.ctx);
        v.setImageResource(res);
        v.setColorFilter(tint);
        v.setLayoutParams(new LinearLayout.LayoutParams(dss.dp(sizeDp), dss.dp(sizeDp)));
        return v;
    }

    static ImageView thumb(Dss dss, Drawable d, float wDp, float hDp) {
        ImageView v = new ImageView(dss.ctx);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setImageDrawable(d);
        v.setBackground(dss.box(dss.bgElev, dss.line));
        v.setPadding(1, 1, 1, 1);
        v.setLayoutParams(new LinearLayout.LayoutParams(dss.dp(wDp), dss.dp(hDp)));
        return v;
    }
}
