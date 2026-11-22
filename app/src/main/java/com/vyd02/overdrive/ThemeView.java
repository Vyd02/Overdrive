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
        long last = SystemClock.uptimeMillis();
        long start = last;
        SurfaceHolder h = getHolder();
        float avg = 16f;          // running average frame time
        boolean steady30 = false;
        int frames = 0; // if the TV can't hold 60, lock to an even 30 instead of stuttering between them
        final float scale = (float) SW / RW;
        while (alive) {
            if (steady30) {
                long wait = 33 - (SystemClock.uptimeMillis() - last);
                if (wait > 0) SystemClock.sleep(wait);
            }
            long now = SystemClock.uptimeMillis();
            frames++;
            if (frames > 120) { // ignore warm-up frames
                avg = avg * 0.95f + Math.min(100, now - last) * 0.05f;
                if (!steady30 && avg > 21f) steady30 = true;
            }
            float dt = Math.min(0.05f, (now - last) / 1000f);
            last = now;
            input.step(dt);
            Canvas c = null;
            try {
                c = Build.VERSION.SDK_INT >= 26 ? h.lockHardwareCanvas() : h.lockCanvas();
                if (c == null) { SystemClock.sleep(16); continue; }
                c.save();
                c.scale(scale, scale);
                theme.draw(c, (now - start) / 1000f, dt, input);
                c.restore();
            } catch (Exception e) {
                SystemClock.sleep(16);
            } finally {
                if (c != null) {
                    try { h.unlockCanvasAndPost(c); } catch (Exception ignored) { }
                }
            }
            if (Build.VERSION.SDK_INT < 26) SystemClock.sleep(8); // software canvas: give the CPU room
        }
    }
}
