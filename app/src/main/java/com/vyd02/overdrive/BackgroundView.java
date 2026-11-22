package com.vyd02.overdrive;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Living console background. Every piece is drawn ONCE into a GPU layer and then only
 * moved / recolored, so nothing is redrawn per frame and the UI thread stays free.
 */
public class BackgroundView extends FrameLayout {

    private final List<Orb> orbs = new ArrayList<>();
    private final List<Animator> loops = new ArrayList<>();
    private int accent = Color.parseColor("#3DA9FF");
    private ValueAnimator colorAnim;
    private boolean built;
    private boolean running = true;

    public BackgroundView(Context c) {
        super(c);
        setClipChildren(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w == 0 || h == 0 || built) return;
        built = true;
        post(() -> build(w, h));
    }

    private void build(int w, int h) {
        addView(new Base(getContext()), new LayoutParams(-1, -1));

        int big = (int) (w * 1.0f), mid = (int) (w * 0.8f), small = (int) (w * 0.6f);
        addOrb(big, 0.62f, 0f, w * 0.80f, h * 0.20f, w * 0.07f, h * 0.10f, 23000, 19000);
        addOrb(mid, 0.50f, 40f, w * 0.15f, h * 0.88f, w * 0.10f, h * 0.08f, 27000, 21000);
        addOrb(small, 0.38f, -55f, w * 0.50f, h * 0.50f, w * 0.22f, h * 0.14f, 41000, 33000);

        addSpecks(w, h, 34, 0.7f, 2.0f, 70000, 0.9f);
        addSpecks(w, h, 40, 0.4f, 1.1f, 140000, 0.5f);

        addView(new Vignette(getContext()), new LayoutParams(-1, -1));
        applyAccent(accent);
        setRunning(running);
    }

    private static final int ORB_PX = 384; // drawn small, scaled up on the GPU (a soft glow loses nothing)

    private void addOrb(int size, float alpha, float hueShift, float cx, float cy,
                        float dx, float dy, long tx, long ty) {
        Orb o = new Orb(getContext(), hueShift);
        o.setAlpha(alpha);
        LayoutParams lp = new LayoutParams(ORB_PX, ORB_PX);
        lp.leftMargin = (int) (cx - ORB_PX / 2f); // position via margins so translation is free to animate
        lp.topMargin = (int) (cy - ORB_PX / 2f);
        addView(o, lp);
        float base = (float) size / ORB_PX;
        o.setScaleX(base);
        o.setScaleY(base);
        loop(ObjectAnimator.ofFloat(o, "translationX", -dx, dx), tx);
        loop(ObjectAnimator.ofFloat(o, "translationY", -dy, dy), ty);
        loop(ObjectAnimator.ofFloat(o, "scaleX", base * 0.9f, base * 1.1f), (tx + ty) / 2);
        loop(ObjectAnimator.ofFloat(o, "scaleY", base * 0.9f, base * 1.1f), (tx + ty) / 2);
        orbs.add(o);
    }

    private void loop(ObjectAnimator a, long dur) {
        a.setDuration(dur);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setInterpolator(new AccelerateDecelerateInterpolator());
        a.setCurrentPlayTime((long) (Math.random() * dur));
        loops.add(a);
    }

    private void addSpecks(int w, int h, int count, float minR, float maxR, long dur, float alpha) {
        Specks s = new Specks(getContext(), count, minR, maxR, h, (int) dur);
        s.setAlpha(alpha);
        addView(s, new LayoutParams(w, h * 2));
        ObjectAnimator drift = ObjectAnimator.ofFloat(s, "translationY", 0, -h);
        drift.setDuration(dur);
        drift.setRepeatCount(ValueAnimator.INFINITE);
        drift.setInterpolator(new LinearInterpolator());
        loops.add(drift);
        ObjectAnimator tw = ObjectAnimator.ofFloat(s, "alpha", alpha * 0.55f, alpha);
        tw.setDuration(2600 + (long) (Math.random() * 1500));
        tw.setRepeatCount(ValueAnimator.INFINITE);
        tw.setRepeatMode(ValueAnimator.REVERSE);
        loops.add(tw);
    }

