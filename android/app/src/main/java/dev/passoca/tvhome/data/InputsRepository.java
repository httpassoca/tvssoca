package dev.passoca.tvhome.data;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/** The TV's inputs (HDMI, AV, the tuner) as TvInputManager lists them; no permission needed. */
public final class InputsRepository {

    public static final class Input {
        public final String id, label;
        public final boolean connected, tuner, hdmi;

        Input(String id, String label, boolean connected, boolean tuner, boolean hdmi) {
            this.id = id; this.label = label; this.connected = connected; this.tuner = tuner; this.hdmi = hdmi;
        }

        /** HDMI and AV switch through their passthrough channel URI; the tuner opens its channel list. */
        public Intent intent() {
            Uri uri = tuner ? TvContract.buildChannelsUriForInput(id) : TvContract.buildChannelUriForPassthroughInput(id);
            return new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
    }

    private InputsRepository() {}

    public static List<Input> load(Context ctx) {
        List<Input> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        List<Input> tuners = new ArrayList<>();
        for (TvInputInfo info : tim.getTvInputList()) {
            if (info.isHidden(ctx)) continue;
            CharSequence custom = info.loadCustomLabel(ctx);
            String label = String.valueOf(custom != null ? custom : info.loadLabel(ctx));
            boolean connected = tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED;
            boolean tuner = info.getType() == TvInputInfo.TYPE_TUNER;
            Input in = new Input(info.getId(), label, connected, tuner, info.getType() == TvInputInfo.TYPE_HDMI);
            if (tuner) tuners.add(in); else out.add(in);
        }
        out.addAll(tuners);   // Live TV last, as in the CleanYourTV Inputs menu
        return out;
    }
}
