package com.vyd02.overdrive;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Overdrive's design language: type, colors, glass surfaces, focus feel, and eased motion. */
final class Ui {
    private Ui() { }

    // ---- color ----
    static final int TEXT = Color.WHITE;
    static final int SUB = Color.argb(178, 255, 255, 255);
    static final int FAINT = Color.argb(110, 255, 255, 255);
    static final int INK = Color.parseColor("#0A0D18");       // text on white surfaces
    static final int ACCENT = Color.parseColor("#3DA9FF");
    static final int GLASS = Color.argb(34, 255, 255, 255);
    static final int GLASS_EDGE = Color.argb(46, 255, 255, 255);
    static final int PANEL = Color.argb(236, 10, 13, 24);

    // ---- motion ----
    static final PathInterpolator EASE = new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);   // standard
    static final PathInterpolator EASE_OUT = new PathInterpolator(0.16f, 1f, 0.3f, 1f);   // glide in
    static final PathInterpolator SPRING = new PathInterpolator(0.2f, 0.9f, 0.25f, 1.08f); // tiny overshoot

    // ---- type ----
    private static Typeface dLight, dMedium, dBold, bRegular, bSemi;

    static Typeface display(Context c, int weight) {
        load(c);
        return weight >= 700 ? dBold : weight >= 500 ? dMedium : dLight;
    }

    static Typeface body(Context c, boolean semibold) {
        load(c);
        return semibold ? bSemi : bRegular;
    }

    private static void load(Context c) {
        if (dBold != null) return;
        try {
            dLight = Typeface.createFromAsset(c.getAssets(), "fonts/outfit_light.ttf");
            dMedium = Typeface.createFromAsset(c.getAssets(), "fonts/outfit_medium.ttf");
            dBold = Typeface.createFromAsset(c.getAssets(), "fonts/outfit_bold.ttf");
            bRegular = Typeface.createFromAsset(c.getAssets(), "fonts/inter_regular.ttf");
            bSemi = Typeface.createFromAsset(c.getAssets(), "fonts/inter_semibold.ttf");
        } catch (Exception e) {
            dLight = Typeface.create("sans-serif-light", Typeface.NORMAL);
            dMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            dBold = Typeface.create("sans-serif", Typeface.BOLD);
            bRegular = Typeface.create("sans-serif", Typeface.NORMAL);
            bSemi = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        }
    }

    /** Text that fades without an extra offscreen pass. */
    static class Txt extends TextView {
        Txt(Context c) { super(c); }
        @Override public boolean hasOverlappingRendering() { return false; }
    }

    static TextView text(Context c, float sp, int color, Typeface tf) {
        TextView t = new Txt(c);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(true);
        return t;
    }

    static float dp(Context c, float v) { return v * c.getResources().getDisplayMetrics().density; }

    // ---- surfaces ----

    static GradientDrawable glass(Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, radiusDp));
        g.setColor(GLASS);
        g.setStroke(Math.round(dp(c, 1)), GLASS_EDGE);
        return g;
    }

    static GradientDrawable solid(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, radiusDp));
        g.setColor(color);
        return g;
    }

    // ---- eased scrolling (the default scroller feels mechanical) ----

    static void scrollY(ScrollView v, int to, long ms) {
        Object old = v.getTag(R.id.scroll_anim);
        if (old instanceof ValueAnimator) ((ValueAnimator) old).cancel();
        int from = v.getScrollY();
        if (Math.abs(from - to) < 2) return;
        ValueAnimator a = ValueAnimator.ofInt(from, to);
        a.setDuration(ms);
        a.setInterpolator(EASE_OUT);
        a.addUpdateListener(an -> v.scrollTo(0, (int) an.getAnimatedValue()));
        v.setTag(R.id.scroll_anim, a);
        a.start();
    }

    static void scrollX(HorizontalScrollView v, int to, long ms) {
        Object old = v.getTag(R.id.scroll_anim);
        if (old instanceof ValueAnimator) ((ValueAnimator) old).cancel();
        int from = v.getScrollX();
        if (Math.abs(from - to) < 2) return;
        ValueAnimator a = ValueAnimator.ofInt(from, to);
        a.setDuration(ms);
        a.setInterpolator(EASE_OUT);
        a.addUpdateListener(an -> v.scrollTo((int) an.getAnimatedValue(), 0));
        v.setTag(R.id.scroll_anim, a);
        a.start();
    }

    // ---- pill buttons (top bar + hero actions) ----

    /** Small line icon drawn with the shared icon set. */
    static class IconView extends View {
        String icon;
        int color = TEXT;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        final RectF r = new RectF();
        IconView(Context c, String icon) { super(c); this.icon = icon; }
        void setColor(int c) { color = c; invalidate(); }
        void setIcon(String i) { icon = i; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            p.setColor(color);
            float s = Math.min(getWidth(), getHeight()) * 0.42f;
            Icons.draw(c, icon, getWidth() / 2f, getHeight() / 2f, s, p, path, r,
                    getResources().getDisplayMetrics().density, 0f);
        }
    }

    /** Focusable pill: glass normally, solid white with dark content when focused. */
    static class Pill extends LinearLayout {
        final IconView icon;
        final TextView label;
        private final GradientDrawable bg;
        private final boolean primary;

        Pill(Context c, String iconName, String text, boolean primary) {
            super(c);
            this.primary = primary;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER);
            setFocusable(true);
            setClickable(true);
            int h = Math.round(dp(c, text == null ? 40 : 38));
            setMinimumHeight(h);
            setPadding(Math.round(dp(c, text == null ? 0 : 16)), 0, Math.round(dp(c, text == null ? 0 : 20)), 0);
            bg = new GradientDrawable();
            bg.setCornerRadius(dp(c, 22));
            setBackground(bg);
            icon = new IconView(c, iconName);
            addView(icon, new LayoutParams(Math.round(dp(c, text == null ? 40 : 22)), Math.round(dp(c, text == null ? 40 : 22))));
            if (text != null) {
                label = text(c, 14, TEXT, body(c, true));
                label.setText(text);
                label.setSingleLine(true);
                LayoutParams lp = new LayoutParams(-2, -2);
                lp.leftMargin = Math.round(dp(c, 8));
                addView(label, lp);
            } else {
                label = null;
            }
            paint(false);
        }

        void setText(String t) { if (label != null) label.setText(t); }

        private void paint(boolean focused) {
            int fill = focused ? Color.WHITE : primary ? Color.argb(235, 255, 255, 255) : GLASS;
            bg.setColor(fill);
            bg.setStroke(Math.round(dp(getContext(), 1)), focused || primary ? 0 : GLASS_EDGE);
            int ink = (focused || primary) ? INK : TEXT;
            icon.setColor(ink);
            if (label != null) label.setTextColor(ink);
        }

        @Override
        protected void onFocusChanged(boolean gained, int dir, android.graphics.Rect prev) {
            super.onFocusChanged(gained, dir, prev);
            paint(gained);
            animate().scaleX(gained ? 1.1f : 1f).scaleY(gained ? 1.1f : 1f)
                    .setInterpolator(gained ? SPRING : EASE).setDuration(gained ? 260 : 180).start();
        }

        @Override public boolean hasOverlappingRendering() { return false; }
    }

    /** Shimmering placeholder shown while apps load. */
    static class Skeleton extends View {
        private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final android.graphics.Matrix m = new android.graphics.Matrix();
        private android.graphics.LinearGradient g;
        private float t;
        private ValueAnimator a;
        Skeleton(Context c) {
            super(c);
            base.setColor(Color.argb(28, 255, 255, 255));
        }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            a = ValueAnimator.ofFloat(-1f, 2f);
            a.setDuration(1400);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.addUpdateListener(an -> { t = (float) an.getAnimatedValue(); invalidate(); });
            a.start();
        }
        @Override protected void onDetachedFromWindow() {
            if (a != null) a.cancel();
            super.onDetachedFromWindow();
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            g = new android.graphics.LinearGradient(-w * 0.4f, 0, w * 0.4f, 0,
                    new int[]{0, Color.argb(40, 255, 255, 255), 0}, null, android.graphics.Shader.TileMode.CLAMP);
            shine.setShader(g);
        }
        @Override protected void onDraw(Canvas c) {
            float rad = dp(getContext(), 14);
            r.set(0, 0, getWidth(), getHeight());
            c.drawRoundRect(r, rad, rad, base);
            if (g != null) {
                m.setTranslate(getWidth() * t, 0);
                g.setLocalMatrix(m);
                c.drawRoundRect(r, rad, rad, shine);
            }
        }
    }

    static FrameLayout.LayoutParams frame(int w, int h, int gravity) {
        return new FrameLayout.LayoutParams(w, h, gravity);
    }
}
