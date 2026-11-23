package com.vyd02.overdrive;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
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
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.AccelerateInterpolator;
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

/**
 * Overdrive's home screen: a cinematic spotlight on whatever you're focused on, with action
 * buttons, rows of apps below, a gliding focus light, and a zoom into the app when you open it.
 */
public class MainActivity extends Activity {

    static final String EXTRA_INTRO = "intro";
    private static final String KEY_LAST_UPDATE_CHECK = "last_update_check";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private AppRepo repo;
    private Categories cats;
    private Sound sound;
    private float d;

    // views
    private FrameLayout root;
    private ThemeView theme;
    private FrameLayout bgLayer;
    private ImageView bgPhoto;
    private View shadeView;
    private String photoFor = null;            // which theme's picture is showing (null = none)
    private int themeGen;                      // newest theme switch, so a slow picture load can't win over a newer one
    private java.util.Set<String> photoThemes; // themes that ship a rendered picture
    private ImageView backA, backB;
    private boolean backAOn = true;
    private LinearLayout content;
    private TextView heroEyebrow, heroName, heroMeta;
    private TileView heroCard;
    private Ui.Pill actOpen, actOptions, actPin;
    private Ui.Pill dockSearch, dockSettings, dockLock;
    private View updateDot;
    private RowScroller scroll;
    private LinearLayout rowsBox;
    private TextView clockTime, clockDate, weatherIcon, weatherTemp, weatherPlace;
    private FrameLayout overlay;
    private LinearLayout panel;
    private View scrim;
    private TextView banner;
    private IntroView intro;
    private SettingsHub hub;
    private SearchOverlay search;
    private FrameLayout saver;
    private LinearLayout saverBox;
    private TextView saverTime, saverDate, saverWeather;
    private FrameLayout launchLayer;
    private View launchFade;

    // state
    private List<AppRepo.App> allApps = new ArrayList<>();
    private final Map<String, AppRepo.App> byPkg = new HashMap<>();
    private Map<String, String> catCache = new HashMap<>();
    private final Map<String, TileView> tileByKey = new HashMap<>();
    private String lastSignature = "";
    private boolean firstBuild = true;
    private boolean dbgNoContent;
    private boolean rowsReady;
    private long rowsReadyAt; // the home screen is built: the intro can start without hiccups
    private View focusBeforePanel, focusBeforeOverlay;
    private int panelFocusIndex;
    private Updater.Release pendingRelease;
    private int currentAccent = Ui.ACCENT;
    private int lastDx, lastDy;
    private String movingPkg;
    private TileView movingTile;
    private long lastInteraction = System.currentTimeMillis();
    private String lastWeatherLine = "";
    private String lastClock = "";
    private View litRow;
    private boolean launching;

    // what the hero buttons act on
    private Runnable heroOpen;
    private AppRepo.App heroApp;
    private TileView heroTile;
    private String heroRowKey;

    // =====================================================================
    // Lifecycle
    // =====================================================================

