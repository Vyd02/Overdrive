package com.vyd02.overdrive;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/** Mini still pictures of each theme, for the theme cards in Settings. */
final class ThemeArt {
    private ThemeArt() { }

    static int accent(String id) {
        switch (id) {
            case "synthwave": return 0xFFFF3D9A;
            case "warp": return 0xFF9AB8FF;
            case "aurora": return 0xFF3DFFB0;
            case "deepsea": return 0xFF2BD9FF;
            case "forge": return 0xFFFF7A2B;
            default: return 0xFF7C6CFF;
        }
    }

    static void bg(Paint p, String id, RectF r) {
        int[] c;
        switch (id) {
            case "synthwave": c = new int[]{0xFF1A0638, 0xFF6A1B6B, 0xFFFF6A3D}; break;
            case "warp": c = new int[]{0xFF02030A, 0xFF0A0F2A, 0xFF02030A}; break;
            case "aurora": c = new int[]{0xFF020617, 0xFF06122B, 0xFF0B2A3A}; break;
            case "deepsea": c = new int[]{0xFF0A4A63, 0xFF03263A, 0xFF010A12}; break;
            case "forge": c = new int[]{0xFF070302, 0xFF1A0A05, 0xFF4A1808}; break;
            default: c = new int[]{0xFF0A0F1F, 0xFF221A4A, 0xFF05070F};
        }
        p.setShader(new LinearGradient(r.left, r.top, r.left, r.bottom, c, null, Shader.TileMode.CLAMP));
    }

    static void motif(Canvas c, String id, RectF r, Paint q, float d) {
        q.setShader(null);
        q.setStyle(Paint.Style.FILL);
        float w = r.width(), h = r.height();
        switch (id) {
            case "synthwave": {
                float hz = r.top + h * 0.55f;
                q.setColor(0xFFFFB347);
                c.drawCircle(r.centerX(), hz - h * 0.08f, h * 0.22f, q);
                q.setColor(0xFF2A0845);
                c.drawRect(r.left, hz, r.right, r.bottom - d * 14, q);
                q.setStyle(Paint.Style.STROKE);
                q.setStrokeWidth(d);
                q.setColor(0xCCFF2BD6);
                for (int i = -5; i <= 5; i++) c.drawLine(r.centerX() + i * 3 * d, hz, r.centerX() + i * w * 0.18f, r.bottom - d * 14, q);
                for (int k = 1; k < 4; k++) { float y = hz + (r.bottom - d * 14 - hz) * (k * k) / 9f; c.drawLine(r.left, y, r.right, y, q); }
                break;
            }
            case "warp":
                q.setStyle(Paint.Style.STROKE);
                q.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 18; i++) {
                    double a = i * 0.349 + 0.2;
                    float r1 = h * (0.12f + (i % 3) * 0.06f), r2 = r1 + h * (0.14f + (i % 4) * 0.05f);
                    q.setStrokeWidth(d * (1 + (i % 3) * 0.6f));
                    q.setColor(Color.argb(150 + (i % 3) * 35, 220, 230, 255));
                    c.drawLine(r.centerX() + (float) Math.cos(a) * r1, r.centerY() - d * 6 + (float) Math.sin(a) * r1,
                            r.centerX() + (float) Math.cos(a) * r2, r.centerY() - d * 6 + (float) Math.sin(a) * r2, q);
                }
                break;
            case "aurora":
                for (int b = 0; b < 3; b++) {
                    q.setColor(Color.argb(110, b == 2 ? 163 : 61, b == 2 ? 92 : 255, b == 1 ? 230 : 176));
                    float y = r.top + h * (0.25f + b * 0.12f);
                    c.drawOval(r.left - w * 0.2f, y, r.right + w * 0.1f, y + h * 0.2f, q);
                }
                q.setColor(0xFF02050A);
                c.drawRect(r.left, r.bottom - h * 0.3f, r.right, r.bottom, q);
                break;
            case "deepsea":
                q.setStyle(Paint.Style.STROKE);
                q.setStrokeWidth(d);
                q.setColor(0xAABFF4FF);
                float[][] bubbles = {{0.2f, 0.6f, 6}, {0.35f, 0.35f, 4}, {0.7f, 0.5f, 8}, {0.8f, 0.25f, 3}, {0.55f, 0.7f, 5}};
                for (float[] b : bubbles) c.drawCircle(r.left + w * b[0], r.top + h * b[1], d * b[2], q);
                break;
            case "forge":
                for (int i = 0; i < 16; i++) {
                    float x = r.left + w * ((i * 37 % 100) / 100f), y = r.top + h * (0.2f + (i * 53 % 60) / 100f);
                    q.setColor(Color.argb(70, 255, 120, 40));
                    c.drawCircle(x, y, d * 4, q);
                    q.setColor(0xFFFFC46B);
                    c.drawCircle(x, y, d * 1.4f, q);
                }
                break;
            default:
                q.setColor(Color.argb(120, 124, 108, 255));
                c.drawCircle(r.left + w * 0.7f, r.top + h * 0.35f, h * 0.35f, q);
                q.setColor(Color.argb(90, 61, 169, 255));
                c.drawCircle(r.left + w * 0.3f, r.top + h * 0.6f, h * 0.3f, q);
        }
        q.setStyle(Paint.Style.FILL);
    }
}
