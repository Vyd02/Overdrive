package com.vyd02.overdrive;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    static final String EXTRA_INTRO = "intro";
    private static final int C_TEXT = Color.WHITE;
    private static final int C_SUB = Color.parseColor("#9AA6BF");
    private static final int C_ACCENT = Color.parseColor("#3DA9FF");
    private static final String KEY_LAST_UPDATE_CHECK = "last_update_check";
    private static final String KEY_THEME = "theme";
    private static final String KEY_INTRO = "intro_on";
    private static final String KEY_SAVER = "saver_minutes";
    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private AppRepo repo;
    private Categories cats;
    private Sound sound;
    private float d;

    // views
    private FrameLayout root;
    private ThemeView theme;
    private LinearLayout content;
    private ImageView backA, backB;
    private boolean backAOn = true;
    private TextView heroName, heroTag, heroMeta;
    private TileView heroCard;
    private RowScroller scroll;
    private LinearLayout rowsBox;
    private TextView clockTime, clockAmPm, clockDate;
    private TextView weatherIcon, weatherTemp, weatherPlace;
    private FrameLayout gear;
    private View updateDot;
    private FrameLayout overlay;
    private LinearLayout panel;
    private View scrim;
    private TextView banner;
    private IntroView intro;
    private FrameLayout saver;
    private LinearLayout saverBox;
    private TextView saverTime, saverDate, saverWeather;

    // state
    private List<AppRepo.App> allApps = new ArrayList<>();
    private final Map<String, AppRepo.App> byPkg = new HashMap<>();
    private String lastSignature = "";
    private boolean firstBuild = true;
    private View focusBeforePanel;
    private int panelFocusIndex;
    private Runnable panelOnClose;
    private Updater.Release pendingRelease;
    private int currentAccent = C_ACCENT;
    private int lastDx, lastDy;
    private String movingPkg;          // favorites move mode
    private TileView movingTile;
    private long lastInteraction = System.currentTimeMillis();
    private String lastWeatherLine = "";
    private final Map<String, TileView> tileByKey = new HashMap<>();

    // =====================================================================
    // Lifecycle
    // =====================================================================

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFormat(PixelFormat.RGBA_8888);
        repo = new AppRepo(this);
        cats = new Categories(this, repo.prefs());
        sound = new Sound(this, repo.prefs());
        d = getResources().getDisplayMetrics().density;
        buildUi();
        setContentView(root);
        reloadApps(true);
        startClock();
        refreshWeather();
        ui.postDelayed(this::maybePromptGuard, 3200);
        ui.postDelayed(saverCheck, 15000);

        boolean introOn = repo.prefs().getBoolean(KEY_INTRO, true);
        if (introOn) playIntro(); else content.setAlpha(1f);

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
        sound.release();
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastInteraction = System.currentTimeMillis();
        reloadApps(false);
        if (!intro.isPlaying()) {
            content.setAlpha(0f);
            content.setScaleX(1.04f);
            content.setScaleY(1.04f);
            content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(EASE).start();
        }
        ui.postDelayed(this::autoCheckUpdate, 2500);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (overlay.getVisibility() == View.VISIBLE) closePanel(false);
        hideSaver();
        endMove();
        if (intent.getBooleanExtra(EXTRA_INTRO, false) && repo.prefs().getBoolean(KEY_INTRO, true)) playIntro();
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

    /** Vertical scroller that lets us decide where rows land (console-style snapping). */
    static class RowScroller extends ScrollView {
        RowScroller(Context c) { super(c); }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) { return 0; }
        @Override public boolean executeKeyEvent(KeyEvent e) { return false; }
    }

    /** Horizontal strip that keeps the focused tile in a steady spot. */
    static class Strip extends HorizontalScrollView {
        Strip(Context c) { super(c); }
        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) { return 0; }
        @Override public boolean executeKeyEvent(KeyEvent e) { return false; }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        theme = new ThemeView(this, repo.prefs().getString(KEY_THEME, "nebula"));
        root.addView(theme, new FrameLayout.LayoutParams(-1, -1));

        // spotlight backdrop: soft enlarged art of the focused app, fading into the theme
        backA = backdrop();
        backB = backdrop();
        root.addView(backA, new FrameLayout.LayoutParams(-1, dp(360)));
        root.addView(backB, new FrameLayout.LayoutParams(-1, dp(360)));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.argb(70, 0, 0, 0), Color.argb(120, 0, 0, 0), Color.argb(0, 0, 0, 0)}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, dp(360)));
        View shadeL = new View(this);
        shadeL.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(170, 0, 0, 0), Color.argb(40, 0, 0, 0), Color.argb(0, 0, 0, 0)}));
        root.addView(shadeL, new FrameLayout.LayoutParams(-1, dp(360)));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(48), dp(24), 0, 0);
        content.setAlpha(0f);
        noClip(content);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        View top = buildTopBar();
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, dp(52));
        tlp.rightMargin = dp(48);
        content.addView(top, tlp);
        content.addView(buildHero(), new LinearLayout.LayoutParams(-1, dp(150)));

        scroll = new RowScroller(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setSmoothScrollingEnabled(true);
        noClip(scroll);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        rowsBox = new LinearLayout(this);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        rowsBox.setPadding(0, dp(4), 0, dp(300));
        noClip(rowsBox);
        scroll.addView(rowsBox, new ScrollView.LayoutParams(-1, -2));

        buildSaver();

        // menus
        overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        scrim = new View(this);
        scrim.setBackgroundColor(Color.argb(170, 0, 0, 0));
        overlay.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(30), dp(40), dp(30), dp(30));
        overlay.addView(panel, new FrameLayout.LayoutParams(dp(400), -1, Gravity.END));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

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

        intro = new IntroView(this);
        intro.setVisibility(View.GONE);
        root.addView(intro, new FrameLayout.LayoutParams(-1, -1));
    }

    private ImageView backdrop() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setAlpha(0f);
        return v;
    }

    private View buildHero() {
        FrameLayout hero = new FrameLayout(this);
        noClip(hero);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_VERTICAL);
        heroName = text(32, C_TEXT, "sans-serif", Typeface.BOLD);
        heroName.setSingleLine(true);
        heroName.setEllipsize(TextUtils.TruncateAt.END);
        heroName.setShadowLayer(dp(14), 0, dp(2), Color.argb(180, 0, 0, 0));
        left.addView(heroName, new LinearLayout.LayoutParams(-1, -2));
        heroTag = text(10.5f, C_ACCENT, "sans-serif-medium", Typeface.NORMAL);
        heroTag.setLetterSpacing(0.22f);
        heroTag.setSingleLine(true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = dp(8);
        left.addView(heroTag, tl);
        heroMeta = text(11.5f, Color.argb(200, 255, 255, 255), "sans-serif", Typeface.NORMAL);
        heroMeta.setSingleLine(true);
        heroMeta.setShadowLayer(dp(8), 0, 0, Color.BLACK);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-1, -2);
        ml.topMargin = dp(10);
        left.addView(heroMeta, ml);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(-1, -1);
        llp.rightMargin = dp(330);
        hero.addView(left, llp);

        heroCard = new TileView(this, 1f);
        heroCard.setFocusable(false);
        heroCard.setFocusableInTouchMode(false);
        heroCard.setClickable(false);
        heroCard.setAlwaysLit();
        heroCard.setElevation(dp(18));
        heroCard.setCameraDistance(8000 * d);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(240), dp(135), Gravity.END | Gravity.CENTER_VERTICAL);
        clp.rightMargin = dp(56);
        hero.addView(heroCard, clp);
        return hero;
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        noClip(bar);

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
        gear.addView(updateDot, new FrameLayout.LayoutParams(dp(12), dp(12), Gravity.TOP | Gravity.END));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(40), dp(40));
        glp.leftMargin = dp(22);
        bar.addView(gear, glp);
        gear.setOnFocusChangeListener((v, has) -> {
            gbg.setColor(has ? Color.WHITE : Color.argb(40, 255, 255, 255));
            g.setTextColor(has ? Color.parseColor("#0A0F1F") : C_TEXT);
            v.animate().scaleX(has ? 1.18f : 1f).scaleY(has ? 1.18f : 1f).rotation(has ? 60 : 0)
                    .setInterpolator(new OvershootInterpolator(2f)).setDuration(300).start();
            if (has) {
                setHero("Menu", "THEMES  ·  SOUNDS  ·  SCREENSAVER  ·  UPDATES", "Press OK to open", Color.WHITE, null, null);
                theme.focusAt(0.95f, 0.08f, lastDx, lastDy);
            }
        });
        gear.setOnClickListener(v -> { sound.play(Sound.OPEN); showMainMenu(); });
        return bar;
    }

    private void breathe(View v) {
        v.animate().alpha(0.25f).scaleX(0.7f).scaleY(0.7f).setDuration(1400)
                .withEndAction(() -> v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(1400)
                        .withEndAction(() -> breathe(v)).start()).start();
    }

    // =====================================================================
    // Rows
    // =====================================================================

    private void reloadApps(boolean force) {
        io.execute(() -> {
            List<AppRepo.App> apps = repo.loadAll();
            List<Inputs.Input> inputs = Inputs.list(this);
            Map<String, String> catOf = new HashMap<>();
            for (AppRepo.App a : apps) catOf.put(a.pkg, cats.of(a.pkg));
            StringBuilder sig = new StringBuilder();
            for (AppRepo.App a : apps) sig.append(a.pkg).append(a.label).append(catOf.get(a.pkg)).append(';');
            for (Inputs.Input in : inputs) sig.append(in.id).append(in.label);
            sig.append('|').append(repo.hidden()).append('|').append(repo.recents()).append('|').append(repo.favorites());
            String s = sig.toString();
            ui.post(() -> {
                if (!force && s.equals(lastSignature)) return;
                if (movingPkg != null) return; // don't rebuild under a move
                lastSignature = s;
                allApps = apps;
                byPkg.clear();
                for (AppRepo.App a : apps) byPkg.put(a.pkg, a);
                buildRows(inputs, catOf);
            });
        });
    }

    private void buildRows(List<Inputs.Input> inputs, Map<String, String> catOf) {
        View f = getCurrentFocus();
        String keepKey = f instanceof TileView && f.getTag() instanceof String ? (String) f.getTag() : null;

        rowsBox.removeAllViews();
        tileByKey.clear();
        Set<String> hidden = repo.hidden();
        List<AppRepo.App> visible = new ArrayList<>();
        for (AppRepo.App a : allApps) if (!hidden.contains(a.pkg)) visible.add(a);
        int idx = 0;

        // favorites
        List<String> favs = repo.favorites();
        List<AppRepo.App> favApps = new ArrayList<>();
        for (String p : favs) { AppRepo.App a = byPkg.get(p); if (a != null && !hidden.contains(p)) favApps.add(a); }
        if (!favApps.isEmpty()) {
            LinearLayout strip = addRow("FAVORITES", "fav");
            for (AppRepo.App a : favApps) idx = addAppTile(strip, a, "fav", 208, 117, 1.1f, "FAVORITE", idx);
        }

        // recent
        List<AppRepo.App> recent = new ArrayList<>();
        for (String p : repo.recents()) { AppRepo.App a = byPkg.get(p); if (a != null && !hidden.contains(p)) recent.add(a); }
        boolean starter = recent.isEmpty() && favApps.isEmpty();
        if (starter) for (int i = 0; i < Math.min(6, visible.size()); i++) recent.add(visible.get(i));
        if (!recent.isEmpty()) {
            LinearLayout strip = addRow(starter ? "START HERE" : "RECENT", "rec");
            for (AppRepo.App a : recent) idx = addAppTile(strip, a, "rec", 208, 117, 1.1f, starter ? "PICK SOMETHING" : "RECENTLY USED", idx);
        }

        // HDMI inputs
        if (!inputs.isEmpty()) {
            LinearLayout strip = addRow("INPUTS", "in");
            for (Inputs.Input in : inputs) idx = addInputTile(strip, in, idx);
        }

        // categories
        Map<String, List<AppRepo.App>> byCat = new LinkedHashMap<>();
        for (String c : Categories.ORDER) byCat.put(c, new ArrayList<>());
        for (AppRepo.App a : visible) {
            List<AppRepo.App> l = byCat.get(catOf.get(a.pkg));
            if (l == null) l = byCat.get(Categories.APPS);
            l.add(a);
        }
        for (Map.Entry<String, List<AppRepo.App>> e : byCat.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            String key = "cat" + e.getKey();
            LinearLayout strip = addRow(e.getKey().toUpperCase(Locale.US), key);
            for (AppRepo.App a : e.getValue()) idx = addAppTile(strip, a, key, 176, 99, 1.12f, e.getKey().toUpperCase(Locale.US), idx);
        }

        // quick settings
        LinearLayout qs = addRow("QUICK SETTINGS", "qs");
        idx = addQuickTile(qs, "Wi-Fi", "wifi", Settings.ACTION_WIFI_SETTINGS, Color.parseColor("#3DA9FF"), idx);
        idx = addQuickTile(qs, "Bluetooth & Remotes", "bt", Settings.ACTION_BLUETOOTH_SETTINGS, Color.parseColor("#7C6CFF"), idx);
        idx = addQuickTile(qs, "Display & Sounds", "display", Settings.ACTION_DISPLAY_SETTINGS, Color.parseColor("#2BD9A8"), idx);
        idx = addQuickTile(qs, "Applications", "apps", Settings.ACTION_APPLICATION_SETTINGS, Color.parseColor("#FFB13D"), idx);
        addQuickTile(qs, "All Settings", "gear", Settings.ACTION_SETTINGS, Color.parseColor("#8FA3C7"), idx);

        firstBuild = false;
        if (overlay.getVisibility() == View.VISIBLE) return;
        TileView restore = keepKey != null ? tileByKey.get(keepKey) : null;
        if (restore != null) restore.requestFocus();
        else if (!gear.isFocused()) focusFirstTile();
    }

    private LinearLayout addRow(String title, String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setTag("row:" + key);
        noClip(row);
        TextView label = text(10.5f, C_TEXT, "sans-serif-medium", Typeface.NORMAL);
        label.setLetterSpacing(0.3f);
        label.setText(title);
        label.setAlpha(0.55f);
        label.setTag("label");
        row.addView(label);
        Strip hs = new Strip(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setPadding(dp(4), dp(12), dp(60), dp(14));
        noClip(hs);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        noClip(strip);
        hs.addView(strip);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.leftMargin = -dp(4);
        row.addView(hs, hlp);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = dp(6);
        rowsBox.addView(row, rlp);
        return strip;
    }

    private void placeTile(LinearLayout strip, TileView t, int w, int h, int idx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(w), dp(h));
        lp.rightMargin = dp(18);
        strip.addView(t, lp);
        if (firstBuild) {
            t.setAlpha(0f);
            t.setTranslationY(dp(30));
            long base = intro.isPlaying() ? 1900 : 120;
            t.animate().alpha(1f).translationY(0).setStartDelay(base + Math.min(idx, 24) * 30L)
                    .setDuration(420).setInterpolator(EASE).start();
        }
    }

    private int addAppTile(LinearLayout strip, AppRepo.App a, String rowKey, int w, int h, float scale, String tag, int idx) {
        TileView t = new TileView(this, scale);
        t.setApp(a);
        String key = rowKey + ":" + a.pkg;
        t.setTag(key);
        tileByKey.put(key, t);
        t.setOnFocusChangeListener((v, has) -> {
            if (!has) return;
            String meta = metaFor(a);
            setHero(a.label, tag + "  ·  ☰ OPTIONS", meta, t.getAccent(), a.blurBmp, t);
            onTileFocused(t);
        });
        t.setOnClickListener(v -> launch(t, a.pkg));
        t.setOnLongClickListener(v -> { showAppMenu(a, t, rowKey); return true; });
        t.setOnKeyListener((v, code, ev) -> {
            if (code == KeyEvent.KEYCODE_MENU) {
                if (ev.getAction() == KeyEvent.ACTION_UP) showAppMenu(a, t, rowKey);
                return true;
            }
            return false;
        });
        placeTile(strip, t, w, h, idx);
        return idx + 1;
    }

    private int addInputTile(LinearLayout strip, Inputs.Input in, int idx) {
        TileView t = new TileView(this, 1.12f);
        final int accent = Color.parseColor("#FF5A7A");
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint port = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(Color.WHITE);
        tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        final Path shape = new Path();
        final RectF[] last = {new RectF()};
        t.setCustom(in.label, accent, (c, r, focus) -> {
            if (!r.equals(last[0])) {
                last[0] = new RectF(r);
                p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                        Color.parseColor("#4A1830"), Color.parseColor("#1A0A14"), Shader.TileMode.CLAMP));
            }
            c.drawRoundRect(r, dp(10), dp(10), p);
            // HDMI port glyph
            float cw = r.width() * 0.26f, ch = r.height() * 0.2f, cx = r.centerX(), cy = r.top + r.height() * 0.38f;
            shape.reset();
            shape.moveTo(cx - cw / 2, cy - ch / 2);
            shape.lineTo(cx + cw / 2, cy - ch / 2);
            shape.lineTo(cx + cw / 2, cy + ch * 0.1f);
            shape.lineTo(cx + cw * 0.35f, cy + ch / 2);
            shape.lineTo(cx - cw * 0.35f, cy + ch / 2);
            shape.lineTo(cx - cw / 2, cy + ch * 0.1f);
            shape.close();
            port.setStyle(Paint.Style.STROKE);
            port.setStrokeWidth(dp(2));
            port.setColor(Themes.withA(accent, 160 + (int) (95 * focus)));
            c.drawPath(shape, port);
            tp.setTextSize(r.height() * 0.15f);
            CharSequence s = TextUtils.ellipsize(in.label, tp, r.width() - dp(16), TextUtils.TruncateAt.END);
            float lw = tp.measureText(s, 0, s.length());
            c.drawText(s, 0, s.length(), r.centerX() - lw / 2f, r.bottom - r.height() * 0.16f, tp);
        });
        String key = "in:" + in.id;
        t.setTag(key);
        tileByKey.put(key, t);
        t.setOnFocusChangeListener((v, has) -> {
            if (!has) return;
            setHero(in.label, "INPUT  ·  " + in.kind, "Switch the TV to this input", accent, null, t);
            onTileFocused(t);
        });
        t.setOnClickListener(v -> {
            t.pulse();
            sound.play(Sound.SELECT);
            theme.select();
            try { startActivity(Inputs.intent(in)); } catch (Exception e) { showBanner("This TV didn't let Overdrive switch inputs"); }
        });
        placeTile(strip, t, 176, 99, idx);
        return idx + 1;
    }

    private int addQuickTile(LinearLayout strip, String label, String icon, String action, int accent, int idx) {
        TileView t = new TileView(this, 1.12f);
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ic = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(Color.WHITE);
        tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        final RectF[] last = {new RectF()};
        final RectF tmp = new RectF();
        t.setCustom(label, accent, (c, r, focus) -> {
            if (!r.equals(last[0])) {
                last[0] = new RectF(r);
                p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                        TileView.darken(accent, 0.42f), TileView.darken(accent, 0.16f), Shader.TileMode.CLAMP));
            }
            c.drawRoundRect(r, dp(10), dp(10), p);
            float cx = r.centerX(), cy = r.top + r.height() * 0.4f, s = r.height() * 0.2f;
            ic.setColor(Color.WHITE);
            ic.setStyle(Paint.Style.STROKE);
            ic.setStrokeWidth(dp(2.2f));
            ic.setStrokeCap(Paint.Cap.ROUND);
            switch (icon) {
                case "wifi":
                    for (int i = 1; i <= 3; i++) {
                        float rr = s * 0.42f * i;
                        tmp.set(cx - rr, cy + s * 0.7f - rr, cx + rr, cy + s * 0.7f + rr);
                        c.drawArc(tmp, 225, 90, false, ic);
                    }
                    ic.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx, cy + s * 0.62f, dp(2.4f), ic);
                    break;
                case "bt":
                    c.drawLine(cx, cy - s, cx, cy + s, ic);
                    c.drawLine(cx, cy - s, cx + s * 0.55f, cy - s * 0.5f, ic);
                    c.drawLine(cx + s * 0.55f, cy - s * 0.5f, cx - s * 0.5f, cy + s * 0.45f, ic);
                    c.drawLine(cx, cy + s, cx + s * 0.55f, cy + s * 0.5f, ic);
                    c.drawLine(cx + s * 0.55f, cy + s * 0.5f, cx - s * 0.5f, cy - s * 0.45f, ic);
                    break;
                case "display":
                    tmp.set(cx - s * 1.2f, cy - s * 0.75f, cx + s * 1.2f, cy + s * 0.6f);
                    c.drawRoundRect(tmp, dp(3), dp(3), ic);
                    c.drawLine(cx - s * 0.5f, cy + s * 0.95f, cx + s * 0.5f, cy + s * 0.95f, ic);
                    break;
                case "apps":
                    ic.setStyle(Paint.Style.FILL);
                    float q = s * 0.38f, gap = s * 0.16f;
                    for (int yy = 0; yy < 2; yy++) for (int xx = 0; xx < 2; xx++) {
                        float l = cx - q - gap / 2 + xx * (q + gap), tt = cy - q - gap / 2 + yy * (q + gap);
                        tmp.set(l, tt, l + q, tt + q);
                        c.drawRoundRect(tmp, dp(2), dp(2), ic);
                    }
                    break;
                default:
                    c.save();
                    c.rotate(focus * 90f, cx, cy);
                    ic.setStyle(Paint.Style.STROKE);
                    c.drawCircle(cx, cy, s * 0.42f, ic);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI * 2 * i / 8;
                        c.drawLine(cx + (float) Math.cos(a) * s * 0.62f, cy + (float) Math.sin(a) * s * 0.62f,
                                cx + (float) Math.cos(a) * s * 0.9f, cy + (float) Math.sin(a) * s * 0.9f, ic);
                    }
                    c.restore();
            }
            tp.setTextSize(r.height() * 0.14f);
            CharSequence ss = TextUtils.ellipsize(label, tp, r.width() - dp(14), TextUtils.TruncateAt.END);
            float lw = tp.measureText(ss, 0, ss.length());
            c.drawText(ss, 0, ss.length(), r.centerX() - lw / 2f, r.bottom - r.height() * 0.15f, tp);
        });
        String key = "qs:" + icon;
        t.setTag(key);
        tileByKey.put(key, t);
        t.setOnFocusChangeListener((v, has) -> {
            if (!has) return;
            setHero(label, "QUICK SETTINGS", "Opens Fire TV settings", accent, null, t);
            onTileFocused(t);
        });
        t.setOnClickListener(v -> {
            t.pulse();
            sound.play(Sound.SELECT);
            theme.select();
            openSettings(action);
        });
        placeTile(strip, t, 150, 84, idx);
        return idx + 1;
    }

    private String metaFor(AppRepo.App a) {
        long t = repo.lastOpened(a.pkg);
        String cat = cats.of(a.pkg);
        if (t == 0) return cat;
        long mins = (System.currentTimeMillis() - t) / 60000;
        String ago;
        if (mins < 1) ago = "just now";
        else if (mins < 60) ago = mins + (mins == 1 ? " minute ago" : " minutes ago");
        else if (mins < 60 * 24) { long h = mins / 60; ago = h + (h == 1 ? " hour ago" : " hours ago"); }
        else { long dd = mins / (60 * 24); ago = dd == 1 ? "yesterday" : dd + " days ago"; }
        return cat + "  ·  Opened " + ago;
    }

    /** Everything that happens when focus lands on a tile: theme reacts, rows glide into place. */
    private void onTileFocused(TileView t) {
        int[] loc = new int[2];
        t.getLocationOnScreen(loc);
        float nx = (loc[0] + t.getWidth() / 2f) / Math.max(1, root.getWidth());
        float ny = (loc[1] + t.getHeight() / 2f) / Math.max(1, root.getHeight());
        theme.focusAt(nx, ny, lastDx, lastDy);
        theme.setAccent(t.getAccent());
        lastDx = 0;
        lastDy = 0;

        // row snapping
        View strip = (View) t.getParent();
        Strip hs = (Strip) strip.getParent();
        View row = (View) hs.getParent();
        scroll.smoothScrollTo(0, Math.max(0, row.getTop() - dp(4)));
        int target = Math.max(0, t.getLeft() - dp(200));
        hs.smoothScrollTo(target, 0);
        // light up this row's label, dim the rest
        for (int i = 0; i < rowsBox.getChildCount(); i++) {
            View r = rowsBox.getChildAt(i);
            View label = r.findViewWithTag("label");
            boolean on = r == row;
            if (label != null) label.animate().alpha(on ? 1f : 0.5f).setDuration(200).start();
            r.animate().alpha(on ? 1f : 0.82f).setDuration(250).start();
        }
    }

    private void focusFirstTile() {
        for (int i = 0; i < rowsBox.getChildCount(); i++) {
            View row = rowsBox.getChildAt(i);
            View strip = ((ViewGroup) ((ViewGroup) row).getChildAt(1)).getChildAt(0);
            if (strip instanceof ViewGroup && ((ViewGroup) strip).getChildCount() > 0) {
                ((ViewGroup) strip).getChildAt(0).requestFocus();
                return;
            }
        }
    }

    // =====================================================================
    // Spotlight
    // =====================================================================

    private String heroKey = "";

    private void setHero(String name, String tag, String meta, int accent, Bitmap blur, TileView card) {
        currentAccent = accent;
        String key = name + "|" + tag;
        boolean changed = !key.equals(heroKey);
        heroKey = key;
        if (!name.contentEquals(heroName.getText())) {
            heroName.setText(name);
            heroName.setAlpha(0f);
            heroName.setTranslationX(dp(26));
            heroName.animate().alpha(1f).translationX(0).setDuration(300).setInterpolator(EASE).start();
        }
        heroTag.setText(tag);
        heroTag.setTextColor(accent);
        heroMeta.setText(meta == null ? "" : meta);
        if (changed && meta != null) {
            heroMeta.setAlpha(0f);
            heroMeta.animate().alpha(1f).setStartDelay(90).setDuration(260).start();
        }

        if (card != null && changed) {
            heroCard.copyFrom(card);
            heroCard.setVisibility(View.VISIBLE);
            heroCard.setAlpha(0f);
            heroCard.setTranslationX(dp(40));
            heroCard.setRotationY(-14f);
            heroCard.animate().alpha(1f).translationX(0).rotationY(0).setDuration(380).setInterpolator(EASE).start();
        } else if (card == null) {
            heroCard.animate().alpha(0f).setDuration(200).start();
        }

        if (changed) crossfadeBackdrop(blur);
    }

    private void crossfadeBackdrop(Bitmap blur) {
        ImageView in = backAOn ? backB : backA;
        ImageView out = backAOn ? backA : backB;
        backAOn = !backAOn;
        out.animate().alpha(0f).setDuration(450).start();
        if (blur == null) return;
        BitmapDrawable bd = new BitmapDrawable(getResources(), blur);
        bd.setFilterBitmap(true);
        in.setImageDrawable(bd);
        in.setScaleX(1.12f);
        in.setScaleY(1.12f);
        in.animate().alpha(0.55f).scaleX(1.0f).scaleY(1.0f).setDuration(1400).setInterpolator(EASE).start();
    }

    // =====================================================================
    // Launching
    // =====================================================================

    private void launch(TileView t, String pkg) {
        Intent i = repo.launchIntent(pkg);
        if (i == null) { showBanner("Can't open that app"); return; }
        t.pulse();
        sound.play(Sound.SELECT);
        theme.select();
        content.animate().alpha(0f).scaleX(1.06f).scaleY(1.06f).setDuration(240)
                .setInterpolator(new AccelerateInterpolator()).start();
        ui.postDelayed(() -> {
            try {
                startActivity(i);
                repo.markLaunched(pkg);
            } catch (Exception e) {
                content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
                showBanner("Can't open that app");
            }
        }, 230);
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            openFireSettings();
        }
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
    // Intro & screensaver
    // =====================================================================

    private void playIntro() {
        content.setAlpha(0f);
        sound.play(Sound.INTRO);
        intro.play(currentAccent, () -> {
            content.setScaleX(0.96f);
            content.setScaleY(0.96f);
            content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(500).setInterpolator(EASE).start();
        });
    }

    private void buildSaver() {
        saver = new FrameLayout(this);
        saver.setVisibility(View.GONE);
        saverBox = new LinearLayout(this);
        saverBox.setOrientation(LinearLayout.VERTICAL);
        saverBox.setGravity(Gravity.CENTER_HORIZONTAL);
        saverTime = text(96, C_TEXT, "sans-serif-thin", Typeface.NORMAL);
        saverTime.setShadowLayer(dp(20), 0, 0, Color.argb(160, 0, 0, 0));
        saverDate = text(18, Color.argb(220, 255, 255, 255), "sans-serif-light", Typeface.NORMAL);
        saverDate.setLetterSpacing(0.08f);
        saverWeather = text(15, Color.argb(200, 255, 255, 255), "sans-serif", Typeface.NORMAL);
        saverBox.addView(saverTime);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
        dl.topMargin = dp(10);
        saverBox.addView(saverDate, dl);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.topMargin = dp(16);
        saverBox.addView(saverWeather, wl);
        saver.addView(saverBox, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        root.addView(saver, new FrameLayout.LayoutParams(-1, -1));
    }

    private final Runnable saverCheck = new Runnable() {
        @Override public void run() {
            int mins = repo.prefs().getInt(KEY_SAVER, 3);
            if (mins > 0 && saver.getVisibility() != View.VISIBLE && hasWindowFocus()
                    && overlay.getVisibility() != View.VISIBLE && !intro.isPlaying()
                    && System.currentTimeMillis() - lastInteraction > mins * 60_000L) {
                showSaver();
            }
            ui.postDelayed(this, 15000);
        }
    };

    private void showSaver() {
        updateSaverText();
        saver.setVisibility(View.VISIBLE);
        saver.setAlpha(0f);
        saver.animate().alpha(1f).setDuration(1200).start();
        content.animate().alpha(0f).setDuration(900).start();
        backA.animate().alpha(0f).setDuration(900).start();
        backB.animate().alpha(0f).setDuration(900).start();
        drift();
    }

    private void drift() {
        if (saver.getVisibility() != View.VISIBLE) return;
        float maxX = root.getWidth() * 0.25f, maxY = root.getHeight() * 0.22f;
        saverBox.animate().translationX((float) (Math.random() * 2 - 1) * maxX)
                .translationY((float) (Math.random() * 2 - 1) * maxY)
                .setDuration(18000).setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(this::drift).start();
        updateSaverText();
    }

    private void updateSaverText() {
        Date now = new Date();
        saverTime.setText(fTime.format(now));
        saverDate.setText(fDate.format(now).toUpperCase(Locale.US));
        saverWeather.setText(lastWeatherLine);
    }

    private boolean hideSaver() {
        if (saver.getVisibility() != View.VISIBLE) return false;
        saverBox.animate().cancel();
        saver.animate().alpha(0f).setDuration(300).withEndAction(() -> saver.setVisibility(View.GONE)).start();
        content.animate().alpha(1f).setDuration(400).start();
        (backAOn ? backA : backB).animate().alpha(0.55f).setDuration(400).start();
        return true;
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
                if (!t.contentEquals(clockTime.getText())) {
                    if (clockTime.getText().length() > 0) {
                        clockTime.setAlpha(0.2f);
                        clockTime.setTranslationY(-dp(6));
                        clockTime.animate().alpha(1f).translationY(0).setDuration(350).start();
                    }
                    clockTime.setText(t);
                    if (saver.getVisibility() == View.VISIBLE) saverTime.setText(t);
                }
                String ap = fAmPm.format(now), dt = fDate.format(now);
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
                    lastWeatherLine = r.icon + "  " + r.temp + "°  " + r.desc + (r.place.isEmpty() ? "" : "  ·  " + r.place);
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
    // Menus
    // =====================================================================

    private static class Item {
        final String label, hint;
        final Runnable action;
        Runnable onFocus;
        Item(String l, String h, Runnable a) { label = l; hint = h; action = a; }
        Item focus(Runnable r) { onFocus = r; return this; }
    }

    private void showMainMenu() {
        List<Item> items = new ArrayList<>();
        items.add(new Item("Themes", Themes.name(theme.themeId()) + "  ·  live preview", this::showThemes));
        boolean snd = sound.on();
        items.add(new Item("Sounds", snd ? "On" : "Off", () -> {
            repo.prefs().edit().putBoolean(Sound.KEY_SOUNDS, !snd).apply();
            if (!snd) sound.play(Sound.SELECT);
            showMainMenu();
        }));
        boolean introOn = repo.prefs().getBoolean(KEY_INTRO, true);
        items.add(new Item("Startup intro", introOn ? "On  ·  plays when the TV turns on" : "Off", () -> {
            repo.prefs().edit().putBoolean(KEY_INTRO, !introOn).apply();
            showMainMenu();
        }));
        int mins = repo.prefs().getInt(KEY_SAVER, 3);
        items.add(new Item("Screensaver", mins == 0 ? "Off" : "After " + mins + " min idle", () -> {
            int next = mins == 0 ? 2 : mins == 2 ? 3 : mins == 3 ? 5 : mins == 5 ? 10 : 0;
            repo.prefs().edit().putInt(KEY_SAVER, next).apply();
            showMainMenu();
        }));
        boolean guardOn = HomeGuardService.isEnabled(this) && repo.prefs().getBoolean(HomeGuardService.KEY_GUARD_ON, true);
        items.add(new Item("Back & Home guard", guardOn ? "On" : "Off  ·  Back can drop you on Amazon's home", this::showGuardPanel));
        boolean openOnStart = repo.prefs().getBoolean(BootReceiver.KEY_OPEN_ON_START, true);
        items.add(new Item("Open when TV turns on", openOnStart
                ? (HomeGuardService.isEnabled(this) ? "On" : "On  ·  needs the guard to work from standby") : "Off", () -> {
            repo.prefs().edit().putBoolean(BootReceiver.KEY_OPEN_ON_START, !openOnStart).apply();
            showMainMenu();
        }));
        int hiddenCount = repo.hidden().size();
        items.add(new Item("Hidden apps", hiddenCount == 0 ? "None hidden" : hiddenCount + " hidden", this::showHiddenApps));
        items.add(new Item("Weather location", repo.prefs().getString(Weather.KEY_NAME, "Automatic"), this::showWeatherPanel));
        items.add(new Item(pendingRelease != null ? "Install update" : "Check for updates",
                pendingRelease != null ? "Version " + pendingRelease.version + " is ready" : "You're on " + BuildConfig.VERSION_NAME,
                this::manualUpdateCheck));
        items.add(new Item("Fire TV settings", "Network, display, apps", () -> { closePanel(true); openFireSettings(); }));
        showPanel("Menu", "Overdrive " + BuildConfig.VERSION_NAME, items, null);
    }

    private void showThemes() {
        final String original = theme.themeId();
        final boolean[] chosen = {false};
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < Themes.IDS.length; i++) {
            final String id = Themes.IDS[i];
            items.add(new Item(Themes.NAMES[i] + (id.equals(original) ? "  ✓" : ""), Themes.BLURBS[i], () -> {
                chosen[0] = true;
                repo.prefs().edit().putString(KEY_THEME, id).apply();
                theme.select();
                showBanner(Themes.name(id) + " theme on");
                closePanel(true);
            }).focus(() -> {
                if (!id.equals(theme.themeId())) {
                    theme.setTheme(id);
                    theme.setAccent(currentAccent);
                    sound.play(Sound.THEME);
                }
            }));
        }
        for (int i = 0; i < Themes.IDS.length; i++) if (Themes.IDS[i].equals(original)) panelFocusIndex = i;
        showPanel("Themes", "Move up and down to try them live. OK keeps it.", items, null);
        scrim.animate().alpha(0.25f).setDuration(250).start(); // let the theme shine through while choosing
        panelOnClose = () -> {
            if (!chosen[0] && !original.equals(theme.themeId())) theme.setTheme(original);
        };
    }

    private void showAppMenu(AppRepo.App a, TileView t, String rowKey) {
        sound.play(Sound.OPEN);
        boolean isHidden = repo.hidden().contains(a.pkg);
        List<String> favs = repo.favorites();
        boolean pinned = favs.contains(a.pkg);
        List<Item> items = new ArrayList<>();
        items.add(new Item("Open", null, () -> { closePanel(false); launch(t, a.pkg); }));
        items.add(new Item(pinned ? "Unpin from Favorites" : "Pin to Favorites", pinned ? null : "Adds it to the top row", () -> {
            List<String> f = repo.favorites();
            if (pinned) f.remove(a.pkg); else f.add(a.pkg);
            repo.setFavorites(f);
            closePanel(true);
            showBanner(pinned ? a.label + " unpinned" : a.label + " pinned to Favorites");
            reloadApps(false);
        }));
        if (pinned && "fav".equals(rowKey)) {
            items.add(new Item("Move", "Use ◀ ▶ to slide it, OK to drop", () -> {
                closePanel(false);
                startMove(a.pkg, t);
            }));
        }
        items.add(new Item("Move to category", "Now in " + cats.of(a.pkg), () -> showCategoryPicker(a)));
        items.add(new Item(isHidden ? "Unhide" : "Hide app",
                isHidden ? "Put it back in your library" : "Only shows up in Menu → Hidden apps", () -> {
            repo.setHidden(a.pkg, !isHidden);
            if (!isHidden) { List<String> f = repo.favorites(); f.remove(a.pkg); repo.setFavorites(f); }
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

    private void showCategoryPicker(AppRepo.App a) {
        List<Item> items = new ArrayList<>();
        String now = cats.of(a.pkg);
        for (String c : Categories.ORDER) {
            items.add(new Item(c + (c.equals(now) ? "  ✓" : ""), null, () -> {
                cats.set(a.pkg, c);
                closePanel(true);
                showBanner(a.label + " moved to " + c);
                reloadApps(false);
            }));
        }
        showPanel("Move to category", a.label, items, null);
    }

    // ---- favorites move mode ----

    private void startMove(String pkg, TileView t) {
        movingPkg = pkg;
        movingTile = t;
        t.requestFocus();
        t.animate().translationY(-dp(10)).scaleX(1.2f).scaleY(1.2f).setDuration(220).setInterpolator(EASE).start();
        bob(t);
        showBanner("◀ ▶ to move  ·  OK to drop");
    }

    private void bob(View t) {
        if (movingTile != t) return;
        t.animate().rotation(2f).setDuration(160).withEndAction(() -> {
            if (movingTile != t) { t.setRotation(0); return; }
            t.animate().rotation(-2f).setDuration(160).withEndAction(() -> bob(t)).start();
        }).start();
    }

    private void stepMove(int dir) {
        LinearLayout strip = (LinearLayout) movingTile.getParent();
        int i = strip.indexOfChild(movingTile);
        int j = i + dir;
        if (j < 0 || j >= strip.getChildCount()) { sound.play(Sound.BUMP); return; }
        sound.play(Sound.MOVE);
        strip.removeView(movingTile);
        strip.addView(movingTile, j);
        movingTile.requestFocus();
        List<String> f = repo.favorites();
        if (f.remove(movingPkg)) {
            int to = Math.max(0, Math.min(f.size(), j));
            f.add(to, movingPkg);
            repo.setFavorites(f);
        }
        Strip hs = (Strip) strip.getParent();
        movingTile.post(() -> hs.smoothScrollTo(Math.max(0, movingTile.getLeft() - dp(200)), 0));
    }

    private void endMove() {
        if (movingTile == null) return;
        TileView t = movingTile;
        movingTile = null;
        movingPkg = null;
        t.animate().cancel();
        t.setRotation(0);
        t.animate().translationY(0).scaleX(t.isFocused() ? 1.1f : 1f).scaleY(t.isFocused() ? 1.1f : 1f)
                .setDuration(240).setInterpolator(new OvershootInterpolator(2f)).start();
        sound.play(Sound.SELECT);
        theme.select();
        lastSignature = ""; // let the next reload pick up the new order
    }

    private void showHiddenApps() {
        Set<String> hidden = repo.hidden();
        List<Item> items = new ArrayList<>();
        for (AppRepo.App a : allApps) {
            if (hidden.contains(a.pkg)) items.add(new Item(a.label, "Open or unhide", () -> {
                TileView dummy = new TileView(this, 1f);
                dummy.setApp(a);
                showAppMenu(a, dummy, "hidden");
            }));
        }
        showPanel("Hidden apps", items.isEmpty() ? "Nothing hidden. Press ☰ on any app to hide it." : "Only you know these are here",
                items, null);
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
        if (overlay.getVisibility() == View.VISIBLE || intro.isPlaying()) return;
        repo.prefs().edit().putBoolean("guard_prompted", true).apply();
        showGuardPanel();
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
        } else if (panelOnClose != null) {
            Runnable r = panelOnClose;
            panelOnClose = null;
            r.run();
        }
        panel.removeAllViews();
        panel.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(240, 14, 18, 32), Color.argb(250, 8, 10, 20)}));

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

        View first = null;
        int want = panelFocusIndex;
        panelFocusIndex = 0;
        for (int i = 0; i < items.size(); i++) {
            View row = panelRow(items.get(i));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.bottomMargin = dp(6);
            list.addView(row, rlp);
            if (first == null || i == want) first = row;
            row.setAlpha(0f);
            row.setTranslationX(dp(30));
            row.animate().alpha(1f).translationX(0).setStartDelay(70 + i * 40L).setDuration(300).setInterpolator(EASE).start();
        }

        TextView hint = text(10, C_SUB, "sans-serif", Typeface.NORMAL);
        hint.setText("BACK to close");
        hint.setLetterSpacing(0.15f);
        panel.addView(hint);

        if (!wasOpen) {
            overlay.setVisibility(View.VISIBLE);
            scrim.setAlpha(0f);
            scrim.animate().alpha(1f).setDuration(250).start();
            panel.setTranslationX(dp(400));
            panel.animate().translationX(0).setDuration(340).setInterpolator(EASE).start();
        } else {
            scrim.animate().alpha(1f).setDuration(200).start();
            panel.setAlpha(0.4f);
            panel.animate().alpha(1f).setDuration(200).start();
        }
        if (first != null) { final View f = first; f.post(f::requestFocus); }
        else if (extra != null) extra.post(extra::requestFocus);
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
            if (has && it.onFocus != null) it.onFocus.run();
        });
        row.setOnClickListener(v -> { sound.play(Sound.SELECT); it.action.run(); });
        return row;
    }

    private void closePanel(boolean animate) {
        if (overlay.getVisibility() != View.VISIBLE) return;
        if (panelOnClose != null) { Runnable r = panelOnClose; panelOnClose = null; r.run(); }
        Runnable done = () -> {
            overlay.setVisibility(View.GONE);
            content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            if (focusBeforePanel != null && focusBeforePanel.isAttachedToWindow()) focusBeforePanel.requestFocus();
            else focusFirstTile();
        };
        if (!animate) { done.run(); return; }
        scrim.animate().alpha(0f).setDuration(200).start();
        panel.animate().translationX(dp(400)).setDuration(240).setInterpolator(new AccelerateInterpolator())
                .withEndAction(done).start();
    }

    // =====================================================================
    // Updates (pulled straight from GitHub)
    // =====================================================================

    private boolean installing;
    private final Runnable updateLoop = this::autoCheckUpdate;

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
                    if (overlay.getVisibility() == View.VISIBLE || intro.isPlaying()) return;
                    showBanner("Updating to " + r.version + "…");
                    startInstall(r);
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
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        lastInteraction = System.currentTimeMillis();

        // intro: any key skips ahead
        if (intro.isPlaying()) {
            if (down && code != KeyEvent.KEYCODE_HOME) intro.skip();
            return code != KeyEvent.KEYCODE_HOME;
        }
        // screensaver: any key wakes the dashboard and is swallowed
        if (saver.getVisibility() == View.VISIBLE) {
            if (down) hideSaver();
            return true;
        }
        // favorites move mode
        if (movingTile != null) {
            if (down) {
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) stepMove(-1);
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) stepMove(1);
                else if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                        || code == KeyEvent.KEYCODE_BACK) endMove();
            }
            return true;
        }

        if (code == KeyEvent.KEYCODE_BACK) {
            View f = getCurrentFocus();
            if (f instanceof EditText && up) hideKeyboard(f);
            if (up && !event.isCanceled()) {
                if (overlay.getVisibility() == View.VISIBLE) { sound.play(Sound.BACK); closePanel(true); }
                else { sound.play(Sound.BACK); scroll.smoothScrollTo(0, 0); focusFirstTile(); }
            }
            return true;
        }

        if (down && isDpad(code)) {
            View f = getCurrentFocus();
            if (f != null && !(f instanceof EditText)) {
                int dir = code == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT
                        : code == KeyEvent.KEYCODE_DPAD_RIGHT ? View.FOCUS_RIGHT
                        : code == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : View.FOCUS_DOWN;
                View next = f.focusSearch(dir);
                lastDx = dir == View.FOCUS_LEFT ? -1 : dir == View.FOCUS_RIGHT ? 1 : 0;
                lastDy = dir == View.FOCUS_UP ? -1 : dir == View.FOCUS_DOWN ? 1 : 0;
                if (next == null || next == f) {
                    sound.play(Sound.BUMP);
                    float sx = lastDx * dp(6), sy = lastDy * dp(6);
                    f.animate().translationX(sx).translationY(sy).setDuration(70)
                            .withEndAction(() -> f.animate().translationX(0).translationY(0).setDuration(140).start()).start();
                } else {
                    sound.play(Sound.MOVE);
                }
            }
        }

        if (up && code == KeyEvent.KEYCODE_MENU && overlay.getVisibility() != View.VISIBLE
                && !(getCurrentFocus() instanceof TileView && byPkgFromFocus() != null)) {
            sound.play(Sound.OPEN);
            showMainMenu();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private AppRepo.App byPkgFromFocus() {
        View f = getCurrentFocus();
        if (!(f instanceof TileView) || !(f.getTag() instanceof String)) return null;
        String tag = (String) f.getTag();
        int i = tag.indexOf(':');
        return i < 0 ? null : byPkg.get(tag.substring(i + 1));
    }

    private static boolean isDpad(int code) {
        return code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back never leaves
    }
}
