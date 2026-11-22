package com.vyd02.overdrive;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Overdrive's Settings: categories on the left, real controls on the right (switches, value
 * pickers, a volume slider, live theme cards), plus every Fire TV settings page one press away.
 */
public class SettingsHub extends FrameLayout {

    interface Host {
        void play(int sound);
        void tileFocused(View v, int accent);
        void banner(String msg);
        void closeHub();
        SharedPreferences prefs();
        String themeId();
        void previewTheme(String id);
        void commitTheme(String id);
        void homeChanged();
        void sub(String which); // hidden, weather, weatherAuto, guard, updates, intro, saver
        int hiddenCount();
    }

    static final String K_THEME = "theme", K_INTRO = "intro_on", K_SAVER = "saver_minutes",
            K_INPUTS = "show_inputs", K_QUICK = "show_quick", K_BACKDROP = "backdrop_on";

    private static final String[] CATS = {"Appearance", "Sound", "Home screen", "Power-up", "Weather", "Privacy", "Updates", "Fire TV", "About"};
    private static final String[] CAT_ICONS = {"palette", "sound", "home", "power", "cloud", "eye", "download", "tv", "info"};
    private static final String[] CAT_DESC = {
            "Themes, artwork and the startup intro.",
            "Interface sounds and TV volume.",
            "What shows on your home screen.",
            "What happens when the TV turns on.",
            "Where your weather comes from.",
            "Apps you've hidden from the home screen.",
            "Overdrive updates itself from GitHub.",
            "Jump straight to any Fire TV settings page.",
            "This TV and this version of Overdrive."};

    private final Host host;
    private final float d;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final LinearLayout catList;
    private final TextView paneTitle, paneDesc, deviceLine;
    private final ScrollView paneScroll;
    private final LinearLayout pane;
    private final List<View> catRows = new ArrayList<>();
    private int activeCat = -1;
    private String committedTheme;
    private String[] info = new String[8];

