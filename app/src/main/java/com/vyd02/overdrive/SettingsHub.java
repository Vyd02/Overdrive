package com.vyd02.overdrive;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
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
import android.graphics.Typeface;
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
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
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
 * Overdrive's own Settings screen. Shows live info (network, storage, device) and opens each
 * Fire TV settings page directly, plus Overdrive's own options and a built-in volume control.
 */
public class SettingsHub extends FrameLayout {

    interface Host {
        void play(int sound);
        void tileFocused(TileView t, String title, String sub, int accent);
        void openOverdrive(String which);
        void banner(String msg);
        void closeHub();
    }

    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);
    private final Host host;
    private final float d;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final LinearLayout overdriveRow;
    private final LinearLayout grid;
    private final TextView info;
    private final ScrollView scroll;
    private final List<TileView> tiles = new ArrayList<>();
    private final List<String[]> subs = new ArrayList<>();
    private TileView volumeTile;
    private String[] volumeSub;
    private boolean adjustingVolume;
    private View lastFocus;

    SettingsHub(Context c, Host host) {
        super(c);
        this.host = host;
        d = c.getResources().getDisplayMetrics().density;
        setVisibility(GONE);
        setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(215, 4, 6, 14), Color.argb(190, 4, 6, 14), Color.argb(230, 2, 3, 8)}));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(48), dp(30), dp(48), 0);
        noClip(col);
        addView(col, new LayoutParams(-1, -1));

        TextView title = text(30, Color.WHITE, "sans-serif", Typeface.BOLD);
        title.setText("Settings");
        col.addView(title);
        info = text(11.5f, Color.argb(190, 255, 255, 255), "sans-serif", Typeface.NORMAL);
        info.setSingleLine(true);
        info.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(-1, -2);
        il.topMargin = dp(6);
        col.addView(info, il);

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setSmoothScrollingEnabled(true);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, 0, 1f);
        sl.topMargin = dp(10);
        col.addView(scroll, sl);

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(4), dp(12), dp(4), dp(40));
        noClip(body);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));

        body.addView(label("OVERDRIVE"));
        overdriveRow = new LinearLayout(c);
        overdriveRow.setOrientation(LinearLayout.HORIZONTAL);
        overdriveRow.setPadding(0, dp(10), 0, dp(18));
        noClip(overdriveRow);
        body.addView(overdriveRow);

        body.addView(label("FIRE TV"));
        grid = new LinearLayout(c);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(10), 0, 0);
        noClip(grid);
        body.addView(grid);

        buildOverdriveRow();
        buildGrid();
    }

    // ------------------------------------------------------------------ building

    private void buildOverdriveRow() {
        int accent = Color.parseColor("#3DA9FF");
        addSmall("Themes", "themes", "palette", Color.parseColor("#B05CFF"));
        addSmall("Sounds", "sounds", "sound", Color.parseColor("#2BD9A8"));
        addSmall("Screensaver", "saver", "moon", Color.parseColor("#5C7CFF"));
        addSmall("Power-up", "power", "power", Color.parseColor("#FF6A3D"));
        addSmall("Hidden apps", "hidden", "eye", Color.parseColor("#8FA3C7"));
        addSmall("Weather", "weather", "cloud", Color.parseColor("#3DC8FF"));
        addSmall("Updates", "updates", "download", accent);
    }

    private void addSmall(String label, String which, String icon, int accent) {
        String[] sub = {null};
        TileView t = makeTile(label, sub, icon, accent);
        t.setOnClickListener(v -> { t.pulse(); host.play(Sound.SELECT); host.openOverdrive(which); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(112), dp(84));
        lp.rightMargin = dp(12);
        overdriveRow.addView(t, lp);
    }

    private static final class Entry {
        final String label, icon, action; final String[] keys; final int accent;
        Entry(String l, String i, String a, int acc, String... k) { label = l; icon = i; action = a; accent = acc; keys = k; }
    }

    private final Entry[] entries = {
            new Entry("Inputs", "inputs", null, 0xFFFF5A7A, "input", "source", "hdmi"),
            new Entry("Network", "wifi", Settings.ACTION_WIFI_SETTINGS, 0xFF3DA9FF, "network", "wifi"),
            new Entry("Device & Software", "tv", Settings.ACTION_DEVICE_INFO_SETTINGS, 0xFF8FA3C7, "mytv", "device", "about", "system"),
            new Entry("Account & Profiles", "person", null, 0xFFFFB13D, "account", "profile"),
            new Entry("Applications", "apps", Settings.ACTION_APPLICATION_SETTINGS, 0xFF2BD9A8, "applications", "allapplications", "apps"),
            new Entry("Display & Sounds", "display", Settings.ACTION_DISPLAY_SETTINGS, 0xFF5C7CFF, "display", "sound", "audio"),
            new Entry("Controllers & Bluetooth", "remote", Settings.ACTION_BLUETOOTH_SETTINGS, 0xFF7C6CFF, "controller", "bluetooth", "remote"),
            new Entry("Preferences", "sliders", null, 0xFFB05CFF, "preference", "privacy"),
            new Entry("Equipment Control", "equipment", null, 0xFF3DC8FF, "equipment"),
            new Entry("Live TV", "antenna", null, 0xFFFF6A3D, "livetv", "live_tv", "channel"),
            new Entry("Notifications", "bell", null, 0xFFFFD34D, "notification"),
            new Entry("Alexa", "alexa", null, 0xFF2BC8FF, "alexa"),
            new Entry("Sleep Timer", "timer", null, 0xFF9AA6BF, "sleep"),
            new Entry("Accessibility", "access", Settings.ACTION_ACCESSIBILITY_SETTINGS, 0xFF2BD9A8, "accessibility"),
            new Entry("Help", "help", null, 0xFF8FA3C7, "help", "support"),
    };

    private void buildGrid() {
        List<View> all = new ArrayList<>();
        // built-in volume control first
        volumeSub = new String[]{null};
        volumeTile = makeTile("Volume", volumeSub, "sound", 0xFF2BD9A8);
        volumeTile.setOnClickListener(v -> toggleVolumeMode());
        all.add(volumeTile);
        for (Entry e : entries) {
            String[] sub = {null};
            subs.add(sub);
            TileView t = makeTile(e.label, sub, e.icon, e.accent);
            t.setOnClickListener(v -> {
                t.pulse();
                host.play(Sound.SELECT);
                open(e);
            });
            all.add(t);
        }
        int cols = 4;
        LinearLayout row = null;
        for (int i = 0; i < all.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                noClip(row);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.bottomMargin = dp(16);
                grid.addView(row, rl);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(204), dp(96));
            if (i % cols != cols - 1) lp.rightMargin = dp(16);
            row.addView(all.get(i), lp);
        }
    }

    private TileView makeTile(String label, String[] sub, String icon, int accent) {
        TileView t = new TileView(getContext(), 1.08f);
        final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        final Paint ic = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        final TextPaint sp = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(Color.WHITE);
        tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        sp.setColor(Color.argb(190, 255, 255, 255));
        final RectF[] last = {new RectF()};
        final RectF tmp = new RectF();
        final Path path = new Path();
        t.setCustom(label, accent, (c, r, focus) -> {
            if (!r.equals(last[0])) {
                last[0] = new RectF(r);
                bg.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                        TileView.darken(accent, 0.38f), TileView.darken(accent, 0.13f), Shader.TileMode.CLAMP));
            }
            c.drawRoundRect(r, dp(10), dp(10), bg);
            boolean wide = r.width() > r.height() * 1.6f;
            float s = r.height() * (wide ? 0.17f : 0.2f);
            float icx = wide ? r.left + r.height() * 0.42f : r.centerX();
            float icy = wide ? r.centerY() : r.top + r.height() * 0.4f;
            ic.setColor(Color.WHITE);
            Icons.draw(c, icon, icx, icy, s, ic, path, tmp, d, focus);
            if (wide) {
                float tx = r.left + r.height() * 0.8f;
                float avail = r.right - tx - dp(10);
                tp.setTextSize(dp(13));
                CharSequence l = TextUtils.ellipsize(label, tp, avail, TextUtils.TruncateAt.END);
                String st = sub[0];
                float ty = st == null ? r.centerY() + dp(5) : r.centerY() - dp(2);
                c.drawText(l, 0, l.length(), tx, ty, tp);
                if (st != null) {
                    sp.setTextSize(dp(10));
                    CharSequence ss = TextUtils.ellipsize(st, sp, avail, TextUtils.TruncateAt.END);
                    c.drawText(ss, 0, ss.length(), tx, ty + dp(16), sp);
                }
                if (t == volumeTile && st != null) {
                    float frac = volumeFrac();
                    float bx = tx, by = r.bottom - dp(14), bw = avail;
                    bar.setColor(Color.argb(60, 255, 255, 255));
                    tmp.set(bx, by, bx + bw, by + dp(4));
                    c.drawRoundRect(tmp, dp(2), dp(2), bar);
                    bar.setColor(adjustingVolume ? Color.WHITE : accent);
                    tmp.set(bx, by, bx + bw * frac, by + dp(4));
                    c.drawRoundRect(tmp, dp(2), dp(2), bar);
                }
            } else {
                tp.setTextSize(r.height() * 0.15f);
                CharSequence l = TextUtils.ellipsize(label, tp, r.width() - dp(12), TextUtils.TruncateAt.END);
                float lw = tp.measureText(l, 0, l.length());
                c.drawText(l, 0, l.length(), r.centerX() - lw / 2f, r.bottom - r.height() * 0.15f, tp);
            }
        });
        t.setOnFocusChangeListener((v, has) -> {
            if (!has) return;
            lastFocus = v;
            host.tileFocused(t, label, sub[0], accent);
        });
        tiles.add(t);
        return t;
    }

    private TextView label(String s) {
        TextView t = text(10.5f, Color.WHITE, "sans-serif-medium", Typeface.NORMAL);
        t.setText(s);
        t.setLetterSpacing(0.3f);
        t.setAlpha(0.7f);
        return t;
    }

    // ------------------------------------------------------------------ show / hide

    boolean isOpen() { return getVisibility() == VISIBLE; }

    void show() {
        setVisibility(VISIBLE);
        setAlpha(0f);
        setScaleX(1.04f);
        setScaleY(1.04f);
        animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(EASE).start();
        refreshInfo();
        View target = lastFocus != null ? lastFocus : overdriveRow.getChildAt(0);
        target.post(target::requestFocus);
    }

    void hide() {
        adjustingVolume = false;
        animate().alpha(0f).scaleX(1.03f).scaleY(1.03f).setDuration(220)
                .withEndAction(() -> setVisibility(GONE)).start();
    }

    // ------------------------------------------------------------------ volume

    private AudioManager audio() { return (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE); }

    private float volumeFrac() {
        AudioManager am = audio();
        if (am == null) return 0f;
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return max <= 0 ? 0f : (float) am.getStreamVolume(AudioManager.STREAM_MUSIC) / max;
    }

    private void updateVolumeSub() {
        volumeSub[0] = adjustingVolume ? "◀ ▶ to change  ·  OK when done" : Math.round(volumeFrac() * 100) + "%";
        volumeTile.invalidate();
    }

    private void toggleVolumeMode() {
        adjustingVolume = !adjustingVolume;
        host.play(Sound.SELECT);
        updateVolumeSub();
    }

    /** Called first with every key while the hub is open. Returns true if it used the key. */
    private boolean swallowUp;

    boolean handleKey(KeyEvent e) {
        if (swallowUp && e.getAction() == KeyEvent.ACTION_UP) { swallowUp = false; return true; }
        if (!adjustingVolume || e.getAction() != KeyEvent.ACTION_DOWN) return adjustingVolume;
        int code = e.getKeyCode();
        AudioManager am = audio();
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (am != null) am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    code == KeyEvent.KEYCODE_DPAD_RIGHT ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
            host.play(Sound.MOVE);
            updateVolumeSub();
        } else if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_BACK) {
            toggleVolumeMode();
            swallowUp = true; // don't let the button's release re-open volume mode
        }
        return true;
    }

    // ------------------------------------------------------------------ live info

    private void refreshInfo() {
        new Thread(() -> {
            Context c = getContext();
            String model = Build.MANUFACTURER.substring(0, 1).toUpperCase(Locale.US) + Build.MANUFACTURER.substring(1)
                    + " " + Build.MODEL;
            String fireOs = Build.VERSION.SDK_INT >= 30 ? "Fire OS 8" : Build.VERSION.SDK_INT >= 28 ? "Fire OS 7" : "Fire OS 6";
            String ip = ipAddress();
            String net = netType(c);
            String storage = storageFree();
            String mem = memFree(c);
            String bt = btState();
            int apps = 0;
            try {
                apps = c.getPackageManager().queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0).size();
            } catch (Exception ignored) { }
            final String line = model + "  ·  " + fireOs + "  ·  " + storage + "  ·  " + (ip == null ? "Offline" : ip);
            final int appCount = apps;
            ui.post(() -> {
                info.setText(line);
                setSub("Network", net == null ? "Not connected" : net + (ip == null ? "" : "  ·  " + ip));
                setSub("Device & Software", fireOs + "  ·  " + mem);
                setSub("Applications", appCount + " apps  ·  " + storage);
                setSub("Controllers & Bluetooth", bt);
                setSub("Display & Sounds", displayInfo());
                setSub("Accessibility", HomeGuardService.isEnabled(c) ? "Overdrive guard on" : null);
                updateVolumeSub();
                for (TileView t : tiles) t.invalidate();
            });
        }).start();
    }

    private void setSub(String label, String value) {
        for (int i = 0; i < entries.length; i++) if (entries[i].label.equals(label)) subs.get(i)[0] = value;
    }

    private String displayInfo() {
        android.util.DisplayMetrics m = getResources().getDisplayMetrics();
        int w = Math.max(m.widthPixels, m.heightPixels), h = Math.min(m.widthPixels, m.heightPixels);
        float hz = getDisplay() != null ? getDisplay().getRefreshRate() : 60f;
        return w + "×" + h + "  ·  " + Math.round(hz) + " Hz";
    }

    private static String ipAddress() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static String netType(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) return null;
            return ni.getType() == ConnectivityManager.TYPE_ETHERNET ? "Ethernet" : "Wi-Fi connected";
        } catch (Exception e) {
            return null;
        }
    }

    private static String storageFree() {
        try {
            StatFs s = new StatFs(Environment.getDataDirectory().getPath());
            double free = s.getAvailableBytes() / 1e9, total = s.getTotalBytes() / 1e9;
            return String.format(Locale.US, "%.1f of %.0f GB free", free, total);
        } catch (Exception e) {
            return "Storage";
        }
    }

    private static String memFree(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return String.format(Locale.US, "%.1f GB memory free", mi.availMem / 1e9);
        } catch (Exception e) {
            return "";
        }
    }

    @SuppressWarnings("MissingPermission")
    private static String btState() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null) return "No Bluetooth";
            if (!a.isEnabled()) return "Bluetooth off";
            int n = a.getBondedDevices().size();
            return n + (n == 1 ? " paired device" : " paired devices");
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ opening Fire TV pages

    private void open(Entry e) {
        Context c = getContext();
        Intent i = resolve(c, e);
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ex) {
            try {
                c.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                host.banner("Opened Fire TV settings. Pick " + e.label + " there.");
            } catch (Exception ignored) {
                host.banner("Couldn't open " + e.label);
            }
        }
    }

    /** Finds the exact Fire TV page: the standard Android shortcut if the TV supports it, otherwise
     *  Amazon's own page found by name inside the Fire TV Settings app. */
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
                ActivityInfo best = null;
                for (String key : e.keys) {
                    for (ActivityInfo a : pi.activities) {
                        if (!a.exported || a.permission != null) continue;
                        String n = a.name.toLowerCase(Locale.US);
                        String simple = n.substring(n.lastIndexOf('.') + 1);
                        if (simple.contains(key) && (best == null || simple.length() < best.name.length() - best.name.lastIndexOf('.'))) {
                            best = a;
                        }
                    }
                    if (best != null) break;
                }
                if (best != null) return new Intent().setClassName(pkg, best.name);
            } catch (Exception ignored) { }
        }
        return new Intent(Settings.ACTION_SETTINGS);
    }

    // ------------------------------------------------------------------ helpers

    private int dp(float v) { return Math.round(v * d); }

    private TextView text(float sp, int color, String font, int style) {
        TextView t = new TextView(getContext());
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(font, style));
        return t;
    }

    private static void noClip(ViewGroup v) {
        v.setClipChildren(false);
        v.setClipToPadding(false);
    }
}
