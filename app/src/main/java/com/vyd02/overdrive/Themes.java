package com.vyd02.overdrive;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/** All the animated themes. Each one reacts to moving focus, selecting, and the focused app's color. */
final class Themes {

    private Themes() { }

    static final String[] IDS = {"nebula", "synthwave", "warp", "aurora", "deepsea", "forge"};
    static final String[] NAMES = {"Nebula", "Synthwave", "Hyperspace", "Aurora", "Deep Sea", "Ember Forge"};
    static final String[] BLURBS = {
            "Glowing clouds that take on the color of whatever app you're on",
            "Neon grid racing toward a striped sunset. Steer it with the remote",
            "A starfield that jumps to light speed when you move",
            "Northern lights that ripple in the wind you make",
            "Light rays and rising bubbles. Select something to burst them",
            "Sparks drifting up from a forge. They flare as you browse"
    };

    static String name(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return NAMES[i];
        return NAMES[0];
    }

    static Theme create(String id) {
        if ("synthwave".equals(id)) return new Synthwave();
        if ("warp".equals(id)) return new Warp();
        if ("aurora".equals(id)) return new Aurora();
        if ("deepsea".equals(id)) return new DeepSea();
        if ("forge".equals(id)) return new Forge();
        return new Nebula();
    }

    // ------------------------------------------------------------------
    // What the themes react to
    // ------------------------------------------------------------------

    static class Input {
        volatile float tfx = 0.5f, tfy = 0.6f;
        final AtomicInteger kickXa = new AtomicInteger(), kickYa = new AtomicInteger(), selectsA = new AtomicInteger();
        volatile int targetAccent = Color.parseColor("#3DA9FF");

        // smoothed values, render thread only
        float fx = 0.5f, fy = 0.6f;   // focus position 0..1
        float vx, vy;                 // swipe velocity, decays
        float boost;                  // 0..1 energy from moving
        float pulse;                  // 0..1 burst from selecting
        float px = 0.5f, py = 0.5f;   // where the last select happened
        int accent = Color.parseColor("#3DA9FF");
        private int seenSelects;

        void step(float dt) {
            float k = Math.min(1f, dt * 7f);
            fx += (tfx - fx) * k;
            fy += (tfy - fy) * k;
            int kx = kickXa.getAndSet(0);
            int ky = kickYa.getAndSet(0);
            vx += kx; vy += ky;
            float decay = (float) Math.exp(-dt * 2.6f);
            vx *= decay; vy *= decay;
            boost = Math.min(1f, boost + (Math.abs(kx) + Math.abs(ky)) * 0.3f);
            boost *= (float) Math.exp(-dt * 1.8f);
            int s = selectsA.get();
            if (s != seenSelects) { seenSelects = s; pulse = 1f; px = fx; py = fy; }
            pulse = Math.max(0f, pulse - dt * 1.2f);
            accent = lerpColor(accent, targetAccent, Math.min(1f, dt * 3.5f));
        }
    }

    abstract static class Theme {
        int w, h;
        final Random rnd = new Random(42);
        abstract String id();
        void init(int w, int h) { this.w = w; this.h = h; setup(); }
        abstract void setup();
        abstract void draw(Canvas c, float t, float dt, Input in);
    }

    // ------------------------------------------------------------------
    // Shared pieces
    // ------------------------------------------------------------------

    /** Soft white glow; recolored with a color filter so one shader serves every color. */
    static final class Glow {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        final RadialGradient g;
        final Matrix m = new Matrix();
        int lastColor = 0;
        Glow(float midStop, int midAlpha) {
            g = new RadialGradient(0, 0, 1f, new int[]{Color.WHITE, Color.argb(midAlpha, 255, 255, 255), 0},
                    new float[]{0f, midStop, 1f}, Shader.TileMode.CLAMP);
            p.setShader(g);
        }
        void draw(Canvas c, float x, float y, float r, int color, int alpha) {
            if (r <= 1 || alpha <= 0) return;
            m.setScale(r, r);
            m.postTranslate(x, y);
            g.setLocalMatrix(m);
            int opaque = color | 0xFF000000;
            if (opaque != lastColor) {
                p.setColorFilter(new PorterDuffColorFilter(opaque, PorterDuff.Mode.SRC_IN));
                lastColor = opaque;
            }
            p.setAlpha(Math.min(255, alpha));
            c.drawCircle(x, y, r, p);
        }
    }

