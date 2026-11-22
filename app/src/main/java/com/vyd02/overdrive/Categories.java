package com.vyd02.overdrive;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/** Sorts apps into Streaming / Games / Music / Tools / Apps. You can override any app from its ☰ menu. */
final class Categories {

    static final String STREAMING = "Streaming", GAMES = "Games", MUSIC = "Music", TOOLS = "Tools", APPS = "Apps";
    static final String[] ORDER = {STREAMING, GAMES, MUSIC, APPS, TOOLS};
    private static final String KEY = "cat_";

    private static final String[] STREAM_HINTS = {
            "netflix", "hulu", "disney", "hbo", ".max", "wbd", "youtube", "avod", "primevideo", "plex", "peacock",
            "paramount", "cbs", "tubi", "pluto", "sling", "espn", "crunchyroll", "twitch", "fubo", "philo", "vudu",
            "fandango", "starz", "showtime", "amc", "britbox", "discovery", "frndly", "kodi", "jellyfin", "emby",
            "stremio", "rewind", "funimation", "roku", "appletv", "apple.atve", "nbc", "foxnow", "fox.", "pbs",
            "freevee", "imdb", "redbox", "mubi", "criterion", "dazn", "nfl", "nba", "mlb", "nhl", "vix", "xumo"};
    private static final String[] GAME_HINTS = {
            "game", "games", "retroarch", "emulat", "luna", "xbox", "xcloud", "geforce", "nvidia", "steamlink",
            "moonlight", "parsec", "chiaki", "dolphin", "ppsspp", "duckstation", "aethersx", "nintendo", "playstation"};
    private static final String[] MUSIC_HINTS = {
            "spotify", "pandora", "music", "iheart", "soundcloud", "tidal", "sirius", "deezer", "audible", "radio",
            "tunein", "podcast", "amazon.mp3", "shazam", "napster", "qobuz"};
    private static final String[] TOOL_HINTS = {
            "settings", "downloader", "esaba", "file", "explorer", "x-plore", "lonelycatgames", "browser", "silk",
            "chrome", "firefox", "vpn", "speedtest", "analiti", "cleaner", "manager", "launcher", "tv.utils", "remote",
            "sendfiles", "installer", "adb", "venezia", "appstore", "terminal", "wifi", "bluetooth", "screen"};

    private final PackageManager pm;
    private final SharedPreferences prefs;

    Categories(Context c, SharedPreferences prefs) {
        this.pm = c.getPackageManager();
        this.prefs = prefs;
    }

    String of(String pkg) {
        String forced = prefs.getString(KEY + pkg, null);
        if (forced != null) return forced;
        String p = pkg.toLowerCase();
        if (has(p, GAME_HINTS)) return GAMES;
        if (has(p, MUSIC_HINTS)) return MUSIC;
        if (has(p, STREAM_HINTS)) return STREAMING;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            if ((ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0) return GAMES;
            if (Build.VERSION.SDK_INT >= 26) {
                switch (ai.category) {
                    case ApplicationInfo.CATEGORY_GAME: return GAMES;
                    case ApplicationInfo.CATEGORY_AUDIO: return MUSIC;
                    case ApplicationInfo.CATEGORY_VIDEO: return STREAMING;
                    case ApplicationInfo.CATEGORY_PRODUCTIVITY: return TOOLS;
                    default: break;
                }
            }
        } catch (Exception ignored) { }
        if (has(p, TOOL_HINTS)) return TOOLS;
        return APPS;
    }

    void set(String pkg, String cat) { prefs.edit().putString(KEY + pkg, cat).apply(); }

    private static boolean has(String p, String[] hints) {
        for (String h : hints) if (p.contains(h)) return true;
        return false;
    }
}
