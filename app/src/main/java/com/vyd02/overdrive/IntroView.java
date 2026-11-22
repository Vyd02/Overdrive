package com.vyd02.overdrive;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Power-up intro: chevrons streak in, the name lights up letter by letter, a ring bursts, then it opens up. */
public class IntroView extends View {

    private static final String WORD = "OVERDRIVE";
    private final float d;
    private final Paint bg = new Paint();
    private final Paint chev = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chevGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letter = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint streak = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Path path = new Path();
    private float p; // 0..1
    private ValueAnimator anim;
    private Runnable onDone;
    private int accent = Color.parseColor("#3DA9FF");

    public IntroView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        bg.setColor(Color.parseColor("#03040A"));
        chev.setStyle(Paint.Style.STROKE);
        chev.setStrokeCap(Paint.Cap.ROUND);
        chev.setStrokeJoin(Paint.Join.ROUND);
        chevGlow.setStyle(Paint.Style.STROKE);
        chevGlow.setStrokeCap(Paint.Cap.ROUND);
        chevGlow.setStrokeJoin(Paint.Join.ROUND);
        letter.setColor(Color.WHITE);
        letter.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        letter.setTextAlign(Paint.Align.CENTER);
        ring.setStyle(Paint.Style.STROKE);
        setClickable(false);
    }

    public void play(int accentColor, Runnable done) {
        accent = accentColor;
        onDone = done;
        setVisibility(VISIBLE);
        setAlpha(1f);
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(2300);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> { p = (float) a.getAnimatedValue(); invalidate(); });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { finish(); }
        });
        anim.start();
    }

    /** Any button press jumps to the end. */
    public void skip() {
        if (anim != null && anim.isRunning() && p < 0.8f) anim.setCurrentPlayTime((long) (2300 * 0.8f));
    }

    public boolean isPlaying() { return getVisibility() == VISIBLE; }

    private void finish() {
        setVisibility(GONE);
        if (onDone != null) { Runnable r = onDone; onDone = null; r.run(); }
    }

    private static float seg(float p, float a, float b) {
        return Math.max(0f, Math.min(1f, (p - a) / (b - a)));
    }

    private static float easeOut(float x) { return 1f - (float) Math.pow(1f - x, 3); }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float out = easeOut(seg(p, 0.8f, 1f));          // final open-up
        bg.setAlpha((int) (255 * (1f - out)));
        c.drawRect(0, 0, w, h, bg);
        if (out >= 1f) return;

        float cx = w / 2f, cy = h / 2f;
        float scale = 1f + out * 0.35f;
        c.save();
        c.scale(scale, scale, cx, cy);
        int alpha = (int) (255 * (1f - out));

        // soft halo builds behind everything
        float hp = easeOut(seg(p, 0.05f, 0.6f));
        halo.setShader(new RadialGradient(cx, cy, Math.max(1f, w * 0.45f * hp),
                new int[]{Themes.withA(accent, (int) (110 * hp * (1 - out))), 0}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, w * 0.45f, halo);

        // chevrons slide in from the left
        float cp = easeOut(seg(p, 0f, 0.3f));
        float size = 64 * d;
        float textW = 360 * d;
        float chevX = cx - textW / 2f - size * 0.9f;
        for (int i = 0; i < 2; i++) {
            float off = (1f - cp) * (-w * 0.6f - i * 120 * d);
            float x = chevX + i * size * 0.55f + off;
            path.reset();
            path.moveTo(x - size * 0.5f, cy - size * 0.6f);
            path.lineTo(x, cy);
            path.lineTo(x - size * 0.5f, cy + size * 0.6f);
            chevGlow.setStrokeWidth(size * 0.5f);
            chevGlow.setColor(Themes.withA(accent, (int) (60 * cp * (1 - out))));
            c.drawPath(path, chevGlow);
            chev.setStrokeWidth(size * 0.18f);
            chev.setColor(Themes.withA(accent, alpha));
            c.drawPath(path, chev);
            // motion trail
            if (cp < 1f) {
                streak.setShader(new LinearGradient(x - size * 4, 0, x, 0, 0, Themes.withA(accent, 140),
                        Shader.TileMode.CLAMP));
                streak.setStrokeWidth(size * 0.08f);
                c.drawLine(x - size * 4, cy, x - size * 0.2f, cy, streak);
            }
        }

        // letters light up one by one
        letter.setTextSize(54 * d);
        float spacing = textW / WORD.length();
        float startX = cx - textW / 2f + spacing / 2f + size * 0.25f;
        for (int i = 0; i < WORD.length(); i++) {
            float lp = easeOut(seg(p, 0.22f + i * 0.035f, 0.40f + i * 0.035f));
            if (lp <= 0) continue;
            letter.setAlpha((int) (alpha * lp));
            c.drawText(String.valueOf(WORD.charAt(i)), startX + i * spacing, cy + 19 * d + (1 - lp) * 24 * d, letter);
        }

        // light streak sweeps across the word
        float sp = seg(p, 0.5f, 0.72f);
        if (sp > 0f && sp < 1f) {
            float sx = cx - textW + sp * textW * 2.2f;
            streak.setShader(new LinearGradient(sx - 90 * d, 0, sx + 90 * d, 0,
                    new int[]{0, Color.argb(170, 255, 255, 255), 0}, null, Shader.TileMode.CLAMP));
            streak.setStrokeWidth(80 * d);
            c.drawLine(sx - 90 * d, cy, sx + 90 * d, cy, streak);
        }

        // ring burst
        float rp = seg(p, 0.6f, 0.95f);
        if (rp > 0f) {
            ring.setStrokeWidth(6 * d * (1 - rp));
            ring.setColor(Themes.withA(accent, (int) (220 * (1 - rp))));
            c.drawCircle(cx, cy, 60 * d + rp * w * 0.5f, ring);
        }
        c.restore();
    }
}
