package dev.passoca.tvhome.data;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Plain HTTPS GETs for the two things Home fetches. Short timeouts; callers run them off the main thread. */
final class Net {

    private static final String UA = "tvhome/0.1 (Android TV launcher)";

    private Net() {}

    static boolean online(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network n = cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    static String getText(String url, long maxBytes) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copy(in, out, maxBytes);
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            c.disconnect();
        }
    }

    /** Downloads to {@code dest} through a .part file, so a dropped connection never leaves half a picture. */
    static void download(String url, File dest, long maxBytes) throws IOException {
        File part = new File(dest.getPath() + ".part");
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
            copy(in, out, maxBytes);
        } finally {
            c.disconnect();
        }
        if (!part.renameTo(dest)) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw new IOException("rename failed: " + dest.getName());
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "application/json, image/*");
        int code = c.getResponseCode();
        if (code != 200) { c.disconnect(); throw new IOException("HTTP " + code + " for " + url); }
        return c;
    }

    private static void copy(InputStream in, OutputStream out, long max) throws IOException {
        byte[] buf = new byte[16 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > max) throw new IOException("response larger than " + max + " bytes");
            out.write(buf, 0, n);
        }
    }
}
