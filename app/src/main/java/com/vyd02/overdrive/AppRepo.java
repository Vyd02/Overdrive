package com.vyd02.overdrive;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds installed apps, and keeps the hidden list and the recently used list. */
public class AppRepo {

    public static class App {
        public String pkg;
        public String label;
        public Drawable banner; // wide TV art, may be null
        public Drawable icon;
        // prepared off the UI thread so scrolling never waits on image work
        public android.graphics.Bitmap artBmp, iconBmp;
        public int accent;
        long updated;
    }

    // keeps prepared images between reloads so returning home doesn't redo image work
    private final Map<String, App> cache = new java.util.concurrent.ConcurrentHashMap<>();

    private static final String PREFS = "overdrive";
    private static final String KEY_HIDDEN = "hidden";
    private static final String KEY_RECENT = "recent";
    private static final int MAX_RECENT = 8;

    private final Context ctx;
    private final SharedPreferences prefs;

    public AppRepo(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = this.ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Every launchable app except this launcher, sorted A–Z. */
    public List<App> loadAll() {
        PackageManager pm = ctx.getPackageManager();
        Map<String, App> map = new LinkedHashMap<>();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, map);
        collect(pm, Intent.CATEGORY_LAUNCHER, map);
        List<App> list = new ArrayList<>(map.values());
        Collections.sort(list, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return list;
    }

    private void collect(PackageManager pm, String category, Map<String, App> out) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> res;
        try {
            res = pm.queryIntentActivities(i, 0);
        } catch (Exception e) {
            return;
        }
        for (ResolveInfo r : res) {
            ActivityInfo ai = r.activityInfo;
            if (ai == null) continue;
            String pkg = ai.packageName;
            if (pkg.equals(ctx.getPackageName()) || out.containsKey(pkg)) continue;
            long updated = 0;
            try { updated = pm.getPackageInfo(pkg, 0).lastUpdateTime; } catch (Exception ignored) { }
            App cached = cache.get(pkg);
            if (cached != null && cached.updated == updated) { out.put(pkg, cached); continue; }
            App a = new App();
            a.pkg = pkg;
            a.updated = updated;
            CharSequence l = r.loadLabel(pm);
            a.label = l == null ? pkg : l.toString().trim();
            // Ask for the sharpest version of the art the app ships, so tiles stay crisp when scaled up
            int bannerId = ai.banner != 0 ? ai.banner : ai.applicationInfo.banner;
            a.banner = hiRes(pm, ai, bannerId, android.util.DisplayMetrics.DENSITY_XXHIGH);
            if (a.banner == null) {
                try {
                    a.banner = ai.loadBanner(pm);
                    if (a.banner == null) a.banner = ai.applicationInfo.loadBanner(pm);
                } catch (Exception ignored) { }
            }
            a.icon = hiRes(pm, ai, ai.getIconResource(), android.util.DisplayMetrics.DENSITY_XXXHIGH);
            if (a.icon == null) {
                try { a.icon = r.loadIcon(pm); } catch (Exception ignored) { }
            }
            a.artBmp = a.banner != null ? TileView.toBitmap(a.banner, 512, 288) : null;
            a.iconBmp = a.icon != null ? TileView.toBitmap(a.icon, 256, 256) : null;
            a.accent = TileView.pickAccent(a.artBmp != null ? a.artBmp : a.iconBmp);
            a.banner = null; // bitmaps are all we keep
            a.icon = null;
            cache.put(pkg, a);
            out.put(pkg, a);
        }
    }

    private static Drawable hiRes(PackageManager pm, ActivityInfo ai, int resId, int density) {
        if (resId == 0) return null;
        try {
            android.content.res.Resources res = pm.getResourcesForApplication(ai.applicationInfo);
            return res.getDrawableForDensity(resId, density, null);
        } catch (Exception e) {
            return null;
        }
    }

    public Intent launchIntent(String pkg) {
        PackageManager pm = ctx.getPackageManager();
        Intent i = null;
        try { i = pm.getLeanbackLaunchIntentForPackage(pkg); } catch (Exception ignored) { }
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    // ---- hidden apps ----

    public Set<String> hidden() {
        return new HashSet<>(prefs.getStringSet(KEY_HIDDEN, new HashSet<>()));
    }

    public void setHidden(String pkg, boolean hide) {
        Set<String> s = hidden();
        if (hide) s.add(pkg); else s.remove(pkg);
        prefs.edit().putStringSet(KEY_HIDDEN, s).apply();
        if (hide) removeRecent(pkg);
    }

    // ---- recents (most recent first) ----

    public List<String> recents() {
        String raw = prefs.getString(KEY_RECENT, "");
        List<String> out = new ArrayList<>();
        for (String p : raw.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    public void markLaunched(String pkg) {
        List<String> r = recents();
        r.remove(pkg);
        r.add(0, pkg);
        while (r.size() > MAX_RECENT) r.remove(r.size() - 1);
        prefs.edit().putString(KEY_RECENT, android.text.TextUtils.join(",", r)).apply();
    }

    private void removeRecent(String pkg) {
        List<String> r = recents();
        if (r.remove(pkg)) prefs.edit().putString(KEY_RECENT, android.text.TextUtils.join(",", r)).apply();
    }

    public SharedPreferences prefs() { return prefs; }
}
