package com.vyd02.overdrive;

import android.app.Application;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Catches any crash, saves the exact error to a file, then lets Android close the app as usual.
 * The next launch shows that error on screen (see CrashScreen) so it can be photographed and fixed.
 */
public class SoraApp extends Application {

    static final String CRASH_FILE = "last_crash.txt";

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                pw.println("Sora " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
                pw.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
                pw.println("Android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")  "
                        + android.os.Build.MODEL);
                pw.println("Thread: " + thread.getName());
                pw.println();
                error.printStackTrace(pw);
                pw.flush();
                try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), CRASH_FILE))) {
                    out.write(sw.toString().getBytes("UTF-8"));
                }
            } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }
}