    /** Smoothly blend the glow to a new color. Only recolors the GPU layers; nothing is redrawn. */
    public void setAccent(int color) {
        if (colorAnim != null) colorAnim.cancel();
        final int from = accent;
        final ArgbEvaluator ev = new ArgbEvaluator();
        colorAnim = ValueAnimator.ofFloat(0f, 1f);
        colorAnim.setDuration(650);
        colorAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        colorAnim.addUpdateListener(a -> applyAccent((int) ev.evaluate(a.getAnimatedFraction(), from, color)));
        colorAnim.start();
    }

    private void applyAccent(int c) {
        accent = c;
        for (Orb o : orbs) o.tint(shiftHue(c, o.hueShift));
    }

    public void setRunning(boolean run) {
        running = run;
        for (Animator a : loops) {
            if (run) {
                if (!a.isStarted()) a.start();
                else if (a.isPaused()) a.resume();
            } else if (a.isRunning()) {
                a.pause();
            }
        }
    }

    // ---------------- pieces ----------------

    private static class Base extends View {
        private final Paint p = new Paint(Paint.DITHER_FLAG);
        Base(Context c) { super(c); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            p.setShader(new LinearGradient(0, 0, w * 0.3f, h,
                    new int[]{Color.parseColor("#05070F"), Color.parseColor("#0A0F1F"), Color.parseColor("#04050B")},
                    null, Shader.TileMode.CLAMP));
        }
        @Override protected void onDraw(Canvas c) { c.drawPaint(p); }
    }

    private static class Vignette extends View {
        private final Paint p = new Paint(Paint.DITHER_FLAG);
        Vignette(Context c) { super(c); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            p.setShader(new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.75f,
                    new int[]{0, 0, Color.argb(200, 0, 0, 0)}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        }
        @Override protected void onDraw(Canvas c) { c.drawPaint(p); }
    }

    /** White soft glow drawn once into a layer; the layer paint tints it. */
    private static class Orb extends View {
        final float hueShift;
        private final Paint draw = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint layer = new Paint();
        Orb(Context c, float hueShift) {
            super(c);
            this.hueShift = hueShift;
            setLayerType(LAYER_TYPE_HARDWARE, layer);
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            draw.setShader(new RadialGradient(w / 2f, h / 2f, w / 2f,
                    new int[]{Color.argb(255, 255, 255, 255), Color.argb(110, 255, 255, 255),
                            Color.argb(30, 255, 255, 255), 0},
                    new float[]{0f, 0.3f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        }
        @Override protected void onDraw(Canvas c) { c.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f, draw); }
        void tint(int color) {
            layer.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            setLayerPaint(layer);
        }
    }

    /** A field of light specks drawn twice (stacked) so it can scroll forever without a seam. */
    private static class Specks extends View {
        private final float[] x, y, r;
        private final int tileH;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Specks(Context c, int count, float minR, float maxR, int tileH, int seed) {
            super(c);
            this.tileH = tileH;
            float d = c.getResources().getDisplayMetrics().density;
            Random rnd = new Random(seed);
            x = new float[count]; y = new float[count]; r = new float[count];
            for (int i = 0; i < count; i++) {
                x[i] = rnd.nextFloat();
                y[i] = rnd.nextFloat() * tileH;
                r[i] = (minR + rnd.nextFloat() * (maxR - minR)) * d;
            }
            p.setColor(Color.WHITE);
            // no layer: the dots are recorded once and the GPU just replays them while sliding
        }
        @Override protected void onDraw(Canvas c) {
            int w = getWidth();
            for (int pass = 0; pass < 2; pass++) {
                float off = pass * tileH;
                for (int i = 0; i < x.length; i++) {
                    p.setAlpha(60 + (i * 37) % 140);
                    c.drawCircle(x[i] * w, y[i] + off, r[i], p);
                }
            }
        }
    }

    static int withAlpha(int c, int a) {
        return Color.argb(a, Color.red(c), Color.green(c), Color.blue(c));
    }

    static int shiftHue(int c, float deg) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[0] = (hsv[0] + deg + 360f) % 360f;
        return Color.HSVToColor(hsv);
    }
}
