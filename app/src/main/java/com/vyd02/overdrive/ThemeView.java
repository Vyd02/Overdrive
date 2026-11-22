package com.vyd02.overdrive;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Animated background drawn on its own thread, so the menus never wait on it.
 * Renders at 1280x720 and lets the TV's hardware scaler stretch it for free.
 * Reacts to what you do: moving focus, selecting, and the color of the focused app.
 */
public class ThemeView extends SurfaceView implements SurfaceHolder.Callback {

    static final int RW = 1280, RH = 720;      // the themes' drawing coordinates
    static final int SW = 960, SH = 540;       // real surface size: 44% fewer pixels, scaled up free by the TV

    private volatile Themes.Theme theme;
    private final Themes.Input input = new Themes.Input();
    private Thread thread;
    private volatile boolean alive;

    public ThemeView(Context c, String themeId) {
        super(c);
        getHolder().setFormat(PixelFormat.RGBA_8888);
        getHolder().setFixedSize(SW, SH);
        getHolder().addCallback(this);
        setTheme(themeId);
    }

    public void setTheme(String id) {
        Themes.Theme t = Themes.create(id);
        t.init(RW, RH);
        theme = t;
    }

    public String themeId() { return theme.id(); }

    // ---- interaction, called from the UI thread ----

    /** Focus moved to a tile at (nx, ny) in 0..1 screen space, nudged in direction (dx, dy). */
    public void focusAt(float nx, float ny, int dx, int dy) {
        input.tfx = nx;
        input.tfy = ny;
        if (dx != 0) input.kickXa.addAndGet(dx);
        if (dy != 0) input.kickYa.addAndGet(dy);
    }

    public void select() { input.selectsA.incrementAndGet(); }

    public void setAccent(int color) { input.targetAccent = color; }

    // ---- render loop ----

    @Override public void surfaceCreated(SurfaceHolder h) { start(); }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) { }
    @Override public void surfaceDestroyed(SurfaceHolder h) { stop(); }

    private void start() {
        if (alive) return;
        alive = true;
        thread = new Thread(this::loop, "overdrive-theme");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    private void stop() {
        alive = false;
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) { }
            thread = null;
        }
    }

    private void loop() {
        final SurfaceHolder holder = getHolder();
        final long frame60 = 16_666_667L;
        final long frame30 = 33_333_333L;
        long nextFrame = System.nanoTime();
        final long animationStart = nextFrame;
        long lastFrame = nextFrame;
        long avgRenderNs = 4_000_000L;
        int samples = 0;
        int slowSamples = 0;
        int fastSamples = 0;
        boolean limited30 = false;
        final float scale = (float) SW / RW;

        while (alive) {
            long frameBudget = limited30 ? frame30 : frame60;
            long waitNs = nextFrame - System.nanoTime();
            if (waitNs > 0) {
                long waitMs = waitNs / 1_000_000L;
                int waitNanos = (int) (waitNs % 1_000_000L);
                try {
                    Thread.sleep(waitMs, waitNanos);
                } catch (InterruptedException ignored) {
                    if (!alive) break;
                }
            }

            long nowNs = System.nanoTime();
            long elapsedNs = Math.max(1L, nowNs - lastFrame);
            lastFrame = nowNs;
            float dt = Math.min(0.05f, elapsedNs / 1_000_000_000f);
            input.step(dt);

            Canvas canvas = null;
            long renderStart = System.nanoTime();
            try {
                if (holder.getSurface().isValid()) {
                    canvas = Build.VERSION.SDK_INT >= 26
                            ? holder.lockHardwareCanvas() : holder.lockCanvas();
                    if (canvas != null) {
                        canvas.save();
                        canvas.scale(scale, scale);
                        Themes.Theme current = theme;
                        if (current != null) current.draw(canvas,
                                (nowNs - animationStart) / 1_000_000_000f, dt, input);
                        canvas.restore();
                    }
                }
            } catch (Exception ignored) {
                // Surface can be recreated while the TV changes screens or resolution.
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas); } catch (Exception ignored) { }
                }
            }

            long renderNs = Math.max(1L, System.nanoTime() - renderStart);
            avgRenderNs = (avgRenderNs * 19L + Math.min(renderNs, 100_000_000L)) / 20L;
            if (++samples >= 120) {
                if (!limited30 && avgRenderNs > 20_000_000L) slowSamples++;
                else slowSamples = 0;
                if (limited30 && avgRenderNs < 12_000_000L) fastSamples++;
                else fastSamples = 0;

                // Require sustained pressure before changing cadence; avoid oscillation.
                if (!limited30 && slowSamples >= 3) {
                    limited30 = true;
                    fastSamples = 0;
                } else if (limited30 && fastSamples >= 5) {
                    limited30 = false;
                    slowSamples = 0;
                }
                samples = 0;
            }

            frameBudget = limited30 ? frame30 : frame60;
            nextFrame += frameBudget;
            long afterDraw = System.nanoTime();
            if (nextFrame < afterDraw - frameBudget) nextFrame = afterDraw + frameBudget;
        }
    }
}
