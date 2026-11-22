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
            case "sakura": return 0xFFFF8FB8;
            case "neon": return 0xFF2BF0FF;
            case "library": return 0xFFFFB45A;
            case "cathedral": return 0xFF8A6CFF;
            case "fireflies": return 0xFFC8FF5A;
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
            case "sakura": c = new int[]{0xFF1A1450, 0xFF6E3A7E, 0xFFF0A0B0}; break;
            case "neon": c = new int[]{0xFF05060F, 0xFF1A1440, 0xFF0A0B18}; break;
            case "library": c = new int[]{0xFF1C0F08, 0xFF2E1A0E, 0xFF120804}; break;
            case "cathedral": c = new int[]{0xFF0B0B14, 0xFF1A1830, 0xFF08080C}; break;
            case "fireflies": c = new int[]{0xFF030812, 0xFF0F2A32, 0xFF040B10}; break;
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
            case "sakura": {
                q.setColor(0xFFFFF4EA);
                c.drawCircle(r.left + w * 0.7f, r.top + h * 0.32f, h * 0.17f, q);
                q.setColor(0xFF1A0F2E);
                for (int i = 0; i < 9; i++) c.drawRect(r.left + i * w / 9f, r.bottom - h * (0.18f + (i * 37 % 20) / 100f), r.left + (i + 1) * w / 9f - d, r.bottom, q);
                q.setColor(0xFFFFB7CC);
                for (int i = 0; i < 14; i++) c.drawOval(r.left + w * ((i * 29 % 100) / 100f), r.top + h * ((i * 43 % 70) / 100f),
                        r.left + w * ((i * 29 % 100) / 100f) + d * 6, r.top + h * ((i * 43 % 70) / 100f) + d * 3.5f, q);
                break;
            }
            case "neon": {
                q.setColor(0xFF0C0F24);
                for (int i = 0; i < 7; i++) c.drawRect(r.left + i * w / 7f, r.top + h * (0.25f + (i * 31 % 30) / 100f), r.left + (i + 1) * w / 7f - d, r.bottom, q);
                int[] cols = {0xFFFF2BD6, 0xFF2BF0FF, 0xFFFFB02B};
                for (int i = 0; i < 3; i++) {
                    q.setColor(cols[i]);
                    float x = r.left + w * (0.18f + i * 0.3f);
                    c.drawRect(x, r.top + h * 0.3f, x + d * 5, r.top + h * 0.6f, q);
                }
                q.setColor(0x70CFE4FF);
                q.setStrokeWidth(d * 0.8f);
                for (int i = 0; i < 16; i++) { float x = r.left + w * ((i * 23 % 100) / 100f), y = r.top + h * ((i * 41 % 80) / 100f);
                    c.drawLine(x, y, x - d * 2, y + d * 8, q); }
                break;
            }
            case "library": {
                int[] sp = {0xFF6B1E1E, 0xFF1F3B2B, 0xFF5A3A1A, 0xFF1A2846, 0xFF7A5A2A};
                for (int row = 0; row < 3; row++) {
                    float y = r.top + h * (0.3f + row * 0.25f);
                    for (int i = 0; i < 18; i++) {
                        if (i > 6 && i < 11) continue;
                        q.setColor(sp[(i + row) % sp.length]);
                        c.drawRect(r.left + i * w / 18f, y - h * 0.18f, r.left + (i + 1) * w / 18f - d * 0.6f, y, q);
                    }
                }
                q.setColor(0xFF22345C);
                c.drawRoundRect(r.left + w * 0.4f, r.top + h * 0.1f, r.left + w * 0.6f, r.top + h * 0.75f, d * 8, d * 8, q);
                q.setColor(0x60FFC870);
                c.drawCircle(r.left + w * 0.66f, r.top + h * 0.72f, h * 0.15f, q);
                break;
            }
            case "cathedral": {
                float cx = r.centerX(), cy = r.top + h * 0.38f, R = h * 0.26f;
                int[] g = {0xFF2448C8, 0xFFC8183C, 0xFFF0B22A, 0xFF7038C8};
                for (int s2 = 0; s2 < 12; s2++) { q.setColor(g[s2 % 4]); c.drawArc(cx - R, cy - R, cx + R, cy + R, s2 * 30, 30, true, q); }
                q.setColor(0xFFFFD25A);
                c.drawCircle(cx, cy, R * 0.25f, q);
                q.setColor(0xFF2A2838);
                c.drawRect(r.left + w * 0.06f, r.top, r.left + w * 0.16f, r.bottom, q);
                c.drawRect(r.right - w * 0.16f, r.top, r.right - w * 0.06f, r.bottom, q);
                break;
            }
            case "fireflies": {
                q.setColor(0xFF0A1B22);
                for (int i = 0; i < 8; i++) {
                    float x = r.left + (i + 0.5f) * w / 8f, top = r.top + h * (0.25f + (i * 37 % 25) / 100f);
                    android.graphics.Path p = new android.graphics.Path();
                    p.moveTo(x, top); p.lineTo(x + w * 0.07f, r.bottom); p.lineTo(x - w * 0.07f, r.bottom); p.close();
                    c.drawPath(p, q);
                }
                for (int i = 0; i < 12; i++) {
                    float x = r.left + w * ((i * 31 % 100) / 100f), y = r.top + h * (0.3f + (i * 47 % 50) / 100f);
                    q.setColor(0x60D8FF6A);
                    c.drawCircle(x, y, d * 4, q);
                    q.setColor(0xFFF4FFC8);
                    c.drawCircle(x, y, d * 1.2f, q);
                }
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
