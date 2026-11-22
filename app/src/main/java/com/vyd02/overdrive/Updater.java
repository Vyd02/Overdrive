package com.vyd02.overdrive;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Self-update from GitHub Releases. Publish a release tagged v1.2.3 with the APK attached;
 * Overdrive compares that to its own version and installs it.
 */
final class Updater {

    static class Release {
        String version;
        String apkUrl;
        String notes;
    }

    interface Progress { void onProgress(int percent); }

    private Updater() { }

    /** Returns the newer release, or null when already up to date. */
    static Release check() throws Exception {
        JSONObject j = new JSONObject(Net.get("https://api.github.com/repos/" + BuildConfig.GITHUB_REPO + "/releases/latest"));
        String tag = j.optString("tag_name", "").replaceFirst("^[vV]", "");
        if (compare(tag, BuildConfig.VERSION_NAME) <= 0) return null;
        JSONArray assets = j.optJSONArray("assets");
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.getJSONObject(i);
            if (a.optString("name").toLowerCase().endsWith(".apk")) {
                Release r = new Release();
                r.version = tag;
                r.apkUrl = a.getString("browser_download_url");
                r.notes = j.optString("body", "");
                return r;
            }
        }
        return null;
    }

    /** Downloads the APK straight into the system installer. The TV then shows its own Install prompt. */
    static void install(Context ctx, Release r, Progress p) throws Exception {
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int id = pi.createSession(params);
        PackageInstaller.Session s = pi.openSession(id);
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(r.apkUrl).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "Overdrive-Launcher");
            long total = c.getContentLengthLong();
            try (InputStream in = c.getInputStream(); OutputStream out = s.openWrite("overdrive.apk", 0, total)) {
                byte[] buf = new byte[65536];
                long done = 0;
                int n, last = -1;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (total > 0) {
                        int pct = (int) (done * 100 / total);
                        if (pct != last) { last = pct; p.onProgress(pct); }
                    }
                }
                s.fsync(out);
            } finally {
                c.disconnect();
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pend = PendingIntent.getBroadcast(ctx, id, new Intent(ctx, InstallReceiver.class), flags);
            s.commit(pend.getIntentSender());
        } catch (Exception e) {
            s.abandon();
            throw e;
        } finally {
            s.close();
        }
    }

    static int compare(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? parse(x[i]) : 0;
            int yi = i < y.length ? parse(y[i]) : 0;
            if (xi != yi) return Integer.compare(xi, yi);
        }
        return 0;
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (Exception e) { return 0; }
    }
}
