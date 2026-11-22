package com.vyd02.overdrive;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** The TV's HDMI (and other) inputs, so you can jump straight to a console or cable box. */
final class Inputs {

    static class Input {
        String id, label, kind;
    }

    private Inputs() { }

    static List<Input> list(Context c) {
        List<Input> out = new ArrayList<>();
        try {
            TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return out;
            List<TvInputInfo> all = tim.getTvInputList();
            for (TvInputInfo info : all) {
                int type = info.getType();
                if (type != TvInputInfo.TYPE_HDMI && type != TvInputInfo.TYPE_COMPOSITE
                        && type != TvInputInfo.TYPE_COMPONENT) continue;
                if (info.getParentId() != null) continue; // devices behind a port are folded into the port below
                Input in = new Input();
                in.id = info.getId();
                in.kind = type == TvInputInfo.TYPE_HDMI ? "HDMI" : "AV";
                CharSequence label = info.loadCustomLabel(c);
                if (TextUtils.isEmpty(label)) label = info.loadLabel(c);
                // if a device on this port named itself (e.g. "PlayStation 5"), show that and switch to it
                for (TvInputInfo child : all) {
                    if (info.getId().equals(child.getParentId())) {
                        CharSequence cl = child.loadLabel(c);
                        if (!TextUtils.isEmpty(cl)) { label = cl; in.id = child.getId(); }
                        break;
                    }
                }
                in.label = label == null ? in.kind : label.toString();
                out.add(in);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static Intent intent(Input in) {
        return new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(in.id))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
