package com.vyd02.overdrive;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static com.vyd02.overdrive.Themes.lerpColor;
import static com.vyd02.overdrive.Themes.vGrad;
import static com.vyd02.overdrive.Themes.withA;

/**
 * Scene themes: Sakura Night, Neon Rain, Victorian Library, Gothic Cathedral, Firefly Forest.
 *
 * Built for this TV's graphics chip: everything that never moves (buildings, shelves, stone,
 * stained glass) is painted ONCE into a picture the size the TV draws the background at, and each
 * frame only adds the moving light: glows, petals, rain, dust, flames. Many small things
 * (rain, dust) are batched into a few draw calls.
 */
final class ThemesExtra {
    private ThemesExtra() { }

    /** The background layer is drawn at 3/4 of the themes' 1280x720 coordinates (960x540). */
    static final float STILL_SCALE = 0.75f;

    /** Keeps the last few painted scenes, so flipping between theme cards in Settings is instant. */
    private static final Map<String, Bitmap> STILLS = new LinkedHashMap<String, Bitmap>(4, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) { return size() > 3; }
    };

    /** Light adds up like real light. */
    static final PorterDuffXfermode ADD_MODE = new PorterDuffXfermode(PorterDuff.Mode.ADD);

    // ------------------------------------------------------------------
    // Base: a still picture + moving light on top
    // ------------------------------------------------------------------

    abstract static class Scene extends Themes.Theme {
        Bitmap still;
        final Paint stillPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        final RectF dst = new RectF();
        Themes.Glow glow;
        private float lastPulse;

        @Override void setup() {
            glow = new Themes.Glow(0.32f, 95);
            dst.set(0, 0, w, h);
            if (photo) { setupMoving(); return; } // the rendered picture is shown underneath instead
            Bitmap b;
            synchronized (STILLS) { b = STILLS.get(id()); }
            if (b == null || b.isRecycled()) {
                b = Bitmap.createBitmap(Math.round(w * STILL_SCALE), Math.round(h * STILL_SCALE), Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(b);
                c.scale(STILL_SCALE, STILL_SCALE);
                Random r = new Random(7);
                paintStill(c, r);
                synchronized (STILLS) { STILLS.put(id(), b); }
            }
            still = b;
            still.prepareToDraw();
            dst.set(0, 0, w, h);
            glow = new Themes.Glow(0.32f, 95);
            setupMoving();
        }

        abstract void paintStill(Canvas c, Random r);
        abstract void setupMoving();
        abstract void drawMoving(Canvas c, float t, float dt, Themes.Input in, boolean selected);
        /** Light and motion laid over the 3D-rendered picture, lined up with what's in it. */
        abstract void drawPhoto(Canvas c, float t, float dt, Themes.Input in, boolean selected);

        @Override void draw(Canvas c, float t, float dt, Themes.Input in) {
            boolean selected = in.pulse > lastPulse + 0.3f; // a fresh select this frame
            lastPulse = in.pulse;
            if (photo) { drawPhoto(c, t, dt, in, selected); return; }
            c.drawBitmap(still, null, dst, stillPaint);
            drawMoving(c, t, dt, in, selected);
        }

        /** A soft light stretched into an oval (mist, reflections, clouds). */
        void oval(Canvas c, float x, float y, float r, float sx, float sy, int color, int alpha) {
            c.save();
            c.translate(x, y);
            c.scale(sx, sy);
            glow.draw(c, 0, 0, r, color, alpha);
            c.restore();
        }
    }

    static Paint fill(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setColor(color);
        return p;
    }

    static Paint stroke(int color, float width) {
        Paint p = fill(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        return p;
    }

    static void softDisc(Canvas c, float x, float y, float r, int color, int alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new RadialGradient(x, y, r, new int[]{withA(color, alpha), withA(color, alpha / 3), 0},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
    }

    static void vignette(Canvas c, int w, int h, int alpha) {
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setShader(new RadialGradient(w / 2f, h / 2f, w * 0.78f, new int[]{0, 0, withA(0xFF000000, alpha)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }

    /** Dust or sparks, batched by brightness into two draws. */
    static final class Motes {
        final int n;
        final float[] x, y, vx, vy, ph, sz;
        final float[] bright, dim;
        int nb, nd;
        final Paint pb = new Paint(Paint.ANTI_ALIAS_FLAG), pd = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF area;

        Motes(Random r, int n, RectF area, float size) {
            this.n = n;
            this.area = area;
            x = new float[n]; y = new float[n]; vx = new float[n]; vy = new float[n]; ph = new float[n]; sz = new float[n];
            bright = new float[n * 2]; dim = new float[n * 2];
            for (int i = 0; i < n; i++) {
                x[i] = area.left + r.nextFloat() * area.width();
                y[i] = area.top + r.nextFloat() * area.height();
                vx[i] = (r.nextFloat() - 0.5f) * 10f;
                vy[i] = (r.nextFloat() - 0.5f) * 8f;
                ph[i] = r.nextFloat() * 6.28f;
            }
            for (Paint p : new Paint[]{pb, pd}) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setXfermode(ADD_MODE);
            }
            pb.setStrokeWidth(size * 1.5f);
            pd.setStrokeWidth(size);
        }

        /** Push everything near (px,py) outward. */
        void burst(float px, float py, float power) {
            for (int i = 0; i < n; i++) {
                float dx = x[i] - px, dy = y[i] - py;
                float d = (float) Math.sqrt(dx * dx + dy * dy) + 30f;
                float f = power * Math.max(0f, 1f - d / 600f);
                vx[i] += dx / d * f;
                vy[i] += dy / d * f;
            }
        }

        void draw(Canvas c, float t, float dt, float wind, int color, int alphaBright, int alphaDim) {
            nb = 0; nd = 0;
            float damp = (float) Math.exp(-dt * 0.9f);
            for (int i = 0; i < n; i++) {
                vx[i] = vx[i] * damp + (float) Math.sin(t * 0.5f + ph[i]) * 4f * dt;
                vy[i] = vy[i] * damp + (float) Math.cos(t * 0.4f + ph[i] * 1.3f) * 3f * dt;
                x[i] += (vx[i] + wind) * dt;
                y[i] += vy[i] * dt - 2f * dt;
                if (x[i] < area.left) x[i] += area.width();
                if (x[i] > area.right) x[i] -= area.width();
                if (y[i] < area.top) y[i] += area.height();
                if (y[i] > area.bottom) y[i] -= area.height();
                if (Math.sin(t * 0.9f + ph[i]) > 0.2f) { bright[nb++] = x[i]; bright[nb++] = y[i]; }
                else { dim[nd++] = x[i]; dim[nd++] = y[i]; }
            }
            pb.setColor(withA(color, alphaBright));
            pd.setColor(withA(color, alphaDim));
            if (nb > 0) c.drawPoints(bright, 0, nb, pb);
            if (nd > 0) c.drawPoints(dim, 0, nd, pd);
        }
    }

    /** A flickering flame: two layered sines plus a little randomness, smoothed. */
    static final class Flicker {
        float v = 1f;
        final float ph;
        Flicker(float ph) { this.ph = ph; }
        float step(float t, float dt, Random r) {
            float target = 0.82f + 0.1f * (float) Math.sin(t * 7.3f + ph) + 0.06f * (float) Math.sin(t * 13.1f + ph * 2f)
                    + (r.nextFloat() - 0.5f) * 0.08f;
            v += (target - v) * Math.min(1f, dt * 12f);
            return v;
        }
    }

    // ------------------------------------------------------------------
    // 1. Sakura Night (anime)
    // ------------------------------------------------------------------

    static final class Sakura extends Scene {
        String id() { return "sakura"; }
        static final int N = 70;
        final float[] x = new float[N], y = new float[N], fall = new float[N], ex = new float[N], ey = new float[N];
        final float[] rot = new float[N], spin = new float[N], flip = new float[N], flipSpeed = new float[N], size = new float[N];
        Bitmap petalA, petalB;
        final Paint petalPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        final Matrix m = new Matrix();
        Themes.Stars stars;
        float wind;

        void paintStill(Canvas c, Random r) {
            c.drawRect(0, 0, w, h, paintOf(vGrad(0, h, new int[]{0xFF0A0A2C, 0xFF261957, 0xFF6E3A7E, 0xFFD9839C, 0xFFFFC2A0},
                    new float[]{0f, 0.34f, 0.6f, 0.8f, 0.92f})));
            // the moon
            softDisc(c, w * 0.7f, h * 0.27f, 330, 0xFFFFE8F2, 70);
            Paint moon = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            moon.setShader(new RadialGradient(w * 0.68f, h * 0.24f, 110, new int[]{0xFFFFFBF2, 0xFFF6E2D6}, null, Shader.TileMode.CLAMP));
            c.drawCircle(w * 0.7f, h * 0.27f, 92, moon);
            Paint crater = fill(0x14A07A80);
            c.drawCircle(w * 0.7f - 30, h * 0.27f - 18, 20, crater);
            c.drawCircle(w * 0.7f + 26, h * 0.27f + 22, 13, crater);
            c.drawCircle(w * 0.7f + 8, h * 0.27f - 40, 9, crater);
            // two layers of city
            skyline(c, r, h * 0.84f, 70, 190, 0xFF3D2A60, 0, 0);
            skyline(c, r, h * 0.9f, 50, 280, 0xFF180F2E, 0xFFFFD27A, 0.16f);
            c.drawRect(0, h * 0.9f, w, h, paintOf(vGrad(h * 0.9f, h, new int[]{0xFF140C28, 0xFF070412}, null)));
            // a blossoming branch reaching in from the top-left
            Paint wood = stroke(0xFF1B0F1F, 16);
            wood.setStrokeCap(Paint.Cap.ROUND);
            Path main = new Path();
            main.moveTo(-30, 90);
            main.cubicTo(120, 60, 260, 120, 470, 40);
            c.drawPath(main, wood);
            wood.setStrokeWidth(8);
            float[][] twigs = {{140, 72, 210, 170}, {250, 96, 300, 10}, {340, 82, 420, 150}, {80, 80, 60, 200}, {420, 55, 520, 90}};
            for (float[] tw : twigs) {
                Path p = new Path();
                p.moveTo(tw[0], tw[1]);
                p.quadTo((tw[0] + tw[2]) / 2f + 20, (tw[1] + tw[3]) / 2f - 20, tw[2], tw[3]);
                c.drawPath(p, wood);
            }
            int[] pink = {0xFFFFC4D4, 0xFFF59AB8, 0xFFFFE4EC, 0xFFE8789E};
            float[][] spots = {{60, 85}, {130, 70}, {210, 170}, {250, 96}, {300, 12}, {340, 82}, {420, 150}, {470, 42}, {60, 200}, {520, 90}, {190, 110}, {390, 60}};
            for (float[] s : spots) {
                softDisc(c, s[0], s[1], 70, 0xFFFF9EC0, 60);
                for (int k = 0; k < 14; k++) {
                    Paint b = fill(withA(pink[r.nextInt(pink.length)], 210 + r.nextInt(45)));
                    c.drawCircle(s[0] + (r.nextFloat() - 0.5f) * 70, s[1] + (r.nextFloat() - 0.5f) * 50, 5 + r.nextFloat() * 9, b);
                }
            }
            vignette(c, w, h, 150);
        }

        void setupMoving() {
            stars = new Themes.Stars(rnd, 45, 0.42f);
            petalA = petal(0xFFFFE0EA, 0xFFF48FB1);
            petalB = petal(0xFFFFF2F6, 0xFFFFB3C8);
            for (int i = 0; i < N; i++) respawn(i, true);
        }

        private Bitmap petal(int inner, int outer) {
            Bitmap b = Bitmap.createBitmap(28, 18, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new RadialGradient(10, 9, 15, inner, outer, Shader.TileMode.CLAMP));
            c.drawOval(1, 1, 27, 17, p);
            return b;
        }

        private void respawn(int i, boolean anywhere) {
            if (anywhere) { x[i] = rnd.nextFloat() * w; y[i] = rnd.nextFloat() * h; }
            else if (photo && rnd.nextFloat() < 0.65f) { x[i] = w * (0.06f + rnd.nextFloat() * 0.36f); y[i] = h * rnd.nextFloat() * 0.38f; } // from the blossoms
            else if (rnd.nextFloat() < 0.6f) { x[i] = rnd.nextFloat() * w; y[i] = -20; }
            else { x[i] = -20; y[i] = rnd.nextFloat() * h * 0.7f; }
            fall[i] = 28 + rnd.nextFloat() * 46;
            rot[i] = rnd.nextFloat() * 360;
            spin[i] = (rnd.nextFloat() - 0.5f) * 120;
            flip[i] = rnd.nextFloat() * 6.28f;
            flipSpeed[i] = 1.5f + rnd.nextFloat() * 2.5f;
            size[i] = photo ? 0.32f + rnd.nextFloat() * 0.42f : 0.55f + rnd.nextFloat() * 0.75f;
            ex[i] = 0; ey[i] = 0;
        }

        void drawPhoto(Canvas c, float t, float dt, Themes.Input in, boolean selected) {
            // the moon in the picture breathes; petals drift from the tree on the left
            glow.draw(c, w * 0.651f, h * 0.486f, 110 * (1 + 0.06f * (float) Math.sin(t * 0.6f)), 0xFFFFF0F6, (int) (95 + 100 * in.pulse));
            glow.draw(c, in.fx * w, in.fy * h, 230, in.accent, (int) (18 + 35 * in.boost));
            petals(c, t, dt, in, selected);
        }

        void drawMoving(Canvas c, float t, float dt, Themes.Input in, boolean selected) {
            stars.draw(c, w, h, t, (in.fx - 0.5f) * 20, 0.55f);
            glow.draw(c, w * 0.7f, h * 0.27f, 250 * (1 + 0.04f * (float) Math.sin(t * 0.6f)), 0xFFFFE6F0, (int) (45 + 90 * in.pulse));
            for (int k = 0; k < 3; k++) {
                float cx = ((t * (6 + k * 3) + k * 520) % (w + 600)) - 300;
                oval(c, cx, h * (0.16f + k * 0.12f), 110, 3.6f, 0.45f, 0xFFC7A6E6, 30);
            }
            glow.draw(c, in.fx * w, in.fy * h, 230, in.accent, (int) (25 + 40 * in.boost));
            petals(c, t, dt, in, selected);
        }

        private void petals(Canvas c, float t, float dt, Themes.Input in, boolean selected) {
            wind += ((22 + in.vx * 70 + in.boost * 40) - wind) * Math.min(1f, dt * 1.5f);
            if (selected) {
                float px = in.px * w, py = in.py * h;
                for (int i = 0; i < N; i++) {
                    float dx = x[i] - px, dy = y[i] - py, d = (float) Math.sqrt(dx * dx + dy * dy) + 40;
                    float f = 520 * Math.max(0f, 1f - d / 700f);
                    ex[i] += dx / d * f; ey[i] += dy / d * f;
                }
            }
            float damp = (float) Math.exp(-dt * 1.6f);
            for (int i = 0; i < N; i++) {
                ex[i] *= damp; ey[i] *= damp;
                x[i] += (wind + ex[i] + (float) Math.sin(t * 1.2f + flip[i]) * 14f) * dt;
                y[i] += (fall[i] + ey[i]) * dt;
                rot[i] += spin[i] * dt;
                flip[i] += flipSpeed[i] * dt;
                if (y[i] > h + 24 || x[i] > w + 30 || x[i] < -40 || y[i] < -60) { respawn(i, false); continue; }
                float fl = 0.25f + 0.75f * Math.abs((float) Math.cos(flip[i]));
                m.setTranslate(-14, -9);
                m.postScale(size[i] * fl, size[i]);
                m.postRotate(rot[i]);
                m.postTranslate(x[i], y[i]);
                if (photo && i % 4 == 1) continue; // a lighter flurry over the picture
                petalPaint.setAlpha(i % 4 == 0 ? 150 : 215);
                c.drawBitmap(i % 3 == 0 ? petalB : petalA, m, petalPaint);
            }
        }
    }

    static Paint paintOf(Shader s) {
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setShader(s);
        return p;
    }

    /** A row of buildings; with a window color, some windows are lit. */
    static void skyline(Canvas c, Random r, float base, float minH, float maxH, int color, int windowColor, float lit) {
        Paint b = fill(color);
        Paint win = windowColor != 0 ? fill(windowColor) : null;
        float x = -10;
        while (x < 1300) {
            float bw = 34 + r.nextFloat() * 70, bh = minH + r.nextFloat() * (maxH - minH);
            c.drawRect(x, base - bh, x + bw, base + 2, b);
            if (r.nextFloat() < 0.25f) c.drawRect(x + bw * 0.45f, base - bh - 22, x + bw * 0.45f + 3, base - bh, b); // antenna
            if (win != null) {
                for (float wy = base - bh + 10; wy < base - 8; wy += 13) {
                    for (float wx = x + 6; wx < x + bw - 8; wx += 10) {
                        if (r.nextFloat() < lit) {
                            win.setAlpha(120 + r.nextInt(135));
                            c.drawRect(wx, wy, wx + 4, wy + 6, win);
                        }
                    }
                }
            }
            x += bw + 2 + r.nextFloat() * 6;
        }
    }

    // ------------------------------------------------------------------
    // 2. Neon Rain (anime city at night)
    // ------------------------------------------------------------------

    static final class NeonRain extends Scene {
        String id() { return "neon"; }
        static final int RAIN = 170, SIGNS = 7;
        final float[] rx = new float[RAIN], ry = new float[RAIN], rs = new float[RAIN], rl = new float[RAIN];
        final float[] nearL = new float[RAIN * 4], farL = new float[RAIN * 4];
        final RectF[] sign = new RectF[SIGNS];
        final int[] signColor = new int[SIGNS];
        final float[] signPh = new float[SIGNS];
        final float[][] glyphs = new float[SIGNS][];
        final Paint near = stroke(0x80CFE4FF, 2.2f), far = stroke(0x50AFC8FF, 1.3f);
        final Paint signFill = fill(0xFFFFFFFF), glyph = stroke(0xFFFFFFFF, 3f), flash = fill(0);
        float street, flashA;

        void paintStill(Canvas c, Random r) {
            float st = h * 0.72f;
            c.drawRect(0, 0, w, st, paintOf(vGrad(0, st, new int[]{0xFF04050E, 0xFF0E1433, 0xFF2A1442}, new float[]{0f, 0.55f, 1f})));
            softDisc(c, w * 0.5f, st, w * 0.6f, 0xFFB03AFF, 50);
            skyline(c, r, st, 160, 420, 0xFF141A36, 0xFF6FA8FF, 0.05f);
            skyline(c, r, st + 4, 90, 300, 0xFF0A0D20, 0xFFFFB86B, 0.10f);
            c.drawRect(0, st, w, h, paintOf(vGrad(st, h, new int[]{0xFF0D0E22, 0xFF04040C}, null)));
            // wet street: faint streaks
            Paint wet = fill(0x14FFFFFF);
            for (int i = 0; i < 26; i++) {
                float y = st + 8 + r.nextFloat() * (h - st);
                c.drawRect(0, y, w, y + 1 + r.nextFloat(), wet);
            }
            vignette(c, w, h, 170);
        }

        void setupMoving() {
            street = h * 0.72f;
            int[] neon = {0xFFFF2BD6, 0xFF2BF0FF, 0xFFFFB02B, 0xFF9B5CFF, 0xFFFF3D5A, 0xFF3DFF9B, 0xFF2BF0FF};
            for (int i = 0; i < SIGNS; i++) {
                boolean vertical = i % 2 == 0;
                float sx = 60 + i * (w - 140) / SIGNS + rnd.nextFloat() * 40;
                float sy = street - 140 - rnd.nextFloat() * 200;
                sign[i] = vertical ? new RectF(sx, sy, sx + 28, sy + 110 + rnd.nextFloat() * 60)
                        : new RectF(sx, sy, sx + 120 + rnd.nextFloat() * 50, sy + 30);
                signColor[i] = neon[i];
                signPh[i] = rnd.nextFloat() * 100;
                // abstract "lettering": short strokes, not real words
                int n = vertical ? 4 : 5;
                float[] g = new float[n * 4];
                for (int k = 0; k < n; k++) {
                    if (vertical) {
                        float y = sign[i].top + 12 + k * (sign[i].height() - 24) / n;
                        g[k * 4] = sign[i].left + 7; g[k * 4 + 1] = y; g[k * 4 + 2] = sign[i].right - 7; g[k * 4 + 3] = y + 6;
                    } else {
                        float x = sign[i].left + 12 + k * (sign[i].width() - 24) / n;
                        g[k * 4] = x; g[k * 4 + 1] = sign[i].top + 8; g[k * 4 + 2] = x + 10; g[k * 4 + 3] = sign[i].bottom - 8;
                    }
                }
                glyphs[i] = g;
            }
            for (int i = 0; i < RAIN; i++) { rx[i] = rnd.nextFloat() * (w + 200) - 100; ry[i] = rnd.nextFloat() * h; drop(i); }
            near.setStrokeCap(Paint.Cap.BUTT);
            far.setStrokeCap(Paint.Cap.BUTT);
            signFill.setStyle(Paint.Style.FILL);
            glyph.setStrokeCap(Paint.Cap.BUTT);
        }

        void drawPhoto(Canvas c, float t, float dt, Themes.Input in, boolean selected) {
            // the street's neon breathes a little; rain and lightning on top
            glow.draw(c, w * 0.5f, h * 0.55f, w * 0.35f, 0xFFB43AFF, (int) (16 + 10 * Math.sin(t * 0.7f) + 40 * in.pulse));
            glow.draw(c, in.fx * w, in.fy * h, 220, in.accent, (int) (18 + 35 * in.boost));
            rain(c, t, dt, in, selected);
        }

        private void drop(int i) { rs[i] = 700 + rnd.nextFloat() * 500; rl[i] = 14 + rnd.nextFloat() * 22; }

        void drawMoving(Canvas c, float t, float dt, Themes.Input in, boolean selected) {
            for (int i = 0; i < SIGNS; i++) {
                float f = 1f;
                float n = (float) Math.sin(t * 2.3f + signPh[i]) + (float) Math.sin(t * 5.7f + signPh[i] * 1.7f);
                if (n > 1.75f) f = 0.25f; // a buzzing flicker now and then
                f = Math.min(1.4f, f + in.pulse * 0.4f);
                int col = signColor[i];
                RectF s = sign[i];
                glow.draw(c, s.centerX(), s.centerY(), Math.max(s.width(), s.height()) * 1.5f, col, (int) (70 * f));
                signFill.setColor(withA(col, (int) (220 * Math.min(1f, f))));
                c.drawRoundRect(s, 6, 6, signFill);
                glyph.setColor(withA(lerpColor(col, Color.WHITE, 0.7f), (int) (230 * Math.min(1f, f))));
                c.drawLines(glyphs[i], glyph);
                // its reflection on the wet street
                oval(c, s.centerX(), street + (street - s.centerY()) * 0.3f + 30, 60, 0.7f, 2.6f, col, (int) (38 * f));
            }
            glow.draw(c, in.fx * w, in.fy * h, 220, in.accent, (int) (25 + 40 * in.boost));
            rain(c, t, dt, in, selected);
        }

        private void rain(Canvas c, float t, float dt, Themes.Input in, boolean selected) {
            float tilt = 0.12f + in.vx * 0.22f + in.boost * 0.1f;
            int nn = 0, nf = 0;
            for (int i = 0; i < RAIN; i++) {
                ry[i] += rs[i] * dt;
                rx[i] += rs[i] * tilt * dt;
                if (ry[i] > h + 30) { ry[i] = -30 - rnd.nextFloat() * 60; rx[i] = rnd.nextFloat() * (w + 300) - 200; drop(i); }
                if (rx[i] > w + 100) rx[i] -= w + 200;
                if (rx[i] < -100) rx[i] += w + 200;
                float len = rl[i];
                float[] L = (i % 3 == 0) ? nearL : farL;
                int k = (i % 3 == 0) ? nn : nf;
                L[k] = rx[i]; L[k + 1] = ry[i]; L[k + 2] = rx[i] - len * tilt; L[k + 3] = ry[i] - len;
                if (i % 3 == 0) nn += 4; else nf += 4;
            }
            c.drawLines(farL, 0, nf, far);
            c.drawLines(nearL, 0, nn, near);

            if (selected) flashA = 1f; // select = lightning
            if (flashA > 0f) {
                flash.setColor(withA(0xFFDDE8FF, (int) (110 * flashA * flashA)));
                c.drawRect(0, 0, w, photo ? h : street, flash);
                flashA = Math.max(0f, flashA - dt * 3f);
            }
        }
    }

    // ------------------------------------------------------------------
    // 3. Victorian Library
    // ------------------------------------------------------------------

    static final class Library extends Scene {
        String id() { return "library"; }
        Motes dust;
        final Paint beam = new Paint(Paint.DITHER_FLAG | Paint.ANTI_ALIAS_FLAG), flame = fill(0xFFFFE6A0);
        final Path beamPath = new Path();
        final Flicker lamp = new Flicker(0.3f), candle = new Flicker(2.1f), hearth = new Flicker(4.4f);

        static final int[] SPINES = {0xFF6B1E1E, 0xFF1F3B2B, 0xFF2B2140, 0xFF5A3A1A, 0xFF1A2846, 0xFF7A5A2A, 0xFF3A1414, 0xFF2E4A3A, 0xFF4A1A2A};

        void paintStill(Canvas c, Random r) {
            c.drawRect(0, 0, w, h, paintOf(vGrad(0, h, new int[]{0xFF1C0F08, 0xFF2A170C, 0xFF120804}, null)));
            float wl = w * 0.41f, wr = w * 0.59f, wt = h * 0.08f, wb = h * 0.66f;
            // tall arched window with moonlight
            Path win = new Path();
            win.moveTo(wl, wb);
            win.lineTo(wl, wt + (wr - wl) / 2f);
            win.arcTo(new RectF(wl, wt, wr, wt + (wr - wl)), 180, 180);
            win.lineTo(wr, wb);
            win.close();
            c.drawPath(win, paintOf(vGrad(wt, wb, new int[]{0xFF2A3F6E, 0xFF15233F, 0xFF0C1427}, null)));
            c.save();
            c.clipPath(win);
            softDisc(c, wl + (wr - wl) * 0.66f, wt + 70, 60, 0xFFE8F0FF, 160);
            c.drawCircle(wl + (wr - wl) * 0.66f, wt + 70, 16, fill(0xFFF2F6FF));
            Paint stars = fill(0xB0FFFFFF);
            for (int i = 0; i < 18; i++) c.drawCircle(wl + r.nextFloat() * (wr - wl), wt + r.nextFloat() * (wb - wt) * 0.7f, 1 + r.nextFloat(), stars);
            c.restore();
            Paint bar = stroke(0xFF2C1A0E, 6);
            c.drawLine((wl + wr) / 2f, wt, (wl + wr) / 2f, wb, bar);
            for (int k = 1; k < 5; k++) { float y = wt + (wr - wl) / 2f + k * (wb - wt - (wr - wl) / 2f) / 5f; c.drawLine(wl, y, wr, y, bar); }
            c.drawCircle((wl + wr) / 2f, wt + (wr - wl) / 2f, (wr - wl) * 0.22f, bar);
            Paint frame = stroke(0xFF3E2512, 14);
            c.drawPath(win, frame);
            // carved pilasters beside the window
            Paint pil = paintOf(new LinearGradient(0, 0, 50, 0, new int[]{0xFF2A160A, 0xFF4A2A14, 0xFF22120A}, null, Shader.TileMode.MIRROR));
            c.drawRect(w * 0.355f, 0, wl - 8, h, pil);
            c.drawRect(wr + 8, 0, w * 0.645f, h, pil);
            // bookshelves
            shelves(c, r, 0, w * 0.355f);
            shelves(c, r, w * 0.645f, w);
            // reading desk, books and a banker's lamp
            float dy = h * 0.8f;
            c.drawRect(w * 0.28f, dy, w * 0.72f, h, paintOf(vGrad(dy, h, new int[]{0xFF2A1408, 0xFF0C0503}, null)));
            c.drawRect(w * 0.28f, dy - 6, w * 0.72f, dy + 4, fill(0xFF4A2A14));
            float bx = w * 0.4f;
            int[] stack = {0xFF6B1E1E, 0xFF1F3B2B, 0xFF5A3A1A};
            for (int k = 0; k < 3; k++) c.drawRect(bx - 4 * k, dy - 14 - k * 13, bx + 90 - 6 * k, dy - 2 - k * 13, fill(stack[k]));
            float lx = w * 0.64f;
            c.drawRect(lx - 3, dy - 70, lx + 3, dy - 6, fill(0xFF8A6A2A));
            c.drawRect(lx - 26, dy - 8, lx + 26, dy - 2, fill(0xFF8A6A2A));
            Path shade = new Path();
            shade.moveTo(lx - 60, dy - 64);
            shade.quadTo(lx, dy - 108, lx + 60, dy - 64);
            shade.close();
            c.drawPath(shade, fill(0xFF1F5A3A));
            c.drawRect(w * 0.335f, dy - 52, w * 0.335f + 14, dy - 4, fill(0xFFEFE2C4)); // candle
            vignette(c, w, h, 190);
        }

        private void shelves(Canvas c, Random r, float x0, float x1) {
            c.drawRect(x0, 0, x1, h, fill(0xFF1E0F07));
            Paint board = fill(0xFF4A2A16), edge = fill(0xFF6B4026), gold = fill(0xB0C9A24A), shadow = fill(0x50000000);
            for (float sy = 70; sy < h * 0.95f; sy += 96) {
                float x = x0 + 10;
                while (x < x1 - 14) {
                    float bw = 10 + r.nextFloat() * 18, bh = 56 + r.nextFloat() * 28;
                    if (r.nextFloat() < 0.06f) { x += bw; continue; } // a gap
                    int col = SPINES[r.nextInt(SPINES.length)];
                    col = lerpColor(col, 0xFF000000, r.nextFloat() * 0.35f);
                    c.drawRect(x, sy - bh, x + bw - 1, sy, fill(col));
                    c.drawRect(x, sy - bh, x + 2, sy, shadow);
                    if (r.nextFloat() < 0.7f) {
                        c.drawRect(x + 1, sy - bh + 7, x + bw - 2, sy - bh + 9, gold);
                        c.drawRect(x + 1, sy - 11, x + bw - 2, sy - 9, gold);
                    }
                    x += bw;
                }
                c.drawRect(x0, sy, x1, sy + 11, board);
                c.drawRect(x0, sy, x1, sy + 2, edge);
            }
        }

        void setupMoving() {
            float wl = w * 0.41f, wr = w * 0.59f;
            beamPath.moveTo(wl, h * 0.2f);
            beamPath.lineTo(wr, h * 0.2f);
            beamPath.lineTo(w * 0.86f, h);
            beamPath.lineTo(w * 0.5f, h);
            beamPath.close();
            beam.setShader(new LinearGradient(0, h * 0.2f, 0, h, new int[]{0x669DB8FF, 0x229DB8FF, 0}, null, Shader.TileMode.CLAMP));
            beam.setXfermode(ADD_MODE);
            dust = new Motes(rnd, 110, new RectF(w * 0.3f, h * 0.1f, w * 0.9f, h), 2.2f);
        }

        Motes beamDust;

        void drawPhoto(Canvas c, float t, float dt, Themes.Input in, boolean selected) {
            // lined up with the rendered room: candle and banker's lamp on the desk, moonbeam from the window
            float fc = candle.step(t, dt, rnd), fl = lamp.step(t, dt, rnd);
            float cx = w * 0.591f, cy = h * 0.735f;
            glow.draw(c, cx, cy, 70 * fc, 0xFFFFA040, (int) (95 * fc));
            glow.draw(c, w * 0.755f, h * 0.715f, 120, 0xFFFFC870, (int) (45 * fl + 50 * in.pulse));
            glow.draw(c, w * 0.52f, h * 0.36f, 180, 0xFF9DB8FF, (int) (22 + 8 * Math.sin(t * 0.35f) + 30 * in.pulse));
            glow.draw(c, in.fx * w, in.fy * h, 210, lerpColor(in.accent, 0xFFFFC870, 0.5f), (int) (14 + 28 * in.boost));
            if (beamDust == null) beamDust = new Motes(rnd, 90, new RectF(w * 0.44f, h * 0.28f, w * 0.7f, h * 0.92f), 1.3f);
            if (selected) beamDust.burst(in.px * w, in.py * h, 160);
            beamDust.draw(c, t, dt, in.vx * 25, 0xFFDDE6FF, 120, 45);
        }

        void drawMoving(Canvas c, float t, float dt, Themes.Input in, boolean selected) {
            float fh = hearth.step(t, dt, rnd);
            glow.draw(c, -40, h * 0.9f, w * 0.55f, 0xFFFF7A2B, (int) (55 * fh)); // fireplace just off screen
            beam.setAlpha((int) (200 + 40 * Math.sin(t * 0.35f) + 15 * in.pulse));
            c.drawPath(beamPath, beam);
            float lx = w * 0.64f, dy = h * 0.8f;
            float fl = lamp.step(t, dt, rnd);
            glow.draw(c, lx, dy - 50, 230, 0xFFFFC870, (int) (110 * fl + 60 * in.pulse));
            float fc = candle.step(t, dt, rnd);
            float cx = w * 0.335f + 7, cy = dy - 60;
            glow.draw(c, cx, cy, 110 * fc, 0xFFFFA040, (int) (130 * fc));
            c.drawOval(cx - 4, cy - 12 * fc, cx + 4, cy + 5, flame);
            glow.draw(c, in.fx * w, in.fy * h, 210, lerpColor(in.accent, 0xFFFFC870, 0.5f), (int) (20 + 35 * in.boost));
            if (selected) dust.burst(in.px * w, in.py * h, 160);
            dust.draw(c, t, dt, in.vx * 25, 0xFFE8EEFF, 210, 90);
        }
    }

    // ------------------------------------------------------------------
    // 4. Gothic Cathedral
    // ------------------------------------------------------------------

    static final class Cathedral extends Scene {
        String id() { return "cathedral"; }
        Motes dust;
        final Paint[] beams = new Paint[3];
        final Path[] beamPaths = new Path[3];
        final Flicker[] candles = new Flicker[6];
        final Paint flame = fill(0xFFFFE6A0);
        float rcx, rcy;

        static final int[] GLASS = {0xFF2448C8, 0xFFC8183C, 0xFFF0B22A, 0xFF7038C8, 0xFF20A060};

        void paintStill(Canvas c, Random r) {
            c.drawRect(0, 0, w, h, paintOf(vGrad(0, h, new int[]{0xFF0B0B14, 0xFF15141F, 0xFF08080C}, null)));
            float cx = w * 0.7f, cy = h * 0.33f; // off to the right, clear of the clock and the titles
            // back wall arch around the rose window
            Paint stone = fill(0xFF1E1D2A);
            Path arch = gothicArch(cx - 250, cx + 250, h * 0.02f, h * 0.82f);
            c.drawPath(arch, stone);
            // tall lancet windows under the rose
            lancet(c, cx - 130, h * 0.55f, 40, 180);
            lancet(c, cx + 130, h * 0.55f, 40, 180);
            // the rose window
            float R = 125;
            softDisc(c, cx, cy, R * 2.1f, 0xFF5A48D8, 70);
            Paint ring = stroke(0xFF2C2A3A, 18);
            for (int s = 0; s < 16; s++) {
                Paint g = fill(lerpColor(GLASS[s % GLASS.length], 0xFF000000, 0.28f));
                c.drawArc(cx - R, cy - R, cx + R, cy + R, s * 22.5f, 22.5f, true, g);
            }
            for (int s = 0; s < 8; s++) {
                Paint g = fill(lerpColor(GLASS[(s + 2) % GLASS.length], 0xFF000000, 0.12f));
                c.drawArc(cx - R * 0.55f, cy - R * 0.55f, cx + R * 0.55f, cy + R * 0.55f, s * 45 + 11, 45, true, g);
            }
            c.drawCircle(cx, cy, R * 0.18f, fill(0xFFE8B84A));
            Paint lead = stroke(0xFF06060A, 4);
            for (int s = 0; s < 16; s++) {
                double a = Math.toRadians(s * 22.5);
                c.drawLine(cx + (float) Math.cos(a) * R * 0.18f, cy + (float) Math.sin(a) * R * 0.18f,
                        cx + (float) Math.cos(a) * R, cy + (float) Math.sin(a) * R, lead);
            }
            c.drawCircle(cx, cy, R * 0.55f, lead);
            c.drawCircle(cx, cy, R * 0.78f, lead);
            c.drawCircle(cx, cy, R * 0.18f, lead);
            c.drawCircle(cx, cy, R + 9, ring);
            // columns and pointed arches receding on both sides
            float[] xs = {0, 110, 205, 285};
            float[] wd = {80, 60, 46, 34};
            for (int i = 0; i < xs.length; i++) {
                int shade = lerpColor(0xFF3A3848, 0xFF15141E, i / 4f);
                for (int side = 0; side < 2; side++) {
                    float x = side == 0 ? xs[i] : w - xs[i] - wd[i];
                    Paint col = paintOf(new LinearGradient(x, 0, x + wd[i], 0,
                            new int[]{lerpColor(shade, 0xFF000000, 0.5f), shade, lerpColor(shade, 0xFF000000, 0.6f)}, null, Shader.TileMode.CLAMP));
                    c.drawRect(x, 0, x + wd[i], h, col);
                    if (i < xs.length - 1) {
                        float nx = side == 0 ? xs[i + 1] : w - xs[i + 1] - wd[i + 1];
                        float l = Math.min(x, nx) + (side == 0 ? wd[i] : wd[i + 1]), rr = Math.max(x, nx);
                        Path a = gothicArch(l, rr, h * 0.12f + i * 18, h * 0.3f + i * 18);
                        Paint ap = stroke(shade, 8);
                        c.drawPath(a, ap);
                    }
                }
            }
            // altar steps and candles
            float fy = h * 0.82f;
            c.drawRect(0, fy, w, h, paintOf(vGrad(fy, h, new int[]{0xFF1A1924, 0xFF060609}, null)));
            cx = altarX(w);
            c.drawRect(cx - 230, fy - 10, cx + 230, fy + 6, fill(0xFF2E2C3C));
            c.drawRect(cx - 170, fy - 26, cx + 170, fy - 10, fill(0xFF26243A));
            c.drawRect(cx - 110, fy - 70, cx + 110, fy - 26, fill(0xFF121119));
            c.drawRect(cx - 110, fy - 74, cx + 110, fy - 68, fill(0xFFB89A4A));
            for (int i = 0; i < 6; i++) {
                float x = cx - 95 + i * 38;
                c.drawRect(x - 4, fy - 112, x + 4, fy - 74, fill(0xFFEDE3CB));
            }
            vignette(c, w, h, 200);
        }

        static float altarX(int w) { return w * 0.7f; }

        static Path gothicArch(float l, float r, float top, float bottom) {
            Path p = new Path();
            float mid = (l + r) / 2f, span = r - l;
            p.moveTo(l, bottom);
            p.lineTo(l, top + span * 0.55f);
            p.quadTo(l, top + span * 0.05f, mid, top);
            p.quadTo(r, top + span * 0.05f, r, top + span * 0.55f);
            p.lineTo(r, bottom);
            return p;
        }

        private void lancet(Canvas c, float cx, float top, float wd, float ht) {
            Path p = gothicArch(cx - wd / 2f, cx + wd / 2f, top, top + ht);
            p.close();
            c.drawPath(p, paintOf(vGrad(top, top + ht, new int[]{0xFF3A58D8, 0xFF8A2A9A, 0xFFC8283C}, new float[]{0f, 0.5f, 1f})));
            Paint lead = stroke(0xFF06060A, 3);
            for (int k = 1; k < 6; k++) c.drawLine(cx - wd / 2f, top + ht * k / 6f + 10, cx + wd / 2f, top + ht * k / 6f + 10, lead);
            c.drawLine(cx, top + 6, cx, top + ht, lead);
            c.drawPath(p, stroke(0xFF2C2A3A, 6));
        }

        void setupMoving() {
            rcx = w * 0.7f; rcy = h * 0.33f;
            int[] cols = {0xFF4A6CFF, 0xFFFF3A5A, 0xFFFFC23A};
            float[][] geo = {{rcx - 60, rcy + 40, 0, w * 0.42f}, {rcx + 30, rcy + 50, 0, w * 0.95f}, {rcx - 30, rcy + 60, 0, w * 0.68f}};
            for (int i = 0; i < 3; i++) {
                Path p = new Path();
                float[] g = geo[i];
                p.moveTo(g[0], g[1]);
                p.lineTo(g[0] + 90, g[1]);
                p.lineTo(g[3] + 140, h);
                p.lineTo(g[3] - 60, h);
                p.close();
                beamPaths[i] = p;
                Paint b = new Paint(Paint.DITHER_FLAG | Paint.ANTI_ALIAS_FLAG);
                b.setShader(new LinearGradient(0, rcy, 0, h, new int[]{withA(cols[i], 90), withA(cols[i], 30), 0}, null, Shader.TileMode.CLAMP));
                b.setXfermode(ADD_MODE);
                beams[i] = b;
            }
            for (int i = 0; i < 6; i++) candles[i] = new Flicker(i * 1.37f);
            dust = new Motes(rnd, 120, new RectF(w * 0.35f, h * 0.35f, w, h), 2f);
        }

        Motes beamDust;

        void drawPhoto(Canvas c, float t, float dt, Themes.Input in, boolean selected) {
            // lined up with the rendered nave: rose window, altar candles, the two rows of side candles
            float rx = w * 0.605f, ry = h * 0.378f;
            int roseCol = Themes.hue(0xFF7A6AFF, 40f * (float) Math.sin(t * 0.12f));
            glow.draw(c, rx, ry, 150 * (1 + 0.05f * (float) Math.sin(t * 0.5f) + 0.3f * in.pulse), roseCol, (int) (30 + 140 * in.pulse));
            for (int i = 0; i < 6; i++) {
                float f = candles[i].step(t, dt, rnd);
                float x = w * (0.585f + i * 0.008f), y = h * 0.705f;
                glow.draw(c, x, y, 26 * f, 0xFFFFA040, (int) (70 * f));
            }
            glow.draw(c, w * 0.485f, h * 0.775f, 40, 0xFFFFA040, (int) (40 * candles[0].v));
            glow.draw(c, w * 0.727f, h * 0.775f, 40, 0xFFFFA040, (int) (40 * candles[3].v));
            glow.draw(c, in.fx * w, in.fy * h, 210, in.accent, (int) (14 + 28 * in.boost));
            if (beamDust == null) beamDust = new Motes(rnd, 100, new RectF(w * 0.4f, h * 0.4f, w * 0.74f, h * 0.92f), 1.3f);
            if (selected) beamDust.burst(in.px * w, in.py * h, 140);
            beamDust.draw(c, t, dt, in.vx * 20, 0xFFF0E6FF, 115, 40);
        }

        void drawMoving(Canvas c, float t, float dt, Themes.Input in, boolean selected) {
            int roseCol = Themes.hue(0xFF6A5AFF, 40f * (float) Math.sin(t * 0.12f));
            glow.draw(c, rcx, rcy, 220 * (1 + 0.05f * (float) Math.sin(t * 0.5f) + 0.25f * in.pulse), roseCol, (int) (60 + 130 * in.pulse));
            glow.draw(c, rcx, rcy, 90, 0xFFFFE6B0, (int) (40 + 80 * in.pulse));
            float sway = (in.fx - 0.5f) * 30;
            for (int i = 0; i < 3; i++) {
                beams[i].setAlpha((int) (170 + 50 * Math.sin(t * 0.3f + i * 2.1f) + 60 * in.pulse));
                c.save();
                c.translate(sway * (i == 2 ? 0.3f : 1f), 0);
                c.drawPath(beamPaths[i], beams[i]);
                c.restore();
            }
            float fy = h * 0.82f;
            for (int i = 0; i < 6; i++) {
                float f = candles[i].step(t, dt, rnd);
                float x = altarX(w) - 95 + i * 38, y = fy - 120;
                glow.draw(c, x, y, 60 * f, 0xFFFFA040, (int) (120 * f));
                c.drawOval(x - 3, y - 10 * f, x + 3, y + 4, flame);
            }
            glow.draw(c, in.fx * w, in.fy * h, 210, in.accent, (int) (20 + 35 * in.boost));
            if (selected) dust.burst(in.px * w, in.py * h, 140);
            dust.draw(c, t, dt, in.vx * 20, 0xFFF0E6FF, 200, 80);
        }
    }

    // ------------------------------------------------------------------
    // 5. Firefly Forest
    // ------------------------------------------------------------------

    static final class Fireflies extends Scene {
        String id() { return "fireflies"; }
        static final int N = 55;
        final float[] x = new float[N], y = new float[N], vx = new float[N], vy = new float[N], ph = new float[N], sp = new float[N];
        final float[] hx = new float[N], hy = new float[N]; // where each one likes to hover
        final Paint core = fill(0xFFF4FFC8);

        void paintStill(Canvas c, Random r) {
            c.drawRect(0, 0, w, h, paintOf(vGrad(0, h, new int[]{0xFF041020, 0xFF0E2C3C, 0xFF235258, 0xFF0A1A1E},
                    new float[]{0f, 0.4f, 0.7f, 1f})));
            softDisc(c, w * 0.78f, h * 0.2f, 300, 0xFFBFF0FF, 90);
            c.drawCircle(w * 0.78f, h * 0.2f, 44, fill(0xFFF2FDFF));
            Paint st = fill(0x90FFFFFF);
            for (int i = 0; i < 40; i++) c.drawCircle(r.nextFloat() * w, r.nextFloat() * h * 0.4f, 0.8f + r.nextFloat(), st);
            forest(c, r, h * 0.62f, 0xFF1A4450, 120, 220, 26);
            mist(c, h * 0.6f, 0x309FD8D0);
            forest(c, r, h * 0.74f, 0xFF0F2A34, 160, 300, 18);
            mist(c, h * 0.72f, 0x288FC8C0);
            forest(c, r, h * 0.92f, 0xFF040B10, 240, 420, 11);
            c.drawRect(0, h * 0.9f, w, h, fill(0xFF03080B));
            vignette(c, w, h, 170);
        }

        private void forest(Canvas c, Random r, float base, int color, float minH, float maxH, int count) {
            Paint p = fill(color);
            for (int i = 0; i < count; i++) {
                float x = (i + r.nextFloat() * 0.8f) * (w + 120) / count - 60;
                float th = minH + r.nextFloat() * (maxH - minH), tw = th * (0.28f + r.nextFloat() * 0.1f);
                Path t = new Path();
                for (int k = 0; k < 4; k++) { // stacked tiers of a pine
                    float tierTop = base - th + k * th * 0.2f, tierBot = base - th * (0.55f - k * 0.17f);
                    float half = tw * (0.45f + k * 0.2f);
                    t.moveTo(x, tierTop);
                    t.lineTo(x + half, tierBot);
                    t.lineTo(x - half, tierBot);
                    t.close();
                }
                c.drawPath(t, p);
                c.drawRect(x - 4, base - th * 0.15f, x + 4, base + 4, p);
            }
            c.drawRect(0, base, w, base + 200, p);
        }

        private void mist(Canvas c, float y, int color) {
            c.drawRect(0, y - 40, w, y + 40, paintOf(vGrad(y - 40, y + 40, new int[]{0, color, 0}, null)));
        }

        void setupMoving() {
            for (int i = 0; i < N; i++) {
                x[i] = hx[i] = rnd.nextFloat() * w;
                y[i] = hy[i] = h * 0.38f + rnd.nextFloat() * h * 0.57f;
                ph[i] = rnd.nextFloat() * 6.28f;
                sp[i] = 0.6f + rnd.nextFloat() * 1.2f;
            }
        }

        void drawPhoto(Canvas c, float t, float dt, Themes.Input in, boolean selected) {
            for (int k = 0; k < 2; k++) {
                float mx = ((t * (9 + k * 5) + k * 430) % (w + 700)) - 350;
                oval(c, mx, h * (0.7f + k * 0.1f), 140, 4.5f, 0.5f, 0xFF9FD8D0, 16);
            }
            flies(c, t, dt, in, selected);
        }

        void drawMoving(Canvas c, float t, float dt, Themes.Input in, boolean selected) {
            for (int k = 0; k < 3; k++) {
                float mx = ((t * (9 + k * 5) + k * 430) % (w + 700)) - 350;
                oval(c, mx, h * (0.6f + k * 0.11f), 140, 4.5f, 0.5f, 0xFF9FD8D0, 26);
            }
            glow.draw(c, w * 0.78f, h * 0.2f, 180 * (1 + 0.04f * (float) Math.sin(t * 0.5f)), 0xFFCFF6FF, 45);
            flies(c, t, dt, in, selected);
        }

        private void flies(Canvas c, float t, float dt, Themes.Input in, boolean selected) {
            float tx = in.fx * w, ty = in.fy * h;
            float pull = in.boost * 0.22f; // they drift toward you while you move, then wander home
            if (selected) {
                for (int i = 0; i < N; i++) {
                    float dx = x[i] - in.px * w, dy = y[i] - in.py * h, d = (float) Math.sqrt(dx * dx + dy * dy) + 30;
                    vx[i] += dx / d * 260; vy[i] += dy / d * 260;
                }
            }
            float damp = (float) Math.exp(-dt * 1.2f);
            for (int i = 0; i < N; i++) {
                vx[i] = vx[i] * damp + ((tx - x[i]) * pull + (hx[i] - x[i]) * 0.08f + (float) Math.sin(t * sp[i] + ph[i]) * 30f) * dt;
                vy[i] = vy[i] * damp + ((ty - y[i]) * pull + (hy[i] - y[i]) * 0.08f + (float) Math.cos(t * sp[i] * 0.8f + ph[i]) * 22f) * dt;
                x[i] += vx[i] * dt;
                y[i] += vy[i] * dt;
                if (x[i] < -20) x[i] = w + 20; else if (x[i] > w + 20) x[i] = -20;
                if (y[i] < h * 0.2f) vy[i] += 20 * dt; else if (y[i] > h - 10) vy[i] -= 30 * dt;
                float b = (float) Math.sin(t * sp[i] * 1.7f + ph[i]);
                b = b > 0 ? b * b : 0f;
                if (b < 0.05f) continue;
                glow.draw(c, x[i], y[i], 16 + 10 * b, 0xFFD8FF6A, (int) (200 * b));
                core.setAlpha((int) (255 * b));
                c.drawCircle(x[i], y[i], 2.2f, core);
            }
            glow.draw(c, tx, ty, 200, in.accent, (int) (18 + 30 * in.boost));
        }
    }
}
