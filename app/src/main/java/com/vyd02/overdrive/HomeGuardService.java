package com.vyd02.overdrive;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;

/**
 * Watches for Amazon's home screen coming up (Back out of an app, Home button, after boot)
 * and sends you straight to Overdrive instead.
 */
public class HomeGuardService extends AccessibilityService {

    static final String KEY_GUARD_ON = "guard_on";
    private static final String AMAZON_HOME = "com.amazon.tv.launcher";
    private long lastBounce;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (e.getPackageName() == null || !AMAZON_HOME.contentEquals(e.getPackageName())) return;
        if (!getSharedPreferences("overdrive", MODE_PRIVATE).getBoolean(KEY_GUARD_ON, true)) return;
        long now = System.currentTimeMillis();
        if (now - lastBounce < 700) return; // don't fire twice for the same screen
        lastBounce = now;
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try { startActivity(i); } catch (Exception ignored) { }
    }

    /** TV waking from standby (the normal power button) — open Overdrive right away. */
    private final BroadcastReceiver wake = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!getSharedPreferences("overdrive", MODE_PRIVATE).getBoolean(BootReceiver.KEY_OPEN_ON_START, true)) return;
            new Handler(Looper.getMainLooper()).postDelayed(() -> BootReceiver.open(HomeGuardService.this), 400);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        try { registerReceiver(wake, f); } catch (Exception ignored) { }
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(wake); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    public void onInterrupt() { }

    static boolean isEnabled(Context c) {
        String list = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null) return false;
        String me = new ComponentName(c, HomeGuardService.class).flattenToString();
        String meShort = new ComponentName(c, HomeGuardService.class).flattenToShortString();
        for (String s : list.split(":")) {
            if (s.equalsIgnoreCase(me) || s.equalsIgnoreCase(meShort)) return true;
        }
        return false;
    }
}
