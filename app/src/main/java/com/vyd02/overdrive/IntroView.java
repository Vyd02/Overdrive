package com.vyd02.overdrive;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/**
 * Sora's power-up intro, "chromatic bloom". Light is added, not painted (additive blending),
 * so everything glows like real light on a dark screen:
 * star dust and color clouds, a spark with a lens flare, rainbow ribbons that fold into a
 * spinning ring with fine tech hairlines, comets spiraling in, S-O-R-A snapping in with a
 * color split, a rainbow shockwave with a flare, then an iris opening onto the home screen.
 */
public class IntroView extends View {

    private static final String WORD = "SORA";
    private static final long DURATION = 3200;
    private static final float REVEAL = 0.8f;
    private static final int[] RAINBOW = {0xFFFF3D71, 0xFFFF9F1C, 0xFFFFE14D, 0xFF3DFFB0, 0xFF2BD9FF, 0xFF6C63FF, 0xFFD65CFF};

    private final float d;
    private final PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
    private final Paint bg = new Paint(Paint.DITHER_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hair = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ribbon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint letter = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint letterAdd = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint shimmer = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path iris = new Path();
    private final RectF arc = new RectF();
    private final Matrix mx = new Matrix();
    private DashPathEffect dashFine, dashLong;

    private SweepGradient sweep;
    private RadialGradient soft;
    private LinearGradient textBand;
    private int lastW;

    private static final int N = 140;                  // comets
    private final float[] pa = new float[N], pr = new float[N], ps = new float[N], pv = new float[N];
    private final int[] pc = new int[N];
    private static final int S = 220;                  // star dust
    private final float[] sx = new float[S], sy = new float[S], sr = new float[S], sph = new float[S], sdep = new float[S];

    private float p;
    private ValueAnimator anim;
    private Runnable onReveal;
    private boolean revealed;

    public IntroView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setXfermode(add);
        hair.setStyle(Paint.Style.STROKE);
        ribbon.setStyle(Paint.Style.STROKE);
        ribbon.setStrokeCap(Paint.Cap.ROUND);
        ribbon.setXfermode(add);
        dot.setXfermode(add);
        glow.setXfermode(add);
        letter.setTypeface(Ui.brand(c, true));
        letter.setTextAlign(Paint.Align.CENTER);
        letterAdd.setTypeface(Ui.brand(c, true));
        letterAdd.setTextAlign(Paint.Align.CENTER);
        letterAdd.setXfermode(add);
        shimmer.setTypeface(Ui.brand(c, true));
        shimmer.setTextAlign(Paint.Align.CENTER);
        iris.setFillType(Path.FillType.EVEN_ODD);
        Random r = new Random(11);
        for (int i = 0; i < N; i++) {
            pa[i] = r.nextFloat() * 6.2832f;
            pr[i] = 0.75f + r.nextFloat() * 0.6f;
            ps[i] = 1.0f + r.nextFloat() * 2.4f;
            pv[i] = 0.6f + r.nextFloat() * 1.4f;
            pc[i] = RAINBOW[i % RAINBOW.length];
        }
        Random r2 = new Random(5);
        for (int i = 0; i < S; i++) {
            sx[i] = r2.nextFloat(); sy[i] = r2.nextFloat();
            sr[i] = 0.3f + r2.nextFloat() * 0.9f; sph[i] = r2.nextFloat() * 6.28f; sdep[i] = 0.2f + r2.nextFloat();
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
    private static float spring(float x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        return 1f - (float) (Math.exp(-6 * x) * Math.cos(10 * x));
    }

    private void build(int w, int h) {
        lastW = w;
        bg.setShader(new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.75f,
                new int[]{0xFF140B2A, 0xFF07040F, 0xFF010103}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        int[] loop = new int[RAINBOW.length + 1];
        System.arraycopy(RAINBOW, 0, loop, 0, RAINBOW.length);
        loop[RAINBOW.length] = RAINBOW[0];
        sweep = new SweepGradient(0, 0, loop, null);
        soft = new RadialGradient(0, 0, 1f, new int[]{0xFFFFFFFF, 0x55FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP);
        textBand = new LinearGradient(-1, 0, 1, 0, new int[]{0x00FFFFFF, 0xFFFF3D71, 0xFFFFE14D, 0xFF3DFFB0, 0xFF2BD9FF, 0xFFD65CFF, 0x00FFFFFF},
                null, Shader.TileMode.CLAMP);
        dashFine = new DashPathEffect(new float[]{2 * d, 6 * d}, 0);
        dashLong = new DashPathEffect(new float[]{14 * d, 10 * d}, 0);
    }

    private void softGlow(Canvas c, float x, float y, float r, int color, float alpha) {
        if (r < 1 || alpha <= 0) return;
        mx.setScale(r, r);
        mx.postTranslate(x, y);
        soft.setLocalMatrix(mx);
        glow.setShader(soft);
        glow.setColorFilter(Themes.tintFilter(color));
        glow.setAlpha((int) Math.min(255, alpha));
        c.drawCircle(x, y, r, glow);
    }

    /** Anamorphic lens flare: a long, thin horizontal glow. */
    private void streak(Canvas c, float x, float y, float length, float thick, int color, float alpha) {
        if (alpha <= 0) return;
        c.save();
        c.translate(x, y);
        c.scale(length / thick, 1f);
        softGlow(c, 0, 0, thick, color, alpha);
        c.restore();
    }

    private void spinSweep(float cx, float cy, float deg) {
        mx.setRotate(deg);
        mx.postTranslate(cx, cy);
        sweep.setLocalMatrix(mx);
    }

    // ---------------------------------------------------------------- draw

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w != lastW) build(w, h);
        float cx = w / 2f, cy = h / 2f;
        float half = (float) Math.hypot(cx, cy);
        float t = p * DURATION / 1000f;

        // ---- background with the iris opening ----
        float open = inOut(seg(p, REVEAL, 1f));
        float holeR = open * half * 1.05f;
        iris.reset();
        iris.addRect(0, 0, w, h, Path.Direction.CW);
        if (holeR > 0) iris.addCircle(cx, cy, holeR, Path.Direction.CW);
        c.drawPath(iris, bg);
        float fade = 1f - open;
        if (holeR > 0) {
            spinSweep(cx, cy, t * 160f);
            ring.setShader(sweep);
            ring.setStrokeWidth(8 * d * fade + 1.5f * d);
            ring.setAlpha((int) (255 * fade));
            c.drawCircle(cx, cy, holeR, ring);
        }
        if (fade <= 0.01f) return;

        float zoom = 1f + open * 0.45f;
        c.save();
        c.scale(zoom, zoom, cx, cy);

        // ---- 0. star dust ----
        float dust = seg(p, 0.02f, 0.25f) * fade;
        if (dust > 0) {
            for (int i = 0; i < S; i++) {
                float tw = 0.5f + 0.5f * (float) Math.sin(t * 2.2f + sph[i]);
                float x = (sx[i] * w + t * 18 * d * sdep[i]) % w;
                dot.setColor(Themes.withA(Color.WHITE, (int) (160 * dust * tw * sdep[i])));
                c.drawCircle(x, sy[i] * h, sr[i] * d * 0.8f, dot);
            }
        }

        // ---- 1. color clouds ----
        float clouds = seg(p, 0.1f, 0.38f) * fade;
        if (clouds > 0) {
            int[] cc = {0xFFFF3D9A, 0xFF2BD9FF, 0xFF7C4DFF};
            for (int i = 0; i < 3; i++) {
                double a = t * 0.9 + i * 2.094;
                softGlow(c, cx + (float) Math.cos(a) * half * 0.18f, cy + (float) Math.sin(a) * half * 0.13f, half * 0.45f, cc[i], 95 * clouds);
            }
        }

        // ---- 2. spark + lens flare ----
        float spark = out3(seg(p, 0f, 0.1f)) * (1f - seg(p, 0.16f, 0.3f));
        if (spark > 0) {
            softGlow(c, cx, cy, 60 * d * spark + 3 * d, Color.WHITE, 255 * spark);
            softGlow(c, cx, cy, 170 * d * spark, 0xFFB06CFF, 150 * spark);
            streak(c, cx, cy, 420 * d * spark, 7 * d, 0xFF8FD8FF, 220 * spark);
        }

        // ---- 3. ribbons ----
        float burst = seg(p, 0.05f, 0.42f);
        float gather = seg(p, 0.3f, 0.5f);
        float ringR = (96 + 54 * out3(seg(p, 0.48f, 0.66f))) * d;
        if (burst > 0 && gather < 1f) {
            for (int i = 0; i < RAINBOW.length; i++) {
                float k = (float) i / RAINBOW.length;
                float rr = (40 + 280 * out3(burst)) * d * (1f - gather) + ringR * gather + i * 7 * d * (1 - gather);
                float start = k * 360f + burst * 420f + i * 12f;
                float sw = 50f + 70f * (float) Math.sin(burst * 3.1416f) + gather * 10f;
                arc.set(cx - rr, cy - rr, cx + rr, cy + rr);
                ribbon.setColor(Themes.withA(RAINBOW[i], (int) (70 * (1 - gather) * fade)));
                ribbon.setStrokeWidth((26 - 14 * burst) * d);
                c.drawArc(arc, start, sw, false, ribbon);
                ribbon.setColor(Themes.withA(RAINBOW[i], (int) (245 * (1 - gather) * fade)));
                ribbon.setStrokeWidth((8 - 5 * burst) * d);
                c.drawArc(arc, start, sw, false, ribbon);
            }
        }

        // ---- 4. the ring, with fine counter-rotating hairlines ----
        float ringIn = out3(seg(p, 0.32f, 0.52f)) * fade;
        float flash = seg(p, 0.66f, 0.72f) * (1f - seg(p, 0.72f, 0.82f));
        if (ringIn > 0) {
            spinSweep(cx, cy, t * 140f);
            ring.setShader(sweep);
            ring.setStrokeWidth(26 * d); ring.setAlpha((int) (56 * ringIn)); c.drawCircle(cx, cy, ringR, ring);
            ring.setStrokeWidth(10 * d); ring.setAlpha((int) (115 * ringIn)); c.drawCircle(cx, cy, ringR, ring);
            ring.setStrokeWidth((3.2f + 3 * flash) * d); ring.setAlpha((int) (255 * ringIn)); c.drawCircle(cx, cy, ringR, ring);
            hair.setStrokeWidth(1.1f * d);
            hair.setColor(Themes.withA(Color.WHITE, (int) (90 * ringIn)));
            c.save(); c.rotate(-t * 40f, cx, cy); hair.setPathEffect(dashFine); c.drawCircle(cx, cy, ringR - 16 * d, hair); c.restore();
            c.save(); c.rotate(t * 25f, cx, cy); hair.setPathEffect(dashLong); c.drawCircle(cx, cy, ringR + 18 * d, hair); c.restore();
            if (flash > 0) {
                ring.setShader(null);
                ring.setColor(Color.WHITE);
                ring.setStrokeWidth(3 * d);
                ring.setAlpha((int) (230 * flash));
                c.drawCircle(cx, cy, ringR, ring);
            }
        }

        // ---- 5. comets spiral in, orbit, then blow outward ----
        float in = inOut(seg(p, 0.08f, 0.56f));
        float blast = out3(seg(p, 0.7f, 0.95f));
        float al = seg(p, 0.06f, 0.2f) * (1f - blast) * fade;
        if (al > 0) {
            for (int i = 0; i < N; i++) {
                float startR = pr[i] * half;
                float orbitR = ringR + (i % 5 - 2) * 7 * d;
                float r = startR + (orbitR - startR) * in + blast * half * (0.6f + pr[i] * 0.6f);
                float spin = pv[i] * (0.4f + 0.6f * in);
                float base = pa[i] + (1f - in) * 2.4f;
                for (int k = 3; k >= 1; k--) {               // fading echoes behind the head
                    float a = base + (t - 0.022f * k) * spin;
                    dot.setColor(Themes.withA(pc[i], (int) (70 * al / k)));
                    c.drawCircle(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, ps[i] * d * (1 - k * 0.18f), dot);
                }
                float a = base + t * spin;
                float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
                dot.setColor(Themes.withA(pc[i], (int) (55 * al)));
                c.drawCircle(x, y, ps[i] * 3 * d, dot);
                dot.setColor(Themes.withA(pc[i], (int) (255 * al)));
                c.drawCircle(x, y, ps[i] * d, dot);
            }
        }

        // ---- 6. the word ----
        float size = 64 * d;
        letter.setTextSize(size);
        letterAdd.setTextSize(size);
        shimmer.setTextSize(size);
        float spacing = size * 0.86f;
        float startX = cx - spacing * (WORD.length() - 1) / 2f;
        float baseY = cy + size * 0.36f;
        float settled = seg(p, 0.66f, 0.74f) * fade;
        if (settled > 0) softGlow(c, cx, cy, 170 * d, 0xFF9A7CFF, 70 * settled);
        for (int i = 0; i < WORD.length(); i++) {
            float lp = seg(p, 0.46f + i * 0.045f, 0.62f + i * 0.045f);
            if (lp <= 0) continue;
            float s = 0.3f + 0.7f * spring(lp);
            float split = (1f - out3(lp)) * 18 * d;
            String ch = String.valueOf(WORD.charAt(i));
            float x = startX + i * spacing;
            c.save();
            c.scale(s, s, x, baseY - size * 0.36f);
            int la = (int) (255 * Math.min(1f, lp * 2f) * fade);
            if (split > 0.5f) {
                letterAdd.setColor(Themes.withA(0xFFFF2BD6, (int) (la * 0.85f)));
                c.drawText(ch, x - split, baseY, letterAdd);
                letterAdd.setColor(Themes.withA(0xFF2BF0FF, (int) (la * 0.85f)));
                c.drawText(ch, x + split, baseY, letterAdd);
            }
            letter.setColor(Themes.withA(Color.WHITE, la));
            c.drawText(ch, x, baseY, letter);
            c.restore();
        }

        // ---- 7. rainbow shimmer across the word ----
        float shine = seg(p, 0.64f, 0.84f);
        if (shine > 0 && shine < 1) {
            float bandX = startX - spacing + shine * (spacing * (WORD.length() + 1));
            mx.setScale(spacing * 0.9f, 1f);
            mx.postTranslate(bandX, 0);
            textBand.setLocalMatrix(mx);
            shimmer.setShader(textBand);
            shimmer.setAlpha((int) (255 * fade));
            for (int i = 0; i < WORD.length(); i++) c.drawText(String.valueOf(WORD.charAt(i)), startX + i * spacing, baseY, shimmer);
        }

        // ---- 8. shockwave + flare ----
        float wave = out3(seg(p, 0.68f, 0.98f));
        if (wave > 0 && wave < 1) {
            spinSweep(cx, cy, -t * 200f);
            ring.setShader(sweep);
            ring.setStrokeWidth(12 * d * (1 - wave) + 1 * d);
            ring.setAlpha((int) (230 * (1 - wave)));
            c.drawCircle(cx, cy, ringR + wave * half * 1.2f, ring);
            softGlow(c, cx, cy, half * (0.4f + wave * 0.6f), 0xFF6C63FF, 90 * (1 - wave));
            streak(c, cx, cy, half * 1.4f * (0.4f + wave), 5 * d, 0xFFA8E6FF, 200 * (1 - wave));
        }
        c.restore();
    }
}
