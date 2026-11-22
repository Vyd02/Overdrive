package com.vyd02.overdrive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Opens Overdrive as soon as the TV finishes starting up, and right after an update installs. */
public class BootReceiver extends BroadcastReceiver {

    static final String KEY_OPEN_ON_START = "open_on_start";

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        boolean update = Intent.ACTION_MY_PACKAGE_REPLACED.equals(a);
        if (!update && !c.getSharedPreferences("overdrive", Context.MODE_PRIVATE).getBoolean(KEY_OPEN_ON_START, true)) return;
        open(c);
    }

    static void open(Context c) {
        try {
            c.startActivity(new Intent(c, MainActivity.class).putExtra(MainActivity.EXTRA_INTRO, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        } catch (Exception ignored) { }
    }
}