    SettingsHub(Context c, Host host) {
        super(c);
        this.host = host;
        d = c.getResources().getDisplayMetrics().density;
        setVisibility(GONE);
        setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(248, 6, 8, 16), Color.argb(225, 8, 10, 20), Color.argb(200, 8, 10, 20)}));

        LinearLayout cols = new LinearLayout(c);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        addView(cols, new LayoutParams(-1, -1));

        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(dp(48), dp(38), dp(14), dp(20));
        noClip(left);
        cols.addView(left, new LinearLayout.LayoutParams(dp(290), -1));
        TextView title = Ui.text(c, 32, Ui.TEXT, Ui.display(c, 700));
        title.setText("Settings");
        left.addView(title);
        deviceLine = Ui.text(c, 11, Ui.FAINT, Ui.body(c, false));
        deviceLine.setSingleLine(true);
        deviceLine.setEllipsize(TextUtils.TruncateAt.END);
        left.addView(deviceLine);
        catList = new LinearLayout(c);
        catList.setOrientation(LinearLayout.VERTICAL);
        catList.setPadding(0, dp(18), 0, 0);
        noClip(catList);
        left.addView(catList);
        for (int i = 0; i < CATS.length; i++) catList.addView(catRow(i));

        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(28), dp(44), dp(48), 0);
        noClip(right);
        cols.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));
        paneTitle = Ui.text(c, 24, Ui.TEXT, Ui.display(c, 500));
        right.addView(paneTitle);
        paneDesc = Ui.text(c, 13, Ui.SUB, Ui.body(c, false));
        right.addView(paneDesc);
        paneScroll = new ScrollView(c);
        paneScroll.setVerticalScrollBarEnabled(false);
        paneScroll.setClipToPadding(false);
        paneScroll.setPadding(dp(6), dp(18), dp(10), dp(60));
        pane = new LinearLayout(c);
        pane.setOrientation(LinearLayout.VERTICAL);
        noClip(pane);
        paneScroll.addView(pane);
        right.addView(paneScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView hint = Ui.text(c, 11, Ui.FAINT, Ui.body(c, false));
        hint.setText("◀ ▶ change a value  ·  OK select  ·  BACK categories");
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.bottomMargin = dp(18);
        right.addView(hint, hl);
    }

    // ==================================================================== categories

    private View catRow(int i) {
        Context c = getContext();
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), 0, dp(14), 0);
        r.setFocusable(true);
        r.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        r.setBackground(bg);
        Ui.IconView ic = new Ui.IconView(c, CAT_ICONS[i]);
        r.addView(ic, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView l = Ui.text(c, 15, Ui.TEXT, Ui.body(c, true));
        l.setText(CATS[i]);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
        ll.leftMargin = dp(14);
        r.addView(l, ll);
        Runnable paint = () -> {
            boolean f = r.isFocused(), on = activeCat == i;
            bg.setColor(f ? Color.WHITE : on ? Color.argb(30, 255, 255, 255) : 0);
            ic.setColor(f ? Ui.INK : on ? Ui.TEXT : Ui.SUB);
            l.setTextColor(f ? Ui.INK : on ? Ui.TEXT : Ui.SUB);
        };
        r.setTag(paint);
        r.setOnFocusChangeListener((v, has) -> {
            if (has) showCat(i);
            paint.run();
            v.animate().translationX(has ? dp(4) : 0).setDuration(180).setInterpolator(Ui.EASE).start();
        });
        r.setOnClickListener(v -> focusPane());
        paint.run();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.bottomMargin = dp(4);
        r.setLayoutParams(lp);
        catRows.add(r);
        return r;
    }

    private void repaintCats() {
        for (View v : catRows) ((Runnable) v.getTag()).run();
    }

    private void focusPane() {
        View f = firstFocusable(pane);
        if (f != null) f.requestFocus();
    }

    private static View firstFocusable(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v.isFocusable() && v.getVisibility() == VISIBLE) return v;
            if (v instanceof ViewGroup) { View f = firstFocusable((ViewGroup) v); if (f != null) return f; }
        }
        return null;
    }

    private void showCat(int i) {
        if (i == activeCat) return;
        if (activeCat == 0) revertThemePreview();
        activeCat = i;
        repaintCats();
        paneTitle.setText(CATS[i]);
        paneDesc.setText(CAT_DESC[i]);
        pane.removeAllViews();
        paneScroll.scrollTo(0, 0);
        switch (i) {
            case 0: buildAppearance(); break;
            case 1: buildSound(); break;
            case 2: buildHome(); break;
            case 3: buildPower(); break;
            case 4: buildWeather(); break;
            case 5: buildPrivacy(); break;
            case 6: buildUpdates(); break;
            case 7: buildFireTv(); break;
            default: buildAbout();
        }
        pane.setAlpha(0f);
        pane.setTranslationX(dp(18));
        pane.animate().withLayer().alpha(1f).translationX(0).setDuration(260).setInterpolator(Ui.EASE_OUT).start();
    }

    // ==================================================================== panes

    private void buildAppearance() {
        pane.addView(section("THEME"));
        HorizontalScrollView hs = new HorizontalScrollView(getContext());
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        hs.setPadding(dp(4), dp(10), dp(30), dp(16));
        LinearLayout cards = new LinearLayout(getContext());
        cards.setOrientation(LinearLayout.HORIZONTAL);
        noClip(cards);
        hs.addView(cards);
        committedTheme = host.themeId();
        for (int i = 0; i < Themes.IDS.length; i++) cards.addView(themeCard(i), cardLp());
        pane.addView(hs);
        pane.addView(section("HOME SCREEN LOOK"));
        pane.addView(toggle("App artwork backdrop", "The focused app's art fills the screen behind it", K_BACKDROP, true, host::homeChanged));
        pane.addView(toggle("Startup intro", "Plays when the TV turns on", K_INTRO, true, null));
        pane.addView(action("Watch the intro", null, () -> host.sub("intro")));
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(150), dp(100));
        lp.rightMargin = dp(16);
        return lp;
    }

    private View themeCard(int i) {
        String id = Themes.IDS[i];
        TileView t = new TileView(getContext(), 1.1f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        tp.setColor(Color.WHITE);
        tp.setTypeface(Ui.display(getContext(), 500));
        RectF[] last = {new RectF()};
        int accent = ThemeArt.accent(id);
        t.setCustom(Themes.NAMES[i], accent, (c, r, focus) -> {
            if (!r.equals(last[0])) { last[0] = new RectF(r); ThemeArt.bg(p, id, r); }
            c.drawRoundRect(r, dp(14), dp(14), p);
            ThemeArt.motif(c, id, r, q, d);
            tp.setTextSize(dp(13));
            c.drawText(Themes.NAMES[i], r.left + dp(12), r.bottom - dp(12), tp);
            if (id.equals(committedTheme)) {
                q.setStyle(Paint.Style.FILL);
                q.setColor(Color.WHITE);
                c.drawCircle(r.right - dp(16), r.top + dp(16), dp(9), q);
                q.setColor(Ui.INK);
                q.setStyle(Paint.Style.STROKE);
                q.setStrokeWidth(dp(2));
                c.drawLine(r.right - dp(20), r.top + dp(16), r.right - dp(17), r.top + dp(19), q);
                c.drawLine(r.right - dp(17), r.top + dp(19), r.right - dp(12), r.top + dp(13), q);
            }
        });
        t.setOnFocusChangeListener((v, has) -> {
            if (!has) return;
            host.tileFocused(t, accent);
            if (!id.equals(host.themeId())) { host.previewTheme(id); host.play(Sound.THEME); }
            paneDesc.setText(Themes.BLURBS[i]);
        });
        t.setOnClickListener(v -> {
            t.pulse();
            committedTheme = id;
            host.commitTheme(id);
            host.play(Sound.SELECT);
            host.banner(Themes.NAMES[i] + " theme on");
            ViewGroup parent = (ViewGroup) t.getParent();
            for (int k = 0; k < parent.getChildCount(); k++) parent.getChildAt(k).invalidate();
        });
        return t;
    }

    private void revertThemePreview() {
        if (committedTheme != null && !committedTheme.equals(host.themeId())) host.previewTheme(committedTheme);
    }

    private void buildSound() {
        pane.addView(toggle("Interface sounds", "Soft clicks, chimes and whooshes as you move", Sound.KEY_SOUNDS, true, () -> host.play(Sound.SELECT)));
        pane.addView(volumeSlider());
    }

    private void buildHome() {
        pane.addView(choice("Screensaver", "Big clock and weather when the TV sits idle", K_SAVER,
                new int[]{0, 2, 3, 5, 10}, new String[]{"Off", "2 min", "3 min", "5 min", "10 min"}, 3));
        pane.addView(action("Preview the screensaver", null, () -> host.sub("saver")));
        pane.addView(section("ROWS"));
        pane.addView(toggle("Inputs row", "HDMI inputs, when this TV shares them", K_INPUTS, true, host::homeChanged));
        pane.addView(toggle("Quick settings row", "Wi-Fi, Bluetooth, display and more at the bottom", K_QUICK, true, host::homeChanged));
    }

    private void buildPower() {
        boolean ready = WakeService.canLaunchFromBackground(getContext());
        pane.addView(toggle("Open when TV turns on", "Built in. You don't need the Launch on Boot app", BootReceiver.KEY_OPEN_ON_START, true,
                () -> WakeService.start(getContext())));
        pane.addView(infoRow("Status", ready ? "Ready on power-up and wake" : "Ready after a restart. Wake needs the phone setup"));
        pane.addView(action("Back & Home guard", HomeGuardService.isEnabled(getContext()) ? "On" : "Set up once from your phone", () -> host.sub("guard")));
        pane.addView(toggle("Startup intro", "Plays when the TV turns on", K_INTRO, true, null));
    }

    private void buildWeather() {
        SharedPreferences p = host.prefs();
        pane.addView(infoRow("Location", p.getString(Weather.KEY_NAME, "Automatic (from your internet connection)")));
        pane.addView(action("Change location", "Type a city or ZIP code", () -> host.sub("weather")));
        pane.addView(action("Use automatic location", null, () -> host.sub("weatherAuto")));
    }

    private void buildPrivacy() {
        int n = host.hiddenCount();
        pane.addView(action("Hidden apps", n == 0 ? "None hidden. Press ☰ on any app to hide it." : n + " hidden", () -> host.sub("hidden")));
    }

    private void buildUpdates() {
        pane.addView(infoRow("Installed", "Overdrive " + BuildConfig.VERSION_NAME));
        pane.addView(infoRow("How it works", "Checks GitHub every 30 minutes while open, then asks you to press Install"));
        pane.addView(action("Check for updates now", null, () -> host.sub("updates")));
    }

    private void buildAbout() {
        String[] labels = {"Device", "Software", "Storage", "Memory", "Network", "IP address", "Display", "Overdrive"};
        for (int i = 0; i < labels.length; i++) pane.addView(infoRow(labels[i], info[i] == null ? "…" : info[i]));
    }

    // ==================================================================== controls

    private TextView section(String s) {
        TextView t = Ui.text(getContext(), 11, Ui.FAINT, Ui.body(getContext(), true));
        t.setLetterSpacing(0.22f);
        t.setText(s);
        t.setPadding(dp(4), dp(14), 0, dp(2));
        return t;
    }

    private LinearLayout row(String label, String sub, View right, boolean focusable) {
        Context c = getContext();
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(18), dp(12), dp(18), dp(12));
        r.setFocusable(focusable);
        r.setClickable(focusable);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(Color.argb(focusable ? 16 : 0, 255, 255, 255));
        r.setBackground(bg);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView l = Ui.text(c, 15, Ui.TEXT, Ui.body(c, true));
        l.setText(label);
        col.addView(l);
        TextView s = null;
        if (sub != null) {
            s = Ui.text(c, 12, Ui.SUB, Ui.body(c, false));
            s.setText(sub);
            col.addView(s);
        }
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        if (right != null) r.addView(right);
        final TextView subF = s;
        if (focusable) {
            r.setOnFocusChangeListener((v, has) -> {
                bg.setColor(has ? Color.WHITE : Color.argb(16, 255, 255, 255));
                l.setTextColor(has ? Ui.INK : Ui.TEXT);
                if (subF != null) subF.setTextColor(has ? Color.argb(170, 10, 13, 24) : Ui.SUB);
                if (right instanceof Recolorable) ((Recolorable) right).onRowFocus(has);
                v.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f).setDuration(180).setInterpolator(Ui.EASE).start();
                if (has) host.tileFocused(v, Ui.ACCENT);
            });
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        r.setLayoutParams(lp);
        return r;
    }

    interface Recolorable { void onRowFocus(boolean focused); }

    private View toggle(String label, String sub, String key, boolean def, Runnable after) {
        SharedPreferences p = host.prefs();
        Switch sw = new Switch(getContext(), p.getBoolean(key, def));
        LinearLayout r = row(label, sub, sw, true);
        r.setOnClickListener(v -> {
            boolean on = !p.getBoolean(key, def);
            p.edit().putBoolean(key, on).apply();
            sw.set(on);
            host.play(Sound.MOVE);
            if (after != null) after.run();
        });
        return r;
    }

    private View choice(String label, String sub, String key, int[] values, String[] names, int def) {
        SharedPreferences p = host.prefs();
        ValueText vt = new ValueText(getContext());
        int[] idx = {0};
        int cur = p.getInt(key, def);
        for (int i = 0; i < values.length; i++) if (values[i] == cur) idx[0] = i;
        vt.set(names[idx[0]]);
        LinearLayout r = row(label, sub, vt, true);
        Runnable apply = () -> { p.edit().putInt(key, values[idx[0]]).apply(); vt.set(names[idx[0]]); host.play(Sound.MOVE); };
        r.setOnClickListener(v -> { idx[0] = (idx[0] + 1) % values.length; apply.run(); });
        r.setTag(R.id.value_row, Boolean.TRUE);
        r.setOnKeyListener((v, code, e) -> {
            if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                int n = idx[0] + (code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                if (n >= 0 && n < values.length) { idx[0] = n; apply.run(); } else host.play(Sound.BUMP);
            }
            return true;
        });
        return r;
    }

    private View action(String label, String sub, Runnable run) {
        ChevronHolder holder = new ChevronHolder(getContext());
        LinearLayout r = row(label, sub, holder, true);
        r.setOnClickListener(v -> { host.play(Sound.SELECT); run.run(); });
        return r;
    }

    private View infoRow(String label, String value) {
        TextView v = Ui.text(getContext(), 13, Ui.SUB, Ui.body(getContext(), false));
        v.setText(value);
        v.setGravity(Gravity.END);
        v.setMaxWidth(dp(330));
        LinearLayout r = row(label, null, v, false);
        r.setPadding(dp(18), dp(8), dp(18), dp(8));
        return r;
    }

    private View volumeSlider() {
        Slider s = new Slider(getContext());
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        Runnable sync = () -> {
            if (am == null) return;
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            s.set(max <= 0 ? 0f : (float) am.getStreamVolume(AudioManager.STREAM_MUSIC) / max);
        };
        sync.run();
        LinearLayout r = row("TV volume", "◀ ▶ to change", s, true);
        r.setTag(R.id.value_row, Boolean.TRUE);
        r.setOnKeyListener((v, code, e) -> {
            if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (e.getAction() == KeyEvent.ACTION_DOWN && am != null) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        code == KeyEvent.KEYCODE_DPAD_RIGHT ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
                sync.run();
                host.play(Sound.MOVE);
            }
            return true;
        });
        return r;
    }

    private class Switch extends View implements Recolorable {
        boolean on; float pos; boolean rowFocus;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        Switch(Context c, boolean on) {
            super(c);
            this.on = on; pos = on ? 1f : 0f;
            setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(26)));
        }
        void set(boolean v) {
            on = v;
            ValueAnimator a = ValueAnimator.ofFloat(pos, v ? 1f : 0f);
            a.setDuration(220);
            a.setInterpolator(Ui.SPRING);
            a.addUpdateListener(an -> { pos = (float) an.getAnimatedValue(); invalidate(); });
            a.start();
        }
        @Override public void onRowFocus(boolean f) { rowFocus = f; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float h = getHeight(), w = getWidth();
            r.set(0, 0, w, h);
            int off = rowFocus ? Color.argb(60, 10, 13, 24) : Color.argb(70, 255, 255, 255);
            p.setColor(Themes.lerpColor(off, Ui.ACCENT, Math.max(0f, Math.min(1f, pos))));
            c.drawRoundRect(r, h / 2, h / 2, p);
            p.setColor(Color.WHITE);
            c.drawCircle(h / 2 + (w - h) * pos, h / 2, h / 2 - dp(3), p);
        }
    }

    private class ValueText extends LinearLayout implements Recolorable {
        final TextView t;
        ValueText(Context c) {
            super(c);
            t = Ui.text(c, 14, Ui.TEXT, Ui.body(c, true));
            addView(t);
        }
        void set(String s) { t.setText("◀  " + s + "  ▶"); }
        @Override public void onRowFocus(boolean f) { t.setTextColor(f ? Ui.INK : Ui.TEXT); }
    }

    private class ChevronHolder extends FrameLayout implements Recolorable {
        final Ui.IconView icon;
        ChevronHolder(Context c) {
            super(c);
            icon = new Ui.IconView(c, "chevron");
            icon.setColor(Ui.SUB);
            addView(icon, new LayoutParams(dp(22), dp(22)));
        }
        @Override public void onRowFocus(boolean f) { icon.setColor(f ? Ui.INK : Ui.SUB); }
    }

    private class Slider extends View implements Recolorable {
        float v; boolean rowFocus;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        Slider(Context c) { super(c); setLayoutParams(new LinearLayout.LayoutParams(dp(170), dp(24))); }
        void set(float x) { v = x; invalidate(); }
        @Override public void onRowFocus(boolean f) { rowFocus = f; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            float cy = getHeight() / 2f, w = getWidth();
            r.set(0, cy - dp(3), w, cy + dp(3));
            p.setColor(rowFocus ? Color.argb(50, 10, 13, 24) : Color.argb(60, 255, 255, 255));
            c.drawRoundRect(r, dp(3), dp(3), p);
            r.set(0, cy - dp(3), w * v, cy + dp(3));
            p.setColor(Ui.ACCENT);
            c.drawRoundRect(r, dp(3), dp(3), p);
            p.setColor(rowFocus ? Ui.INK : Color.WHITE);
            c.drawCircle(Math.max(dp(8), Math.min(w - dp(8), w * v)), cy, dp(8), p);
        }
    }

    // ==================================================================== Fire TV pages

    private static final class Entry {
        final String label, icon, action; final String[] keys; final int accent;
        Entry(String l, String i, String a, int acc, String... k) { label = l; icon = i; action = a; accent = acc; keys = k; }
    }

    private static final Entry[] ENTRIES = {
            new Entry("Network", "wifi", Settings.ACTION_WIFI_SETTINGS, 0xFF3DA9FF, "network", "wifi"),
            new Entry("Display & Sounds", "display", Settings.ACTION_DISPLAY_SETTINGS, 0xFF5C7CFF, "display", "sound", "audio"),
            new Entry("Controllers & Bluetooth", "remote", Settings.ACTION_BLUETOOTH_SETTINGS, 0xFF7C6CFF, "controller", "bluetooth", "remote"),
            new Entry("Applications", "apps", Settings.ACTION_APPLICATION_SETTINGS, 0xFF2BD9A8, "applications", "allapplications", "apps"),
            new Entry("Inputs", "inputs", null, 0xFFFF5A7A, "input", "source", "hdmi"),
            new Entry("Device & Software", "tv", Settings.ACTION_DEVICE_INFO_SETTINGS, 0xFF8FA3C7, "mytv", "device", "about", "system"),
            new Entry("Account & Profiles", "person", null, 0xFFFFB13D, "account", "profile"),
            new Entry("Preferences", "sliders", null, 0xFFB05CFF, "preference", "privacy"),
            new Entry("Equipment Control", "equipment", null, 0xFF3DC8FF, "equipment"),
            new Entry("Live TV", "antenna", null, 0xFFFF6A3D, "livetv", "live_tv", "channel"),
            new Entry("Notifications", "bell", null, 0xFFFFD34D, "notification"),
            new Entry("Alexa", "alexa", null, 0xFF2BC8FF, "alexa"),
            new Entry("Sleep Timer", "timer", null, 0xFF9AA6BF, "sleep"),
            new Entry("Accessibility", "access", Settings.ACTION_ACCESSIBILITY_SETTINGS, 0xFF2BD9A8, "accessibility"),
            new Entry("Help", "help", null, 0xFF8FA3C7, "help", "support"),
    };

    private void buildFireTv() {
        LinearLayout row = null;
        int cols = 3;
        for (int i = 0; i < ENTRIES.length; i++) {
            if (i % cols == 0) {
                row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                noClip(row);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.bottomMargin = dp(14);
                pane.addView(row, rl);
            }
            TileView t = fireTile(ENTRIES[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(92), 1f);
            if (i % cols != cols - 1) lp.rightMargin = dp(14);
            row.addView(t, lp);
        }
    }

    private TileView fireTile(Entry e) {
        TileView t = new TileView(getContext(), 1.07f);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        Paint ic = new Paint(Paint.ANTI_ALIAS_FLAG);
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        tp.setColor(Color.WHITE);
        tp.setTypeface(Ui.body(getContext(), true));
        RectF[] last = {new RectF()};
        RectF tmp = new RectF();
        Path path = new Path();
        t.setCustom(e.label, e.accent, (c, r, focus) -> {
            if (!r.equals(last[0])) {
                last[0] = new RectF(r);
                bg.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                        TileView.darken(e.accent, 0.38f), TileView.darken(e.accent, 0.12f), Shader.TileMode.CLAMP));
            }
            c.drawRoundRect(r, dp(14), dp(14), bg);
            ic.setColor(Color.WHITE);
            Icons.draw(c, e.icon, r.left + dp(28), r.top + dp(30), dp(11), ic, path, tmp, d, focus);
            tp.setTextSize(dp(13));
            CharSequence l = TextUtils.ellipsize(e.label, tp, r.width() - dp(24), TextUtils.TruncateAt.END);
            c.drawText(l, 0, l.length(), r.left + dp(14), r.bottom - dp(16), tp);
        });
        t.setOnFocusChangeListener((v, has) -> { if (has) host.tileFocused(t, e.accent); });
        t.setOnClickListener(v -> { t.pulse(); host.play(Sound.SELECT); open(e); });
        return t;
    }

    private void open(Entry e) {
        Context c = getContext();
        try {
            c.startActivity(resolve(c, e).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ex) {
            try {
                c.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                host.banner("Opened Fire TV settings. Pick " + e.label + " there.");
            } catch (Exception ignored) {
                host.banner("Couldn't open " + e.label);
            }
        }
    }

    private static Intent resolve(Context c, Entry e) {
        PackageManager pm = c.getPackageManager();
        if (e.action != null) {
            Intent std = new Intent(e.action);
            if (std.resolveActivity(pm) != null) return std;
        }
        for (String pkg : new String[]{"com.amazon.tv.settings.v2", "com.amazon.tv.settings"}) {
            try {
                PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
                if (pi.activities == null) continue;
                for (String key : e.keys) {
                    ActivityInfo best = null;
                    for (ActivityInfo a : pi.activities) {
                        if (!a.exported || a.permission != null) continue;
                        String n = a.name.toLowerCase(Locale.US);
                        String simple = n.substring(n.lastIndexOf('.') + 1);
                        if (simple.contains(key) && (best == null || a.name.length() < best.name.length())) best = a;
                    }
                    if (best != null) return new Intent().setClassName(pkg, best.name);
                }
            } catch (Exception ignored) { }
        }
        return new Intent(Settings.ACTION_SETTINGS);
    }

    // ==================================================================== show / hide / keys

    boolean isOpen() { return getVisibility() == VISIBLE; }

    void show() {
        setVisibility(VISIBLE);
        setAlpha(0f);
        setTranslationX(dp(30));
        animate().withLayer().alpha(1f).translationX(0).setDuration(320).setInterpolator(Ui.EASE_OUT).start();
        loadInfo();
        activeCat = -1;
        View first = catRows.get(0);
        first.post(first::requestFocus);
    }

    void hide() {
        if (activeCat == 0) revertThemePreview();
        animate().withLayer().alpha(0f).translationX(dp(20)).setDuration(200)
                .withEndAction(() -> setVisibility(GONE)).start();
    }

    /** Back: from the right pane return to the categories; from the categories close Settings. */
    boolean back() {
        View f = findFocus();
        if (f != null && !catRows.contains(f) && activeCat >= 0) {
            catRows.get(activeCat).requestFocus();
            host.play(Sound.BACK);
            return true;
        }
        return false;
    }

    /** Rebuild the visible pane after a sub-panel changed something. */
    void refreshPane() {
        if (activeCat < 0) return;
        int c = activeCat;
        boolean inPane = findFocus() != null && !catRows.contains(findFocus());
        activeCat = -1;
        showCat(c);
        if (inPane) focusPane(); else catRows.get(c).requestFocus();
    }

    // ==================================================================== live info

    private void loadInfo() {
        new Thread(() -> {
            Context c = getContext();
            String model = cap(Build.MANUFACTURER) + " " + Build.MODEL;
            String os = (Build.VERSION.SDK_INT >= 30 ? "Fire OS 8" : Build.VERSION.SDK_INT >= 28 ? "Fire OS 7" : "Fire OS 6")
                    + " (Android " + Build.VERSION.RELEASE + ")";
            String ip = ip();
            String[] v = {model, os, storage(), memory(c), net(c), ip == null ? "Offline" : ip, display(), BuildConfig.VERSION_NAME};
            ui.post(() -> {
                info = v;
                deviceLine.setText(model + "  ·  " + (ip == null ? "Offline" : ip));
                if (activeCat == 8) {
                    pane.removeAllViews();
                    buildAbout();
                }
            });
        }).start();
    }

    private static String cap(String s) { return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.US) + s.substring(1); }

    private String display() {
        android.util.DisplayMetrics m = getResources().getDisplayMetrics();
        int w = Math.max(m.widthPixels, m.heightPixels), h = Math.min(m.widthPixels, m.heightPixels);
        float hz = getDisplay() != null ? getDisplay().getRefreshRate() : 60f;
        return w + " × " + h + " at " + Math.round(hz) + " Hz";
    }

    private static String ip() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
            }
        } catch (Exception ignored) { }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static String net(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) return "Not connected";
            String bt = bt();
            return (ni.getType() == ConnectivityManager.TYPE_ETHERNET ? "Ethernet" : "Wi-Fi") + (bt == null ? "" : "  ·  " + bt);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private static String storage() {
        try {
            StatFs s = new StatFs(Environment.getDataDirectory().getPath());
            return String.format(Locale.US, "%.1f GB free of %.0f GB", s.getAvailableBytes() / 1e9, s.getTotalBytes() / 1e9);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private static String memory(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return String.format(Locale.US, "%.1f GB free of %.1f GB", mi.availMem / 1e9, mi.totalMem / 1e9);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    @SuppressWarnings("MissingPermission")
    private static String bt() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null || !a.isEnabled()) return "Bluetooth off";
            int n = a.getBondedDevices().size();
            return n + (n == 1 ? " Bluetooth device" : " Bluetooth devices");
        } catch (Exception e) {
            return null;
        }
    }

    private int dp(float v) { return Math.round(v * d); }

    private static void noClip(ViewGroup v) {
        v.setClipChildren(false);
        v.setClipToPadding(false);
    }
}
