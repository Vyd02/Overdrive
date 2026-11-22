package com.vyd02.overdrive;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;

/** A plain, can't-fail screen that shows the last crash so it can be photographed. */
final class CrashScreen {

    private CrashScreen() { }

    /** The saved crash, if it happened in the last 10 minutes and hasn't been shown yet. */
    static String pending(Context c) {
        File f = new File(c.getFilesDir(), SoraApp.CRASH_FILE);
        if (!f.exists()) return null;
        if (System.currentTimeMillis() - f.lastModified() > 10 * 60 * 1000L) { f.delete(); return null; }
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    static void clear(Context c) { new File(c.getFilesDir(), SoraApp.CRASH_FILE).delete(); }

    /** Shows the error. "Start Sora" clears it and restarts the screen; "Safe start" also turns the intro off. */
    static void show(Activity a, String report) {
        float d = a.getResources().getDisplayMetrics().density;
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Color.parseColor("#0A0D18"));
        col.setPadding((int) (40 * d), (int) (28 * d), (int) (40 * d), (int) (20 * d));

        TextView title = new TextView(a);
        title.setText("Sora hit an error");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(title);
        TextView sub = new TextView(a);
        sub.setText("Take a photo of this screen and send it over, then choose an option below.");
        sub.setTextColor(Color.argb(190, 255, 255, 255));
        sub.setTextSize(14);
        sub.setPadding(0, (int) (6 * d), 0, (int) (12 * d));
        col.addView(sub);

        LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView start = button(a, "Start Sora", d);
        TextView safe = button(a, "Safe start (no intro)", d);
        buttons.addView(start);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.leftMargin = (int) (14 * d);
        buttons.addView(safe, sl);
        col.addView(buttons);

        ScrollView sv = new ScrollView(a);
        TextView body = new TextView(a);
        body.setText(report);
        body.setTextColor(Color.parseColor("#FFB4C0"));
        body.setTextSize(12);
        body.setTypeface(Typeface.MONOSPACE);
        body.setPadding(0, (int) (14 * d), 0, 0);
        sv.addView(body);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        start.setOnClickListener(v -> { clear(a); a.recreate(); });
        safe.setOnClickListener(v -> {
            clear(a);
            a.getSharedPreferences("overdrive", Context.MODE_PRIVATE).edit().putBoolean(SettingsHub.K_INTRO, false).apply();
            a.recreate();
        });
        a.setContentView(col);
        start.requestFocus();
    }

    private static TextView button(Activity a, String text, float d) {
        TextView b = new TextView(a);
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding((int) (20 * d), (int) (10 * d), (int) (20 * d), (int) (10 * d));
        b.setFocusable(true);
        b.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(22 * d);
        bg.setColor(Color.argb(40, 255, 255, 255));
        b.setBackground(bg);
        b.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Color.WHITE : Color.argb(40, 255, 255, 255));
            b.setTextColor(has ? Color.parseColor("#0A0D18") : Color.WHITE);
        });
        return b;
    }
}
