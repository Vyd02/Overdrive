package com.vyd02.overdrive;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Choreographer;
import android.view.View;

/**
 * Animated background, drawn as part of the home screen's own frame.
 *
 * Measured on the Insignia Fire TV: when the background had its own surface, its frames
 * queued on the same graphics pipeline as the menus, so every move waited behind a
 * background frame (and the TV had to blend two full-screen layers each frame).
 * Drawing it in the same pass as the menus means one frame per refresh, in perfect step.
 */
public class ThemeView extends View implements Choreographer.FrameCallback {

    static final int RW = 1280, RH = 720; // the themes' drawing coordinates
    static final int DOWNSCALE = 2;       // the view is laid out at 1/2 size and scaled up

    private volatile Themes.Theme theme;
    private final Themes.Input input = new Themes.Input();
    private final Choreographer choreo = Choreographer.getInstance();

    private boolean running, paused;
    private long startNs, lastNs, frameNs;
    private float dt;

    // smoothness log for tuning over ADB
    private int perfFrames, perfLate;
    static boolean verbose;
    private long perfStartNs;

    public ThemeView(Context c, String themeId) {
        super(c);
        setWillNotDraw(false);
        setTheme(themeId);
    }

    public void setTheme(String id) { setTheme(id, false); }

    /** @param photo the theme's rendered picture is shown underneath, so only its moving light is drawn here */
    public void setTheme(String id, boolean photo) {
        Themes.Theme t = Themes.create(id);
        t.photo = photo;
        t.init(RW, RH);
        theme = t;
        invalidate();
    }

    public String themeId() { return theme.id(); }

    @Override public boolean hasOverlappingRendering() { return false; }
    @Override public boolean isOpaque() { return theme == null || !theme.photo; }

    // ---- interaction, called from the UI thread ----

    public void focusAt(float nx, float ny, int dx, int dy) {
        input.tfx = nx;
        input.tfy = ny;
        if (dx != 0) input.kickXa.addAndGet(dx);
        if (dy != 0) input.kickYa.addAndGet(dy);
    }

    public void select() { input.selectsA.incrementAndGet(); }

    /** Stop animating (intro, apps on top, screensaver). The last frame stays on screen. */
    public void setPaused(boolean p) {
        if (paused == p) return;
        paused = p;
        if (!p) { lastNs = 0; schedule(); }
    }

    public void setAccent(int color) { input.targetAccent = color; }

    // ---- frame loop ----

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        lastNs = 0;
        schedule();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        running = false;
        choreo.removeFrameCallback(this);
        scheduled = false;
    }

    @Override
    protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        if (v == VISIBLE) { lastNs = 0; schedule(); }
        else { choreo.removeFrameCallback(this); scheduled = false; }
    }

    private boolean scheduled;
    private void schedule() {
        if (scheduled || !running || paused) return;
        scheduled = true;
        choreo.postFrameCallback(this);
    }

    @Override
    public void doFrame(long ns) {
        scheduled = false;
        if (!running || paused || getWindowVisibility() != VISIBLE) return;
        if (lastNs == 0) { lastNs = ns; if (startNs == 0) startNs = ns; }
        long interval = ns - lastNs;
        lastNs = ns;
        frameNs = ns;
        dt = Math.min(0.05f, Math.max(0f, interval / 1_000_000_000f));
        input.step(dt);
        trackPerf(ns, interval);
        invalidate();
        schedule();
    }

    @Override
    protected void onDraw(Canvas c) {
        Themes.Theme t = theme;
        if (t == null) return;
        if (startNs == 0) startNs = System.nanoTime();
        long now = frameNs != 0 ? frameNs : System.nanoTime();
        float scale = (float) getWidth() / RW;
        int save = c.save();
        c.scale(scale, (float) getHeight() / RH);
        t.draw(c, (now - startNs) / 1_000_000_000f, paused ? 0f : dt, input);
        c.restoreToCount(save);
    }

    private void trackPerf(long ns, long interval) {
        if (interval <= 0) return;
        perfFrames++;
        if (interval > 25_000_000L) { perfLate++; if (verbose) android.util.Log.i("SoraPerf", "late frame " + interval / 1_000_000 + "ms"); }
        if (perfStartNs == 0) perfStartNs = ns;
        if (perfFrames >= 300) {
            android.util.Log.i("SoraPerf", "theme " + theme.id() + " frames=" + perfFrames + " late=" + perfLate
                    + " fps=" + (perfFrames * 1e9f / Math.max(1, ns - perfStartNs)));
            perfFrames = 0; perfLate = 0; perfStartNs = ns;
        }
    }
}
