package com.vyd02.overdrive;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int C_TEXT = Color.WHITE;
    private static final int C_SUB = Color.parseColor("#9AA6BF");
    private static final int C_ACCENT = Color.parseColor("#3DA9FF");
    private static final String KEY_LAST_UPDATE_CHECK = "last_update_check";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private AppRepo repo;
    private float d;

    // views
    private FrameLayout root;
    private BackgroundView bg;
    private LinearLayout content;
    private ScrollView scroll;
    private TextView heroName, heroTag, recentLabel;
    private LinearLayout recentRow, libraryGrid;
    private TextView clockTime, clockAmPm, clockDate;
    private TextView weatherIcon, weatherTemp, weatherPlace;
    private FrameLayout gear;
    private View updateDot;
    private FrameLayout overlay;
    private LinearLayout panel;
    private View scrim;
    private TextView banner;

    // state
    private List<AppRepo.App> allApps = new ArrayList<>();
    private final Map<String, AppRepo.App> byPkg = new HashMap<>();
    private String lastSignature = "";
    private boolean firstBuild = true;
    private View focusBeforePanel;
    private Updater.Release pendingRelease;
    private int currentAccent = C_ACCENT;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFormat(android.graphics.PixelFormat.RGBA_8888); // full color depth: no banding in gradients
        repo = new AppRepo(this);
        d = getResources().getDisplayMetrics().density;
        buildUi();
        setContentView(root);
        reloadApps(true);
        startClock();
        refreshWeather();
        ui.postDelayed(this::maybePromptGuard, 1800);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(pkgReceiver, f);
    }

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { reloadApps(false); }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
        ui.removeCallbacksAndMessages(null);
        io.shutdownNow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bg.setRunning(true);
        reloadApps(false);
        ui.postDelayed(this::autoCheckUpdate, 2500);
        // slide the dashboard back in after returning from an app
        content.setAlpha(0f);
        content.setScaleX(1.04f);
        content.setScaleY(1.04f);
        content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        bg.setRunning(false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already here: close menus and jump back to the top
        if (overlay.getVisibility() == View.VISIBLE) closePanel(false);
        scroll.smoothScrollTo(0, 0);
        focusFirstTile();
    }

    // =====================================================================
    // UI construction
    // =====================================================================

    private int dp(float v) { return Math.round(v * d); }

    private TextView text(float sp, int color, String font, int style) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(font, style));
        t.setIncludeFontPadding(false);
        return t;
    }

    private void noClip(ViewGroup v) {
        v.setClipChildren(false);
        v.setClipToPadding(false);
    }

    private void buildUi() {
        root = new FrameLayout(this);
        noClip(root);
        bg = new BackgroundView(this);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(48), dp(24), dp(48), 0);
        noClip(content);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        content.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(52)));

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setSmoothScrollingEnabled(true);
        scroll.setFillViewport(true);
        noClip(scroll);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(10), 0, dp(60));
        noClip(body);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));

        heroName = text(30, C_TEXT, "sans-serif", Typeface.BOLD);
        heroName.setSingleLine(true);
        heroName.setShadowLayer(dp(12), 0, dp(2), Color.argb(160, 0, 0, 0));
        heroName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        // full-width so changing the name only repaints it instead of re-laying-out the whole screen
        body.addView(heroName, new LinearLayout.LayoutParams(-1, -2));
        heroTag = text(10.5f, C_ACCENT, "sans-serif-medium", Typeface.NORMAL);
        heroTag.setLetterSpacing(0.22f);
        heroTag.setSingleLine(true);
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(-1, -2);
        tagLp.topMargin = dp(6);
        body.addView(heroTag, tagLp);

        recentLabel = sectionLabel("RECENT");
        body.addView(recentLabel, sectionLp(18));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setPadding(dp(4), dp(14), dp(40), dp(14));
        noClip(hs);
        recentRow = new LinearLayout(this);
        recentRow.setOrientation(LinearLayout.HORIZONTAL);
        noClip(recentRow);
        hs.addView(recentRow);
        LinearLayout.LayoutParams hsLp = new LinearLayout.LayoutParams(-1, -2);
        hsLp.leftMargin = -dp(4);
        body.addView(hs, hsLp);

        body.addView(sectionLabel("LIBRARY"), sectionLp(6));
        libraryGrid = new LinearLayout(this);
        libraryGrid.setOrientation(LinearLayout.VERTICAL);
        libraryGrid.setPadding(0, dp(12), 0, 0);
        noClip(libraryGrid);
        body.addView(libraryGrid, new LinearLayout.LayoutParams(-1, -2));

        // overlay layer for menus
        overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        scrim = new View(this);
        scrim.setBackgroundColor(Color.argb(170, 0, 0, 0));
        overlay.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(30), dp(40), dp(30), dp(30));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(380), -1, Gravity.END);
        overlay.addView(panel, plp);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        // pop-down notice
        banner = text(13, C_TEXT, "sans-serif-medium", Typeface.NORMAL);
        banner.setPadding(dp(20), dp(10), dp(20), dp(10));
        GradientDrawable bgd = new GradientDrawable();
        bgd.setCornerRadius(dp(22));
        bgd.setColor(Color.argb(235, 18, 24, 40));
        bgd.setStroke(dp(1), Color.argb(90, 255, 255, 255));
        banner.setBackground(bgd);
        banner.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        blp.topMargin = dp(22);
        root.addView(banner, blp);
    }

    private TextView sectionLabel(String s) {
        TextView t = text(10, C_SUB, "sans-serif-medium", Typeface.NORMAL);
        t.setLetterSpacing(0.3f);
        t.setText(s);
        return t;
    }

    private LinearLayout.LayoutParams sectionLp(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(top);
        return lp;
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        noClip(bar);

        // brand mark with a breathing light
        View dot = new View(this);
        GradientDrawable dd = new GradientDrawable();
        dd.setShape(GradientDrawable.OVAL);
        dd.setColor(C_ACCENT);
        dot.setBackground(dd);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dlp.rightMargin = dp(10);
        bar.addView(dot, dlp);
        breathe(dot);
        TextView brand = text(12, C_TEXT, "sans-serif-black", Typeface.NORMAL);
        brand.setText("OVERDRIVE");
        brand.setLetterSpacing(0.45f);
        brand.setAlpha(0.9f);
        bar.addView(brand);

        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        // weather
        weatherIcon = text(22, C_TEXT, "sans-serif", Typeface.NORMAL);
        weatherIcon.setText("·");
        bar.addView(weatherIcon);
        LinearLayout wcol = new LinearLayout(this);
        wcol.setOrientation(LinearLayout.VERTICAL);
        wcol.setPadding(dp(8), 0, 0, 0);
        weatherTemp = text(17, C_TEXT, "sans-serif-medium", Typeface.NORMAL);
        weatherTemp.setText("--°");
        weatherPlace = text(9.5f, C_SUB, "sans-serif", Typeface.NORMAL);
        weatherPlace.setText("Weather");
        wcol.addView(weatherTemp);
        LinearLayout.LayoutParams wplp = new LinearLayout.LayoutParams(-2, -2);
        wplp.topMargin = dp(3);
        wcol.addView(weatherPlace, wplp);
        bar.addView(wcol);

        View div = new View(this);
        div.setBackgroundColor(Color.argb(60, 255, 255, 255));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(dp(1), dp(28));
        divLp.leftMargin = dp(20);
        divLp.rightMargin = dp(20);
        bar.addView(div, divLp);

        // clock
        LinearLayout ccol = new LinearLayout(this);
        ccol.setOrientation(LinearLayout.VERTICAL);
        ccol.setGravity(Gravity.END);
        LinearLayout trow = new LinearLayout(this);
        trow.setOrientation(LinearLayout.HORIZONTAL);
        trow.setGravity(Gravity.BOTTOM);
        clockTime = text(26, C_TEXT, "sans-serif-light", Typeface.NORMAL);
        clockAmPm = text(11, C_SUB, "sans-serif-medium", Typeface.NORMAL);
        clockAmPm.setPadding(dp(4), 0, 0, dp(3));
        trow.addView(clockTime);
        trow.addView(clockAmPm);
        ccol.addView(trow);
        clockDate = text(9.5f, C_SUB, "sans-serif", Typeface.NORMAL);
        LinearLayout.LayoutParams cdl = new LinearLayout.LayoutParams(-2, -2);
        cdl.topMargin = dp(3);
        cdl.gravity = Gravity.END;
        ccol.addView(clockDate, cdl);
        bar.addView(ccol);

        // menu button
        gear = new FrameLayout(this);
        noClip(gear);
        gear.setFocusable(true);
        gear.setClickable(true);
        final GradientDrawable gbg = new GradientDrawable();
        gbg.setShape(GradientDrawable.OVAL);
        gbg.setColor(Color.argb(40, 255, 255, 255));
        gbg.setStroke(dp(1), Color.argb(60, 255, 255, 255));
        gear.setBackground(gbg);
        TextView g = text(18, C_TEXT, "sans-serif", Typeface.NORMAL);
        g.setText("⚙");
        gear.addView(g, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        updateDot = new View(this);
        GradientDrawable ud = new GradientDrawable();
        ud.setShape(GradientDrawable.OVAL);
        ud.setColor(Color.parseColor("#FF4D6D"));
        ud.setStroke(dp(2), Color.parseColor("#0A0F1F"));
        updateDot.setBackground(ud);
        updateDot.setVisibility(View.GONE);
        FrameLayout.LayoutParams udl = new FrameLayout.LayoutParams(dp(12), dp(12), Gravity.TOP | Gravity.END);
        gear.addView(updateDot, udl);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(40), dp(40));
        glp.leftMargin = dp(22);
        bar.addView(gear, glp);
        gear.setOnFocusChangeListener((v, has) -> {
            gbg.setColor(has ? Color.WHITE : Color.argb(40, 255, 255, 255));
            g.setTextColor(has ? Color.parseColor("#0A0F1F") : C_TEXT);
            v.animate().scaleX(has ? 1.18f : 1f).scaleY(has ? 1.18f : 1f).rotation(has ? 60 : 0)
                    .setInterpolator(new OvershootInterpolator(2f)).setDuration(300).start();
            if (has) setHero("Menu", "SETTINGS  ·  HIDDEN APPS  ·  UPDATES", Color.WHITE);
        });
        gear.setOnClickListener(v -> showMainMenu());
        return bar;
    }

    private void breathe(View v) {
        v.animate().alpha(0.25f).scaleX(0.7f).scaleY(0.7f).setDuration(1400)
                .withEndAction(() -> v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(1400)
                        .withEndAction(() -> breathe(v)).start()).start();
    }

    // =====================================================================
    // Apps
    // =====================================================================

    private void reloadApps(boolean force) {
        io.execute(() -> {
            List<AppRepo.App> apps = repo.loadAll();
            Set<String> hidden = repo.hidden();
            List<String> recents = repo.recents();
            StringBuilder sig = new StringBuilder();
            for (AppRepo.App a : apps) sig.append(a.pkg).append(a.label).append(';');
            sig.append('|').append(hidden).append('|').append(recents);
            String s = sig.toString();
            ui.post(() -> {
                if (!force && s.equals(lastSignature)) return;
                lastSignature = s;
                allApps = apps;
                byPkg.clear();
                for (AppRepo.App a : apps) byPkg.put(a.pkg, a);
                buildRows(hidden, recents);
            });
        });
    }

    private void buildRows(Set<String> hidden, List<String> recents) {
        View f = getCurrentFocus();
        String keepPkg = f != null && f.getTag() instanceof String ? (String) f.getTag() : null;
        boolean keepInRecent = f != null && f.getParent() == recentRow;

        recentRow.removeAllViews();
        libraryGrid.removeAllViews();

        List<AppRepo.App> visible = new ArrayList<>();
        for (AppRepo.App a : allApps) if (!hidden.contains(a.pkg)) visible.add(a);

        // ---- recent row ----
        List<AppRepo.App> recent = new ArrayList<>();
        for (String p : recents) {
            AppRepo.App a = byPkg.get(p);
            if (a != null && !hidden.contains(p)) recent.add(a);
        }
        boolean starter = recent.isEmpty();
        if (starter) for (int i = 0; i < Math.min(6, visible.size()); i++) recent.add(visible.get(i));
        recentLabel.setText(starter ? "START HERE" : "RECENT");
        final String recentTag = starter ? "PICK SOMETHING  ·  ☰ FOR OPTIONS" : "RECENTLY USED  ·  ☰ FOR OPTIONS";

        int idx = 0;
        View restore = null;
        for (AppRepo.App a : recent) {
            TileView t = makeAppTile(a, 1.1f, recentTag);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(224), dp(126));
            lp.rightMargin = dp(20);
            recentRow.addView(t, lp);
            entrance(t, idx++);
            if (keepInRecent && a.pkg.equals(keepPkg)) restore = t;
        }

        // ---- library grid ----
        final int cols = 6;
        LinearLayout row = null;
        int count = 0;
        List<View> libTiles = new ArrayList<>();
        for (AppRepo.App a : visible) {
            TileView t = makeAppTile(a, 1.14f, "LIBRARY  ·  ☰ FOR OPTIONS");
            libTiles.add(t);
            if (!keepInRecent && a.pkg.equals(keepPkg)) restore = t;
        }
        libTiles.add(makeSettingsTile());
        for (View t : libTiles) {
            if (count % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                noClip(row);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
                rlp.bottomMargin = dp(16);
                libraryGrid.addView(row, rlp);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(128), dp(72));
            if (count % cols != cols - 1) lp.rightMargin = dp(16);
            row.addView(t, lp);
            entrance(t, idx++);
            count++;
        }

        firstBuild = false;
        if (overlay.getVisibility() == View.VISIBLE) return;
        if (restore != null) restore.requestFocus();
        else if (!gear.isFocused()) focusFirstTile();
    }

    private void entrance(View t, int i) {
        if (!firstBuild) return;
        t.setAlpha(0f);
        t.setTranslationY(dp(28));
        t.animate().alpha(1f).translationY(0).setStartDelay(120 + Math.min(i, 18) * 35L)
                .setDuration(420).setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    private void focusFirstTile() {
        if (recentRow.getChildCount() > 0) recentRow.getChildAt(0).requestFocus();
        else if (libraryGrid.getChildCount() > 0)
            ((ViewGroup) libraryGrid.getChildAt(0)).getChildAt(0).requestFocus();
    }

    private TileView makeAppTile(AppRepo.App a, float scale, String tag) {
        TileView t = new TileView(this, scale);
        t.setApp(a);
        t.setTag(a.pkg);
        t.setOnFocusChangeListener((v, has) -> {
            if (has) {
                setHero(a.label, tag, t.getAccent());
                bg.setAccent(t.getAccent());
            }
        });
        t.setOnClickListener(v -> launch(t, a.pkg));
        t.setOnLongClickListener(v -> { showAppMenu(a); return true; });
        t.setOnKeyListener((v, code, ev) -> {
            if (code == KeyEvent.KEYCODE_MENU && ev.getAction() == KeyEvent.ACTION_UP) {
                showAppMenu(a);
                return true;
            }
            return code == KeyEvent.KEYCODE_MENU;
        });
        return t;
    }

    private TileView makeSettingsTile() {
        TileView t = new TileView(this, 1.14f);
        final int accent = Color.parseColor("#8FA3C7");
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(Color.WHITE);
        final RectF[] last = {new RectF()};
        t.setCustom("Fire TV Settings", accent, (Canvas c, RectF r, float focus) -> {
            if (!r.equals(last[0])) { // build the gradient only when the size changes
                last[0] = new RectF(r);
                p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                        Color.parseColor("#2A3550"), Color.parseColor("#141A2B"), Shader.TileMode.CLAMP));
            }
            c.drawRoundRect(r, dp(10), dp(10), p);
            c.save();
            c.rotate(focus * 90f, r.centerX(), r.centerY() - r.height() * 0.08f);
            tp.setTextSize(r.height() * 0.42f);
            float gw = tp.measureText("⚙");
            c.drawText("⚙", r.centerX() - gw / 2f, r.centerY() + r.height() * 0.08f, tp);
            c.restore();
            tp.setTextSize(r.height() * 0.13f);
            float lw = tp.measureText("Settings");
            c.drawText("Settings", r.centerX() - lw / 2f, r.bottom - r.height() * 0.12f, tp);
        });
        t.setTag("__settings");
        t.setOnFocusChangeListener((v, has) -> {
            if (has) { setHero("Fire TV Settings", "NETWORK  ·  DISPLAY  ·  APPS  ·  REMOTES", accent); bg.setAccent(accent); }
        });
        t.setOnClickListener(v -> { t.pulse(); openFireSettings(); });
        return t;
    }

    private void setHero(String name, String tag, int accent) {
        currentAccent = accent;
        if (!name.equals(heroName.getText().toString())) {
            heroName.setText(name);
            heroName.setAlpha(0f);
            heroName.setTranslationX(dp(22));
            heroName.animate().alpha(1f).translationX(0).setDuration(260)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
        heroTag.setText(tag);
        heroTag.setTextColor(accent);
    }

    private void launch(TileView t, String pkg) {
        Intent i = repo.launchIntent(pkg);
        if (i == null) { showBanner("Can't open that app"); return; }
        t.pulse();
        content.animate().alpha(0f).scaleX(1.06f).scaleY(1.06f).setDuration(220)
                .setInterpolator(new AccelerateInterpolator()).start();
        ui.postDelayed(() -> {
            try {
                startActivity(i);
                repo.markLaunched(pkg);
            } catch (Exception e) {
                content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
                showBanner("Can't open that app");
            }
        }, 200);
    }

    private void openFireSettings() {
        Intent[] tries = {
                new Intent(Settings.ACTION_SETTINGS),
                getPackageManager().getLaunchIntentForPackage("com.amazon.tv.settings.v2"),
                getPackageManager().getLaunchIntentForPackage("com.android.tv.settings")
        };
        for (Intent i : tries) {
            if (i == null) continue;
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Exception ignored) { }
        }
        showBanner("Couldn't open Settings");
    }

    // =====================================================================
    // Clock & weather
    // =====================================================================

    private final SimpleDateFormat fTime = new SimpleDateFormat("h:mm", Locale.US);
    private final SimpleDateFormat fAmPm = new SimpleDateFormat("a", Locale.US);
    private final SimpleDateFormat fDate = new SimpleDateFormat("EEEE, MMM d", Locale.US);

    private void startClock() {
        Runnable tick = new Runnable() {
            @Override public void run() {
                Date now = new Date();
                String t = fTime.format(now);
                if (!t.equals(clockTime.getText().toString())) {
                    if (clockTime.getText().length() > 0) {
                        clockTime.setAlpha(0.2f);
                        clockTime.setTranslationY(-dp(6));
                        clockTime.animate().alpha(1f).translationY(0).setDuration(350).start();
                    }
                    clockTime.setText(t);
                }
                String ap = fAmPm.format(now), dt = fDate.format(now);
                // only touch the text when it actually changes, so the screen isn't re-laid-out every second
                if (!ap.contentEquals(clockAmPm.getText())) clockAmPm.setText(ap);
                if (!dt.contentEquals(clockDate.getText())) clockDate.setText(dt);
                ui.postDelayed(this, 1000);
            }
        };
        tick.run();
    }

    private void refreshWeather() {
        io.execute(() -> {
            try {
                Weather.Result r = Weather.fetch(repo.prefs());
                ui.post(() -> {
                    weatherIcon.setText(r.icon);
                    weatherTemp.setText(r.temp + "°");
                    weatherPlace.setText((r.place.isEmpty() ? r.desc : r.place) + "  ·  " + r.hi + "° / " + r.lo + "°");
                    weatherIcon.setScaleX(0.4f);
                    weatherIcon.setScaleY(0.4f);
                    weatherIcon.animate().scaleX(1f).scaleY(1f)
                            .setInterpolator(new OvershootInterpolator(2.5f)).setDuration(450).start();
                });
            } catch (Exception e) {
                ui.post(() -> {
                    if (weatherTemp.getText().toString().startsWith("--")) weatherPlace.setText("Weather offline");
                });
            }
        });
        ui.removeCallbacks(weatherLoop);
        ui.postDelayed(weatherLoop, 30 * 60 * 1000L);
    }

    private final Runnable weatherLoop = this::refreshWeather;

    // =====================================================================
    // Menus (slide-in panel)
    // =====================================================================

    private static class Item {
        final String label, hint;
        final Runnable action;
        Item(String l, String h, Runnable a) { label = l; hint = h; action = a; }
    }

    private void showMainMenu() {
        int hiddenCount = repo.hidden().size();
        List<Item> items = new ArrayList<>();
        boolean guardOn = HomeGuardService.isEnabled(this)
                && repo.prefs().getBoolean(HomeGuardService.KEY_GUARD_ON, true);
        items.add(new Item("Back & Home guard", guardOn ? "On · Back out of apps lands here"
                : "Off · Back can drop you on Amazon's home", this::showGuardPanel));
        boolean openOnStart = repo.prefs().getBoolean(BootReceiver.KEY_OPEN_ON_START, true);
        items.add(new Item("Open when TV turns on", openOnStart
                ? (HomeGuardService.isEnabled(this) ? "On" : "On · turn on the guard so it works from standby")
                : "Off", () -> {
            repo.prefs().edit().putBoolean(BootReceiver.KEY_OPEN_ON_START, !openOnStart).apply();
            showBanner(openOnStart ? "Won't open on power-up" : "Opens as soon as the TV turns on");
            showMainMenu();
        }));
        items.add(new Item("Hidden apps", hiddenCount == 0 ? "None hidden" : hiddenCount + " hidden", this::showHiddenApps));
        items.add(new Item("Weather location", repo.prefs().getString(Weather.KEY_NAME, "Automatic"), this::showWeatherPanel));
        items.add(new Item(pendingRelease != null ? "Install update" : "Check for updates",
                pendingRelease != null ? "Version " + pendingRelease.version + " is ready" : "You're on " + BuildConfig.VERSION_NAME,
                this::manualUpdateCheck));
        items.add(new Item("Fire TV settings", "Network, display, apps", () -> { closePanel(true); openFireSettings(); }));
        showPanel("Menu", "Overdrive " + BuildConfig.VERSION_NAME, items, null);
    }

    private void showAppMenu(AppRepo.App a) {
        boolean isHidden = repo.hidden().contains(a.pkg);
        List<Item> items = new ArrayList<>();
        items.add(new Item("Open", null, () -> {
            closePanel(false);
            Intent i = repo.launchIntent(a.pkg);
            if (i != null) { startActivity(i); repo.markLaunched(a.pkg); }
        }));
        items.add(new Item(isHidden ? "Unhide" : "Hide app",
                isHidden ? "Put it back in your library" : "Only shows up in Menu → Hidden apps", () -> {
            repo.setHidden(a.pkg, !isHidden);
            closePanel(true);
            showBanner(isHidden ? a.label + " is back in your library" : a.label + " hidden");
            reloadApps(false);
        }));
        items.add(new Item("App info", "Storage, force stop, uninstall", () -> {
            closePanel(false);
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                showBanner("App info isn't available on this TV");
            }
        }));
        showPanel(a.label, isHidden ? "HIDDEN APP" : "APP OPTIONS", items, null);
    }

    private void showGuardPanel() {
        boolean enabled = HomeGuardService.isEnabled(this);
        boolean on = repo.prefs().getBoolean(HomeGuardService.KEY_GUARD_ON, true);
        List<Item> items = new ArrayList<>();
        if (!enabled) {
            items.add(new Item("Check again", "After running the command from your phone", () -> {
                repo.prefs().edit().putBoolean(HomeGuardService.KEY_GUARD_ON, true).apply();
                if (HomeGuardService.isEnabled(this)) { showBanner("Guard is on"); closePanel(true); }
                else showBanner("Not on yet");
            }));
            items.add(new Item("Open Developer options", "Turn on ADB debugging", this::openFireSettings));
            items.add(new Item("Later", null, () -> closePanel(true)));
            showPanel("Back & Home guard",
                    "Fire TV hides this switch, so it's turned on once from your phone:\n\n"
                            + "1. My Fire TV → Developer options → ADB debugging ON\n"
                            + "2. My Fire TV → About → Network: note the IP address\n"
                            + "3. On your phone, open an ADB app, connect to that IP, allow it on the TV\n"
                            + "4. Run:\nsettings put secure enabled_accessibility_services com.vyd02.overdrive/com.vyd02.overdrive.HomeGuardService\n"
                            + "settings put secure accessibility_enabled 1",
                    items, null);
        } else {
            items.add(new Item(on ? "Turn off" : "Turn on", on ? "Let Amazon's home stay open" : "Always come back to Overdrive", () -> {
                repo.prefs().edit().putBoolean(HomeGuardService.KEY_GUARD_ON, !on).apply();
                closePanel(true);
                showBanner(on ? "Guard off" : "Guard on");
            }));
            showPanel("Back & Home guard", on ? "Guard is on." : "Guard is off.", items, null);
        }
    }

    private void maybePromptGuard() {
        if (HomeGuardService.isEnabled(this) || repo.prefs().getBoolean("guard_prompted", false)) return;
        repo.prefs().edit().putBoolean("guard_prompted", true).apply();
        showGuardPanel();
    }

    private void showHiddenApps() {
        Set<String> hidden = repo.hidden();
        List<Item> items = new ArrayList<>();
        for (AppRepo.App a : allApps) {
            if (hidden.contains(a.pkg)) items.add(new Item(a.label, "Open or unhide", () -> showAppMenu(a)));
        }
        showPanel("Hidden apps", items.isEmpty() ? "Nothing hidden. Press ☰ on any app to hide it." : "Only you know these are here",
                items, null);
    }

    private void showWeatherPanel() {
        EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        et.setImeOptions(EditorInfo.IME_ACTION_DONE);
        et.setHint("City or ZIP code");
        et.setText(repo.prefs().getString(Weather.KEY_PLACE, ""));
        et.setTextColor(C_TEXT);
        et.setHintTextColor(C_SUB);
        et.setTextSize(16);
        et.setPadding(dp(16), dp(12), dp(16), dp(12));
        final GradientDrawable eb = new GradientDrawable();
        eb.setCornerRadius(dp(10));
        eb.setColor(Color.argb(40, 255, 255, 255));
        eb.setStroke(dp(2), Color.argb(70, 255, 255, 255));
        et.setBackground(eb);
        et.setOnFocusChangeListener((v, has) -> eb.setStroke(dp(2), has ? currentAccent : Color.argb(70, 255, 255, 255)));
        Runnable save = () -> {
            String q = et.getText().toString().trim();
            hideKeyboard(et);
            if (q.isEmpty()) return;
            showBanner("Looking up " + q + "…");
            io.execute(() -> {
                try {
                    String name = Weather.setPlace(repo.prefs(), q);
                    ui.post(() -> { showBanner("Weather set to " + name); closePanel(true); refreshWeather(); });
                } catch (Exception e) {
                    ui.post(() -> showBanner(e.getMessage() != null && e.getMessage().startsWith("Couldn't")
                            ? e.getMessage() : "No connection, try again"));
                }
            });
        };
        et.setOnEditorActionListener((v, act, ev) -> { save.run(); return true; });
        List<Item> items = new ArrayList<>();
        items.add(new Item("Save", null, save));
        items.add(new Item("Use automatic location", "Based on your internet connection", () -> {
            Weather.clearPlace(repo.prefs());
            closePanel(true);
            showBanner("Weather set to automatic");
            refreshWeather();
        }));
        showPanel("Weather location", "Select the box to type", items, et);
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private void showPanel(String title, String subtitle, List<Item> items, View extra) {
        boolean wasOpen = overlay.getVisibility() == View.VISIBLE;
        if (!wasOpen) {
            focusBeforePanel = getCurrentFocus();
            content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        panel.removeAllViews();

        GradientDrawable pbg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(245, 14, 18, 32), Color.argb(250, 8, 10, 20)});
        panel.setBackground(pbg);

        View bar = new View(this);
        GradientDrawable bd = new GradientDrawable();
        bd.setCornerRadius(dp(2));
        bd.setColor(currentAccent);
        bar.setBackground(bd);
        panel.addView(bar, new LinearLayout.LayoutParams(dp(36), dp(4)));

        TextView t = text(24, C_TEXT, "sans-serif", Typeface.BOLD);
        t.setText(title);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(16);
        panel.addView(t, tlp);
        if (subtitle != null) {
            TextView s = text(11, C_SUB, "sans-serif", Typeface.NORMAL);
            s.setText(subtitle);
            s.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
            slp.topMargin = dp(8);
            slp.bottomMargin = dp(18);
            panel.addView(s, slp);
        }
        if (extra != null) {
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-1, -2);
            elp.bottomMargin = dp(14);
            panel.addView(extra, elp);
        }

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        noClip(sv);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        noClip(list);
        sv.addView(list);
        panel.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        View first = extra;
        for (int i = 0; i < items.size(); i++) {
            View row = panelRow(items.get(i));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.bottomMargin = dp(6);
            list.addView(row, rlp);
            if (first == null) first = row;
            row.setAlpha(0f);
            row.setTranslationX(dp(30));
            row.animate().alpha(1f).translationX(0).setStartDelay(80 + i * 45L).setDuration(300)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }

        TextView hint = text(10, C_SUB, "sans-serif", Typeface.NORMAL);
        hint.setText("BACK to close");
        hint.setLetterSpacing(0.15f);
        panel.addView(hint);

        if (!wasOpen) {
            overlay.setVisibility(View.VISIBLE);
            scrim.setAlpha(0f);
            scrim.animate().alpha(1f).setDuration(250).start();
            panel.setTranslationX(dp(380));
            panel.animate().translationX(0).setDuration(320).setInterpolator(new DecelerateInterpolator(2.5f)).start();
        } else {
            panel.setAlpha(0.4f);
            panel.animate().alpha(1f).setDuration(200).start();
        }
        if (first != null) {
            final View f = first;
            if (f instanceof EditText) {
                // land on Save first so the keyboard doesn't pop up uninvited
                f.post(() -> { if (list.getChildCount() > 0) list.getChildAt(0).requestFocus(); else f.requestFocus(); });
            } else {
                f.post(f::requestFocus);
            }
        }
    }

    private View panelRow(Item it) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setFocusable(true);
        row.setClickable(true);
        final GradientDrawable rb = new GradientDrawable();
        rb.setCornerRadius(dp(10));
        rb.setColor(Color.TRANSPARENT);
        row.setBackground(rb);
        TextView l = text(15, C_TEXT, "sans-serif-medium", Typeface.NORMAL);
        l.setText(it.label);
        row.addView(l);
        TextView h = null;
        if (it.hint != null) {
            h = text(10.5f, C_SUB, "sans-serif", Typeface.NORMAL);
            h.setText(it.hint);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
            hlp.topMargin = dp(4);
            row.addView(h, hlp);
        }
        final TextView hint = h;
        row.setOnFocusChangeListener((v, has) -> {
            rb.setColor(has ? Color.WHITE : Color.TRANSPARENT);
            l.setTextColor(has ? Color.parseColor("#0A0F1F") : C_TEXT);
            if (hint != null) hint.setTextColor(has ? Color.parseColor("#4A5570") : C_SUB);
            v.animate().translationX(has ? dp(6) : 0).scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f)
                    .setDuration(180).start();
        });
        row.setOnClickListener(v -> it.action.run());
        return row;
    }

    private void closePanel(boolean animate) {
        if (overlay.getVisibility() != View.VISIBLE) return;
        Runnable done = () -> {
            overlay.setVisibility(View.GONE);
            content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            if (focusBeforePanel != null && focusBeforePanel.isAttachedToWindow()) focusBeforePanel.requestFocus();
            else focusFirstTile();
        };
        if (!animate) { done.run(); return; }
        scrim.animate().alpha(0f).setDuration(200).start();
        panel.animate().translationX(dp(380)).setDuration(240).setInterpolator(new AccelerateInterpolator())
                .withEndAction(done).start();
    }

    // =====================================================================
    // Updates
    // =====================================================================

    private boolean installing;
    private final Runnable updateLoop = this::autoCheckUpdate;

    /** Runs on its own: checks GitHub every 30 min while Overdrive is open, downloads, and opens the Install screen. */
    private void autoCheckUpdate() {
        ui.removeCallbacks(updateLoop);
        ui.postDelayed(updateLoop, 30 * 60 * 1000L);
        long last = repo.prefs().getLong(KEY_LAST_UPDATE_CHECK, 0);
        if (installing || !hasWindowFocus() || System.currentTimeMillis() - last < 30 * 60 * 1000L) return;
        io.execute(() -> {
            try {
                Updater.Release r = Updater.check();
                repo.prefs().edit().putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply();
                if (r != null) ui.post(() -> {
                    pendingRelease = r;
                    updateDot.setVisibility(View.VISIBLE);
                    if (overlay.getVisibility() == View.VISIBLE) return; // don't yank a menu out from under you
                    if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                        startInstall(r); // shows the one-time permission setup
                    } else {
                        showBanner("Updating to " + r.version + "…");
                        startInstall(r);
                    }
                });
            } catch (Exception ignored) { }
        });
    }

    private void manualUpdateCheck() {
        if (pendingRelease != null) { showUpdatePanel(pendingRelease); return; }
        showBanner("Checking for updates…");
        io.execute(() -> {
            try {
                Updater.Release r = Updater.check();
                repo.prefs().edit().putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply();
                ui.post(() -> {
                    if (r == null) showBanner("You're up to date (" + BuildConfig.VERSION_NAME + ")");
                    else { pendingRelease = r; updateDot.setVisibility(View.VISIBLE); showUpdatePanel(r); }
                });
            } catch (Exception e) {
                ui.post(() -> showBanner("Couldn't reach GitHub"));
            }
        });
    }

    private void showUpdatePanel(Updater.Release r) {
        String notes = r.notes == null ? "" : r.notes.trim();
        if (notes.length() > 220) notes = notes.substring(0, 220) + "…";
        List<Item> items = new ArrayList<>();
        items.add(new Item("Install now", "Downloads and asks the TV to install", () -> startInstall(r)));
        items.add(new Item("Later", null, () -> closePanel(true)));
        showPanel("Update " + r.version, notes.isEmpty() ? "A new version of Overdrive is ready." : notes, items, null);
    }

    private void startInstall(Updater.Release r) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            List<Item> items = new ArrayList<>();
            items.add(new Item("Open settings", "Turn on Overdrive, then come back", () -> {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    openFireSettings();
                }
            }));
            items.add(new Item("Try again", null, () -> startInstall(r)));
            showPanel("One-time setup",
                    "Let Overdrive install its own updates:\nSettings → My Fire TV → Developer options → Install unknown apps → Overdrive → ON",
                    items, null);
            return;
        }
        if (installing) return;
        installing = true;
        showBanner("Downloading update…");
        closePanel(true);
        io.execute(() -> {
            try {
                Updater.install(this, r, pct -> {
                    if (pct % 10 == 0) ui.post(() -> showBanner("Downloading update… " + pct + "%"));
                });
                ui.post(() -> showBanner("Press Install to finish the update"));
            } catch (Exception e) {
                ui.post(() -> showBanner("Update failed: " + (e.getMessage() == null ? "download error" : e.getMessage())));
            } finally {
                ui.postDelayed(() -> installing = false, 60 * 1000L);
            }
        });
    }

    // =====================================================================
    // Notice banner
    // =====================================================================

    private final Runnable hideBanner = () -> banner.animate().alpha(0f).translationY(-dp(30)).setDuration(260)
            .withEndAction(() -> banner.setVisibility(View.INVISIBLE)).start();

    private void showBanner(String msg) {
        banner.setText(msg);
        ui.removeCallbacks(hideBanner);
        if (banner.getVisibility() != View.VISIBLE) {
            banner.setVisibility(View.VISIBLE);
            banner.setAlpha(0f);
            banner.setTranslationY(-dp(30));
        }
        banner.animate().alpha(1f).translationY(0).setInterpolator(new OvershootInterpolator(1.5f))
                .setDuration(320).start();
        ui.postDelayed(hideBanner, 3500);
    }

    // =====================================================================
    // Keys
    // =====================================================================

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Back is caught here, before anything else can see it, so it can never leave Overdrive.
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            View f = getCurrentFocus();
            if (f instanceof EditText) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null && imm.isActive(f) && event.getAction() == KeyEvent.ACTION_UP) hideKeyboard(f);
            }
            if (event.getAction() == KeyEvent.ACTION_UP && event.getRepeatCount() == 0 && !event.isCanceled()) {
                if (overlay.getVisibility() == View.VISIBLE) closePanel(true);
                else { scroll.smoothScrollTo(0, 0); focusFirstTile(); }
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && overlay.getVisibility() != View.VISIBLE) {
            showMainMenu();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back never leaves (dispatchKeyEvent already handles it)
    }

    @SuppressWarnings("unused")
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