    static final class Stars {
        final float[] x, y, r, ph, depth;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Stars(Random rnd, int n, float maxY) {
            x = new float[n]; y = new float[n]; r = new float[n]; ph = new float[n]; depth = new float[n];
            for (int i = 0; i < n; i++) {
                x[i] = rnd.nextFloat(); y[i] = rnd.nextFloat() * maxY;
                r[i] = 0.6f + rnd.nextFloat() * 1.6f; ph[i] = rnd.nextFloat() * 6.28f;
                depth[i] = 0.2f + rnd.nextFloat();
            }
            p.setColor(Color.WHITE);
        }
        void draw(Canvas c, int w, int h, float t, float shiftX, float alpha) {
            for (int i = 0; i < x.length; i++) {
                float tw = 0.55f + 0.45f * (float) Math.sin(t * 1.4f + ph[i]);
                p.setAlpha((int) (alpha * 200 * tw));
                float sx = ((x[i] * w - shiftX * depth[i]) % w + w) % w;
                c.drawCircle(sx, y[i] * h, r[i], p);
            }
        }
    }

    static Shader vGrad(float y0, float y1, int[] colors, float[] stops) {
        return new LinearGradient(0, y0, 0, y1, colors, stops, Shader.TileMode.CLAMP);
    }

    static int lerpColor(int a, int b, float f) {
        return Color.argb(255,
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }

    static int withA(int c, int a) { return (c & 0x00FFFFFF) | (Math.max(0, Math.min(255, a)) << 24); }

    static Path mountains(Random rnd, int w, float baseY, float height, int peaks) {
        Path p = new Path();
        p.moveTo(0, baseY);
        float step = (float) w / peaks;
        for (int i = 0; i <= peaks; i++) {
            float x = i * step;
            float y = baseY - height * (0.35f + rnd.nextFloat() * 0.65f);
            p.lineTo(x - step / 2f, baseY - height * rnd.nextFloat() * 0.3f);
            p.lineTo(x, y);
        }
        p.lineTo(w, baseY);
        p.lineTo(w, baseY + 400);
        p.lineTo(0, baseY + 400);
        p.close();
        return p;
    }

    // ------------------------------------------------------------------
    // 1. Nebula
    // ------------------------------------------------------------------

    static final class Nebula extends Theme {
        String id() { return "nebula"; }
        final Paint base = new Paint(Paint.DITHER_FLAG), vig = new Paint(Paint.DITHER_FLAG);
        final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        Glow glow; Stars near, far;

        void setup() {
            base.setShader(new LinearGradient(0, 0, w * 0.3f, h,
                    new int[]{0xFF05070F, 0xFF0A0F1F, 0xFF04050B}, null, Shader.TileMode.CLAMP));
            vig.setShader(new RadialGradient(w / 2f, h / 2f, w * 0.75f, new int[]{0, 0, 0xC8000000},
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            glow = new Glow(0.4f, 90);
            near = new Stars(rnd, 50, 1f);
            far = new Stars(rnd, 70, 1f);
            ring.setStyle(Paint.Style.STROKE);
        }

        void draw(Canvas c, float t, float dt, Input in) {
            c.drawPaint(base);
            int a = in.accent;
            float px = (in.fx - 0.5f), py = (in.fy - 0.5f);
            glow.draw(c, w * (0.78f + 0.07f * (float) Math.sin(t * 0.13)) - px * 90, h * (0.22f + 0.1f * (float) Math.cos(t * 0.11)) - py * 50,
                    w * 0.55f * (1 + 0.05f * (float) Math.sin(t * 0.3)), a, 120);
            glow.draw(c, w * (0.16f + 0.1f * (float) Math.cos(t * 0.09)) - px * 60, h * (0.86f + 0.08f * (float) Math.sin(t * 0.12)) - py * 40,
                    w * 0.45f, BackgroundView.shiftHue(a, 40), 95);
            glow.draw(c, w * (0.5f + 0.25f * (float) Math.sin(t * 0.05)) - px * 30, h * (0.5f + 0.15f * (float) Math.sin(t * 0.07)),
                    w * 0.32f, BackgroundView.shiftHue(a, -55), 70);
            // a soft light that follows whatever you're focused on
            glow.draw(c, in.fx * w, in.fy * h, 260 + in.boost * 80, a, (int) (55 + in.boost * 60));
            far.draw(c, w, h, t, px * 30 + in.vx * 6, 0.45f);
            near.draw(c, w, h, t, px * 80 + in.vx * 16, 0.8f);
            if (in.pulse > 0f) {
                float r = (1f - in.pulse) * 520f;
                ring.setStrokeWidth(10f * in.pulse);
                ring.setColor(withA(a, (int) (220 * in.pulse)));
                c.drawCircle(in.px * w, in.py * h, r, ring);
                glow.draw(c, in.px * w, in.py * h, 200 * (1.2f - in.pulse), Color.WHITE, (int) (90 * in.pulse));
            }
            c.drawPaint(vig);
        }
    }

    // ------------------------------------------------------------------
    // 2. Synthwave
    // ------------------------------------------------------------------

    static final class Synthwave extends Theme {
        String id() { return "synthwave"; }
        final Paint sky = new Paint(Paint.DITHER_FLAG), ground = new Paint(Paint.DITHER_FLAG);
        final Paint sun = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), lineGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint mtnBack = new Paint(Paint.ANTI_ALIAS_FLAG), mtnFront = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path back, front; Glow glow; Stars stars;
        float hz, scroll, steer;

        void setup() {
            hz = h * 0.56f;
            sky.setShader(vGrad(0, hz, new int[]{0xFF0B0221, 0xFF2A0845, 0xFF7A1F6E, 0xFFFF6A3D},
                    new float[]{0f, 0.45f, 0.82f, 1f}));
            ground.setShader(vGrad(hz, h, new int[]{0xFF14052B, 0xFF07010F}, null));
            sun.setShader(vGrad(hz - 360, hz, new int[]{0xFFFFE66D, 0xFFFF9A3C, 0xFFFF3D7F}, new float[]{0f, 0.5f, 1f}));
            back = mountains(rnd, w, hz, 120, 9);
            front = mountains(rnd, w, hz, 70, 14);
            mtnBack.setColor(0xFF3A0F55);
            mtnFront.setColor(0xFF1C0634);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2f);
            lineGlow.setStyle(Paint.Style.STROKE);
            lineGlow.setStrokeWidth(7f);
            glow = new Glow(0.35f, 110);
            stars = new Stars(rnd, 70, 0.45f);
        }

        void draw(Canvas c, float t, float dt, Input in) {
            steer += ((in.fx - 0.5f) * 1.4f + in.vx * 0.5f - steer) * Math.min(1f, dt * 2f);
            scroll = (scroll + dt * (0.55f + in.boost * 2.6f + in.pulse * 2f)) % 1f;

            c.drawRect(0, 0, w, hz, sky);
            stars.draw(c, w, h, t, steer * 40, 0.9f);

            // sun with stripes cut through it
            float sx = w / 2f - steer * 60, sy = hz - 150, sr = 175 * (1 + 0.06f * in.pulse);
            glow.draw(c, sx, sy, sr * 2.4f, 0xFFFF4F8B, (int) (120 + 100 * in.pulse));
            c.save();
            c.translate(0, 0);
            c.drawCircle(sx, sy, sr, sun);
            for (int i = 0; i < 7; i++) {
                float y = sy + sr * (0.08f + i * 0.14f);
                float gap = 3 + i * 2.6f;
                c.drawRect(sx - sr - 2, y, sx + sr + 2, y + gap, sky);
            }
            c.restore();

            c.save();
            c.translate(-steer * 30, 0);
            c.drawPath(back, mtnBack);
            c.restore();
            c.save();
            c.translate(-steer * 70, 0);
            c.drawPath(front, mtnFront);
            c.restore();

            c.drawRect(0, hz, w, h, ground);
            int neon = lerpColor(0xFFFF2BD6, in.accent, 0.35f);
            int cyan = lerpColor(0xFF2BF0FF, in.accent, 0.25f);
            float flash = 1f + in.pulse * 0.8f;
            float vpX = w / 2f - steer * 260;
            // vertical lines converge on the horizon
            for (int i = -14; i <= 14; i++) {
                float bx = w / 2f + i * 150 - steer * 520;
                lineGlow.setColor(withA(neon, (int) (40 * flash)));
                line.setColor(withA(neon, (int) (170 * flash)));
                c.drawLine(vpX + i * 8, hz, bx, h, lineGlow);
                c.drawLine(vpX + i * 8, hz, bx, h, line);
            }
            // horizontal lines rush toward you
            int rows = 16;
            for (int k = 0; k < rows; k++) {
                float z = ((k + scroll) % rows) / rows;
                float y = hz + (h - hz) * (float) Math.pow(z, 2.3);
                int al = (int) (200 * Math.min(1f, z * 1.6f) * flash);
                lineGlow.setColor(withA(cyan, al / 4));
                line.setColor(withA(cyan, al));
                c.drawLine(0, y, w, y, lineGlow);
                c.drawLine(0, y, w, y, line);
            }
            // horizon haze + focus glow
            glow.draw(c, w / 2f, hz, w * 0.6f, 0xFFFF3D7F, 70);
            glow.draw(c, in.fx * w, in.fy * h, 220, in.accent, (int) (40 + 50 * in.boost));
        }
    }

    // ------------------------------------------------------------------
    // 3. Hyperspace
    // ------------------------------------------------------------------

    static final class Warp extends Theme {
        String id() { return "warp"; }
        static final int N = 280;
        final float[] x = new float[N], y = new float[N], z = new float[N];
        final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint flash = new Paint();
        Glow glow;
        float cx, cy, speed;

        void setup() {
            for (int i = 0; i < N; i++) respawn(i, rnd.nextFloat());
            star.setStrokeCap(Paint.Cap.ROUND);
            glow = new Glow(0.3f, 80);
            cx = w / 2f; cy = h / 2f;
        }

        void respawn(int i, float zz) {
            x[i] = (rnd.nextFloat() * 2 - 1) * 1.2f;
            y[i] = (rnd.nextFloat() * 2 - 1) * 0.8f;
            z[i] = 0.05f + zz * 0.95f;
        }

        void draw(Canvas c, float t, float dt, Input in) {
            c.drawColor(0xFF02030A);
            float tx = w / 2f - (in.fx - 0.5f) * 220 - in.vx * 70;
            float ty = h / 2f - (in.fy - 0.5f) * 120 - in.vy * 50;
            cx += (tx - cx) * Math.min(1f, dt * 3f);
            cy += (ty - cy) * Math.min(1f, dt * 3f);
            float target = 0.10f + in.boost * 1.3f + in.pulse * 2.6f;
            speed += (target - speed) * Math.min(1f, dt * 5f);

            glow.draw(c, cx, cy, w * 0.5f, in.accent, (int) (60 + 80 * in.boost));
            glow.draw(c, cx, cy, w * 0.18f, BackgroundView.shiftHue(in.accent, 60), (int) (40 + 60 * in.pulse));

            float f = w * 0.42f;
            int tint = lerpColor(Color.WHITE, in.accent, 0.25f);
            for (int i = 0; i < N; i++) {
                float zPrev = z[i] + speed * 0.06f + 0.004f;
                z[i] -= dt * speed;
                if (z[i] <= 0.03f) { respawn(i, 1f); continue; }
                float sx = cx + x[i] / z[i] * f, sy = cy + y[i] / z[i] * f;
                if (sx < -50 || sx > w + 50 || sy < -50 || sy > h + 50) { respawn(i, 1f); continue; }
                float px = cx + x[i] / zPrev * f, py = cy + y[i] / zPrev * f;
                float near = 1f - z[i];
                star.setStrokeWidth(0.8f + near * 3.2f);
                star.setColor(withA(i % 3 == 0 ? tint : Color.WHITE, (int) (60 + 195 * near)));
                c.drawLine(px, py, sx, sy, star);
            }
            if (in.pulse > 0.6f) {
                flash.setColor(withA(Color.WHITE, (int) ((in.pulse - 0.6f) * 160)));
                c.drawPaint(flash);
            }
        }
    }

    // ------------------------------------------------------------------
    // 4. Aurora
    // ------------------------------------------------------------------

    static final class Aurora extends Theme {
        String id() { return "aurora"; }
        final Paint sky = new Paint(Paint.DITHER_FLAG);
        final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        final Paint mtn = new Paint(Paint.ANTI_ALIAS_FLAG), mtn2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        Path hills, hills2; Stars stars; Glow glow;
        float wind;
        static final int[] BASE = {0xFF3DFFB0, 0xFF2EE6E6, 0xFFA35CFF};

        void setup() {
            sky.setShader(vGrad(0, h, new int[]{0xFF020617, 0xFF06122B, 0xFF0B2A3A}, new float[]{0f, 0.6f, 1f}));
            hills = mountains(rnd, w, h * 0.86f, 150, 7);
            hills2 = mountains(rnd, w, h * 0.92f, 90, 11);
            mtn.setColor(0xFF050B14);
            mtn2.setColor(0xFF02050A);
            stars = new Stars(rnd, 110, 0.8f);
            glow = new Glow(0.4f, 80);
        }

        void draw(Canvas c, float t, float dt, Input in) {
            wind += dt * (0.35f + in.boost * 2.2f) + in.vx * dt * 1.5f;
            c.drawPaint(sky);
            stars.draw(c, w, h, t, (in.fx - 0.5f) * 30, 0.8f);
            float bright = 1f + in.pulse * 0.9f + in.boost * 0.3f;
            for (int b = 0; b < 3; b++) {
                int col = lerpColor(BASE[b], in.accent, 0.28f);
                float baseY = h * (0.42f + b * 0.08f);
                float amp = 55 + b * 12;
                float height = 230 - b * 30;
                path.reset();
                int seg = 48;
                for (int i = 0; i <= seg; i++) {
                    float xx = (float) i / seg * (w + 200) - 100;
                    float yy = baseY
                            + amp * (float) Math.sin(xx * 0.0042f + wind * (0.8f + b * 0.3f) + b * 1.7f)
                            + amp * 0.45f * (float) Math.sin(xx * 0.011f - wind * 1.3f + b);
                    if (i == 0) path.moveTo(xx, yy); else path.lineTo(xx, yy);
                }
                for (int i = seg; i >= 0; i--) {
                    float xx = (float) i / seg * (w + 200) - 100;
                    float yy = baseY - height
                            + amp * 0.8f * (float) Math.sin(xx * 0.0042f + wind * (0.8f + b * 0.3f) + b * 1.7f + 0.4f);
                    path.lineTo(xx, yy);
                }
                path.close();
                band.setShader(vGrad(baseY + amp, baseY - height - amp,
                        new int[]{withA(col, (int) Math.min(255, 150 * bright)), withA(col, (int) (60 * bright)), 0},
                        new float[]{0f, 0.35f, 1f}));
                c.drawPath(path, band);
            }
            glow.draw(c, in.fx * w, in.fy * h, 240, in.accent, (int) (35 + 50 * in.boost));
            c.save();
            c.translate(-(in.fx - 0.5f) * 25, 0);
            c.drawPath(hills, mtn);
            c.restore();
            c.save();
            c.translate(-(in.fx - 0.5f) * 55, 0);
            c.drawPath(hills2, mtn2);
            c.restore();
        }
    }

    // ------------------------------------------------------------------
    // 5. Deep Sea
    // ------------------------------------------------------------------

    static final class DeepSea extends Theme {
        String id() { return "deepsea"; }
        static final int N = 46, BURST = 40;
        final float[] bx = new float[N], by = new float[N], br = new float[N], bs = new float[N], bw = new float[N];
        final float[] qx = new float[BURST], qy = new float[BURST], qvx = new float[BURST], qvy = new float[BURST],
                qr = new float[BURST], ql = new float[BURST];
        final Paint water = new Paint(Paint.DITHER_FLAG), ray = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        final Paint bub = new Paint(Paint.ANTI_ALIAS_FLAG), shine = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path rayPath = new Path();
        Stars plankton; Glow glow;
        int burstNext;

        void setup() {
            water.setShader(vGrad(0, h, new int[]{0xFF0A4A63, 0xFF03263A, 0xFF010A12}, new float[]{0f, 0.45f, 1f}));
            ray.setShader(vGrad(0, h * 0.9f, new int[]{0x33FFFFFF, 0x00FFFFFF}, null));
            for (int i = 0; i < N; i++) spawn(i, rnd.nextFloat() * h);
            bub.setStyle(Paint.Style.STROKE);
            bub.setStrokeWidth(1.6f);
            shine.setColor(Color.WHITE);
            plankton = new Stars(rnd, 70, 1f);
            glow = new Glow(0.4f, 80);
        }

        void spawn(int i, float y) {
            bx[i] = rnd.nextFloat() * w; by[i] = y; br[i] = 3 + rnd.nextFloat() * 11;
            bs[i] = 30 + rnd.nextFloat() * 70; bw[i] = rnd.nextFloat() * 6.28f;
        }

        void draw(Canvas c, float t, float dt, Input in) {
            c.drawPaint(water);
            // light rays swaying from the surface
            for (int i = 0; i < 6; i++) {
                float x0 = w * (0.1f + i * 0.17f) + 40 * (float) Math.sin(t * 0.25f + i) - (in.fx - 0.5f) * 40;
                float spread = 120 + 40 * (float) Math.sin(t * 0.4f + i * 2);
                rayPath.reset();
                rayPath.moveTo(x0 - 18, 0);
                rayPath.lineTo(x0 + 18, 0);
                rayPath.lineTo(x0 + spread + 160, h);
                rayPath.lineTo(x0 - spread + 160, h);
                rayPath.close();
                c.drawPath(rayPath, ray);
            }
            glow.draw(c, w * 0.5f, -80, w * 0.6f, 0xFF6FE6FF, 70);
            plankton.draw(c, w, h, t, (in.fx - 0.5f) * 40 + in.vx * 10, 0.35f);
            glow.draw(c, in.fx * w, in.fy * h, 250, lerpColor(0xFF2BD9FF, in.accent, 0.5f), (int) (40 + 60 * in.boost));

            float drift = in.vx * 60f;
            for (int i = 0; i < N; i++) {
                by[i] -= bs[i] * dt * (1f + in.boost * 0.8f);
                bx[i] += ((float) Math.sin(t * 1.3f + bw[i]) * 14f + drift) * dt;
                if (by[i] < -20) spawn(i, h + 20);
                if (bx[i] < -20) bx[i] += w + 40; else if (bx[i] > w + 20) bx[i] -= w + 40;
                drawBubble(c, bx[i], by[i], br[i], 140);
            }
            if (in.pulse > 0.98f) {
                for (int k = 0; k < 14; k++) {
                    int j = burstNext++ % BURST;
                    qx[j] = in.px * w; qy[j] = in.py * h;
                    double ang = rnd.nextFloat() * Math.PI * 2;
                    float sp = 80 + rnd.nextFloat() * 220;
                    qvx[j] = (float) Math.cos(ang) * sp; qvy[j] = (float) Math.sin(ang) * sp - 60;
                    qr[j] = 3 + rnd.nextFloat() * 8; ql[j] = 1f;
                }
            }
            for (int j = 0; j < BURST; j++) {
                if (ql[j] <= 0) continue;
                ql[j] -= dt * 0.8f;
                qvy[j] -= 120 * dt;
                qvx[j] *= 0.97f;
                qx[j] += qvx[j] * dt; qy[j] += qvy[j] * dt;
                drawBubble(c, qx[j], qy[j], qr[j], (int) (220 * Math.max(0, ql[j])));
            }
        }

        void drawBubble(Canvas c, float x, float y, float r, int a) {
            bub.setColor(withA(0xFFBFF4FF, a));
            c.drawCircle(x, y, r, bub);
            shine.setAlpha(Math.min(255, a));
            c.drawCircle(x - r * 0.35f, y - r * 0.35f, Math.max(1f, r * 0.22f), shine);
        }
    }

    // ------------------------------------------------------------------
    // 6. Ember Forge
    // ------------------------------------------------------------------

    static final class Forge extends Theme {
        String id() { return "forge"; }
        static final int N = 170;
        final float[] x = new float[N], y = new float[N], vx = new float[N], vy = new float[N], life = new float[N], size = new float[N];
        final Paint bg = new Paint(Paint.DITHER_FLAG), ember = new Paint(Paint.ANTI_ALIAS_FLAG);
        Glow glow;
        int next;

        void setup() {
            bg.setShader(vGrad(0, h, new int[]{0xFF070302, 0xFF140804, 0xFF3A1206}, new float[]{0f, 0.6f, 1f}));
            for (int i = 0; i < N; i++) { spawn(i, w * rnd.nextFloat(), h + rnd.nextFloat() * h, false); life[i] = rnd.nextFloat(); }
            glow = new Glow(0.35f, 90);
        }

        void spawn(int i, float sx, float sy, boolean burst) {
            x[i] = sx; y[i] = sy;
            if (burst) {
                double a = rnd.nextFloat() * Math.PI * 2;
                float sp = 120 + rnd.nextFloat() * 260;
                vx[i] = (float) Math.cos(a) * sp; vy[i] = (float) Math.sin(a) * sp - 80;
            } else {
                vx[i] = (rnd.nextFloat() - 0.5f) * 30; vy[i] = -(40 + rnd.nextFloat() * 90);
            }
            life[i] = 1f; size[i] = 1.2f + rnd.nextFloat() * 2.6f;
        }

        void draw(Canvas c, float t, float dt, Input in) {
            c.drawPaint(bg);
            float heat = 1f + in.boost * 0.8f + in.pulse;
            glow.draw(c, w * 0.5f - (in.fx - 0.5f) * 80, h + 60, w * 0.75f, 0xFFFF5A1F, (int) (150 * Math.min(1.4f, heat)));
            glow.draw(c, w * 0.2f, h + 40, w * 0.4f, 0xFFFF8C2B, 90);
            glow.draw(c, in.fx * w, in.fy * h, 240, lerpColor(0xFFFF7A2B, in.accent, 0.4f), (int) (40 + 60 * in.boost));

            if (in.pulse > 0.98f) {
                for (int k = 0; k < 26; k++) spawn(next++ % N, in.px * w, in.py * h, true);
            }
            float wind = in.vx * 80f;
            for (int i = 0; i < N; i++) {
                life[i] -= dt * (0.18f + in.boost * 0.1f);
                vx[i] += ((float) Math.sin(t * 2 + i) * 20f + wind - vx[i] * 0.5f) * dt;
                vy[i] -= 10 * dt;
                x[i] += vx[i] * dt * (1f + in.boost * 0.6f);
                y[i] += vy[i] * dt * (1f + in.boost * 0.6f);
                if (life[i] <= 0 || y[i] < -20) { spawn(i, w * rnd.nextFloat(), h + 10, false); continue; }
                float flick = 0.7f + 0.3f * (float) Math.sin(t * 13 + i * 3);
                int col = lerpColor(0xFFFF4A12, 0xFFFFD36B, life[i]);
                float a = life[i] * flick * Math.min(1.3f, heat);
                ember.setColor(withA(col, (int) (45 * a)));
                c.drawCircle(x[i], y[i], size[i] * 3.2f, ember);
                ember.setColor(withA(col, (int) (240 * Math.min(1f, a))));
                c.drawCircle(x[i], y[i], size[i], ember);
            }
        }
    }
}
