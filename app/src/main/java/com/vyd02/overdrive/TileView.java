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
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * One app tile. On focus it pops forward, gets a glowing ring in the app's own
 * color, and a light sheen sweeps across it every few seconds.
 */
public class TileView extends View {

    public interface Painter { void paint(Canvas c, RectF r, float focus); }

    private final float d;
    private Bitmap art;          // banner art (fills tile)
    private Bitmap icon;         // fallback square icon
    private String label = "";
    private int accent = Color.parseColor("#3DA9FF");
    private Painter custom;      // for special tiles (settings)

    private float focus = 0f;    // 0..1
    private float sheen = -1f;   // sweep position, -1 = idle
    private ValueAnimator focusAnim, sheenAnim;
    private final Runnable sheenLoop = this::runSheen;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path();
    private final Matrix mx = new Matrix();
    private final float scaleUp;

    public TileView(Context c, float scaleUp) {
        super(c);
        this.scaleUp = scaleUp;
        d = c.getResources().getDisplayMetrics().density;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        ring.setStyle(Paint.Style.STROKE);
        glow.setStyle(Paint.Style.STROKE);
        text.setColor(Color.WHITE);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        dim.setColor(Color.BLACK);
    }

    public void setApp(AppRepo.App a) {
        label = a.label;
        art = a.banner != null ? toBitmap(a.banner, 320, 180) : null;
        icon = a.icon != null ? toBitmap(a.icon, 192, 192) : null;
        accent = pickAccent(art != null ? art : icon);
        invalidate();
    }

    public void setCustom(String label, int accent, Painter p) {
        this.label = label;
        this.accent = accent;
        this.custom = p;
        invalidate();
    }

    public int getAccent() { return accent; }
    public String getLabel() { return label; }

    @Override
    protected void onFocusChanged(boolean gained, int dir, Rect prev) {
        super.onFocusChanged(gained, dir, prev);
        animateFocus(gained ? 1f : 0f);
        animate().scaleX(gained ? scaleUp : 1f).scaleY(gained ? scaleUp : 1f)
                .translationZ(gained ? 16 * d : 0)
                .setInterpolator(gained ? new OvershootInterpolator(1.6f) : new DecelerateInterpolator())
                .setDuration(gained ? 320 : 200).start();
        removeCallbacks(sheenLoop);
        if (gained) postDelayed(sheenLoop, 180);
        else if (sheenAnim != null) { sheenAnim.cancel(); sheen = -1f; }
    }

    private void animateFocus(float to) {
        if (focusAnim != null) focusAnim.cancel();
        focusAnim = ValueAnimator.ofFloat(focus, to);
        focusAnim.setDuration(240);
        focusAnim.addUpdateListener(a -> { focus = (float) a.getAnimatedValue(); invalidate(); });
        focusAnim.start();
    }

    private void runSheen() {
        if (!isFocused()) return;
        sheenAnim = ValueAnimator.ofFloat(-0.4f, 1.4f);
        sheenAnim.setDuration(900);
        sheenAnim.setInterpolator(new DecelerateInterpolator());
        sheenAnim.addUpdateListener(a -> { sheen = (float) a.getAnimatedValue(); invalidate(); });
        sheenAnim.start();
        postDelayed(sheenLoop, 3200);
    }

    /** Little "press" bounce when the tile is selected. */
    public void pulse() {
        animate().scaleX(scaleUp * 0.92f).scaleY(scaleUp * 0.92f).setDuration(90)
                .withEndAction(() -> animate().scaleX(scaleUp).scaleY(scaleUp)
                        .setInterpolator(new OvershootInterpolator(3f)).setDuration(260).start())
                .start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float inset = 4 * d;
        r.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float rad = 10 * d;
        clip.reset();
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);

        // outer glow in app color
        if (focus > 0f) {
            for (int i = 3; i >= 1; i--) {
                glow.setStrokeWidth(i * 2.2f * d);
                glow.setColor(BackgroundView.withAlpha(accent, (int) (focus * 70 / i)));
                c.drawRoundRect(r, rad, rad, glow);
            }
        }

