package com.vyd02.overdrive;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Remote-friendly search: an on-screen keyboard on the left, live results on the right. */
public class SearchOverlay extends FrameLayout {

    /** Fades apply to each piece directly: no full-screen offscreen pass while animating. */
    @Override public boolean hasOverlappingRendering() { return false; }

    interface Host {
        List<AppRepo.App> apps();
        void launch(AppRepo.App a, TileView from);
        void play(int sound);
        void focused(TileView t, int accent);
    }

    private static final String[] ROWS = {"abcdef", "ghijkl", "mnopqr", "stuvwx", "yz0123", "456789"};
    private final Host host;
    private final float d;
    private final StringBuilder query = new StringBuilder();
    private final TextView queryText, resultsLabel;
    private final View caret;
    private final LinearLayout results;
    private View firstKey;

    SearchOverlay(Context c, Host host) {
        super(c);
        this.host = host;
        d = c.getResources().getDisplayMetrics().density;
        setVisibility(GONE);
        setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.argb(245, 6, 8, 16), Color.argb(225, 6, 8, 16), Color.argb(200, 6, 8, 16)}));

        LinearLayout cols = new LinearLayout(c);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        cols.setPadding(dp(48), dp(36), dp(40), 0);
        addView(cols, new LayoutParams(-1, -1));

        // ---- left: query + keyboard ----
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        noClip(left);
        cols.addView(left, new LinearLayout.LayoutParams(dp(330), -1));

        TextView title = Ui.text(c, 30, Ui.TEXT, Ui.display(c, 700));
        title.setText("Search");
        left.addView(title);

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(14), 0, dp(14), 0);
        box.setBackground(Ui.glass(c, 14));
        Ui.IconView mag = new Ui.IconView(c, "search");
        mag.setColor(Ui.SUB);
        box.addView(mag, new LinearLayout.LayoutParams(dp(24), dp(24)));
        queryText = Ui.text(c, 20, Ui.TEXT, Ui.display(c, 500));
        queryText.setSingleLine(true);
        queryText.setHint("Type an app name");
        queryText.setHintTextColor(Ui.FAINT);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(-2, -2);
        qlp.leftMargin = dp(10);
        box.addView(queryText, qlp);
        caret = new View(c);
        caret.setBackgroundColor(Ui.ACCENT);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(2), dp(22));
        clp.leftMargin = dp(2);
        box.addView(caret, clp);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(52));
        blp.topMargin = dp(16);
        blp.bottomMargin = dp(18);
        left.addView(box, blp);

        for (String row : ROWS) {
            LinearLayout r = keyRow();
            for (char ch : row.toCharArray()) r.addView(key(String.valueOf(ch).toUpperCase(Locale.US), String.valueOf(ch), 1), keyLp(1));
            left.addView(r);
        }
        LinearLayout last = keyRow();
        last.addView(key("Space", " ", 3), keyLp(3));
        last.addView(key("⌫", "\b", 2), keyLp(2));
        last.addView(key("Clear", "\u0000", 1), keyLp(1));
        left.addView(last);

        // ---- right: results ----
        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(36), dp(6), 0, 0);
        noClip(right);
        right.setClipChildren(true); // results can't scroll up over their heading
        cols.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));
        resultsLabel = Ui.text(c, 12, Ui.SUB, Ui.body(c, true));
        resultsLabel.setLetterSpacing(0.2f);
        right.addView(resultsLabel);
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setPadding(dp(4), dp(14), dp(10), dp(40));
        results = new LinearLayout(c);
        results.setOrientation(LinearLayout.VERTICAL);
        noClip(results);
        sv.addView(results);
        right.addView(sv, new LinearLayout.LayoutParams(-1, -1));
    }

    private LinearLayout keyRow() {
        LinearLayout r = new LinearLayout(getContext());
        r.setOrientation(LinearLayout.HORIZONTAL);
        noClip(r);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        r.setLayoutParams(lp);
        return r;
    }

    private LinearLayout.LayoutParams keyLp(int span) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), span);
        lp.rightMargin = dp(8);
        return lp;
    }

    private View key(String label, String value, int span) {
        TextView k = Ui.text(getContext(), span > 1 || label.length() > 1 ? 14 : 17, Ui.TEXT, Ui.display(getContext(), 500));
        k.setText(label);
        k.setGravity(Gravity.CENTER);
        k.setFocusable(true);
        k.setClickable(true);
        GradientDrawable bg = Ui.glass(getContext(), 10);
        k.setBackground(bg);
        k.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Color.WHITE : Ui.GLASS);
            k.setTextColor(has ? Ui.INK : Ui.TEXT);
            v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f)
                    .setInterpolator(has ? Ui.SPRING : Ui.EASE).setDuration(has ? 220 : 160).start();
        });
        k.setOnClickListener(v -> {
            host.play(Sound.MOVE);
            if (value.equals("\b")) { if (query.length() > 0) query.setLength(query.length() - 1); }
            else if (value.equals("\u0000")) query.setLength(0);
            else if (!(value.equals(" ") && (query.length() == 0 || query.charAt(query.length() - 1) == ' '))) query.append(value);
            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(70)
                    .withEndAction(() -> v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(160).setInterpolator(Ui.SPRING).start()).start();
            refresh();
        });
        if (firstKey == null) firstKey = k;
        return k;
    }

    boolean isOpen() { return getVisibility() == VISIBLE; }

    void show() {
        query.setLength(0);
        refresh();
        setVisibility(VISIBLE);
        setAlpha(0f);
        setTranslationY(dp(24));
        animate().alpha(1f).translationY(0).setDuration(320).setInterpolator(Ui.EASE_OUT).start();
        firstKey.post(firstKey::requestFocus);
        blink();
    }

    void hide() {
        animate().alpha(0f).translationY(dp(16)).setDuration(200)
                .withEndAction(() -> setVisibility(GONE)).start();
    }

    private void blink() {
        if (!isOpen()) return;
        caret.animate().alpha(caret.getAlpha() > 0.5f ? 0f : 1f).setDuration(480).withEndAction(this::blink).start();
    }

    private void refresh() {
        String q = query.toString().trim().toLowerCase(Locale.US);
        queryText.setText(query.toString().toUpperCase(Locale.US).isEmpty() ? "" : query.toString());
        List<AppRepo.App> all = host.apps();
        List<AppRepo.App> hits = new ArrayList<>();
        if (q.isEmpty()) {
            hits.addAll(all);
            resultsLabel.setText("ALL APPS");
        } else {
            for (AppRepo.App a : all) if (a.label.toLowerCase(Locale.US).startsWith(q)) hits.add(a);
            for (AppRepo.App a : all) {
                String l = a.label.toLowerCase(Locale.US);
                if (!hits.contains(a) && (l.contains(q) || initials(l).startsWith(q.replace(" ", "")))) hits.add(a);
            }
            resultsLabel.setText(hits.isEmpty() ? "NO MATCHES FOR \"" + query.toString().trim().toUpperCase(Locale.US) + "\""
                    : hits.size() + (hits.size() == 1 ? " RESULT" : " RESULTS"));
        }
        results.removeAllViews();
        LinearLayout row = null;
        int cols = 3;
        for (int i = 0; i < hits.size() && i < 30; i++) {
            if (i % cols == 0) {
                row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                noClip(row);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.bottomMargin = dp(16);
                results.addView(row, rl);
            }
            AppRepo.App a = hits.get(i);
            TileView t = new TileView(getContext(), 1.1f);
            t.setApp(a);
            t.setOnClickListener(v -> host.launch(a, t));
            t.setOnFocusChangeListener((v, has) -> { if (has) host.focused(t, t.getAccent()); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(150), dp(84));
            lp.rightMargin = dp(16);
            row.addView(t, lp);
            if (i < 6) {
                t.setAlpha(0f);
                t.setTranslationY(dp(10));
                t.animate().alpha(1f).translationY(0).setStartDelay(i * 30L).setDuration(260).setInterpolator(Ui.EASE_OUT).start();
            }
        }
    }

    private static String initials(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : s.split("[^a-z0-9]+")) if (!w.isEmpty()) b.append(w.charAt(0));
        return b.toString();
    }

    private int dp(float v) { return Math.round(v * d); }

    private static void noClip(ViewGroup v) {
        v.setClipChildren(false);
        v.setClipToPadding(false);
    }
}
