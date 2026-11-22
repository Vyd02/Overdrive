package com.vyd02.overdrive;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/**
 * Sora's power-up intro, "chromatic bloom":
 * a spark ignites, bursts into rainbow ribbons that swirl into a spinning color ring while
 * particles spiral in, the letters S-O-R-A snap in with a color-split, a rainbow shockwave
 * fires, and the screen opens through an expanding circle onto the home screen.
 */
public class IntroView extends View {

    private static final String WORD = "SORA";
    private static final long DURATION = 2900;
    private static final float REVEAL = 0.8f; // home screen starts appearing here
    private static final int[] RAINBOW = {0xFFFF3D71, 0xFFFF9F1C, 0xFFFFE14D, 0xFF3DFFB0, 0xFF2BD9FF, 0xFF6C63FF, 0xFFD65CFF};

    private final float d;
    private final Paint bg = new Paint(Paint.DITHER_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ribbon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint letter = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint shimmer = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path iris = new Path();
    private final RectF arc = new RectF();
    private final Matrix mx = new Matrix();

    private SweepGradient sweep;       // the spinning rainbow, built once
    private RadialGradient soft;       // white soft glow, scaled + tinted
    private LinearGradient textBand;   // rainbow band that sweeps across the word
    private int lastW;

    private static final int N = 90;
    private final float[] pa = new float[N], pr = new float[N], ps = new float[N], pv = new float[N];
    private final int[] pc = new int[N];

    private float p;
    private ValueAnimator anim;
    private Runnable onReveal;
    private boolean revealed;

    public IntroView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        ribbon.setStyle(Paint.Style.STROKE);
        ribbon.setStrokeCap(Paint.Cap.ROUND);
        letter.setTypeface(Ui.brand(c, true));
        letter.setTextAlign(Paint.Align.CENTER);
        shimmer.setTypeface(Ui.brand(c, true));
        shimmer.setTextAlign(Paint.Align.CENTER);
        iris.setFillType(Path.FillType.EVEN_ODD);
        Random r = new Random(11);
        for (int i = 0; i < N; i++) {
            pa[i] = r.nextFloat() * 6.2832f;          // start angle
            pr[i] = 0.75f + r.nextFloat() * 0.6f;     // start distance (x screen half-diagonal)
            ps[i] = 1.2f + r.nextFloat() * 2.6f;      // size
            pv[i] = 0.6f + r.nextFloat() * 1.4f;      // spin speed
            pc[i] = RAINBOW[i % RAINBOW.length];
        }
    }

    // ---------------------------------------------------------------- control