        if (custom != null) {
            custom.paint(c, r, focus);
        } else if (art != null) {
            BitmapShader sh = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float s = Math.max(r.width() / art.getWidth(), r.height() / art.getHeight());
            mx.reset();
            mx.setScale(s, s);
            mx.postTranslate(r.left + (r.width() - art.getWidth() * s) / 2f,
                    r.top + (r.height() - art.getHeight() * s) / 2f);
            sh.setLocalMatrix(mx);
            fill.setShader(sh);
            c.drawRoundRect(r, rad, rad, fill);
            fill.setShader(null);
        } else {
            int dark = darken(accent, 0.35f);
            fill.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                    darken(accent, 0.75f), dark, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, rad, rad, fill);
            fill.setShader(null);
            float isz = r.height() * 0.46f;
            if (icon != null) {
                Rect dst = new Rect((int) (r.centerX() - isz / 2), (int) (r.top + r.height() * 0.14f),
                        (int) (r.centerX() + isz / 2), (int) (r.top + r.height() * 0.14f + isz));
                c.drawBitmap(icon, null, dst, fill);
            }
            text.setTextSize(Math.max(9 * d, r.height() * 0.13f));
            CharSequence t = TextUtils.ellipsize(label, text, r.width() - 12 * d, TextUtils.TruncateAt.END);
            float tw = text.measureText(t, 0, t.length());
            c.drawText(t, 0, t.length(), r.centerX() - tw / 2f, r.bottom - r.height() * 0.12f, text);
        }

        // dim unfocused tiles so the focused one pops
        dim.setAlpha((int) (80 * (1f - focus)));
        c.drawRoundRect(r, rad, rad, dim);

        // sheen sweep
        if (sheen > -1f && focus > 0.5f) {
            float x = r.left + r.width() * sheen;
            float band = r.width() * 0.35f;
            sheenPaint.setShader(new LinearGradient(x - band, r.top, x + band, r.bottom,
                    new int[]{0, Color.argb(70, 255, 255, 255), 0}, null, Shader.TileMode.CLAMP));
            c.save();
            c.clipPath(clip);
            c.drawRect(r, sheenPaint);
            c.restore();
        }

        // bright ring
        if (focus > 0f) {
            ring.setStrokeWidth(2.5f * d);
            ring.setColor(Color.argb((int) (255 * focus), 255, 255, 255));
            c.drawRoundRect(r, rad, rad, ring);
        }
    }

    // ---------- helpers ----------

    static Bitmap toBitmap(Drawable dr, int w, int h) {
        if (dr instanceof BitmapDrawable && ((BitmapDrawable) dr).getBitmap() != null) {
            return ((BitmapDrawable) dr).getBitmap();
        }
        int iw = dr.getIntrinsicWidth() > 0 ? dr.getIntrinsicWidth() : w;
        int ih = dr.getIntrinsicHeight() > 0 ? dr.getIntrinsicHeight() : h;
        float s = Math.min(1f, Math.min(2f * w / iw, 2f * h / ih)); // cap size, keep aspect
        iw = (int) (iw * s);
        ih = (int) (ih * s);
        Bitmap b = Bitmap.createBitmap(Math.max(1, iw), Math.max(1, ih), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        dr.setBounds(0, 0, iw, ih);
        dr.draw(c);
        return b;
    }

    /** Picks the most vivid color in the art so the glow matches the app. */
    static int pickAccent(Bitmap b) {
        if (b == null) return Color.parseColor("#3DA9FF");
        float bestScore = -1f;
        float rs = 0, gs = 0, bs = 0, ws = 0;
        float[] hsv = new float[3];
        int step = 12;
        for (int yi = 0; yi < step; yi++) {
            for (int xi = 0; xi < step; xi++) {
                int px = b.getPixel(xi * (b.getWidth() - 1) / (step - 1), yi * (b.getHeight() - 1) / (step - 1));
                if (Color.alpha(px) < 128) continue;
                Color.colorToHSV(px, hsv);
                float w = hsv[1] * hsv[1] * hsv[2];
                if (w > bestScore) bestScore = w;
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
