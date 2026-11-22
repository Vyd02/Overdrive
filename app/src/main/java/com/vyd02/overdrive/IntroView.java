package com.vyd02.overdrive;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.view.Choreographer;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.util.Random;

/**
 * Sora's power-up intro, "chromatic bloom".
 *
 * Drawn on its OWN thread, locked to the TV's refresh (like a game), on a transparent surface
 * that sits above the home screen. Nothing the app does on the main thread (loading the app list,
 * building rows) can make it stutter. Particles and stars are batched into a few draw calls.
 */
public class IntroView extends SurfaceView implements SurfaceHolder.Callback, Choreographer.FrameCallback {

    private static final String WORD = "SORA";
    private static final long DURATION_NS = 3_200_000_000L;
    private static final float REVEAL = 0.8f;
    private static final int[] RAINBOW = {0xFFFF3D71, 0xFFFF9F1C, 0xFFFFE14D, 0xFF3DFFB0, 0xFF2BD9FF, 0xFF6C63FF, 0xFFD65CFF};

    private float d;                 // dp in SURFACE pixels (the surface may be smaller than the screen)
    private final float density;
    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread thread;
    private Choreographer choreo;
    private volatile boolean alive, playing;
    private volatile long skipToNs = -1;
    private long startNs;
    private Runnable onReveal;
    private boolean revealed;
    private int w, h;

    // paints (render thread only)
    private final PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
    private final Paint bg = new Paint(Paint.DITHER_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hair = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ribbon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pts = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    private final Typeface brand;

    // comets, grouped by color x size so each group is ONE draw call
    private static final int N = 105, SIZES = 3;
    private final float[] pa = new float[N], pr = new float[N], pv = new float[N];
    private final int[] pcol = new int[N], psz = new int[N];
    private final float[][] bucket = new float[RAINBOW.length * SIZES][];
    private final int[] bucketN = new int[RAINBOW.length * SIZES];
    private static final float[] SIZE_DP = {1.4f, 2.2f, 3.2f};

    // star dust, 4 twinkle groups = 4 draw calls
    private static final int S = 200, SG = 4;
    private final float[] sx = new float[S], sy = new float[S], sdep = new float[S];
    private final float[][] starPts = new float[SG][];
    private final int[] starN = new int[SG];

    public IntroView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        d = density;
        brand = Ui.brand(c, true);
        setZOrderOnTop(true);                                   // above the home screen...
        getHolder().setFormat(PixelFormat.TRANSLUCENT);         // ...and see-through where we don't draw
        getHolder().addCallback(this);
        setVisibility(GONE);

        ring.setStyle(Paint.Style.STROKE);
        ring.setXfermode(add);
        hair.setStyle(Paint.Style.STROKE);
        ribbon.setStyle(Paint.Style.STROKE);
        ribbon.setStrokeCap(Paint.Cap.ROUND);
        ribbon.setXfermode(add);
        pts.setStrokeCap(Paint.Cap.ROUND);
        pts.setXfermode(add);
        glow.setXfermode(add);
        for (Paint p : new Paint[]{letter, letterAdd, shimmer}) { p.setTypeface(brand); p.setTextAlign(Paint.Align.CENTER); }
        letterAdd.setXfermode(add);
        iris.setFillType(Path.FillType.EVEN_ODD);

        Random r = new Random(11);
        for (int i = 0; i < N; i++) {
            pa[i] = r.nextFloat() * 6.2832f;
            pr[i] = 0.75f + r.nextFloat() * 0.6f;
            pv[i] = 0.6f + r.nextFloat() * 1.4f;
            pcol[i] = i % RAINBOW.length;
            psz[i] = r.nextInt(SIZES);
        }
        for (int b = 0; b < bucket.length; b++) bucket[b] = new float[N * 2];
        Random r2 = new Random(5);
        for (int i = 0; i < S; i++) { sx[i] = r2.nextFloat(); sy[i] = r2.nextFloat(); sdep[i] = 0.2f + r2.nextFloat(); }
        for (int g = 0; g < SG; g++) starPts[g] = new float[S * 2];
    }

    // ---------------------------------------------------------------- control (main thread)

    @Override
    protected void onSizeChanged(int vw, int vh, int ow, int oh) {
        super.onSizeChanged(vw, vh, ow, oh);
        // Never draw more than 1080p: on a 4K screen the TV's scaler stretches it for free.
        if (vw > 1920) {
            float k = 1920f / vw;
            getHolder().setFixedSize(1920, Math.round(vh * k));
        }
    }

    public void play(int accentColor, Runnable reveal) {
        onReveal = reveal;
        revealed = false;
        skipToNs = -1;
        playing = true;
        setVisibility(VISIBLE); // creating the surface starts the render thread
    }

    public void skip() { skipToNs = (long) (DURATION_NS * REVEAL); }

