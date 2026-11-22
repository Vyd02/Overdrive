package com.vyd02.overdrive;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Living console-style background: deep gradient, slow drifting glow orbs that
 * take on the color of whatever app is focused, and floating light specks.
 */
public class BackgroundView extends View {

    private final Paint basePaint = new Paint();
    private final Paint orbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speckPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vignettePaint = new Paint();
    private final Matrix m = new Matrix();

    private RadialGradient orbA, orbB, orbC;
    private int accent = Color.parseColor("#3DA9FF");
    private int accentFrom = accent;
    private ValueAnimator colorAnim;

    private static final int SPECKS = 46;
    private final float[] sx = new float[SPECKS], sy = new float[SPECKS], ss = new float[SPECKS],
            sp = new float[SPECKS], sv = new float[SPECKS];
    private final long start = SystemClock.uptimeMillis();
    private boolean running = true;

    public BackgroundView(Context c) {
        super(c);
        Random r = new Random(7);
        for (int i = 0; i < SPECKS; i++) {
            sx[i] = r.nextFloat();
            sy[i] = r.nextFloat();
            ss[i] = 0.6f + r.nextFloat() * 1.8f;
            sp[i] = r.nextFloat() * 6.28f;
            sv[i] = 0.004f + r.nextFloat() * 0.012f;
        }
        speckPaint.setColor(Color.WHITE);
        buildOrbs();
    }

    private void buildOrbs() {
        int a = accent;
        int b = shiftHue(accent, 40);
        int c = shiftHue(accent, -55);
        orbA = new RadialGradient(0, 0, 1f, new int[]{withAlpha(a, 150), withAlpha(a, 40), 0},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        orbB = new RadialGradient(0, 0, 1f, new int[]{withAlpha(b, 110), withAlpha(b, 25), 0},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        orbC = new RadialGradient(0, 0, 1f, new int[]{withAlpha(c, 90), 0},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
    }

    /** Smoothly blend the glow to a new color. */
    public void setAccent(int color) {
        if (colorAnim != null) colorAnim.cancel();
        accentFrom = accent;
        final int to = color;
        colorAnim = ValueAnimator.ofFloat(0f, 1f);
        colorAnim.setDuration(700);
        final ArgbEvaluator ev = new ArgbEvaluator();
        colorAnim.addUpdateListener(an -> {
            accent = (int) ev.evaluate(an.getAnimatedFraction(), accentFrom, to);
            buildOrbs();
        });
        colorAnim.start();
    }

    public void setRunning(boolean run) {
        running = run;
        if (run) invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        basePaint.setShader(new LinearGradient(0, 0, w * 0.3f, h,
                new int[]{Color.parseColor("#05070F"), Color.parseColor("#0A0F1F"), Color.parseColor("#04050B")},
                null, Shader.TileMode.CLAMP));
        vignettePaint.setShader(new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.75f,
                new int[]{0, 0, Color.argb(200, 0, 0, 0)}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
    }

    private void drawOrb(Canvas c, RadialGradient g, float cx, float cy, float r) {
        m.reset();
        m.setScale(r, r);
        m.postTranslate(cx, cy);
        g.setLocalMatrix(m);
        orbPaint.setShader(g);
        c.drawCircle(cx, cy, r, orbPaint);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float t = (SystemClock.uptimeMillis() - start) / 1000f;
        c.drawRect(0, 0, w, h, basePaint);

        drawOrb(c, orbA, w * (0.78f + 0.08f * (float) Math.sin(t * 0.13)),
                h * (0.22f + 0.10f * (float) Math.cos(t * 0.11)), w * 0.55f);
        drawOrb(c, orbB, w * (0.18f + 0.10f * (float) Math.cos(t * 0.09)),
                h * (0.85f + 0.08f * (float) Math.sin(t * 0.12)), w * 0.45f);
        drawOrb(c, orbC, w * (0.50f + 0.25f * (float) Math.sin(t * 0.05)),
                h * (0.50f + 0.15f * (float) Math.sin(t * 0.07)), w * 0.35f);

        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < SPECKS; i++) {
            float y = (sy[i] - t * sv[i]) % 1f;
            if (y < 0) y += 1f;
            float x = sx[i] + 0.01f * (float) Math.sin(t * 0.5 + sp[i]);
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 1.3 + sp[i]);
            speckPaint.setAlpha((int) (25 + 90 * tw));
            c.drawCircle(x * w, y * h, ss[i] * d, speckPaint);
        }
        c.drawRect(0, 0, w, h, vignettePaint);

        if (running) postInvalidateDelayed(33); // ~30 fps keeps the TV cool
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
