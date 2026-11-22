package com.vyd02.overdrive;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;

/** Console-style UI sounds. Short, soft, and mutable from the menu. */
final class Sound {

    static final String KEY_SOUNDS = "sounds_on";
    static final int MOVE = 0, SELECT = 1, BACK = 2, OPEN = 3, BUMP = 4, THEME = 5, INTRO = 6;

    private final SoundPool pool;
    private final int[] ids = new int[7];
    private final boolean[] ready = new boolean[7];
    private final SharedPreferences prefs;
    private long lastMove;

    Sound(Context c, SharedPreferences prefs) {
        this.prefs = prefs;
        pool = new SoundPool.Builder().setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build();
        int[] res = {R.raw.snd_move, R.raw.snd_select, R.raw.snd_back, R.raw.snd_open,
                R.raw.snd_bump, R.raw.snd_theme, R.raw.snd_intro};
        pool.setOnLoadCompleteListener((p, sampleId, status) -> {
            for (int i = 0; i < ids.length; i++) if (ids[i] == sampleId && status == 0) ready[i] = true;
        });
        for (int i = 0; i < res.length; i++) ids[i] = pool.load(c, res[i], 1);
    }

    boolean on() { return prefs.getBoolean(KEY_SOUNDS, true); }

    void play(int which) {
        if (!on() || !ready[which]) return;
        if (which == MOVE) {
            long now = System.currentTimeMillis();
            if (now - lastMove < 45) return; // holding the D-pad shouldn't machine-gun
            lastMove = now;
        }
        float vol = which == MOVE ? 0.35f : which == INTRO ? 0.85f : 0.5f; // soft by design
        pool.play(ids[which], vol, vol, 1, 0, 1f);
    }

    void release() { pool.release(); }
}
