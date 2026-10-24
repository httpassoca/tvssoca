package dev.passoca.tvhome.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps a local cache in step with a GitHub folder: one call to the contents API lists the folder, each
 * file's git sha says whether the cached copy is current, and the plan says what to download and what to
 * delete. Rotation only ever reads the cache, so Home keeps its wallpapers offline.
 */
public final class WallpaperSync {

    public static final int MAX_FILES = 100;                 // CleanYourTV's collection limit
    public static final long MAX_BYTES = 10L * 1024 * 1024;  // per picture

    private WallpaperSync() {}

    public static String apiUrl(Config.WallpaperSource s) {
        return "https://api.github.com/repos/" + s.repo + "/contents/" + s.path + "?ref=" + s.branch;
    }

    public static final class Remote {
        public final String name, sha, url;
        public final long size;
        public Remote(String name, String sha, long size, String url) { this.name = name; this.sha = sha; this.size = size; this.url = url; }
    }

    /** Parses the contents API answer: image files only, within the size limit, in filename order, capped. */
    public static List<Remote> parseListing(String json) {
        List<Remote> out = new ArrayList<>();
        JSONArray a;
        try { a = new JSONArray(json); } catch (Exception e) { return out; }
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || !"file".equals(o.optString("type"))) continue;
            String name = o.optString("name", "");
            long size = o.optLong("size", 0);
            String url = o.optString("download_url", "");
            if (!isImage(name) || size <= 0 || size > MAX_BYTES || url.isEmpty() || !url.startsWith("https://")) continue;
            if (name.contains("/") || name.contains("..")) continue;
            out.add(new Remote(name, o.optString("sha", ""), size, url));
        }
        out.sort((x, y) -> x.name.compareTo(y.name));
        return out.size() > MAX_FILES ? new ArrayList<>(out.subList(0, MAX_FILES)) : out;
    }

    public static boolean isImage(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }

    public static final class Plan {
        public final List<Remote> download;
        public final List<String> delete;
        /** The manifest to save once every download succeeded: name → sha. */
        public final Map<String, String> manifest;
        Plan(List<Remote> download, List<String> delete, Map<String, String> manifest) {
            this.download = download; this.delete = delete; this.manifest = manifest;
        }
        public boolean isEmpty() { return download.isEmpty() && delete.isEmpty(); }
    }

    /**
     * @param remote  the folder as listed now
     * @param cached  the saved manifest (name → sha) of the files on disk
     */
    public static Plan plan(List<Remote> remote, Map<String, String> cached) {
        List<Remote> download = new ArrayList<>();
        Map<String, String> manifest = new LinkedHashMap<>();
        for (Remote r : remote) {
            if (!r.sha.equals(cached.get(r.name))) download.add(r);
            manifest.put(r.name, r.sha);
        }
        List<String> delete = new ArrayList<>();
        for (String name : cached.keySet()) if (!manifest.containsKey(name)) delete.add(name);
        Collections.sort(delete);
        return new Plan(download, delete, manifest);
    }

    public static String manifestToJson(Map<String, String> m) {
        return new JSONObject(m).toString();
    }

    public static Map<String, String> manifestFromJson(String json) {
        Map<String, String> m = new HashMap<>();
        if (json == null) return m;
        try {
            JSONObject o = new JSONObject(json);
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                m.put(k, o.optString(k));
            }
        } catch (Exception ignored) { }
        return m;
    }
}