    private boolean crashMode; // showing the last crash instead of the home screen

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String crash = CrashScreen.pending(this);
        if (crash != null) {
            crashMode = true;
            CrashScreen.show(this, crash);
            return;
        }
        crashMode = false;
        getWindow().setFormat(PixelFormat.RGBA_8888);
        getWindow().setBackgroundDrawable(null);
        repo = new AppRepo(this);
        cats = new Categories(this, repo.prefs());
        sound = new Sound(this, repo.prefs());
        d = getResources().getDisplayMetrics().density;
        buildUi();
        setContentView(root);
        // Fire TV plays its own click when something is selected; turn that off everywhere in Sora
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> muteSystemSounds(root));
        showSkeleton();
        reloadApps(true);
        startClock();
        refreshWeather();
        ui.postDelayed(this::maybePromptGuard, 3200);
        ui.postDelayed(saverCheck, 15000);
        if (repo.prefs().getBoolean(BootReceiver.KEY_OPEN_ON_START, true)) WakeService.start(this);

        if (repo.prefs().getBoolean(SettingsHub.K_INTRO, true)) playIntro(); else content.setAlpha(1f);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(pkgReceiver, f);
        // test commands over ADB without disturbing the screen: am broadcast -a com.vyd02.overdrive.DBG --es c <cmd>
        IntentFilter df = new IntentFilter("com.vyd02.overdrive.DBG");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(dbgReceiver, df, Context.RECEIVER_EXPORTED);
        else registerReceiver(dbgReceiver, df);
    }

    private final Runnable resumeReload = () -> reloadApps(false);

    private final BroadcastReceiver dbgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String cmd = i.getStringExtra("c");
            if (cmd != null && Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1) debugCommand(cmd);
        }
    };

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { reloadApps(false); }
    };

    // While another app is in front (Netflix, YouTube...), Sora lets go of its big pieces (the
    // full-HD theme picture and the sound effects) so the TV's memory stays free for the app you're
    // watching and for Fire TV's own services, like Alexa's playback controls. They come back
    // the moment you return home.
    private boolean slimmedDown;

    @Override
    protected void onStop() {
        super.onStop();
        if (crashMode || bgPhoto == null || isChangingConfigurations()) return;
        slimmedDown = true;
        bgPhoto.setImageDrawable(null);
        photoFor = null;
        if (sound != null) sound.release();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (crashMode || !slimmedDown || bgPhoto == null) return;
        slimmedDown = false;
        if (sound != null) sound.wake();
        applyTheme(theme.themeId());
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND && bgPhoto != null && slimmedDown) bgPhoto.setImageDrawable(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sound == null) return; // crash screen was shown, nothing else was set up
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
        try { unregisterReceiver(dbgReceiver); } catch (Exception ignored) { }
        ui.removeCallbacksAndMessages(null);
        io.shutdownNow();
        sound.release();
    }

    @Override
    protected void onResume() {
        HomeGuardService.restore(this);
        super.onResume();
        if (crashMode || content == null) return;
        lastInteraction = System.currentTimeMillis();
        updateKeepAwake();
        // refresh the rows after the welcome-back animation, so rebuilding never lands mid-animation
        ui.removeCallbacks(resumeReload);
        ui.postDelayed(resumeReload, 700);
        if (launching) {
            // coming back from an app: fade the zoom layer away and glide the home screen in
            launching = false;
            // the zoomed-up tile is hidden under the black fade: drop it now instead of drawing it every frame
            for (int k = launchLayer.getChildCount() - 1; k >= 0; k--) {
                if (launchLayer.getChildAt(k) != launchFade) launchLayer.removeViewAt(k);
            }
            launchFade.animate().alpha(0f).setDuration(320).withEndAction(() -> {
                launchLayer.removeAllViews();
                launchLayer.setVisibility(View.GONE);
            }).start();
        }
        if (!intro.isPlaying()) {
            // first frame back: the background is redrawn (and the home screen warmed up invisibly);
            // the home screen glides in from the next frame, so those two costs never share a frame
            content.animate().cancel();
            content.setAlpha(0.004f);
            content.setScaleX(1.04f);
            content.setScaleY(1.04f);
            content.postOnAnimation(() -> content.postOnAnimation(() ->
                    content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(Ui.EASE_OUT).start()));
        }
        ui.postDelayed(this::autoCheckUpdate, 2500);
        refreshLock();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (crashMode || content == null) return;
        String dbg = intent.getStringExtra("sora_dbg");
        if (dbg != null) {
            // test commands for tuning on the TV over ADB; ignored unless ADB debugging is switched on
            if (Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1) debugCommand(dbg);
            return;
        }
        if (overlay.getVisibility() == View.VISIBLE) closePanel(false);
        if (hub.isOpen()) closeSettings();
        if (search.isOpen()) closeSearch();
        hideSaver();
        endMove();
        if (intent.getBooleanExtra(EXTRA_INTRO, false) && repo.prefs().getBoolean(SettingsHub.K_INTRO, true)) playIntro();
        scroll.scrollTo(0, 0);
        focusFirstTile();
    }

    /**
     * Switch theme. Scene themes show their 3D-rendered picture underneath at full HD, and the
     * half-size light layer on top is blended in as light (adds, never darkens). Others draw everything.
     */
    private void applyTheme(String id) {
        if (photoThemes == null) {
            photoThemes = new java.util.HashSet<>();
            try {
                String[] list = getAssets().list("themes");
                if (list != null) for (String f : list) if (f.endsWith(".jpg")) photoThemes.add(f.substring(0, f.length() - 4));
            } catch (Exception ignored) { }
        }
        final int gen = ++themeGen;
        if (!photoThemes.contains(id)) {
            theme.setTheme(id, false);
            showPhoto(null, null);
            return;
        }
        if (id.equals(photoFor)) { theme.setTheme(id, true); return; }
        io.execute(() -> {
            Bitmap b = loadPhoto(id);
            ui.post(() -> {
                if (gen != themeGen) return; // a newer switch happened meanwhile
                if (b == null) { theme.setTheme(id, false); showPhoto(null, null); return; }
                theme.setTheme(id, true);
                showPhoto(id, b);
            });
        });
    }

    private Bitmap loadPhoto(String id) {
        try (java.io.InputStream in = getAssets().open("themes/" + id + ".jpg")) {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inMutable = true;
            Bitmap b = android.graphics.BitmapFactory.decodeStream(in, null, o);
            if (b == null) return null;
            // bake the home screen's shading into the picture once, instead of drawing it every frame
            Shade sh = new Shade(this);
            sh.layout(0, 0, b.getWidth(), b.getHeight());
            sh.onSizeChanged(b.getWidth(), b.getHeight(), 0, 0);
            sh.draw(new Canvas(b));
            b.prepareToDraw();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private static final Paint LIGHT_LAYER = new Paint();
    static { LIGHT_LAYER.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)); }

    private void showPhoto(String id, Bitmap b) {
        photoFor = id;
        if (b == null) {
            bgPhoto.setImageDrawable(null);
            bgPhoto.setVisibility(View.GONE);
            shadeView.setVisibility(View.VISIBLE);
            bgLayer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        } else {
            bgPhoto.setImageBitmap(b);
            bgPhoto.setVisibility(View.VISIBLE);
            shadeView.setVisibility(View.GONE);
            bgLayer.setLayerType(View.LAYER_TYPE_HARDWARE, LIGHT_LAYER);
        }
    }

    /** Test switches sent over ADB while tuning on the real TV. Visual-only, nothing private. */
    private void debugCommand(String cmd) {
        switch (cmd) {
            case "theme_pause": theme.setPaused(true); break;
            case "theme_resume": theme.setPaused(false); break;
            case "theme_hide": theme.setPaused(true); theme.setVisibility(View.INVISIBLE); break;
            case "theme_show": theme.setVisibility(View.VISIBLE); theme.setPaused(false); break;
            case "backdrop_hide": backA.setVisibility(View.INVISIBLE); backB.setVisibility(View.INVISIBLE); break;
            case "backdrop_show": backA.setVisibility(View.VISIBLE); backB.setVisibility(View.VISIBLE); break;
            case "intro": playIntro(); break;
            case "settings": openSettings(); break;
            case "search": openSearch(); break;
            case "saver": showSaver(); break;
            case "weather": showWeatherPanel(); break;
            case "guard": showGuardPanel(); break;
            case "verbose": ThemeView.verbose = !ThemeView.verbose; break;
            case "nocontent": dbgNoContent = !dbgNoContent; break;
            case "spin": {
                // redraw the whole window every frame for 3 seconds, to measure the cost of one full frame
                final int[] n = {0};
                Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
                    @Override public void doFrame(long t) {
                        root.invalidate();
                        content.invalidate();
                        if (++n[0] < 180) Choreographer.getInstance().postFrameCallback(this);
                    }
                });
                break;
            }
            default:
                if (cmd.startsWith("nav:")) {
                    // play remote presses inside the app (U D L R O=ok B=back M=menu), spaced like a person
                    String seq = cmd.substring(4);
                    int gap = 420;
                    for (int i = 0; i < seq.length(); i++) {
                        final int code;
                        switch (seq.charAt(i)) {
                            case 'U': code = KeyEvent.KEYCODE_DPAD_UP; break;
                            case 'D': code = KeyEvent.KEYCODE_DPAD_DOWN; break;
                            case 'L': code = KeyEvent.KEYCODE_DPAD_LEFT; break;
                            case 'R': code = KeyEvent.KEYCODE_DPAD_RIGHT; break;
                            case 'O': code = KeyEvent.KEYCODE_DPAD_CENTER; break;
                            case 'B': code = KeyEvent.KEYCODE_BACK; break;
                            case 'M': code = KeyEvent.KEYCODE_MENU; break;
                            case 'F': code = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD; break;
                            default: code = 0;
                        }
                        if (code == 0) continue;
                        ui.postDelayed(() -> {
                            long now = android.os.SystemClock.uptimeMillis();
                            dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0));
                            dispatchKeyEvent(new KeyEvent(now, now + 60, KeyEvent.ACTION_UP, code, 0));
                        }, (long) i * gap);
                    }
                }
                else if (cmd.startsWith("theme:")) applyTheme(cmd.substring(6));
                else if (cmd.startsWith("hide:")) root.getChildAt(Integer.parseInt(cmd.substring(5))).setVisibility(View.INVISIBLE);
                else if (cmd.startsWith("show:")) root.getChildAt(Integer.parseInt(cmd.substring(5))).setVisibility(View.VISIBLE);
                else if (cmd.startsWith("hidec:")) content.getChildAt(Integer.parseInt(cmd.substring(6))).setVisibility(View.INVISIBLE);
                else if (cmd.startsWith("showc:")) content.getChildAt(Integer.parseInt(cmd.substring(6))).setVisibility(View.VISIBLE);
        }
        android.util.Log.i("SoraPerf", "dbg " + cmd);
    }

    // =====================================================================
    // Building the screen
    // =====================================================================

    private int dp(float v) { return Math.round(v * d); }

    private void noClip(ViewGroup v) {
        v.setClipChildren(false);
        v.setClipToPadding(false);
    }

    /** Vertical scroller that lets us decide where rows land. */
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

    /** One soft shade over the backdrop: darkens the bottom (rows) and the left (title). */
    static class Shade extends View {
        private final Paint v = new Paint(Paint.DITHER_FLAG), h = new Paint(Paint.DITHER_FLAG);
        Shade(Context c) { super(c); }
        @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
            v.setShader(new LinearGradient(0, 0, 0, hh, new int[]{Color.argb(90, 0, 0, 0), Color.argb(30, 0, 0, 0),
                    Color.argb(215, 4, 5, 10), Color.argb(225, 4, 5, 10)}, new float[]{0f, 0.3f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            h.setShader(new LinearGradient(0, 0, w * 0.6f, 0, new int[]{Color.argb(170, 4, 5, 10), 0}, null, Shader.TileMode.CLAMP));
        }
        @Override protected void onDraw(Canvas c) {
            c.drawRect(0, 0, getWidth(), getHeight(), v);
            c.drawRect(0, 0, getWidth() * 0.6f, getHeight(), h);
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);

        theme = new ThemeView(this, "nebula");
        // The whole background (animated theme, blurred app art, dark shading) is drawn at half
        // the screen's size into one small layer, then scaled up once. It's all soft light, so it
        // looks the same, and the TV's graphics chip does a quarter of the work every frame.
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int sw = Math.max(dm.widthPixels, dm.heightPixels), sh = Math.min(dm.widthPixels, dm.heightPixels);
        int k = ThemeView.DOWNSCALE;
        // Scene themes have a 3D-rendered picture: shown full-HD underneath, with the shading baked in
        bgPhoto = new ImageView(this);
        bgPhoto.setScaleType(ImageView.ScaleType.FIT_XY);
        bgPhoto.setVisibility(View.GONE);
        root.addView(bgPhoto, new FrameLayout.LayoutParams(-1, -1));
        bgLayer = new FrameLayout(this);
        root.addView(bgLayer, new FrameLayout.LayoutParams(sw / k, sh / k));
        bgLayer.setPivotX(0f);
        bgLayer.setPivotY(0f);
        bgLayer.setScaleX(k);
        bgLayer.setScaleY(k);
        bgLayer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        bgLayer.addView(theme, new FrameLayout.LayoutParams(-1, -1));
        backA = backdrop();
        backB = backdrop();
        bgLayer.addView(backA, new FrameLayout.LayoutParams(-1, dp(340) / k));
        bgLayer.addView(backB, new FrameLayout.LayoutParams(-1, dp(340) / k));
        shadeView = new Shade(this);
        bgLayer.addView(shadeView, new FrameLayout.LayoutParams(-1, -1));
        applyTheme(repo.prefs().getString(SettingsHub.K_THEME, "nebula"));

        // Fades draw each piece with the fade applied, instead of painting the whole screen offscreen first
        content = new LinearLayout(this) { @Override public boolean hasOverlappingRendering() { return false; } };
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(56), dp(26), 0, 0);
        content.setAlpha(0f);
        noClip(content);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, dp(46));
        dl.rightMargin = dp(48);
        content.addView(buildDock(), dl);
        content.addView(buildHero(), new LinearLayout.LayoutParams(-1, dp(214)));

        scroll = new RowScroller(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // Rows are cut off cleanly at the top of their area so nothing slides over the title.
        // Android only clips to the box when the box has padding, so give it a 1px top padding.
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);
        scroll.setPadding(0, 1, 0, 0);
        // full screen width (tiles still slide off the left edge), with the rows' inset moved inside
        LinearLayout.LayoutParams scl = new LinearLayout.LayoutParams(-1, 0, 1f);
        scl.leftMargin = -dp(56);
        content.addView(scroll, scl);
        rowsBox = new LinearLayout(this);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        rowsBox.setPadding(dp(56), dp(ROW_GAP), 0, dp(320));
        noClip(rowsBox);
        scroll.addView(rowsBox, new ScrollView.LayoutParams(-1, -2));

        buildSaver();

        search = new SearchOverlay(this, new SearchOverlay.Host() {
            @Override public List<AppRepo.App> apps() { return visibleApps(); }
            @Override public void launch(AppRepo.App a, TileView from) {
                launchApp(from, repo.launchIntent(a.pkg), a.pkg);
                search.hide(); // come back to the home screen, not the search box
                theme.setPaused(false);
                content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
            @Override public void play(int s) { sound.play(s); }
            @Override public void focused(TileView t, int accent) { themeAt(t, accent); }
        });
        root.addView(search, new FrameLayout.LayoutParams(-1, -1));

        hub = new SettingsHub(this, new SettingsHub.Host() {
            @Override public void play(int s) { sound.play(s); }
            @Override public void tileFocused(View v, int accent) { themeAt(v, accent); }
            @Override public void banner(String msg) { showBanner(msg); }
            @Override public void closeHub() { closeSettings(); }
            @Override public SharedPreferences prefs() { return repo.prefs(); }
            @Override public String themeId() { return theme.themeId(); }
            @Override public void previewTheme(String id) { applyTheme(id); theme.setAccent(currentAccent); }
            @Override public void commitTheme(String id) {
                repo.prefs().edit().putString(SettingsHub.K_THEME, id).apply();
                if (!id.equals(theme.themeId())) applyTheme(id);
                theme.select();
            }
            @Override public void homeChanged() { lastSignature = ""; reloadApps(true); }
            @Override public void sub(String which) { hubSub(which); }
            @Override public int hiddenCount() { return repo.hidden().size(); }
        });
        root.addView(hub, new FrameLayout.LayoutParams(-1, -1));

        // zoom-into-app layer
        launchLayer = new FrameLayout(this);
        launchLayer.setVisibility(View.GONE);
        launchLayer.setClipChildren(false);
        launchFade = new View(this);
        launchFade.setBackgroundColor(Color.BLACK);
        launchFade.setAlpha(0f);
        root.addView(launchLayer, new FrameLayout.LayoutParams(-1, -1));

        // menus
        overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        scrim = new View(this);
        scrim.setBackgroundColor(Color.argb(170, 0, 0, 0));
        overlay.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(32), dp(44), dp(32), dp(30));
        overlay.addView(panel, new FrameLayout.LayoutParams(dp(420), -1, Gravity.END));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        banner = Ui.text(this, 13, Ui.TEXT, Ui.body(this, true));
        banner.setPadding(dp(22), dp(11), dp(22), dp(11));
        GradientDrawable bgd = new GradientDrawable();
        bgd.setCornerRadius(dp(24));
        bgd.setColor(Color.argb(238, 16, 20, 34));
        bgd.setStroke(dp(1), Color.argb(70, 255, 255, 255));
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
        v.setVisibility(View.INVISIBLE);
        return v;
    }

    /** Sora's mark: a rainbow ring (the intro's ring) and the wordmark in the Sora typeface. */
    static class Logo extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final android.graphics.Matrix m = new android.graphics.Matrix();
        private android.graphics.SweepGradient sweep;
        private final float d;
        private float spin;
        private android.animation.ValueAnimator a;
        Logo(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(3.2f * d);
            sweep = new android.graphics.SweepGradient(0, 0, new int[]{0xFFFF3D71, 0xFFFF9F1C, 0xFFFFE14D, 0xFF3DFFB0,
                    0xFF2BD9FF, 0xFF6C63FF, 0xFFD65CFF, 0xFFFF3D71}, null);
            ring.setShader(sweep);
            t.setColor(Color.WHITE);
            t.setTypeface(Ui.brand(c, true));
            t.setTextSize(17 * d);
            t.setLetterSpacing(0.28f);
        }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            spinOnce();
        }
        /** One turn of the ring (on arrival). Not looping, so the screen can sit perfectly still. */
        void spinOnce() {
            if (a != null) a.cancel();
            a = android.animation.ValueAnimator.ofFloat(spin, spin + 360f);
            a.setDuration(1400);
            a.setInterpolator(Ui.EASE_OUT);
            a.addUpdateListener(an -> { spin = (float) an.getAnimatedValue(); invalidate(); });
            a.start();
        }
        @Override protected void onDetachedFromWindow() { if (a != null) a.cancel(); super.onDetachedFromWindow(); }
        @Override protected void onDraw(Canvas c) {
            float cy = getHeight() / 2f, r = 9 * d, cx = r + 2 * d;
            m.setRotate(spin);
            m.postTranslate(cx, cy);
            sweep.setLocalMatrix(m);
            c.drawCircle(cx, cy, r, ring);
            c.drawText("SORA", cx + r + 12 * d, cy + 6 * d, t);
        }
    }

    private View buildDock() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        noClip(bar);
        bar.addView(new Logo(this), new LinearLayout.LayoutParams(dp(220), dp(32)));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        weatherIcon = Ui.text(this, 20, Ui.TEXT, Ui.body(this, false));
        weatherIcon.setText("·");
        bar.addView(weatherIcon);
        LinearLayout wcol = new LinearLayout(this);
        wcol.setOrientation(LinearLayout.VERTICAL);
        wcol.setPadding(dp(8), 0, 0, 0);
        weatherTemp = Ui.text(this, 15, Ui.TEXT, Ui.display(this, 500));
        weatherTemp.setText("--°");
        weatherPlace = Ui.text(this, 10, Ui.FAINT, Ui.body(this, false));
        weatherPlace.setSingleLine(true);
        weatherPlace.setEllipsize(android.text.TextUtils.TruncateAt.END);
        weatherPlace.setMaxWidth(dp(230));
        weatherPlace.setText("Weather");
        wcol.addView(weatherTemp, new LinearLayout.LayoutParams(-2, -2));
        wcol.addView(weatherPlace, new LinearLayout.LayoutParams(-2, -2)); // sized to its text, so it grows when the weather arrives
        bar.addView(wcol);

        View div = new View(this);
        div.setBackgroundColor(Color.argb(50, 255, 255, 255));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(dp(1), dp(26));
        divLp.leftMargin = dp(18);
        divLp.rightMargin = dp(18);
        bar.addView(div, divLp);

        LinearLayout ccol = new LinearLayout(this);
        ccol.setOrientation(LinearLayout.VERTICAL);
        ccol.setGravity(Gravity.END);
        clockTime = Ui.text(this, 20, Ui.TEXT, Ui.display(this, 500));
        clockTime.setSingleLine(true);
        ccol.addView(clockTime, new LinearLayout.LayoutParams(-2, -2));
        clockDate = Ui.text(this, 10, Ui.FAINT, Ui.body(this, false));
        clockDate.setSingleLine(true);
        LinearLayout.LayoutParams cdl = new LinearLayout.LayoutParams(-2, -2);
        cdl.gravity = Gravity.END;
        ccol.addView(clockDate, cdl);
        bar.addView(ccol);

        // lock: keeps you in Sora (bounces Amazon's home screen away)
        dockLock = new Ui.Pill(this, "lock", null, false);
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(dp(40), dp(40));
        kl.leftMargin = dp(22);
        bar.addView(dockLock, kl);
        dockLock.setOnClickListener(v -> toggleLock());
        dockLock.setOnFocusChangeListener((v, has) -> {
            if (has) { dockHero(lockTitle(), lockMeta()); themeAt(v, Ui.ACCENT); }
            v.post(this::refreshLock); // keep the lock tinted after the pill repaints itself
        });
        refreshLock();

        dockSearch = new Ui.Pill(this, "search", null, false);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(40), dp(40));
        sl.leftMargin = dp(12);
        bar.addView(dockSearch, sl);
        dockSearch.setOnClickListener(v -> { sound.play(Sound.OPEN); openSearch(); });
        dockSearch.setOnFocusChangeListener((v, has) -> { if (has) { dockHero("Search", "Find any app by name"); themeAt(v, Ui.ACCENT); } });

        FrameLayout gearWrap = new FrameLayout(this);
        noClip(gearWrap);
        dockSettings = new Ui.Pill(this, "gear", null, false);
        gearWrap.addView(dockSettings, new FrameLayout.LayoutParams(dp(40), dp(40)));
        updateDot = new View(this);
        GradientDrawable ud = new GradientDrawable();
        ud.setShape(GradientDrawable.OVAL);
        ud.setColor(Color.parseColor("#FF4D6D"));
        ud.setStroke(dp(2), Color.parseColor("#0A0D18"));
        updateDot.setBackground(ud);
        updateDot.setVisibility(View.GONE);
        gearWrap.addView(updateDot, new FrameLayout.LayoutParams(dp(12), dp(12), Gravity.TOP | Gravity.END));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(dp(40), dp(40));
        gl.leftMargin = dp(12);
        bar.addView(gearWrap, gl);
        dockSettings.setOnClickListener(v -> { sound.play(Sound.OPEN); openSettings(); });
        dockSettings.setOnFocusChangeListener((v, has) -> { if (has) { dockHero("Settings", "Themes, sounds, power-up and Fire TV"); themeAt(v, Ui.ACCENT); } });
        return bar;
    }

    private View buildHero() {
        FrameLayout hero = new FrameLayout(this);
        noClip(hero);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_VERTICAL);
        noClip(left);
        heroEyebrow = Ui.text(this, 11.5f, Ui.ACCENT, Ui.body(this, true));
        heroEyebrow.setLetterSpacing(0.26f);
        heroEyebrow.setSingleLine(true);
        left.addView(heroEyebrow, new LinearLayout.LayoutParams(-1, -2));
        heroName = Ui.text(this, 44, Ui.TEXT, Ui.display(this, 700));
        heroName.setSingleLine(true);
        heroName.setEllipsize(TextUtils.TruncateAt.END);
        heroName.setLetterSpacing(-0.01f);
        heroName.setShadowLayer(dp(8), 0, dp(2), Color.argb(150, 0, 0, 0));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = dp(2);
        left.addView(heroName, nl);
        heroMeta = Ui.text(this, 13.5f, Ui.SUB, Ui.body(this, false));
        heroMeta.setSingleLine(true);
        left.addView(heroMeta, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout acts = new LinearLayout(this);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        noClip(acts);
        actOpen = new Ui.Pill(this, "play", "Open", true);
        actOptions = new Ui.Pill(this, "dots", "Options", false);
        actPin = new Ui.Pill(this, "pin", "Pin", false);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-2, dp(40));
        al.rightMargin = dp(12);
        acts.addView(actOpen, al);
        acts.addView(actOptions, new LinearLayout.LayoutParams(al));
        acts.addView(actPin, new LinearLayout.LayoutParams(al));
        LinearLayout.LayoutParams acl = new LinearLayout.LayoutParams(-2, -2);
        acl.topMargin = dp(16);
        left.addView(acts, acl);
        actOpen.setOnClickListener(v -> { if (heroOpen != null) heroOpen.run(); });
        actOptions.setOnClickListener(v -> { if (heroApp != null) showAppMenu(heroApp, heroTile, heroRowKey); });
        actPin.setOnClickListener(v -> { if (heroApp != null) togglePin(heroApp); });
        View.OnFocusChangeListener actFocus = (v, has) -> { if (has) themeAt(v, currentAccent); };
        actOpen.setOnFocusChangeListener(actFocus);
        actOptions.setOnFocusChangeListener(actFocus);
        actPin.setOnFocusChangeListener(actFocus);

        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(-1, -1);
        llp.rightMargin = dp(380);
        hero.addView(left, llp);

        heroCard = new TileView(this, 1f);
        heroCard.setFocusable(false);
        heroCard.setFocusableInTouchMode(false);
        heroCard.setClickable(false);
        heroCard.setAlwaysLit();
        heroCard.setPreferCrisp(true); // never blow up a small, blurry app image
        heroCard.setCameraDistance(8000 * d);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(300), dp(169), Gravity.END | Gravity.CENTER_VERTICAL);
        clp.rightMargin = dp(58);
        hero.addView(heroCard, clp);
        return hero;
    }

    private void themeAt(View v, int accent) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        theme.focusAt((loc[0] + v.getWidth() / 2f) / Math.max(1, root.getWidth()),
                (loc[1] + v.getHeight() / 2f) / Math.max(1, root.getHeight()), lastDx, lastDy);
        theme.setAccent(accent);
        lastDx = 0;
        lastDy = 0;
    }

    // =====================================================================
    // Rows
    // =====================================================================

    private static final int ROW_GAP = 14;

    private void showSkeleton() {
        rowsBox.removeAllViews();
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            View label = new Ui.Skeleton(this);
            row.addView(label, new LinearLayout.LayoutParams(dp(110), dp(12)));
            LinearLayout strip = new LinearLayout(this);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            strip.setPadding(0, dp(14), 0, dp(26));
            for (int i = 0; i < 5; i++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(192), dp(108));
                lp.rightMargin = dp(20);
                strip.addView(new Ui.Skeleton(this), lp);
            }
            row.addView(strip);
            rowsBox.addView(row);
        }
    }

    private List<AppRepo.App> visibleApps() {
        Set<String> hidden = repo.hidden();
        List<AppRepo.App> out = new ArrayList<>();
        for (AppRepo.App a : allApps) if (!hidden.contains(a.pkg)) out.add(a);
        return out;
    }

    private void reloadApps(boolean force) {
        io.execute(() -> {
            List<AppRepo.App> apps = repo.loadAll();
            boolean showInputs = repo.prefs().getBoolean(SettingsHub.K_INPUTS, true);
            List<Inputs.Input> inputs = showInputs ? Inputs.list(this) : new ArrayList<>();
            Map<String, String> catOf = new HashMap<>();
            for (AppRepo.App a : apps) catOf.put(a.pkg, cats.of(a.pkg));
            StringBuilder sig = new StringBuilder();
            for (AppRepo.App a : apps) sig.append(a.pkg).append(a.label).append(catOf.get(a.pkg)).append(';');
            for (Inputs.Input in : inputs) sig.append(in.id).append(in.label);
            sig.append('|').append(repo.hidden()).append('|').append(repo.recents()).append('|').append(repo.favorites())
                    .append('|').append(repo.prefs().getBoolean(SettingsHub.K_QUICK, true));
            String s = sig.toString();
            ui.post(() -> {
                if (!force && s.equals(lastSignature)) return;
                if (movingPkg != null) return;
                lastSignature = s;
                allApps = apps;
                byPkg.clear();
                for (AppRepo.App a : apps) byPkg.put(a.pkg, a);
                long b0 = android.os.SystemClock.uptimeMillis();
                buildRows(inputs, catOf);
                android.util.Log.i("SoraPerf", "buildRows " + (android.os.SystemClock.uptimeMillis() - b0) + "ms");
                if (!rowsReady) { rowsReady = true; rowsReadyAt = android.os.SystemClock.uptimeMillis(); }
            });
        });
    }

    private void buildRows(List<Inputs.Input> inputs, Map<String, String> catOf) {
        View f = getCurrentFocus();
        String keepKey = f instanceof TileView && f.getTag() instanceof String ? (String) f.getTag() : null;

        rowsBox.removeAllViews();
        tileByKey.clear();
        litRow = null;
        catCache = catOf;
        Set<String> hidden = repo.hidden();
        List<AppRepo.App> visible = visibleApps();
        List<String> favs = repo.favorites();
        int idx = 0;

        // favorites (always first; a friendly hint when empty)
        List<AppRepo.App> favApps = new ArrayList<>();
        for (String p : favs) { AppRepo.App a = byPkg.get(p); if (a != null && !hidden.contains(p)) favApps.add(a); }
        LinearLayout favStrip = addRow("FAVORITES", "fav");
        if (favApps.isEmpty()) idx = addHintTile(favStrip, idx);
        for (AppRepo.App a : favApps) idx = addAppTile(favStrip, a, "fav", 208, 117, 1.1f, "FAVORITE", idx, true);

        // recent
        List<AppRepo.App> recent = new ArrayList<>();
        for (String p : repo.recents()) { AppRepo.App a = byPkg.get(p); if (a != null && !hidden.contains(p)) recent.add(a); }
        if (recent.isEmpty()) for (int i = 0; i < Math.min(6, visible.size()); i++) recent.add(visible.get(i));
        if (!recent.isEmpty()) {
            boolean starter = repo.recents().isEmpty();
            LinearLayout strip = addRow(starter ? "START HERE" : "CONTINUE", "rec");
            for (AppRepo.App a : recent) idx = addAppTile(strip, a, "rec", 208, 117, 1.1f, starter ? "PICK SOMETHING" : "RECENTLY OPENED", idx, favs.contains(a.pkg));
        }

        if (!inputs.isEmpty()) {
            LinearLayout strip = addRow("INPUTS", "in");
            for (Inputs.Input in : inputs) idx = addInputTile(strip, in, idx);
        }

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
            String title = e.getKey().toUpperCase(Locale.US);
            LinearLayout strip = addRow(title, key);
            for (AppRepo.App a : e.getValue()) idx = addAppTile(strip, a, key, 192, 108, 1.12f, title, idx, favs.contains(a.pkg));
        }

        if (repo.prefs().getBoolean(SettingsHub.K_QUICK, true)) {
            LinearLayout qs = addRow("QUICK SETTINGS", "qs");
            idx = addQuickTile(qs, "Wi-Fi", "wifi", Settings.ACTION_WIFI_SETTINGS, Color.parseColor("#3DA9FF"), idx);
            idx = addQuickTile(qs, "Bluetooth", "remote", Settings.ACTION_BLUETOOTH_SETTINGS, Color.parseColor("#7C6CFF"), idx);
            idx = addQuickTile(qs, "Display & Sound", "display", Settings.ACTION_DISPLAY_SETTINGS, Color.parseColor("#2BD9A8"), idx);
            idx = addQuickTile(qs, "Applications", "apps", Settings.ACTION_APPLICATION_SETTINGS, Color.parseColor("#FFB13D"), idx);
            addQuickTile(qs, "All Settings", "gear", null, Color.parseColor("#8FA3C7"), idx);
        }

        firstBuild = false;
        if (overlay.getVisibility() == View.VISIBLE || hub.isOpen() || search.isOpen()) return;
        TileView restore = keepKey != null ? tileByKey.get(keepKey) : null;
        if (restore != null) restore.requestFocus();
        else if (f == null || !f.isAttachedToWindow() || f instanceof TileView) focusFirstTile(); // keep focus on dock/buttons
    }

    /** Row = label + a strip of tiles with the gliding focus light under it. */
    private LinearLayout addRow(String title, String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setTag("row:" + key);
        noClip(row);
        TextView label = Ui.text(this, 11.5f, Ui.TEXT, Ui.body(this, true));
        label.setLetterSpacing(0.24f);
        label.setText(title);
        label.setAlpha(0.5f);
        label.setTag("label");
        row.addView(label);
        Strip hs = new Strip(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setPadding(dp(4), dp(14), dp(80), dp(10));
        noClip(hs);
        FrameLayout holder = new FrameLayout(this);
        holder.setPadding(0, 0, 0, dp(16));
        noClip(holder);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        noClip(strip);
        holder.addView(strip, new FrameLayout.LayoutParams(-2, -2));
        // the signature "drive line": a light that glides to whatever you focus in this row
        View glow = new View(this);
        glow.setBackground(Ui.solid(this, Color.argb(70, 61, 169, 255), 6));
        glow.setTag("glow");
        glow.setAlpha(0f);
        holder.addView(glow, new FrameLayout.LayoutParams(dp(100), dp(10), Gravity.BOTTOM | Gravity.START));
        View line = new View(this);
        line.setBackground(Ui.solid(this, Ui.ACCENT, 2));
        line.setTag("line");
        line.setAlpha(0f);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(dp(100), dp(3), Gravity.BOTTOM | Gravity.START);
        ll.bottomMargin = dp(3.5f);
        holder.addView(line, ll);
        hs.addView(holder);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.leftMargin = -dp(4);
        row.addView(hs, hlp);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = dp(4);
        rowsBox.addView(row, rlp);
        return strip;
    }

    private void placeTile(LinearLayout strip, View t, int w, int h, int idx) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(w), dp(h));
        lp.rightMargin = dp(20);
        strip.addView(t, lp);
        if (firstBuild && idx < 12 && !intro.isPlaying()) { // with the intro, the whole screen zooms in instead
            t.setAlpha(0f);
            t.setTranslationY(dp(26));
            long base = intro.isPlaying() ? 2550 : 80;
            t.animate().alpha(1f).translationY(0).setStartDelay(base + Math.min(idx, 20) * 28L)
                    .setDuration(460).setInterpolator(Ui.EASE_OUT).start();
        }
    }

    private int addAppTile(LinearLayout strip, AppRepo.App a, String rowKey, int w, int h, float scale, String tag, int idx, boolean pinned) {
        TileView t = new TileView(this, scale);
        t.setApp(a);
        t.setBadge(pinned && !"fav".equals(rowKey));
        String key = rowKey + ":" + a.pkg;
        t.setTag(key);
        tileByKey.put(key, t);
        t.setOnFocusChangeListener((v, has) -> {
            if (!has) return;
            heroApp = a;
            heroTile = t;
            heroRowKey = rowKey;
            heroOpen = () -> launchApp(t, repo.launchIntent(a.pkg), a.pkg);
            setHero(a.label, tag, metaFor(a), t.getAccent(), a.blurBmp, t, true, repo.favorites().contains(a.pkg));
            onTileFocused(t);
        });
        t.setOnClickListener(v -> launchApp(t, repo.launchIntent(a.pkg), a.pkg));
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

    private int addHintTile(LinearLayout strip, int idx) {
        TileView t = new TileView(this, 1.06f);
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ic = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint big = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        big.setColor(Color.WHITE);
        big.setTypeface(Ui.display(this, 500));
        small.setColor(Ui.SUB);
        small.setTypeface(Ui.body(this, false));
        final Path path = new Path();
        final RectF tmp = new RectF();
        t.setCustom("Pin your favorites", Ui.ACCENT, (c, r, focus) -> {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(22, 255, 255, 255));
            c.drawRoundRect(r, dp(14), dp(14), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.5f));
            p.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(6), dp(5)}, 0));
            p.setColor(Color.argb(90, 255, 255, 255));
            c.drawRoundRect(r, dp(14), dp(14), p);
            p.setPathEffect(null);
            ic.setColor(Ui.ACCENT);
            Icons.draw(c, "pin", r.left + dp(30), r.centerY() - dp(10), dp(10), ic, path, tmp, d, 0);
            big.setTextSize(dp(15));
            c.drawText("Pin your favorites", r.left + dp(52), r.centerY() - dp(4), big);
            small.setTextSize(dp(11));
            c.drawText("Press ☰ on any app, then Pin", r.left + dp(52), r.centerY() + dp(14), small);
        });
        t.setTag("fav:hint");
        t.setOnFocusChangeListener((v, has) -> {
            if (!has) return;
            heroApp = null;
            heroOpen = null;
            setHero("Your favorites live here", "FAVORITES", "Press ☰ on any app below and choose Pin", Ui.ACCENT, null, null, false, false);
            onTileFocused(t);
        });
        t.setOnClickListener(v -> { t.pulse(); sound.play(Sound.BUMP); showBanner("Press ☰ on any app, then pick Pin"); });
        placeTile(strip, t, 320, 117, idx);
        return idx + 1;
    }

    private int addInputTile(LinearLayout strip, Inputs.Input in, int idx) {
        TileView t = new TileView(this, 1.12f);
        final int accent = Color.parseColor("#FF5A7A");
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ic = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        tp.setColor(Color.WHITE);
        tp.setTypeface(Ui.body(this, true));
        final RectF[] last = {new RectF()};
        final Path path = new Path();
        final RectF tmp = new RectF();
        t.setCustom(in.label, accent, (c, r, focus) -> {
            if (!r.equals(last[0])) {
                last[0] = new RectF(r);
                p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                        Color.parseColor("#4A1830"), Color.parseColor("#160810"), Shader.TileMode.CLAMP));
            }
            c.drawRoundRect(r, dp(14), dp(14), p);
            ic.setColor(Color.WHITE);
            Icons.draw(c, "inputs", r.centerX(), r.top + r.height() * 0.4f, r.height() * 0.17f, ic, path, tmp, d, focus);
            tp.setTextSize(r.height() * 0.14f);
            CharSequence s = TextUtils.ellipsize(in.label, tp, r.width() - dp(16), TextUtils.TruncateAt.END);
            c.drawText(s, 0, s.length(), r.centerX() - tp.measureText(s, 0, s.length()) / 2f, r.bottom - r.height() * 0.16f, tp);
        });
        String key = "in:" + in.id;
        t.setTag(key);
        tileByKey.put(key, t);
        Runnable open = () -> {
            t.pulse();
            sound.play(Sound.SELECT);
            theme.select();
            try { startActivity(Inputs.intent(in)); } catch (Exception e) { showBanner("This TV didn't let Sora switch inputs"); }
        };
        t.setOnFocusChangeListener((v, has) -> {
            if (!has) return;
            heroApp = null;
            heroOpen = open;
            setHero(in.label, "INPUT  ·  " + in.kind, "Switch the TV to this input", accent, null, t, false, false);
            onTileFocused(t);
        });
        t.setOnClickListener(v -> open.run());
        placeTile(strip, t, 192, 108, idx);
        return idx + 1;
    }

    private int addQuickTile(LinearLayout strip, String label, String icon, String action, int accent, int idx) {
        TileView t = new TileView(this, 1.12f);
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ic = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        tp.setColor(Color.WHITE);
        tp.setTypeface(Ui.body(this, true));
        final RectF[] last = {new RectF()};
        final RectF tmp = new RectF();
        final Path path = new Path();
        t.setCustom(label, accent, (c, r, focus) -> {
            if (!r.equals(last[0])) {
                last[0] = new RectF(r);
                p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                        TileView.darken(accent, 0.4f), TileView.darken(accent, 0.14f), Shader.TileMode.CLAMP));
            }
            c.drawRoundRect(r, dp(14), dp(14), p);
            ic.setColor(Color.WHITE);
            Icons.draw(c, icon, r.centerX(), r.top + r.height() * 0.4f, r.height() * 0.18f, ic, path, tmp, d, focus);
            tp.setTextSize(r.height() * 0.14f);
            CharSequence ss = TextUtils.ellipsize(label, tp, r.width() - dp(14), TextUtils.TruncateAt.END);
            c.drawText(ss, 0, ss.length(), r.centerX() - tp.measureText(ss, 0, ss.length()) / 2f, r.bottom - r.height() * 0.15f, tp);
        });
        String key = "qs:" + icon;
        t.setTag(key);
        tileByKey.put(key, t);
        Runnable open = () -> {
            t.pulse();
            sound.play(Sound.SELECT);
            theme.select();
            if (action == null) openSettings(); else openSystemSettings(action);
        };
        t.setOnFocusChangeListener((v, has) -> {
            if (!has) return;
            heroApp = null;
            heroOpen = open;
            setHero(label, "QUICK SETTINGS", action == null ? "Sora and Fire TV settings" : "Opens the Fire TV page", accent, null, t, false, false);
            onTileFocused(t);
        });
        t.setOnClickListener(v -> open.run());
        placeTile(strip, t, 160, 90, idx);
        return idx + 1;
    }

    private String metaFor(AppRepo.App a) {
        long t = repo.lastOpened(a.pkg);
        String cat = catCache.containsKey(a.pkg) ? catCache.get(a.pkg) : Categories.APPS;
        if (t == 0) return cat;
        long mins = (System.currentTimeMillis() - t) / 60000;
        String ago;
        if (mins < 1) ago = "just now";
        else if (mins < 60) ago = mins + (mins == 1 ? " minute ago" : " minutes ago");
        else if (mins < 60 * 24) { long h = mins / 60; ago = h + (h == 1 ? " hour ago" : " hours ago"); }
        else { long dd = mins / (60 * 24); ago = dd == 1 ? "yesterday" : dd + " days ago"; }
        return cat + "  ·  Opened " + ago;
    }

    /** Focus landed on a tile: theme reacts, the row glides into place, the drive line slides over. */
    private void onTileFocused(TileView t) {
        long f0 = System.nanoTime();
        onTileFocused0(t);
        if (ThemeView.verbose) android.util.Log.i("SoraPerf", "focus work " + (System.nanoTime() - f0) / 100_000 / 10f + "ms");
    }

    private void onTileFocused0(TileView t) {
        themeAt(t, t.getAccent());
        LinearLayout strip = (LinearLayout) t.getParent();
        FrameLayout holder = (FrameLayout) strip.getParent();
        Strip hs = (Strip) holder.getParent();
        View row = (View) hs.getParent();

        int wantY = Math.max(0, row.getTop() - dp(ROW_GAP));
        Ui.scrollY(scroll, wantY, 420);
        int sx = hs.getScrollX(), vw = hs.getWidth();
        int leftEdge = sx + dp(40), rightEdge = sx + vw - dp(300);
        if (t.getLeft() < leftEdge) Ui.scrollX(hs, Math.max(0, t.getLeft() - dp(40)), 380);
        else if (t.getRight() > rightEdge) Ui.scrollX(hs, t.getRight() - vw + dp(300), 380);

        // drive line
        View line = holder.findViewWithTag("line"), glow = holder.findViewWithTag("glow");
        float tw = t.getWidth() > 0 ? t.getWidth() : dp(192);
        float lw = tw * 0.42f;
        float lx = t.getLeft() + (tw - lw) / 2f;
        moveLight(line, lx, lw / dp(100), row != litRow);
        moveLight(glow, lx - lw * 0.1f, lw * 1.2f / dp(100), row != litRow);
        // recolor in place (no new objects while moving)
        if (line.getBackground() instanceof GradientDrawable) ((GradientDrawable) line.getBackground()).setColor(t.getAccent() | 0xFF000000);
        if (glow.getBackground() instanceof GradientDrawable) ((GradientDrawable) glow.getBackground()).setColor(Themes.withA(t.getAccent(), 80));

        if (row != litRow) {
            if (litRow != null) {
                View old = litRow.findViewWithTag("label");
                if (old != null) old.animate().alpha(0.5f).setDuration(200).start();
                View oh = ((ViewGroup) ((ViewGroup) litRow).getChildAt(1)).getChildAt(0);
                View ol = oh.findViewWithTag("line"), og = oh.findViewWithTag("glow");
                if (ol != null) ol.animate().alpha(0f).setDuration(160).start();
                if (og != null) og.animate().alpha(0f).setDuration(160).start();
            }
            View label = row.findViewWithTag("label");
            if (label != null) label.animate().alpha(1f).setDuration(220).start();
            litRow = row;
        }
    }

    private void moveLight(View v, float x, float scaleX, boolean jump) {
        v.setPivotX(0);
        if (jump || v.getAlpha() < 0.05f) {
            v.animate().cancel();
            v.setTranslationX(x);
            v.setScaleX(scaleX);
            v.animate().alpha(1f).setDuration(220).start();
        } else {
            v.animate().translationX(x).scaleX(scaleX).alpha(1f).setDuration(340).setInterpolator(Ui.SPRING).start();
        }
    }

    private void focusFirstTile() {
        for (int i = 0; i < rowsBox.getChildCount(); i++) {
            View row = rowsBox.getChildAt(i);
            if (!(row instanceof ViewGroup) || ((ViewGroup) row).getChildCount() < 2) continue;
            View hsv = ((ViewGroup) row).getChildAt(1);
            if (!(hsv instanceof Strip)) continue;
            ViewGroup holder = (ViewGroup) ((Strip) hsv).getChildAt(0);
            ViewGroup strip = (ViewGroup) holder.getChildAt(0);
            if (strip.getChildCount() > 0) {
                // skip the empty-favorites hint if there's a real app below
                View first = strip.getChildAt(0);
                if ("fav:hint".equals(first.getTag()) && i + 1 < rowsBox.getChildCount()) continue;
                first.requestFocus();
                return;
            }
        }
    }

    // =====================================================================
    // Spotlight
    // =====================================================================

    private String heroKey = "";

    private void dockHero(String title, String meta) {
        heroApp = null;
        heroOpen = null;
        setHero(title, "SORA", meta, Ui.ACCENT, null, null, false, false);
        actOpen.setVisibility(View.GONE);
    }

    private void setHero(String name, String eyebrow, String meta, int accent, Bitmap blur, TileView card, boolean isApp, boolean pinned) {
        currentAccent = accent;
        String key = name + "|" + eyebrow;
        boolean changed = !key.equals(heroKey);
        heroKey = key;
        if (!name.contentEquals(heroName.getText())) {
            heroName.setText(name);
            heroName.setAlpha(0f);
            heroName.setTranslationY(dp(14));
            heroName.animate().alpha(1f).translationY(0).setDuration(360).setInterpolator(Ui.EASE_OUT).start();
        }
        heroEyebrow.setText(eyebrow);
        heroEyebrow.setTextColor(Themes.lerpColor(accent | 0xFF000000, Color.WHITE, 0.25f));
        heroMeta.setText(meta == null ? "" : meta);
        if (changed) {
            heroMeta.setAlpha(0f);
            heroMeta.animate().alpha(1f).setStartDelay(80).setDuration(300).start();
        }
        actOpen.setVisibility(heroOpen != null ? View.VISIBLE : View.GONE);
        actOptions.setVisibility(isApp ? View.VISIBLE : View.GONE);
        actPin.setVisibility(isApp ? View.VISIBLE : View.GONE);
        actPin.setText(pinned ? "Pinned" : "Pin");
        actPin.icon.setIcon(pinned ? "pinned" : "pin");

        if (card != null && changed) {
            heroCard.copyFrom(card);
            heroCard.setAlpha(0f);
            heroCard.setTranslationX(dp(34));
            heroCard.setScaleX(0.96f);
            heroCard.setScaleY(0.96f);
            heroCard.animate().alpha(1f).translationX(0).scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(Ui.EASE_OUT).start();
        } else if (card == null) {
            heroCard.animate().alpha(0f).setDuration(200).start();
        }

        if (changed) {
            pendingBlur = repo.prefs().getBoolean(SettingsHub.K_BACKDROP, false) && photoFor == null ? blur : null;
            ui.removeCallbacks(backdropRunner);
            ui.postDelayed(backdropRunner, 150);
        }
    }

    private Bitmap pendingBlur;
    private final Runnable backdropRunner = () -> crossfadeBackdrop(pendingBlur);

    private void crossfadeBackdrop(Bitmap blur) {
        if (ThemeView.verbose) android.util.Log.i("SoraPerf", "backdrop crossfade");
        ImageView in = backAOn ? backB : backA;
        ImageView out = backAOn ? backA : backB;
        backAOn = !backAOn;
        out.animate().alpha(0f).setDuration(500).withEndAction(() -> { if (out.getAlpha() == 0f) out.setVisibility(View.INVISIBLE); }).start();
        if (blur == null) return;
        BitmapDrawable bd = new BitmapDrawable(getResources(), blur);
        bd.setFilterBitmap(true);
        in.setImageDrawable(bd);
        in.setVisibility(View.VISIBLE);
        in.animate().alpha(0.62f).setDuration(600).setInterpolator(Ui.EASE_OUT).start();
    }

    // =====================================================================
    // Launching: the tile zooms to fill the screen, then the app opens
    // =====================================================================

    private void launchApp(TileView from, Intent i, String pkg) {
        if (i == null) { showBanner("Can't open that app"); return; }
        if (launching) return;
        launching = true;
        sound.play(Sound.SELECT);
        theme.select();
        if (from != null && from.isAttachedToWindow() && from.getWidth() > 0) {
            int[] loc = new int[2];
            from.getLocationOnScreen(loc);
            int[] rl = new int[2];
            root.getLocationOnScreen(rl);
            TileView ghost = new TileView(this, 1f);
            ghost.copyFrom(from);
            ghost.setAlwaysLit();
            ghost.setFocusable(false);
            int w = from.getWidth(), h = from.getHeight();
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
            launchLayer.removeAllViews();
            launchLayer.addView(ghost, lp);
            launchLayer.addView(launchFade, new FrameLayout.LayoutParams(-1, -1));
            launchLayer.setVisibility(View.VISIBLE);
            float cx = loc[0] - rl[0] + w / 2f, cy = loc[1] - rl[1] + h / 2f;
            ghost.setTranslationX(cx - w / 2f);
            ghost.setTranslationY(cy - h / 2f);
            ghost.setScaleX(from.getScaleX());
            ghost.setScaleY(from.getScaleY());
            float target = Math.max((float) root.getWidth() / w, (float) root.getHeight() / h) * 1.08f;
            ghost.animate().withLayer().translationX(root.getWidth() / 2f - w / 2f).translationY(root.getHeight() / 2f - h / 2f)
                    .scaleX(target).scaleY(target).setDuration(420).setInterpolator(Ui.EASE).start();
            launchFade.setAlpha(0f);
            launchFade.animate().alpha(1f).setStartDelay(160).setDuration(300).start();
        }
        content.animate().alpha(0f).scaleX(0.97f).scaleY(0.97f).setDuration(300)
                .setInterpolator(new AccelerateInterpolator()).start();
        ui.postDelayed(() -> {
            try {
                startActivity(i);
                if (pkg != null) repo.markLaunched(pkg);
            } catch (Exception e) {
                launching = false;
                launchLayer.removeAllViews();
                launchLayer.setVisibility(View.GONE);
                content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
                showBanner("Can't open that app");
            }
        }, 440);
    }

    private void togglePin(AppRepo.App a) {
        List<String> f = repo.favorites();
        boolean pinned = f.contains(a.pkg);
        if (pinned) f.remove(a.pkg); else f.add(a.pkg);
        repo.setFavorites(f);
        sound.play(Sound.SELECT);
        theme.select();
        actPin.setText(pinned ? "Pin" : "Pinned");
        actPin.icon.setIcon(pinned ? "pin" : "pinned");
        showBanner(pinned ? a.label + " unpinned" : a.label + " pinned to Favorites");
        View keep = getCurrentFocus();
        reloadApps(false);
        if (keep != null) keep.post(keep::requestFocus);
    }

    // =====================================================================
    // Search & Settings
    // =====================================================================

    private void blockHome(boolean block) {
        content.setDescendantFocusability(block ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
        content.animate().alpha(block ? 0.0f : 1f).setDuration(240).start();
    }

    private void openSearch() {
        if (search.isOpen()) return;
        focusBeforeOverlay = getCurrentFocus();
        blockHome(true);
        search.show();
        // the background is nearly hidden behind Search: hold it still so the keyboard gets the full frame budget
        ui.postDelayed(() -> { if (search.isOpen()) theme.setPaused(true); }, 340);
    }

    private void closeSearch() {
        if (!search.isOpen()) return;
        sound.play(Sound.BACK);
        search.hide();
        theme.setPaused(false);
        blockHome(false);
        restoreHomeFocus();
    }

    private void openSettings() {
        if (hub.isOpen()) return;
        if (search.isOpen()) { search.hide(); theme.setPaused(false); }
        else focusBeforeOverlay = getCurrentFocus();
        blockHome(true);
        hub.show();
    }

    private void closeSettings() {
        if (!hub.isOpen()) return;
        sound.play(Sound.BACK);
        hub.hide();
        blockHome(false);
        restoreHomeFocus();
    }

    private void restoreHomeFocus() {
        if (focusBeforeOverlay != null && focusBeforeOverlay.isAttachedToWindow()) focusBeforeOverlay.requestFocus();
        else dockSettings.requestFocus();
    }

    private void hubSub(String which) {
        switch (which) {
            case "hidden": showHiddenApps(); break;
            case "weather": showWeatherPanel(); break;
            case "weatherAuto":
                Weather.clearPlace(repo.prefs());
                showBanner("Weather set to automatic");
                refreshWeather();
                hub.refreshPane();
                break;
            case "guard": showGuardPanel(); break;
            case "updates": manualUpdateCheck(); break;
            case "intro": closeSettings(); ui.postDelayed(this::playIntro, 250); break;
            case "saver": closeSettings(); ui.postDelayed(this::showSaver, 350); break;
            default: break;
        }
    }

    private void openSystemSettings(String action) {
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
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) { }
        }
        showBanner("Couldn't open Settings");
    }

    // =====================================================================
    // Intro & screensaver
    // =====================================================================

    private void playIntro() {
        // Warm-up: while the intro holds its first (solid) frame, the home screen is drawn underneath it
        // so every picture, letter and effect is ready on the graphics chip before the animation starts.
        // Then the reveal doesn't stutter on its first frames.
        content.setAlpha(1f); // hidden behind the intro's solid first frame
        theme.setPaused(true);
        bgLayer.setVisibility(View.INVISIBLE); // fully covered by the intro: give the graphics chip to the intro
        bgPhoto.setAlpha(0f);
        intro.onDone = () -> {
            // the background glows in once the intro is gone (drawing both at once is too much for this TV)
            bgLayer.setAlpha(0f);
            bgLayer.setVisibility(View.VISIBLE);
            bgPhoto.animate().alpha(1f).setDuration(450).setInterpolator(Ui.EASE_OUT).start();
            bgLayer.animate().alpha(1f).setDuration(450).setInterpolator(Ui.EASE_OUT)
                    .withEndAction(() -> theme.setPaused(false)).start();
            ui.postDelayed(() -> { bgLayer.setAlpha(1f); theme.setPaused(false); }, 600);
        };
        intro.play(currentAccent,
                () -> sound.ready(Sound.INTRO) && rowsReady && android.os.SystemClock.uptimeMillis() - rowsReadyAt > 150,
                () -> { content.setAlpha(0f); sound.play(Sound.INTRO); }, () -> {
            content.setScaleX(0.96f);
            content.setScaleY(0.96f);
            if (dbgNoContent) { ui.postDelayed(() -> content.setAlpha(1f), 900); return; }
            content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(560).setInterpolator(Ui.EASE_OUT).start();
        });
    }

    // =====================================================================
    // Lock: never fall back to Amazon's home screen
    // =====================================================================

    private boolean locked() {
        return HomeGuardService.isEnabled(this) && repo.prefs().getBoolean(HomeGuardService.KEY_GUARD_ON, true);
    }

    private String lockTitle() { return locked() ? "Locked to Sora" : "Unlocked"; }

    private String lockMeta() {
        if (!HomeGuardService.isEnabled(this)) return "Needs a one-time setup from your phone. Press OK";
        return locked() ? "Amazon's home screen bounces right back to Sora. OK to unlock"
                : "Back and Home can land on Amazon's home screen. OK to lock";
    }

    private void refreshLock() {
        if (dockLock == null) return;
        dockLock.icon.setIcon(locked() ? "lock" : "unlock");
        dockLock.icon.setColor(dockLock.isFocused() ? Ui.INK : locked() ? Ui.ACCENT : Ui.TEXT);
        dockLock.setAlpha(HomeGuardService.isEnabled(this) ? 1f : 0.7f);
    }

    private void toggleLock() {
        if (!HomeGuardService.isEnabled(this)) {
            // the lock rides on the Back & Home guard, which Fire TV only lets you switch on from a phone
            sound.play(Sound.OPEN);
            showGuardPanel();
            return;
        }
        boolean now = !locked();
        repo.prefs().edit().putBoolean(HomeGuardService.KEY_GUARD_ON, now).apply();
        sound.play(now ? Sound.SELECT : Sound.BACK);
        theme.select();
        refreshLock();
        dockLock.animate().rotation(now ? 0 : -12).setDuration(120)
                .withEndAction(() -> dockLock.animate().rotation(0).setInterpolator(Ui.SPRING).setDuration(260).start()).start();
        showBanner(now ? "Locked. Sora stays on screen" : "Unlocked");
        dockHero(lockTitle(), lockMeta());
    }

    private void buildSaver() {
        saver = new FrameLayout(this);
        saver.setVisibility(View.GONE);
        saverBox = new LinearLayout(this);
        saverBox.setOrientation(LinearLayout.VERTICAL);
        saverBox.setGravity(Gravity.CENTER_HORIZONTAL);
        saverTime = Ui.text(this, 104, Ui.TEXT, Ui.display(this, 300));
        saverTime.setSingleLine(true);
        saverTime.setShadowLayer(dp(20), 0, 0, Color.argb(160, 0, 0, 0));
        saverDate = Ui.text(this, 17, Color.argb(220, 255, 255, 255), Ui.body(this, true));
        saverDate.setLetterSpacing(0.18f);
        saverWeather = Ui.text(this, 15, Ui.SUB, Ui.body(this, false));
        // sized to their text, so "10:33" isn't cut off after the box was measured for "7:44"
        saverBox.addView(saverTime, new LinearLayout.LayoutParams(-2, -2));
        saverBox.addView(saverDate, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.topMargin = dp(14);
        saverBox.addView(saverWeather, wl);
        saver.addView(saverBox, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        root.addView(saver, new FrameLayout.LayoutParams(-1, -1));
    }

    private final Runnable saverCheck = new Runnable() {
        @Override public void run() {
            int mins = repo.prefs().getInt(SettingsHub.K_SAVER, 3);
            if (mins > 0 && saver.getVisibility() != View.VISIBLE && hasWindowFocus()
                    && overlay.getVisibility() != View.VISIBLE && !intro.isPlaying() && !hub.isOpen() && !search.isOpen()
                    && System.currentTimeMillis() - lastInteraction > mins * 60_000L) {
                showSaver();
            }
            updateKeepAwake();
            ui.postDelayed(this, 15000);
        }
    };

    // While Sora is on screen, Fire TV's own screensaver stays away: Sora's screensaver takes its place.
    // After 2 hours without a button press, Sora lets go so the TV can still go to sleep on its own.
    private static final long KEEP_AWAKE_MS = 2 * 60 * 60_000L;
    private boolean keepingAwake;

    private void updateKeepAwake() {
        boolean want = repo.prefs().getInt(SettingsHub.K_SAVER, 3) > 0
                && System.currentTimeMillis() - lastInteraction < KEEP_AWAKE_MS;
        if (want == keepingAwake) return;
        keepingAwake = want;
        if (want) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

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
        saverBox.requestLayout();
    }

    private boolean hideSaver() {
        if (saver.getVisibility() != View.VISIBLE) return false;
        saverBox.animate().cancel();
        saver.animate().alpha(0f).setDuration(300).withEndAction(() -> saver.setVisibility(View.GONE)).start();
        content.animate().alpha(1f).setDuration(400).start();
        ImageView on = backAOn ? backA : backB;
        if (on.getVisibility() == View.VISIBLE) on.animate().alpha(0.62f).setDuration(400).start();
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
                String t = fTime.format(now), ap = fAmPm.format(now);
                String key = t + ap;
                if (!key.equals(lastClock)) {
                    boolean animate = !lastClock.isEmpty();
                    lastClock = key;
                    android.text.SpannableString sp = new android.text.SpannableString(t + " " + ap);
                    sp.setSpan(new android.text.style.RelativeSizeSpan(0.55f), t.length(), sp.length(), 0);
                    sp.setSpan(new android.text.style.ForegroundColorSpan(Ui.SUB), t.length(), sp.length(), 0);
                    clockTime.setText(sp);
                    if (animate) {
                        clockTime.setAlpha(0.3f);
                        clockTime.animate().alpha(1f).setDuration(400).start();
                    }
                    if (saver.getVisibility() == View.VISIBLE) saverTime.setText(t);
                }
                String dt = fDate.format(now);
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
                    // re-measure the weather column so the longer line isn't cut off ("Raleigh," bug)
                    View wc = (View) weatherPlace.getParent();
                    wc.post(() -> { wc.requestLayout(); wc.invalidate(); });
                    lastWeatherLine = r.icon + "  " + r.temp + "°  " + r.desc + (r.place.isEmpty() ? "" : "  ·  " + r.place);
                    weatherIcon.setScaleX(0.4f);
                    weatherIcon.setScaleY(0.4f);
                    weatherIcon.animate().scaleX(1f).scaleY(1f).setInterpolator(Ui.SPRING).setDuration(450).start();
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
    // Side panels (app options, hidden apps, weather, guard, updates)
    // =====================================================================

    private static class Item {
        final String label, hint;
        final Runnable action;
        Item(String l, String h, Runnable a) { label = l; hint = h; action = a; }
    }

    private void showAppMenu(AppRepo.App a, TileView t, String rowKey) {
        sound.play(Sound.OPEN);
        boolean isHidden = repo.hidden().contains(a.pkg);
        boolean pinned = repo.favorites().contains(a.pkg);
        List<Item> items = new ArrayList<>();
        items.add(new Item("Open", null, () -> { closePanel(false); launchApp(t, repo.launchIntent(a.pkg), a.pkg); }));
        items.add(new Item(pinned ? "Unpin from Favorites" : "Pin to Favorites", pinned ? null : "Adds it to the top row", () -> {
            closePanel(true);
            togglePin(a);
        }));
        if (pinned && "fav".equals(rowKey)) {
            items.add(new Item("Move", "Use ◀ ▶ to slide it, OK to drop", () -> { closePanel(false); startMove(a.pkg, t); }));
        }
        items.add(new Item("Move to category", "Now in " + cats.of(a.pkg), () -> showCategoryPicker(a)));
        items.add(new Item(isHidden ? "Unhide" : "Hide app",
                isHidden ? "Put it back in your library" : "Only shows up in Settings → Privacy", () -> {
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
        for (int i = 0; i < Categories.ORDER.length; i++) {
            String c = Categories.ORDER[i];
            if (c.equals(now)) panelFocusIndex = i;
            items.add(new Item(c + (c.equals(now) ? "  ✓" : ""), null, () -> {
                cats.set(a.pkg, c);
                closePanel(true);
                showBanner(a.label + " moved to " + c);
                reloadApps(false);
            }));
        }
        showPanel("Move to category", a.label, items, null);
    }

    private void startMove(String pkg, TileView t) {
        movingPkg = pkg;
        movingTile = t;
        t.requestFocus();
        t.animate().translationY(-dp(10)).scaleX(1.2f).scaleY(1.2f).setDuration(220).setInterpolator(Ui.EASE).start();
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
            f.add(Math.max(0, Math.min(f.size(), j)), movingPkg);
            repo.setFavorites(f);
        }
        TileView t = movingTile;
        t.post(() -> { if (t.getParent() != null) onTileFocused(t); });
    }

    private void endMove() {
        if (movingTile == null) return;
        TileView t = movingTile;
        movingTile = null;
        movingPkg = null;
        t.animate().cancel();
        t.setRotation(0);
        t.animate().translationY(0).scaleX(t.isFocused() ? 1.1f : 1f).scaleY(t.isFocused() ? 1.1f : 1f)
                .setDuration(240).setInterpolator(Ui.SPRING).start();
        sound.play(Sound.SELECT);
        theme.select();
        lastSignature = "";
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
        showPanel("Hidden apps", items.isEmpty() ? "Nothing hidden. Press ☰ on any app to hide it." : "Only you know these are here", items, null);
    }

    private void showGuardPanel() {
        boolean enabled = HomeGuardService.isEnabled(this);
        boolean on = repo.prefs().getBoolean(HomeGuardService.KEY_GUARD_ON, true);
        List<Item> items = new ArrayList<>();
        if (!enabled) {
            items.add(new Item("Check again", "After running the commands from your phone", () -> {
                repo.prefs().edit().putBoolean(HomeGuardService.KEY_GUARD_ON, true).apply();
                if (HomeGuardService.isEnabled(this)) { showBanner("Guard is on"); closePanel(true); }
                else showBanner("Not on yet");
            }));
            items.add(new Item("Open Fire TV settings", "My Fire TV → Developer options → ADB debugging", this::openFireSettings));
            items.add(new Item("Later", null, () -> closePanel(true)));
            showPanel("Back & Home guard",
                    "Fire TV hides this switch, so it's turned on once from your phone:\n\n"
                            + "1. My Fire TV → Developer options → ADB debugging ON\n"
                            + "2. My Fire TV → About → Network: note the IP address\n"
                            + "3. On your phone, open an ADB app, connect to that IP, allow it on the TV\n"
                            + "4. Run:\nsettings put secure enabled_accessibility_services com.vyd02.overdrive/com.vyd02.overdrive.HomeGuardService\n"
                            + "settings put secure accessibility_enabled 1\n"
                            + "appops set com.vyd02.overdrive SYSTEM_ALERT_WINDOW allow",
                    items, null);
        } else {
            items.add(new Item(on ? "Turn off" : "Turn on", on ? "Let Amazon's home stay open" : "Always come back to Sora", () -> {
                repo.prefs().edit().putBoolean(HomeGuardService.KEY_GUARD_ON, !on).apply();
                closePanel(true);
                showBanner(on ? "Guard off" : "Guard on");
            }));
            showPanel("Back & Home guard", on ? "Guard is on." : "Guard is off.", items, null);
        }
    }

    private void maybePromptGuard() {
        if (HomeGuardService.isEnabled(this) || repo.prefs().getBoolean("guard_prompted", false)) return;
        if (overlay.getVisibility() == View.VISIBLE || intro.isPlaying() || hub.isOpen() || search.isOpen()) return;
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
        et.setTextColor(Ui.TEXT);
        et.setHintTextColor(Ui.FAINT);
        et.setTypeface(Ui.body(this, false));
        et.setTextSize(16);
        et.setPadding(dp(16), dp(12), dp(16), dp(12));
        final GradientDrawable eb = new GradientDrawable();
        eb.setCornerRadius(dp(12));
        eb.setColor(Color.argb(40, 255, 255, 255));
        eb.setStroke(dp(2), Color.argb(70, 255, 255, 255));
        et.setBackground(eb);
        et.setOnFocusChangeListener((v, has) -> eb.setStroke(dp(2), has ? Ui.ACCENT : Color.argb(70, 255, 255, 255)));
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
            hub.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            search.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        panel.removeAllViews();
        panel.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(242, 12, 15, 28), Color.argb(250, 7, 9, 18)}));

        View bar = new View(this);
        bar.setBackground(Ui.solid(this, currentAccent | 0xFF000000, 2));
        panel.addView(bar, new LinearLayout.LayoutParams(dp(36), dp(4)));
        TextView t = Ui.text(this, 26, Ui.TEXT, Ui.display(this, 700));
        t.setText(title);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(16);
        panel.addView(t, tlp);
        if (subtitle != null) {
            TextView s = Ui.text(this, 12, Ui.SUB, Ui.body(this, false));
            s.setText(subtitle);
            s.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
            slp.topMargin = dp(6);
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
            row.animate().withLayer().alpha(1f).translationX(0).setStartDelay(60 + i * 40L).setDuration(320).setInterpolator(Ui.EASE_OUT).start();
        }
        TextView hint = Ui.text(this, 11, Ui.FAINT, Ui.body(this, false));
        hint.setText("BACK to close");
        hint.setLetterSpacing(0.15f);
        panel.addView(hint);

        if (!wasOpen) {
            overlay.setVisibility(View.VISIBLE);
            scrim.setAlpha(0f);
            scrim.animate().alpha(1f).setDuration(250).start();
            panel.setTranslationX(dp(420));
            panel.animate().translationX(0).setDuration(360).setInterpolator(Ui.EASE_OUT).start();
        } else {
            panel.setAlpha(0.4f);
            panel.animate().alpha(1f).setDuration(200).start();
        }
        if (first != null) { final View f = first; f.post(f::requestFocus); }
        else if (extra != null) extra.post(extra::requestFocus);
    }

    private View panelRow(Item it) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(18), dp(13), dp(18), dp(13));
        row.setFocusable(true);
        row.setClickable(true);
        final GradientDrawable rb = new GradientDrawable();
        rb.setCornerRadius(dp(14));
        rb.setColor(Color.argb(14, 255, 255, 255));
        row.setBackground(rb);
        TextView l = Ui.text(this, 15, Ui.TEXT, Ui.body(this, true));
        l.setText(it.label);
        row.addView(l);
        TextView h = null;
        if (it.hint != null) {
            h = Ui.text(this, 12, Ui.SUB, Ui.body(this, false));
            h.setText(it.hint);
            row.addView(h);
        }
        final TextView hint = h;
        row.setOnFocusChangeListener((v, has) -> {
            rb.setColor(has ? Color.WHITE : Color.argb(14, 255, 255, 255));
            l.setTextColor(has ? Ui.INK : Ui.TEXT);
            if (hint != null) hint.setTextColor(has ? Color.argb(170, 10, 13, 24) : Ui.SUB);
            v.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f).setDuration(180).setInterpolator(Ui.EASE).start();
        });
        row.setOnClickListener(v -> { sound.play(Sound.SELECT); it.action.run(); });
        return row;
    }

    private void closePanel(boolean animate) {
        if (overlay.getVisibility() != View.VISIBLE) return;
        Runnable done = () -> {
            overlay.setVisibility(View.GONE);
            refreshLock();
            hub.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            search.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            if (!hub.isOpen() && !search.isOpen()) content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            if (hub.isOpen()) hub.refreshPane();
            else if (focusBeforePanel != null && focusBeforePanel.isAttachedToWindow()) focusBeforePanel.requestFocus();
            else focusFirstTile();
        };
        if (!animate) { done.run(); return; }
        scrim.animate().alpha(0f).setDuration(200).start();
        panel.animate().translationX(dp(420)).setDuration(240).setInterpolator(new AccelerateInterpolator())
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
                    if (overlay.getVisibility() == View.VISIBLE || intro.isPlaying() || hub.isOpen() || search.isOpen()) return;
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
        if (notes.length() > 240) notes = notes.substring(0, 240) + "…";
        List<Item> items = new ArrayList<>();
        items.add(new Item("Install now", "Downloads and asks the TV to install", () -> startInstall(r)));
        items.add(new Item("Later", null, () -> closePanel(true)));
        showPanel("Update " + r.version, notes.isEmpty() ? "A new version of Sora is ready." : notes, items, null);
    }

    private void startInstall(Updater.Release r) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            List<Item> items = new ArrayList<>();
            items.add(new Item("Open settings", "Turn on Sora, then come back", () -> {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    openFireSettings();
                }
            }));
            items.add(new Item("Try again", null, () -> startInstall(r)));
            showPanel("One-time setup",
                    "Let Sora install its own updates:\nSettings → My Fire TV → Developer options → Install unknown apps → Sora → ON",
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
        banner.animate().alpha(1f).translationY(0).setInterpolator(Ui.SPRING).setDuration(340).start();
        ui.postDelayed(hideBanner, 3500);
    }

    // =====================================================================
    // Keys
    // =====================================================================

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (crashMode || content == null) return super.dispatchKeyEvent(event);
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        lastInteraction = System.currentTimeMillis();
        if (!keepingAwake) updateKeepAwake();

        if (launching) return code != KeyEvent.KEYCODE_HOME;
        if (intro.isPlaying()) {
            if (down && code != KeyEvent.KEYCODE_HOME) intro.skip();
            return code != KeyEvent.KEYCODE_HOME;
        }
        if (saver.getVisibility() == View.VISIBLE) {
            if (down) hideSaver();
            return true;
        }
        if (movingTile != null) {
            if (down) {
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) stepMove(-1);
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) stepMove(1);
                else if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_BACK) endMove();
            }
            return true;
        }

        boolean panelOpen = overlay.getVisibility() == View.VISIBLE;
        if (code == KeyEvent.KEYCODE_BACK) {
            View f = getCurrentFocus();
            if (f instanceof EditText && up) hideKeyboard(f);
            if (up && !event.isCanceled()) {
                if (panelOpen) { sound.play(Sound.BACK); closePanel(true); }
                else if (hub.isOpen()) { if (!hub.back()) closeSettings(); }
                else if (search.isOpen()) closeSearch();
                else { sound.play(Sound.BACK); scroll.scrollTo(0, 0); focusFirstTile(); }
            }
            return true;
        }

        // Console-style routing between the rows and the spotlight buttons:
        // up from a tile -> its Open/Options/Pin, down -> back to that tile, up again -> the row above.
        if (down && !panelOpen && !hub.isOpen() && !search.isOpen()
                && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN)) {
            View f = getCurrentFocus();
            boolean onTile = f instanceof TileView && f == heroTile && isRowTile(f);
            boolean onButton = f == actOpen || f == actOptions || f == actPin;
            if (code == KeyEvent.KEYCODE_DPAD_UP && onTile && firstVisibleAction() != null) {
                lastDy = -1;
                sound.play(Sound.MOVE);
                firstVisibleAction().requestFocus();
                return true;
            }
            if (onButton && heroTile != null && heroTile.isAttachedToWindow()) {
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    lastDy = 1;
                    sound.play(Sound.MOVE);
                    heroTile.requestFocus();
                    return true;
                }
                View above = tileInRowAbove(heroTile);
                if (above != null) {
                    lastDy = -1;
                    sound.play(Sound.MOVE);
                    above.requestFocus();
                    return true;
                }
                // no row above: fall through, so Up reaches the top bar
            }
        }

        if (down && isDpad(code)) {
            View f = getCurrentFocus();
            boolean valueRow = f != null && f.getTag(R.id.value_row) != null
                    && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT);
            if (f != null && !(f instanceof EditText) && !valueRow) {
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
                    return true; // nowhere to go: don't let the system click either
                } else {
                    sound.play(Sound.MOVE);
                    // Moving focus here (instead of letting Android do it) keeps Fire TV's click silent
                    if (next.requestFocus(dir)) return true;
                }
            }
        }

        if (code == KeyEvent.KEYCODE_MENU && !panelOpen && !hub.isOpen() && !search.isOpen()) {
            View f = getCurrentFocus();
            boolean appTile = f instanceof TileView && byPkgFromFocus() != null;
            if (!appTile) {
                if (up) { sound.play(Sound.OPEN); openSettings(); }
                return true;
            }
        }
        if (code == KeyEvent.KEYCODE_SEARCH && up && !panelOpen && !hub.isOpen()) { openSearch(); return true; }
        return super.dispatchKeyEvent(event);
    }

    private View firstVisibleAction() {
        if (actOpen.getVisibility() == View.VISIBLE) return actOpen;
        if (actOptions.getVisibility() == View.VISIBLE) return actOptions;
        return null;
    }

    /** True for tiles that live in the home screen's rows. */
    private boolean isRowTile(View v) {
        View p = (View) v.getParent();
        for (int i = 0; i < 4 && p != null; i++) {
            if (p == rowsBox) return true;
            p = p.getParent() instanceof View ? (View) p.getParent() : null;
        }
        return false;
    }

    /** The tile in the row above that sits closest (left to right) to this one. */
    private View tileInRowAbove(View tile) {
        View row = tile;
        while (row != null && row.getParent() != rowsBox) row = row.getParent() instanceof View ? (View) row.getParent() : null;
        if (row == null) return null;
        int idx = rowsBox.indexOfChild(row);
        int[] here = new int[2];
        tile.getLocationOnScreen(here);
        for (int r = idx - 1; r >= 0; r--) {
            View prev = rowsBox.getChildAt(r);
            if (!(prev instanceof ViewGroup) || ((ViewGroup) prev).getChildCount() < 2) continue;
            View hsv = ((ViewGroup) prev).getChildAt(1);
            if (!(hsv instanceof Strip)) continue;
            ViewGroup strip = (ViewGroup) ((ViewGroup) ((Strip) hsv).getChildAt(0)).getChildAt(0);
            View best = null;
            int bestDist = Integer.MAX_VALUE;
            for (int i = 0; i < strip.getChildCount(); i++) {
                View c = strip.getChildAt(i);
                int[] loc = new int[2];
                c.getLocationOnScreen(loc);
                int dist = Math.abs(loc[0] - here[0]);
                if (dist < bestDist) { bestDist = dist; best = c; }
            }
            if (best != null) return best;
        }
        return null;
    }

    private static void muteSystemSounds(View v) {
        v.setSoundEffectsEnabled(false);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) muteSystemSounds(g.getChildAt(i));
        }
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
