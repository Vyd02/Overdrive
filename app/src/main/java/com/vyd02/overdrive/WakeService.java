package com.vyd02.overdrive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

/**
 * Built-in "launch on boot / launch on wake". Stays running quietly so that the moment the
 * TV wakes from standby (the normal power button), Sora comes up instead of Amazon's home.
 */
public class WakeService extends Service {

    private static final String CHANNEL = "overdrive_wake";

    private final BroadcastReceiver wake = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!getSharedPreferences("overdrive", MODE_PRIVATE).getBoolean(BootReceiver.KEY_OPEN_ON_START, true)) return;
            // the guard (if on) already handles this instantly; this is the backup
            new Handler(Looper.getMainLooper()).postDelayed(() -> BootReceiver.open(WakeService.this), 250);
        }
    };

    static void start(Context c) {
        try {
            Intent i = new Intent(c, WakeService.class);
            // From the open app a plain start is allowed and can't trip Android's 5-second
            // "foreground service" deadline; only the boot receiver needs the foreground start.
            if (Build.VERSION.SDK_INT >= 26 && !(c instanceof android.app.Activity)) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) { }
    }

    /** Can Sora open itself from the background on this TV? */
    static boolean canLaunchFromBackground(Context c) {
        if (Build.VERSION.SDK_INT < 29) return true;                     // Fire OS 7 and older: always
        if (HomeGuardService.isEnabled(c)) return true;                  // accessibility exemption
        return Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(c); // "display over other apps"
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Open on power-up", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            if (nm != null) nm.createNotificationChannel(ch);
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this).setPriority(Notification.PRIORITY_MIN);
        }
        Notification n = b.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Sora")
                .setContentText("Ready to open when the TV turns on")
                .setOngoing(true)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(7, n);
        } catch (Exception ignored) { }
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        try { registerReceiver(wake, f); } catch (Exception ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(wake); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
