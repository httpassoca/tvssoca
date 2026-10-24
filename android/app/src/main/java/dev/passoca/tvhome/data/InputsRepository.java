package dev.passoca.tvhome.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The TV's own inputs as TvInputManager lists them; no permission needed. Only the physical ports (HDMI, AV,
 * component...) and the built-in tuner are kept: the "tuner" inputs that apps such as Play Movies, VLC or
 * Globoplay register for their channel rows are not inputs a person switches to.
 */
public final class InputsRepository {

    public static final class Input {
        public final String id, label;
        /** A device is plugged in and on (true), plugged in but asleep (null), or nothing is there (false). */
        public final Boolean connected;
        public final boolean tuner, hdmi;
        final String sortKey;

        Input(String id, String label, Boolean connected, boolean tuner, boolean hdmi, String sortKey) {
            this.id = id; this.label = label; this.connected = connected; this.tuner = tuner; this.hdmi = hdmi; this.sortKey = sortKey;
        }

        /** HDMI and AV switch through their passthrough channel URI; the tuner opens its channel list. */
        public Intent intent() {
            Uri uri = tuner ? TvContract.buildChannelsUriForInput(id) : TvContract.buildChannelUriForPassthroughInput(id);
            return new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
    }

    private InputsRepository() {}

    /** HDMI ports first, then the other physical ports, then Live TV; each group in port order. */
    public static List<Input> load(Context ctx) {
        List<Input> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        List<TvInputInfo> all = tim.getTvInputList();
        // The package that owns the physical ports is the TV maker's input service; its tuner is the real one.
        Set<String> hardwarePkgs = new HashSet<>();
        for (TvInputInfo info : all) if (info.isPassthroughInput()) hardwarePkgs.add(info.getServiceInfo().packageName);

        List<Input> hdmi = new ArrayList<>(), other = new ArrayList<>(), tuners = new ArrayList<>();
        for (TvInputInfo info : all) {
            if (info.isHidden(ctx) || info.getParentId() != null) continue;
            boolean tuner = info.getType() == TvInputInfo.TYPE_TUNER;
            if (!info.isPassthroughInput() && !(tuner && isBuiltInTuner(info, hardwarePkgs))) continue;
            CharSequence custom = info.loadCustomLabel(ctx);
            String plain = String.valueOf(info.loadLabel(ctx));
            String label = custom != null && custom.length() > 0 ? String.valueOf(custom) : plain;
            int state = tim.getInputState(info.getId());
            Boolean connected = tuner ? Boolean.FALSE
                    : state == TvInputManager.INPUT_STATE_CONNECTED ? Boolean.TRUE
                    : state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY ? null : Boolean.FALSE;
            boolean isHdmi = info.getType() == TvInputInfo.TYPE_HDMI;
            Input in = new Input(info.getId(), label, connected, tuner, isHdmi, naturalKey(plain));
            (tuner ? tuners : isHdmi ? hdmi : other).add(in);
        }
        for (List<Input> group : List.of(hdmi, other, tuners)) {
            group.sort((a, b) -> a.sortKey.compareTo(b.sortKey));
            out.addAll(group);
        }
        return out;
    }

    /** One input by id, if the TV has it and it is a physical port or the built-in tuner. */
    public static Input find(Context ctx, String id) {
        for (Input in : load(ctx)) if (in.id.equals(id)) return in;
        return null;
    }

    /** From the same package as the TV's ports, or, on a TV without ports, a system app's tuner. */
    private static boolean isBuiltInTuner(TvInputInfo info, Set<String> hardwarePkgs) {
        ServiceInfo si = info.getServiceInfo();
        if (!hardwarePkgs.isEmpty()) return hardwarePkgs.contains(si.packageName);
        return si.applicationInfo != null && (si.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    /** "HDMI 10" after "HDMI 2": numbers are zero-padded so the order is the port order. */
    private static String naturalKey(String s) {
        StringBuilder b = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+|\\D+").matcher(s.toLowerCase(java.util.Locale.ROOT));
        while (m.find()) {
            String part = m.group();
            b.append(Character.isDigit(part.charAt(0)) ? String.format(java.util.Locale.ROOT, "%12s", part).replace(' ', '0') : part);
        }
        return b.toString();
    }
}
