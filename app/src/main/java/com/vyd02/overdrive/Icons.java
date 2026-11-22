package com.vyd02.overdrive;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Simple line icons drawn in code, so they stay razor sharp at any size. */
final class Icons {
    private Icons() { }

    static void draw(Canvas c, String name, float cx, float cy, float s, Paint p, Path path, RectF r, float d, float focus) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.6f * d, s * 0.11f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        switch (name) {
            case "wifi":
                for (int i = 1; i <= 3; i++) {
                    float rr = s * 0.42f * i;
                    r.set(cx - rr, cy + s * 0.7f - rr, cx + rr, cy + s * 0.7f + rr);
                    c.drawArc(r, 225, 90, false, p);
                }
                fill(p); c.drawCircle(cx, cy + s * 0.62f, s * 0.1f, p);
                break;
            case "inputs":
                r.set(cx - s * 0.6f, cy - s * 0.6f, cx + s, cy + s * 0.6f);
                c.drawRoundRect(r, s * 0.12f, s * 0.12f, p);
                c.drawLine(cx - s, cy, cx + s * 0.2f, cy, p);
                c.drawLine(cx - s * 0.1f, cy - s * 0.3f, cx + s * 0.2f, cy, p);
                c.drawLine(cx - s * 0.1f, cy + s * 0.3f, cx + s * 0.2f, cy, p);
                break;
            case "tv":
            case "display":
                r.set(cx - s, cy - s * 0.65f, cx + s, cy + s * 0.5f);
                c.drawRoundRect(r, s * 0.1f, s * 0.1f, p);
                c.drawLine(cx - s * 0.45f, cy + s * 0.8f, cx + s * 0.45f, cy + s * 0.8f, p);
                if (name.equals("display")) {
                    path.reset();
                    path.moveTo(cx - s * 0.3f, cy - s * 0.15f); path.lineTo(cx - s * 0.1f, cy - s * 0.15f);
                    path.lineTo(cx + s * 0.15f, cy - s * 0.35f); path.lineTo(cx + s * 0.15f, cy + s * 0.2f);
                    path.lineTo(cx - s * 0.1f, cy); path.lineTo(cx - s * 0.3f, cy); path.close();
                    c.drawPath(path, p);
                }
                break;
            case "person":
            case "access":
                c.drawCircle(cx, cy - s * 0.55f, s * 0.28f, p);
                if (name.equals("person")) {
                    r.set(cx - s * 0.7f, cy - s * 0.05f, cx + s * 0.7f, cy + s * 1.2f);
                    c.drawArc(r, 180, 180, false, p);
                } else {
                    c.drawLine(cx - s * 0.8f, cy - s * 0.1f, cx + s * 0.8f, cy - s * 0.1f, p);
                    c.drawLine(cx, cy - s * 0.15f, cx, cy + s * 0.3f, p);
                    c.drawLine(cx, cy + s * 0.3f, cx - s * 0.35f, cy + s, p);
                    c.drawLine(cx, cy + s * 0.3f, cx + s * 0.35f, cy + s, p);
                }
                break;
            case "apps":
                float q = s * 0.6f, g = s * 0.22f;
                for (int yy = 0; yy < 2; yy++) for (int xx = 0; xx < 2; xx++) {
                    float l = cx - q - g / 2 + xx * (q + g), t = cy - q - g / 2 + yy * (q + g);
                    if (xx == 1 && yy == 1) {
                        c.drawLine(l + q / 2, t + q * 0.1f, l + q / 2, t + q * 0.9f, p);
                        c.drawLine(l + q * 0.1f, t + q / 2, l + q * 0.9f, t + q / 2, p);
                    } else {
                        r.set(l, t, l + q, t + q);
                        c.drawRoundRect(r, s * 0.08f, s * 0.08f, p);
                    }
                }
                break;
            case "remote":
                r.set(cx - s * 0.32f, cy - s, cx + s * 0.32f, cy + s);
                c.drawRoundRect(r, s * 0.3f, s * 0.3f, p);
                c.drawCircle(cx, cy - s * 0.5f, s * 0.15f, p);
                fill(p);
                for (int i = 0; i < 3; i++) c.drawCircle(cx, cy + s * (0.05f + i * 0.28f), s * 0.06f, p);
                break;
            case "sliders":
                for (int i = 0; i < 3; i++) {
                    float y = cy - s * 0.6f + i * s * 0.6f;
                    c.drawLine(cx - s, y, cx + s, y, p);
                    float kx = cx + s * (i == 1 ? -0.4f : 0.4f);
                    fill(p); c.drawCircle(kx, y, s * 0.17f, p); stroke(p, s, d);
                }
                break;
            case "equipment":
                r.set(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s * 0.2f);
                c.drawRoundRect(r, s * 0.08f, s * 0.08f, p);
                r.set(cx - s, cy + s * 0.5f, cx - s * 0.2f, cy + s * 0.8f);
                c.drawRoundRect(r, s * 0.05f, s * 0.05f, p);
                r.set(cx + s * 0.2f, cy + s * 0.5f, cx + s, cy + s * 0.8f);
                c.drawRoundRect(r, s * 0.05f, s * 0.05f, p);
                break;
            case "antenna":
                c.drawLine(cx, cy - s * 0.2f, cx - s * 0.45f, cy + s, p);
                c.drawLine(cx, cy - s * 0.2f, cx + s * 0.45f, cy + s, p);
                c.drawLine(cx - s * 0.25f, cy + s * 0.45f, cx + s * 0.25f, cy + s * 0.45f, p);
                for (int i = 1; i <= 2; i++) {
                    float rr = s * 0.38f * i;
                    r.set(cx - rr, cy - s * 0.3f - rr, cx + rr, cy - s * 0.3f + rr);
                    c.drawArc(r, 200, 140, false, p);
                }
                break;
            case "bell":
                path.reset();
                path.moveTo(cx - s * 0.7f, cy + s * 0.5f);
                path.quadTo(cx - s * 0.55f, cy + s * 0.3f, cx - s * 0.55f, cy - s * 0.1f);
                path.quadTo(cx - s * 0.5f, cy - s * 0.85f, cx, cy - s * 0.85f);
                path.quadTo(cx + s * 0.5f, cy - s * 0.85f, cx + s * 0.55f, cy - s * 0.1f);
                path.quadTo(cx + s * 0.55f, cy + s * 0.3f, cx + s * 0.7f, cy + s * 0.5f);
                path.close();
                c.drawPath(path, p);
                c.drawLine(cx - s * 0.15f, cy + s * 0.8f, cx + s * 0.15f, cy + s * 0.8f, p);
                break;
            case "alexa":
                c.drawCircle(cx, cy, s * 0.85f, p);
                p.setStrokeWidth(p.getStrokeWidth() * 1.6f);
                r.set(cx - s * 0.5f, cy - s * 0.5f, cx + s * 0.5f, cy + s * 0.5f);
                c.drawArc(r, 120, 270, false, p);
                break;
            case "timer":
                c.drawCircle(cx, cy + s * 0.1f, s * 0.8f, p);
                c.drawLine(cx, cy + s * 0.1f, cx + s * 0.35f, cy - s * 0.25f, p);
                c.drawLine(cx - s * 0.2f, cy - s, cx + s * 0.2f, cy - s, p);
                break;
            case "help":
                c.drawCircle(cx, cy, s * 0.9f, p);
                r.set(cx - s * 0.32f, cy - s * 0.55f, cx + s * 0.32f, cy + s * 0.05f);
                c.drawArc(r, 180, 250, false, p);
                c.drawLine(cx, cy + s * 0.05f, cx, cy + s * 0.2f, p);
                fill(p); c.drawCircle(cx, cy + s * 0.48f, s * 0.08f, p);
                break;
            case "palette":
                c.drawCircle(cx, cy, s * 0.85f, p);
                fill(p);
                c.drawCircle(cx - s * 0.35f, cy - s * 0.3f, s * 0.13f, p);
                c.drawCircle(cx + s * 0.1f, cy - s * 0.45f, s * 0.13f, p);
                c.drawCircle(cx + s * 0.45f, cy - s * 0.1f, s * 0.13f, p);
                c.drawCircle(cx - s * 0.4f, cy + s * 0.2f, s * 0.13f, p);
                break;
            case "sound":
                path.reset();
                path.moveTo(cx - s * 0.9f, cy - s * 0.3f); path.lineTo(cx - s * 0.5f, cy - s * 0.3f);
                path.lineTo(cx, cy - s * 0.75f); path.lineTo(cx, cy + s * 0.75f);
                path.lineTo(cx - s * 0.5f, cy + s * 0.3f); path.lineTo(cx - s * 0.9f, cy + s * 0.3f); path.close();
                c.drawPath(path, p);
                for (int i = 1; i <= 2; i++) {
                    float rr = s * 0.35f * i;
                    r.set(cx + s * 0.05f - rr, cy - rr, cx + s * 0.05f + rr, cy + rr);
                    c.drawArc(r, -45, 90, false, p);
                }
                break;
            case "moon":
                path.reset();
                r.set(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s * 0.8f);
                path.addArc(r, 40, 280);
                c.drawArc(r, 60, 250, false, p);
                r.set(cx - s * 0.2f, cy - s * 0.95f, cx + s * 1.0f, cy + s * 0.35f);
                c.drawArc(r, 110, 140, false, p);
                break;
            case "power":
                r.set(cx - s * 0.75f, cy - s * 0.65f, cx + s * 0.75f, cy + s * 0.85f);
                c.drawArc(r, -60, 300, false, p);
                c.drawLine(cx, cy - s * 0.9f, cx, cy, p);
                break;
            case "eye":
                path.reset();
                path.moveTo(cx - s, cy);
                path.quadTo(cx, cy - s * 0.9f, cx + s, cy);
                path.quadTo(cx, cy + s * 0.9f, cx - s, cy);
                c.drawPath(path, p);
                c.drawCircle(cx, cy, s * 0.25f, p);
                break;
            case "cloud":
                path.reset();
                path.moveTo(cx - s * 0.7f, cy + s * 0.45f);
                path.lineTo(cx + s * 0.65f, cy + s * 0.45f);
                c.drawPath(path, p);
                r.set(cx - s, cy - s * 0.15f, cx - s * 0.3f, cy + s * 0.55f); c.drawArc(r, 90, 180, false, p);
                r.set(cx - s * 0.55f, cy - s * 0.7f, cx + s * 0.35f, cy + s * 0.2f); c.drawArc(r, 180, 160, false, p);
                r.set(cx + s * 0.15f, cy - s * 0.3f, cx + s, cy + s * 0.55f); c.drawArc(r, 270, 180, false, p);
                break;
            case "search":
                c.drawCircle(cx - s * 0.15f, cy - s * 0.15f, s * 0.55f, p);
                c.drawLine(cx + s * 0.25f, cy + s * 0.25f, cx + s * 0.75f, cy + s * 0.75f, p);
                break;
            case "play":
                fill(p);
                path.reset();
                path.moveTo(cx - s * 0.45f, cy - s * 0.65f);
                path.lineTo(cx + s * 0.65f, cy);
                path.lineTo(cx - s * 0.45f, cy + s * 0.65f);
                path.close();
                c.drawPath(path, p);
                break;
            case "dots":
                fill(p);
                for (int i = -1; i <= 1; i++) c.drawCircle(cx + i * s * 0.55f, cy, s * 0.14f, p);
                break;
            case "pin":
            case "pinned":
                path.reset();
                path.moveTo(cx, cy + s * 0.8f);
                path.cubicTo(cx - s * 1.1f, cy - s * 0.05f, cx - s * 0.55f, cy - s * 0.95f, cx, cy - s * 0.42f);
                path.cubicTo(cx + s * 0.55f, cy - s * 0.95f, cx + s * 1.1f, cy - s * 0.05f, cx, cy + s * 0.8f);
                path.close();
                if (name.equals("pinned")) fill(p);
                c.drawPath(path, p);
                break;
            case "back":
                c.drawLine(cx + s * 0.25f, cy - s * 0.55f, cx - s * 0.3f, cy, p);
                c.drawLine(cx - s * 0.3f, cy, cx + s * 0.25f, cy + s * 0.55f, p);
                break;
            case "chevron":
                c.drawLine(cx - s * 0.2f, cy - s * 0.5f, cx + s * 0.3f, cy, p);
                c.drawLine(cx + s * 0.3f, cy, cx - s * 0.2f, cy + s * 0.5f, p);
                break;
            case "info":
                c.drawCircle(cx, cy, s * 0.85f, p);
                c.drawLine(cx, cy - s * 0.1f, cx, cy + s * 0.45f, p);
                fill(p); c.drawCircle(cx, cy - s * 0.42f, s * 0.08f, p);
                break;
            case "home":
                path.reset();
                path.moveTo(cx - s * 0.8f, cy - s * 0.05f); path.lineTo(cx, cy - s * 0.8f); path.lineTo(cx + s * 0.8f, cy - s * 0.05f);
                c.drawPath(path, p);
                r.set(cx - s * 0.55f, cy - s * 0.25f, cx + s * 0.55f, cy + s * 0.75f);
                c.drawRoundRect(r, s * 0.08f, s * 0.08f, p);
                break;
            case "download":
                c.drawLine(cx, cy - s * 0.8f, cx, cy + s * 0.3f, p);
                c.drawLine(cx - s * 0.4f, cy - s * 0.1f, cx, cy + s * 0.3f, p);
                c.drawLine(cx + s * 0.4f, cy - s * 0.1f, cx, cy + s * 0.3f, p);
                c.drawLine(cx - s * 0.8f, cy + s * 0.8f, cx + s * 0.8f, cy + s * 0.8f, p);
                break;
            default:
                c.save();
                c.rotate(focus * 90f, cx, cy);
                c.drawCircle(cx, cy, s * 0.42f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    c.drawLine(cx + (float) Math.cos(a) * s * 0.62f, cy + (float) Math.sin(a) * s * 0.62f,
                            cx + (float) Math.cos(a) * s * 0.9f, cy + (float) Math.sin(a) * s * 0.9f, p);
                }
                c.restore();
        }
        stroke(p, s, d);
    }

    private static void fill(Paint p) { p.setStyle(Paint.Style.FILL); }

    private static void stroke(Paint p, float s, float d) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.6f * d, s * 0.11f));
    }
}