    public void play(int accentColor, Runnable reveal) {
        onReveal = reveal;
        revealed = false;
        setVisibility(VISIBLE);
        setAlpha(1f);
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(DURATION);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            p = (float) a.getAnimatedValue();
            if (!revealed && p >= REVEAL) fireReveal();
            invalidate();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                if (!revealed) fireReveal();
                setVisibility(GONE);
            }
        });
        anim.start();
    }

    /** Any button: jump straight to the reveal. */
    public void skip() {
        if (anim != null && anim.isRunning() && p < REVEAL) anim.setCurrentPlayTime((long) (DURATION * REVEAL));
    }

    public boolean isPlaying() { return getVisibility() == VISIBLE; }

    private void fireReveal() {
        revealed = true;
        if (onReveal != null) { Runnable r = onReveal; onReveal = null; r.run(); }
    }

    // ---------------------------------------------------------------- helpers

    private static float seg(float p, float a, float b) { return Math.max(0f, Math.min(1f, (p - a) / (b - a))); }
    private static float out3(float x) { return 1f - (float) Math.pow(1f - x, 3); }
    private static float inOut(float x) { return x < 0.5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2; }
    private static float spring(float x) { // overshoots a little, then settles
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        return 1f - (float) (Math.exp(-6 * x) * Math.cos(10 * x));
    }

    private void build(int w, int h) {
        lastW = w;
        bg.setShader(new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.75f,
                new int[]{0xFF120A24, 0xFF07040F, 0xFF020105}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        int[] loop = new int[RAINBOW.length + 1];
        System.arraycopy(RAINBOW, 0, loop, 0, RAINBOW.length);
        loop[RAINBOW.length] = RAINBOW[0];
        sweep = new SweepGradient(0, 0, loop, null);
        soft = new RadialGradient(0, 0, 1f, new int[]{0xFFFFFFFF, 0x55FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP);
        textBand = new LinearGradient(-1, 0, 1, 0, new int[]{0x00FFFFFF, 0xFFFF3D71, 0xFFFFE14D, 0xFF3DFFB0, 0xFF2BD9FF, 0xFFD65CFF, 0x00FFFFFF},
                null, Shader.TileMode.CLAMP);
    }

    private void softGlow(Canvas c, float x, float y, float r, int color, int alpha) {
        if (r < 1 || alpha <= 0) return;
        mx.setScale(r, r);
        mx.postTranslate(x, y);
        soft.setLocalMatrix(mx);
        glow.setShader(soft);
        glow.setColorFilter(Themes.tintFilter(color));
        glow.setAlpha(Math.min(255, alpha));
        c.drawCircle(x, y, r, glow);
    }

    // ---------------------------------------------------------------- draw

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w != lastW) build(w, h);
        float cx = w / 2f, cy = h / 2f;
        float half = (float) Math.hypot(cx, cy);
        float t = p * DURATION / 1000f; // seconds, for continuous spin

        // ---- background, with the iris opening at the end ----
        float open = inOut(seg(p, REVEAL, 1f));
        float holeR = open * half * 1.05f;
        iris.reset();
        iris.addRect(0, 0, w, h, Path.Direction.CW);
        if (holeR > 0) iris.addCircle(cx, cy, holeR, Path.Direction.CW);
        c.drawPath(iris, bg);
        if (holeR > 0) {
            // colorful rim on the opening
            mx.setRotate(t * 160f);
            mx.postTranslate(cx, cy);
            sweep.setLocalMatrix(mx);
            ring.setShader(sweep);
            ring.setStrokeWidth(6 * d * (1 - open) + 1.5f * d);
            ring.setAlpha((int) (255 * (1 - open)));
            c.drawCircle(cx, cy, holeR, ring);
        }

        float fadeOut = 1f - open;
        if (fadeOut <= 0.01f) return;
        float zoom = 1f + open * 0.45f;
        c.save();
        c.scale(zoom, zoom, cx, cy);

        // ---- 0. color clouds drifting behind everything ----
        float clouds = seg(p, 0.12f, 0.4f) * fadeOut;
        if (clouds > 0) {
            int[] cc = {0xFFFF3D9A, 0xFF2BD9FF, 0xFF7C4DFF};
            for (int i = 0; i < 3; i++) {
                double a = t * 0.9 + i * 2.094;
                float ox = cx + (float) Math.cos(a) * half * 0.16f, oy = cy + (float) Math.sin(a) * half * 0.12f;
                softGlow(c, ox, oy, half * 0.42f, cc[i], (int) (120 * clouds));
            }
        }

        // ---- 1. spark ----
        float spark = out3(seg(p, 0f, 0.12f)) * (1f - seg(p, 0.18f, 0.3f));
        if (spark > 0) {
            softGlow(c, cx, cy, 70 * d * spark + 4 * d, Color.WHITE, (int) (255 * spark));
            softGlow(c, cx, cy, 160 * d * spark, 0xFFD65CFF, (int) (120 * spark));
        }

        // ---- 2. ribbons burst outward and swirl ----
        float burst = seg(p, 0.06f, 0.42f);
        float gather = seg(p, 0.3f, 0.5f);           // ribbons fold into the ring
        float ringR = (96 + 54 * out3(seg(p, 0.48f, 0.66f))) * d;
        if (burst > 0 && gather < 1f) {
            for (int i = 0; i < RAINBOW.length; i++) {
                float k = (float) i / RAINBOW.length;
                float rr = (40 + 260 * out3(burst)) * d * (1f - gather) + ringR * gather + i * 6 * d * (1 - gather);
                float start = k * 360f + burst * 420f + i * 12f;
                float sweepDeg = 50f + 70f * (float) Math.sin(burst * 3.1416f) + gather * 10f;
                arc.set(cx - rr, cy - rr, cx + rr, cy + rr);
                ribbon.setColor(RAINBOW[i]);
                ribbon.setAlpha((int) (235 * (1f - gather) * fadeOut));
                ribbon.setStrokeWidth((10 - 6 * burst) * d);
                c.drawArc(arc, start, sweepDeg, false, ribbon);
            }
        }

        // ---- 3. the spinning rainbow ring ----
        float ringIn = out3(seg(p, 0.32f, 0.52f)) * fadeOut;
        float flash = seg(p, 0.66f, 0.72f) * (1f - seg(p, 0.72f, 0.82f));
        if (ringIn > 0) {
            mx.setRotate(t * 140f);
            mx.postTranslate(cx, cy);
            sweep.setLocalMatrix(mx);
            ring.setShader(sweep);
            ring.setStrokeWidth(22 * d);                       // glow pass
            ring.setAlpha((int) (60 * ringIn));
            c.drawCircle(cx, cy, ringR, ring);
            ring.setStrokeWidth((5 + 3 * flash) * d);          // crisp pass
            ring.setAlpha((int) (255 * ringIn));
            c.drawCircle(cx, cy, ringR, ring);
            if (flash > 0) {
                ring.setShader(null);
                ring.setColor(Color.WHITE);
                ring.setStrokeWidth(4 * d);
                ring.setAlpha((int) (220 * flash));
                c.drawCircle(cx, cy, ringR, ring);
            }
        }

        // ---- 4. particles spiral in, orbit, then blow outward ----
        float in = inOut(seg(p, 0.08f, 0.56f));
        float blast = out3(seg(p, 0.7f, 0.95f));
        for (int i = 0; i < N; i++) {
            float startR = pr[i] * half;
            float orbitR = ringR + (i % 5 - 2) * 7 * d;
            float r = startR + (orbitR - startR) * in + blast * half * (0.6f + pr[i] * 0.6f);
            float a = pa[i] + (1f - in) * 2.4f + t * pv[i] * (0.4f + 0.6f * in);
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            float al = seg(p, 0.06f, 0.2f) * (1f - blast);
            if (al <= 0) continue;
            dot.setColor(pc[i]);
            dot.setAlpha((int) (70 * al));
            c.drawCircle(x, y, ps[i] * 2.6f * d, dot);
            dot.setAlpha((int) (255 * al));
            c.drawCircle(x, y, ps[i] * d, dot);
        }

        // ---- 5. letters snap in with a color split ----
        float size = 62 * d;
        letter.setTextSize(size);
        shimmer.setTextSize(size);
        float spacing = size * 0.86f;
        float startX = cx - spacing * (WORD.length() - 1) / 2f;
        float baseY = cy + size * 0.36f;
        for (int i = 0; i < WORD.length(); i++) {
            float lp = seg(p, 0.46f + i * 0.045f, 0.62f + i * 0.045f);
            if (lp <= 0) continue;
            float s = 0.3f + 0.7f * spring(lp);
            float split = (1f - out3(lp)) * 16 * d;
            String ch = String.valueOf(WORD.charAt(i));
            float x = startX + i * spacing;
            c.save();
            c.scale(s, s, x, baseY - size * 0.36f);
            int al = (int) (255 * Math.min(1f, lp * 2f) * fadeOut);
            if (split > 0.5f) {
                letter.setColor(0xFFFF2BD6);
                letter.setAlpha((int) (al * 0.8f));
                c.drawText(ch, x - split, baseY, letter);
                letter.setColor(0xFF2BF0FF);
                letter.setAlpha((int) (al * 0.8f));
                c.drawText(ch, x + split, baseY, letter);
            }
            letter.setColor(Color.WHITE);
            letter.setAlpha(al);
            c.drawText(ch, x, baseY, letter);
            c.restore();
        }

        // ---- 6. rainbow shimmer sweeps across the word ----
        float sw = seg(p, 0.64f, 0.84f);
        if (sw > 0 && sw < 1) {
            float bandX = startX - spacing + sw * (spacing * (WORD.length() + 1));
            mx.setScale(spacing * 0.9f, 1f);
            mx.postTranslate(bandX, 0);
            textBand.setLocalMatrix(mx);
            shimmer.setShader(textBand);
            shimmer.setAlpha((int) (255 * fadeOut));
            for (int i = 0; i < WORD.length(); i++) c.drawText(String.valueOf(WORD.charAt(i)), startX + i * spacing, baseY, shimmer);
        }

        // ---- 7. shockwave ----
        float wave = out3(seg(p, 0.68f, 0.98f));
        if (wave > 0 && wave < 1) {
            mx.setRotate(-t * 200f);
            mx.postTranslate(cx, cy);
            sweep.setLocalMatrix(mx);
            ring.setShader(sweep);
            ring.setStrokeWidth(10 * d * (1 - wave) + 1 * d);
            ring.setAlpha((int) (230 * (1 - wave)));
            c.drawCircle(cx, cy, ringR + wave * half * 1.2f, ring);
            softGlow(c, cx, cy, half * (0.4f + wave * 0.6f), 0xFF6C63FF, (int) (90 * (1 - wave)));
        }
        c.restore();
    }
}
