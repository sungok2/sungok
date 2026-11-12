package com.galaxyuniversalio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.Locale;

/** Explicit user-selected, IPv4 private-LAN eSCL HTTP or verified HTTPS; no redirects. */
public final class LanEsclTransport implements EsclScanClient.Transport {
    private final String origin;

    public LanEsclTransport(String address) throws IOException {
        if (address == null) throw new IOException("Scanner address missing");
        // Bare IPv4 defaults to HTTP for older AirScan devices. Explicit HTTPS uses
        // Android's regular certificate and hostname verification. No trust-all bypass.
        String scheme = "http";
        String hostAndPort = address.trim();
        if (hostAndPort.startsWith("https://")) {
            scheme = "https";
            hostAndPort = hostAndPort.substring(8);
        } else if (hostAndPort.startsWith("http://")) {
            hostAndPort = hostAndPort.substring(7);
        }
        if (!hostAndPort.matches("[0-9.]{7,15}(:[0-9]{1,5})?"))
            throw new IOException("Use a private IPv4 address with optional http(s):// and :port");
        String[] parts = hostAndPort.split(":", -1);
        String[] octets = parts[0].split("\\.", -1);
        if (octets.length != 4) throw new IOException("Invalid IPv4 address");
        int[] b = new int[4];
        for (int i = 0; i < 4; i++) {
            if (octets[i].isEmpty() || octets[i].length() > 3)
                throw new IOException("Invalid IPv4 component");
            try { b[i] = Integer.parseInt(octets[i]); }
            catch (NumberFormatException e) { throw new IOException("Invalid IPv4 address"); }
            if (b[i] > 255) throw new IOException("Invalid IPv4 address");
        }
        if (!((b[0] == 10) || (b[0] == 172 && b[1] >= 16 && b[1] <= 31)
                || (b[0] == 192 && b[1] == 168) || (b[0] == 169 && b[1] == 254)))
            throw new IOException("Only private LAN or link-local scanner addresses are allowed");
        int port = "https".equals(scheme) ? 443 : 80;
        if (parts.length == 2) {
            try { port = Integer.parseInt(parts[1]); }
            catch (NumberFormatException e) { throw new IOException("Invalid TCP port"); }
            if (port < 1 || port > 65535) throw new IOException("Invalid TCP port");
        }
        String canonical = b[0] + "." + b[1] + "." + b[2] + "." + b[3];
        origin = scheme + "://" + canonical + ":" + port;
    }

    @Override public String origin() { return origin; }
    @Override public void close() { }

    @Override public EsclScanClient.Reply exchange(String method, String path,
            String contentType, byte[] body, int maxBytes) throws IOException {
        if (!path.startsWith("/eSCL/") || path.contains("..") || path.contains("?")
                || path.contains("#") || path.contains("\\"))
            throw new IOException("Unsafe eSCL endpoint");
        HttpURLConnection conn = (HttpURLConnection) new URL(origin + path).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(90000);
        conn.setUseCaches(false);
        conn.setRequestProperty("Accept", "image/jpeg, text/xml, application/xml");
        conn.setRequestMethod(method);
        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType);
            conn.setFixedLengthStreamingMode(body.length);
        }
        try {
            if (body != null) try (OutputStream out = conn.getOutputStream()) { out.write(body); }
            int status = conn.getResponseCode();
            String location = conn.getHeaderField("Location");
            String type = conn.getContentType();
            InputStream input = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            byte[] bytes = new byte[0];
            if (input != null) try (InputStream in = input) { bytes = bounded(in, maxBytes); }
            return new EsclScanClient.Reply(status, location, type, bytes);
        } finally { conn.disconnect(); }
    }

    static byte[] bounded(InputStream input, int limit) throws IOException {
        if (limit < 0 || limit > 64 * 1024 * 1024) throw new IOException("Invalid size limit");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int count;
        while ((count = input.read(buf)) != -1) {
            if (count == 0) continue;
            if (out.size() > limit - count) throw new IOException("Scanner response exceeds size limit");
            out.write(buf, 0, count);
        }
        return out.toByteArray();
    }
}
