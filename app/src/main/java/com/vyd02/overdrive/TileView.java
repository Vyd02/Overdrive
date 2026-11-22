package com.vyd02.overdrive;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;

/**
 * One app tile. On focus it glides forward, gets a glowing ring in the app's own color,
 * and a light sheen sweeps across it every few seconds.
 * All shaders are built once per size, so animation frames only move and fade things.
 */
public class TileView extends View {

    public interface Painter { void paint(Canvas c, RectF r, float focus); }

    private static final PathInterpolator EASE_OUT = new PathInterpolator(0.2f, 0.9f, 0.25f, 1.08f);
    private static final PathInterpolator EASE_IN_OUT = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    private final float d;
    private final float scaleUp;
    private Bitmap art, icon;
    private String label = "";
    private int accent = Color.parseColor("#3DA9FF");
    private Painter custom;

    private float focus = 0f;
    private float sheen = -1f;
    private ValueAnimator focusAnim, sheenAnim;
    private final Runnable sheenLoop = this::runSheen;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF r = new RectF();
    private final Rect iconDst = new Rect();
    private final Matrix sheenMx = new Matrix();
    private Shader artShader, bgShader, sheenShader;
    private CharSequence shownLabel = "";
    private float labelX, labelY;
    private float rad;

    public TileView(Context c, float scaleUp) {
        super(c);
        this.scaleUp = scaleUp;
        d = c.getResources().getDisplayMetrics().density;
        rad = 10 * d;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2.5f * d);
        glow.setStyle(Paint.Style.STROKE);
        text.setColor(Color.WHITE);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        dim.setColor(Color.BLACK);
    }

    public void setApp(AppRepo.App a) {
        label = a.label;
        art = a.artBmp;
        icon = a.iconBmp;
        accent = a.accent;
        rebuild();
    }

    public void setCustom(String label, int accent, Painter p) {
        this.label = label;
        this.accent = accent;
        this.custom = p;
        rebuild();
    }

    /** Mirror another tile's look (used by the big spotlight card). */
    public void copyFrom(TileView o) {
        label = o.label; art = o.art; icon = o.icon; accent = o.accent; custom = o.custom;
        rebuild();
    }

    /** Spotlight card: always drawn bright, never dimmed. */
    public void setAlwaysLit() { focus = 1f; lit = true; invalidate(); }
    private boolean lit;

    public int getAccent() { return accent; }
    public String getLabel() { return label; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        rebuild();
    }

    /** Builds every shader / text position once for the current size. */
    private void rebuild() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float inset = 4 * d;
        r.set(inset, inset, w - inset, h - inset);

        artShader = null;
        if (art != null) {
            BitmapShader sh = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float s = Math.max(r.width() / art.getWidth(), r.height() / art.getHeight());
            Matrix m = new Matrix();
            m.setScale(s, s);
            m.postTranslate(r.left + (r.width() - art.getWidth() * s) / 2f,
                    r.top + (r.height() - art.getHeight() * s) / 2f);
            sh.setLocalMatrix(m);
            artShader = sh;
        }
        bgShader = new LinearGradient(r.left, r.top, r.right, r.bottom,
                darken(accent, 0.75f), darken(accent, 0.32f), Shader.TileMode.CLAMP);

        float isz = r.height() * 0.46f;
        iconDst.set((int) (r.centerX() - isz / 2), (int) (r.top + r.height() * 0.13f),
                (int) (r.centerX() + isz / 2), (int) (r.top + r.height() * 0.13f + isz));
        text.setTextSize(Math.max(9 * d, r.height() * 0.13f));
        shownLabel = TextUtils.ellipsize(label, text, r.width() - 12 * d, TextUtils.TruncateAt.END);
        labelX = r.centerX() - text.measureText(shownLabel, 0, shownLabel.length()) / 2f;
        labelY = r.bottom - r.height() * 0.12f;

        // sheen: a soft diagonal band built once, slid across with a matrix
        float band = r.width() * 0.35f;
        sheenShader = new LinearGradient(-band, 0, band, r.height() * 0.6f,
                new int[]{0, Color.argb(80, 255, 255, 255), 0}, null, Shader.TileMode.CLAMP);
        sheenPaint.setShader(sheenShader);
        invalidate();
    }

    @Override
    protected void onFocusChanged(boolean gained, int dir, Rect prev) {
        super.onFocusChanged(gained, dir, prev);
        animateFocus(gained ? 1f : 0f);
        animate().cancel();
        animate().scaleX(gained ? scaleUp : 1f).scaleY(gained ? scaleUp : 1f)
                .translationZ(gained ? 14 * d : 0)
                .setInterpolator(gained ? EASE_OUT : EASE_IN_OUT)
                .setDuration(gained ? 300 : 220).start();
        removeCallbacks(sheenLoop);
        if (gained) postDelayed(sheenLoop, 220);
        else if (sheenAnim != null) { sheenAnim.cancel(); sheen = -1f; }
    }

    private void animateFocus(float to) {
        if (lit) return;
        if (focusAnim != null) focusAnim.cancel();
        focusAnim = ValueAnimator.ofFloat(focus, to);
        focusAnim.setDuration(220);
        focusAnim.setInterpolator(EASE_IN_OUT);
        focusAnim.addUpdateListener(a -> { focus = (float) a.getAnimatedValue(); invalidate(); });
        focusAnim.start();
    }

    private void runSheen() {
        if (!isFocused()) return;
        sheenAnim = ValueAnimator.ofFloat(-0.4f, 1.4f);
        sheenAnim.setDuration(1000);
        sheenAnim.setInterpolator(new DecelerateInterpolator(1.4f));
        sheenAnim.addUpdateListener(a -> { sheen = (float) a.getAnimatedValue(); invalidate(); });
        sheenAnim.start();
        postDelayed(sheenLoop, 3400);
    }

    /** Little "press" bounce when the tile is selected. */
    public void pulse() {
        animate().cancel();
        animate().scaleX(scaleUp * 0.94f).scaleY(scaleUp * 0.94f).setDuration(90).setInterpolator(EASE_IN_OUT)
                .withEndAction(() -> animate().scaleX(scaleUp).scaleY(scaleUp)
                        .setInterpolator(EASE_OUT).setDuration(240).start())
                .start();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (r.isEmpty()) return;

        if (focus > 0.01f) {
            for (int i = 3; i >= 1; i--) {
                glow.setStrokeWidth(i * 2.4f * d);
                glow.setColor(BackgroundView.withAlpha(accent, (int) (focus * 75 / i)));
                c.drawRoundRect(r, rad, rad, glow);
            }
        }

        if (custom != null) {
            custom.paint(c, r, focus);
        } else if (artShader != null) {
            fill.setShader(artShader);
            c.drawRoundRect(r, rad, rad, fill);
        } else {
            fill.setShader(bgShader);
            c.drawRoundRect(r, rad, rad, fill);
            if (icon != null) c.drawBitmap(icon, null, iconDst, iconPaint);
            c.drawText(shownLabel, 0, shownLabel.length(), labelX, labelY, text);
        }

        int dimA = (int) (80 * (1f - focus));
        if (dimA > 0) {
            dim.setAlpha(dimA);
            c.drawRoundRect(r, rad, rad, dim);
        }

        if (sheen > -1f && focus > 0.5f) {
            sheenMx.setTranslate(r.left + r.width() * sheen, r.top);
            sheenShader.setLocalMatrix(sheenMx);
            c.drawRoundRect(r, rad, rad, sheenPaint); // anti-aliased, no hard clip edges
        }

        if (focus > 0.01f) {
            ring.setColor(Color.argb((int) (255 * focus), 255, 255, 255));
            c.drawRoundRect(r, rad, rad, ring);
        }
    }

    // ---------- helpers ----------

    /** Renders a drawable at (up to) the requested size. Real bitmaps are kept as-is, no resampling. */
    static Bitmap toBitmap(Drawable dr, int w, int h) {
        if (dr instanceof BitmapDrawable && ((BitmapDrawable) dr).getBitmap() != null) {
            Bitmap source = ((BitmapDrawable) dr).getBitmap();
            int sw = source.getWidth(), sh = source.getHeight();
            if (sw <= w && sh <= h) return source;
            float scale = Math.min((float) w / sw, (float) h / sh);
            int tw = Math.max(1, Math.round(sw * scale));
            int th = Math.max(1, Math.round(sh * scale));
            try {
                return Bitmap.createScaledBitmap(source, tw, th, true);
            } catch (OutOfMemoryError oom) {
                return source;
            }
        }
        int iw = dr.getIntrinsicWidth(), ih = dr.getIntrinsicHeight();
        float aspect = (iw > 0 && ih > 0) ? (float) iw / ih : (float) w / h;
        int bw, bh;
        if (aspect >= (float) w / h) { bw = w; bh = Math.max(1, Math.round(w / aspect)); }
        else { bh = h; bw = Math.max(1, Math.round(h * aspect)); }
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        dr.setBounds(0, 0, bw, bh);
        dr.draw(c);
        return b;
    }

    /** Picks the most vivid color in the art so the glow matches the app. */
    static int pickAccent(Bitmap b) {
        if (b == null) return Color.parseColor("#3DA9FF");
        if (Build.VERSION.SDK_INT >= 26 && b.getConfig() == Bitmap.Config.HARDWARE) {
            b = b.copy(Bitmap.Config.ARGB_8888, false);
            if (b == null) return Color.parseColor("#3DA9FF");
        }
        float rs = 0, gs = 0, bs = 0, ws = 0;
        float[] hsv = new float[3];
        int step = 12;
        for (int yi = 0; yi < step; yi++) {
            for (int xi = 0; xi < step; xi++) {
                int px = b.getPixel(xi * (b.getWidth() - 1) / (step - 1), yi * (b.getHeight() - 1) / (step - 1));
                if (Color.alpha(px) < 128) continue;
                Color.colorToHSV(px, hsv);
                float w = hsv[1] * hsv[1] * hsv[2];
                rs += Color.red(px) * w; gs += Color.green(px) * w; bs += Color.blue(px) * w; ws += w;
            }
        }
        if (ws < 0.5f) return Color.parseColor("#7A8BA8");
        int c = Color.rgb((int) (rs / ws), (int) (gs / ws), (int) (bs / ws));
        Color.colorToHSV(c, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.3f + 0.1f);
        hsv[2] = Math.max(0.75f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    static int darken(int c, float f) {
        return Color.rgb((int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }
}
