package com.vyd02.overdrive;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.view.Choreographer;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Animated background, drawn on its own thread and locked to the TV's refresh signal (vsync)
 * the same way the system UI draws. Every frame uses the display's own timestamp, so motion
 * is perfectly even. If the TV ever struggles, it lowers the background's resolution instead
 * of dropping frames, so it stays at full frame rate.
 */
public class ThemeView extends SurfaceView implements SurfaceHolder.Callback, Choreographer.FrameCallback {

    static final int RW = 1280, RH = 720;                       // the themes' drawing coordinates
    // 1280x720 is the sweet spot: soft glows look identical to 1080p at less than half the work
    private static final int[][] TIERS = {{1280, 720}, {1088, 612}, {960, 540}};

    private volatile Themes.Theme theme;
    private final Themes.Input input = new Themes.Input();
    private final Handler main = new Handler(Looper.getMainLooper());

    private HandlerThread thread;
    private Handler render;
    private Choreographer choreo;
    private volatile boolean alive;
    private volatile int surfW = TIERS[0][0];
    private int tier = 0;

    // frame timing (render thread only)
    private long startNs, lastNs;
    private long vsyncNs = 16_666_667L;
    private int windowFrames, windowJank, calmFrames;
    private int sinceUpgrade = Integer.MAX_VALUE / 2;
    private boolean noMoreUpgrades;

    public ThemeView(Context c, String themeId) {
        super(c);
        getHolder().setFormat(PixelFormat.RGBA_8888);
        getHolder().setFixedSize(TIERS[0][0], TIERS[0][1]);
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

    public void focusAt(float nx, float ny, int dx, int dy) {
        input.tfx = nx;
        input.tfy = ny;
        if (dx != 0) input.kickXa.addAndGet(dx);
        if (dy != 0) input.kickYa.addAndGet(dy);
    }

    public void select() { input.selectsA.incrementAndGet(); }

    private volatile boolean paused;
    /** Stop drawing (e.g. while the intro covers the screen) so the TV's graphics chip has all its power for that. */
    public void setPaused(boolean p) { paused = p; if (!p) lastNs = 0; }

    public void setAccent(int color) { input.targetAccent = color; }

    // ---- surface lifecycle ----

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        if (getDisplay() != null) {
            float hz = getDisplay().getRefreshRate();
            if (hz > 20f) vsyncNs = (long) (1_000_000_000L / hz);
        }
        alive = true;
        thread = new HandlerThread("overdrive-theme", Process.THREAD_PRIORITY_DEFAULT);
        thread.start();
        render = new Handler(thread.getLooper());
        render.post(() -> {
            choreo = Choreographer.getInstance(); // bound to this thread's looper
            lastNs = 0;
            choreo.postFrameCallback(this);
        });
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int format, int w, int hh) { surfW = w; }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        alive = false;
        if (render != null) {
            render.post(() -> { if (choreo != null) choreo.removeFrameCallback(this); });
        }
        if (thread != null) {
            thread.quitSafely();
            try { thread.join(500); } catch (InterruptedException ignored) { }
            thread = null;
        }
    }

    // ---- one frame per vsync ----

    @Override
    public void doFrame(long frameNs) {
        if (!alive) return;
        choreo.postFrameCallback(this); // book the next vsync first
        if (paused) return;

        if (lastNs == 0) { lastNs = frameNs; startNs = frameNs; }
        long interval = frameNs - lastNs;
        lastNs = frameNs;
        float dt = Math.min(0.05f, Math.max(0f, interval / 1_000_000_000f));
        trackSmoothness(interval);
        input.step(dt);

        SurfaceHolder h = getHolder();
        Canvas c = null;
        try {
            if (!h.getSurface().isValid()) return;
            c = Build.VERSION.SDK_INT >= 26 ? h.lockHardwareCanvas() : h.lockCanvas();
            if (c == null) return;
            float scale = (float) surfW / RW;
            c.save();
            c.scale(scale, scale);
            Themes.Theme t = theme;
            if (t != null) t.draw(c, (frameNs - startNs) / 1_000_000_000f, dt, input);
            c.restore();
        } catch (Exception ignored) {
            // surface can be swapped out while the TV changes screens
        } finally {
            if (c != null) {
                try { h.unlockCanvasAndPost(c); } catch (Exception ignored) { }
            }
        }
    }

    /** If more than ~8% of frames arrive late, step the background down a size; recover after a long calm. */
    private void trackSmoothness(long interval) {
        if (interval <= 0) return;
        boolean late = interval > vsyncNs * 3 / 2;
        windowFrames++;
        if (late) windowJank++;
        calmFrames = late ? 0 : calmFrames + 1;
        sinceUpgrade++;
        if (windowFrames >= 180) {
            if (windowJank > 14 && tier < TIERS.length - 1) {
                if (sinceUpgrade < 60 * 15) noMoreUpgrades = true; // sharper size didn't hold: stay put
                setTier(tier + 1);
            }
            windowFrames = 0;
            windowJank = 0;
        }
        if (!noMoreUpgrades && calmFrames > 60 * 30 && tier > 0) { // 30 calm seconds: try the sharper size again
            calmFrames = 0;
            sinceUpgrade = 0;
            setTier(tier - 1);
        }
    }

    private void setTier(int t) {
        tier = t;
        final int w = TIERS[t][0], hh = TIERS[t][1];
        main.post(() -> getHolder().setFixedSize(w, hh));
    }
}