    public boolean isPlaying() { return playing; }

    private void finishOnMain() {
        main.post(() -> {
            if (!revealed) fireReveal();
            playing = false;
            setVisibility(GONE);
        });
    }

    private void fireReveal() {
        revealed = true;
        if (onReveal != null) { Runnable r = onReveal; onReveal = null; r.run(); }
    }

    // ---------------------------------------------------------------- surface + thread

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        alive = true;
        revealedPosted = false;
        thread = new HandlerThread("sora-intro", Process.THREAD_PRIORITY_DEFAULT);
        thread.start();
        new Handler(thread.getLooper()).post(() -> {
            choreo = Choreographer.getInstance();
            startNs = 0;
            choreo.postFrameCallback(this);
        });
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        int vw = getWidth() > 0 ? getWidth() : width;
        d = density * width / (float) vw;   // keep every size the same on screen at any surface size
        w = width;
        h = height;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        alive = false;
        if (thread != null) {
            thread.quitSafely();
            try { thread.join(400); } catch (InterruptedException ignored) { }
            thread = null;
        }
    }

    @Override
    public void doFrame(long frameNs) {
        if (!alive) return;
        if (startNs == 0) startNs = frameNs;
        long skip = skipToNs;
        if (skip >= 0) {
            if (frameNs - startNs < skip) startNs = frameNs - skip; // only ever jump forward
            skipToNs = -1;
        }
        long el = frameNs - startNs;
        float p = Math.min(1f, el / (float) DURATION_NS);
        if (p >= REVEAL && !revealedPosted) { revealedPosted = true; main.post(() -> { if (!revealed) fireReveal(); }); }
        if (p >= 1f) {
            clearSurface();
            revealedPosted = false;
            finishOnMain();
            return;
        }
        choreo.postFrameCallback(this);
        SurfaceHolder holder = getHolder();
        Canvas c = null;
        try {
            if (!holder.getSurface().isValid() || w == 0) return;
            c = Build.VERSION.SDK_INT >= 26 ? holder.lockHardwareCanvas() : holder.lockCanvas();
            if (c == null) return;
            c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            draw(c, p, el / 1e9f);
        } catch (Exception ignored) {
        } finally {
            if (c != null) try { holder.unlockCanvasAndPost(c); } catch (Exception ignored) { }
        }
    }
    private boolean revealedPosted;

    private void clearSurface() {
        SurfaceHolder holder = getHolder();
        try {
            Canvas c = Build.VERSION.SDK_INT >= 26 ? holder.lockHardwareCanvas() : holder.lockCanvas();
            if (c != null) { c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); holder.unlockCanvasAndPost(c); }
        } catch (Exception ignored) { }
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

    private int builtW;

    private void build() {
        builtW = w;
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

    // ---------------------------------------------------------------- draw (render thread)

    private void draw(Canvas c, float p, float t) {
        if (w != builtW) build();
        float cx = w / 2f, cy = h / 2f;
        float half = (float) Math.hypot(cx, cy);

        // background with the iris opening (transparent hole shows the home screen)
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

        // 0. star dust: 4 batched calls
        float dust = seg(p, 0.02f, 0.25f) * fade;
        if (dust > 0) {
            for (int g = 0; g < SG; g++) starN[g] = 0;
            for (int i = 0; i < S; i++) {
                int g = i % SG;
                float x = (sx[i] * w + t * 18 * d * sdep[i]) % w;
                starPts[g][starN[g]++] = x;
                starPts[g][starN[g]++] = sy[i] * h;
            }
            pts.setStrokeWidth(1.6f * d);
            for (int g = 0; g < SG; g++) {
                float tw = 0.5f + 0.5f * (float) Math.sin(t * 2.2f + g * 1.57f);
                pts.setColor(Themes.withA(Color.WHITE, (int) (130 * dust * tw)));
                c.drawPoints(starPts[g], 0, starN[g], pts);
            }
        }

        // 1. color clouds
        float clouds = seg(p, 0.1f, 0.38f) * fade;
        if (clouds > 0) {
            int[] cc = {0xFFFF3D9A, 0xFF2BD9FF, 0xFF7C4DFF};
            for (int i = 0; i < 3; i++) {
                double a = t * 0.9 + i * 2.094;
                softGlow(c, cx + (float) Math.cos(a) * half * 0.18f, cy + (float) Math.sin(a) * half * 0.13f, half * 0.4f, cc[i], 95 * clouds);
            }
        }

        // 2. spark + lens flare
        float spark = out3(seg(p, 0f, 0.1f)) * (1f - seg(p, 0.16f, 0.3f));
        if (spark > 0) {
            softGlow(c, cx, cy, 60 * d * spark + 3 * d, Color.WHITE, 255 * spark);
            softGlow(c, cx, cy, 170 * d * spark, 0xFFB06CFF, 150 * spark);
            streak(c, cx, cy, 420 * d * spark, 7 * d, 0xFF8FD8FF, 220 * spark);
        }

        // 3. ribbons
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

        // 4. ring with counter-rotating hairlines
        float ringIn = out3(seg(p, 0.32f, 0.52f)) * fade;
        float flash = seg(p, 0.66f, 0.72f) * (1f - seg(p, 0.72f, 0.82f));
        if (ringIn > 0) {
            spinSweep(cx, cy, t * 140f);
            ring.setShader(sweep);
            ring.setStrokeWidth(24 * d); ring.setAlpha((int) (56 * ringIn)); c.drawCircle(cx, cy, ringR, ring);
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

        // 5. comets: batched by color and size (21 groups), head + halo + one echo
        float in = inOut(seg(p, 0.08f, 0.56f));
        float blast = out3(seg(p, 0.7f, 0.95f));
        float al = seg(p, 0.06f, 0.2f) * (1f - blast) * fade;
        if (al > 0) {
            for (int pass = 0; pass < 3; pass++) {         // 0 = echo, 1 = halo, 2 = head
                float tt = pass == 0 ? t - 0.05f : t;
                for (int b = 0; b < bucketN.length; b++) bucketN[b] = 0;
                for (int i = 0; i < N; i++) {
                    float startR = pr[i] * half;
                    float orbitR = ringR + (i % 5 - 2) * 7 * d;
                    float r = startR + (orbitR - startR) * in + blast * half * (0.6f + pr[i] * 0.6f);
                    float a = pa[i] + (1f - in) * 2.4f + tt * pv[i] * (0.4f + 0.6f * in);
                    int b = pcol[i] * SIZES + psz[i];
                    bucket[b][bucketN[b]++] = cx + (float) Math.cos(a) * r;
                    bucket[b][bucketN[b]++] = cy + (float) Math.sin(a) * r;
                }
                for (int b = 0; b < bucketN.length; b++) {
                    if (bucketN[b] == 0) continue;
                    float size = SIZE_DP[b % SIZES] * d;
                    int col = RAINBOW[b / SIZES];
                    if (pass == 0) { pts.setStrokeWidth(size * 1.4f); pts.setColor(Themes.withA(col, (int) (60 * al))); }
                    else if (pass == 1) { pts.setStrokeWidth(size * 5f); pts.setColor(Themes.withA(col, (int) (45 * al))); }
                    else { pts.setStrokeWidth(size * 2f); pts.setColor(Themes.withA(col, (int) (255 * al))); }
                    c.drawPoints(bucket[b], 0, bucketN[b], pts);
                }
            }
        }

        // 6. the word
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
            float x = startX + i * spacing;
            c.save();
            c.scale(s, s, x, baseY - size * 0.36f);
            int la = (int) (255 * Math.min(1f, lp * 2f) * fade);
            if (split > 0.5f) {
                letterAdd.setColor(Themes.withA(0xFFFF2BD6, (int) (la * 0.85f)));
                c.drawText(WORD, i, i + 1, x - split, baseY, letterAdd);
                letterAdd.setColor(Themes.withA(0xFF2BF0FF, (int) (la * 0.85f)));
                c.drawText(WORD, i, i + 1, x + split, baseY, letterAdd);
            }
            letter.setColor(Themes.withA(Color.WHITE, la));
            c.drawText(WORD, i, i + 1, x, baseY, letter);
            c.restore();
        }

        // 7. shimmer
        float shine = seg(p, 0.64f, 0.84f);
        if (shine > 0 && shine < 1) {
            float bandX = startX - spacing + shine * (spacing * (WORD.length() + 1));
            mx.setScale(spacing * 0.9f, 1f);
            mx.postTranslate(bandX, 0);
            textBand.setLocalMatrix(mx);
            shimmer.setShader(textBand);
            shimmer.setAlpha((int) (255 * fade));
            for (int i = 0; i < WORD.length(); i++) c.drawText(WORD, i, i + 1, startX + i * spacing, baseY, shimmer);
        }

        // 8. shockwave + flare
        float wave = out3(seg(p, 0.68f, 0.98f));
        if (wave > 0 && wave < 1) {
            spinSweep(cx, cy, -t * 200f);
            ring.setShader(sweep);
            ring.setStrokeWidth(12 * d * (1 - wave) + 1 * d);
            ring.setAlpha((int) (230 * (1 - wave)));
            c.drawCircle(cx, cy, ringR + wave * half * 1.2f, ring);
            softGlow(c, cx, cy, half * (0.35f + wave * 0.45f), 0xFF6C63FF, 80 * (1 - wave));
            streak(c, cx, cy, half * 1.4f * (0.4f + wave), 5 * d, 0xFFA8E6FF, 200 * (1 - wave));
        }
        c.restore();
    }
}
