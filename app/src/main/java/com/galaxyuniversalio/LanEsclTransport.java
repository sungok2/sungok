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
    private final String resourceRoot;
    private volatile HttpURLConnection activeConnection;
    private volatile boolean closed;

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
        String[] addressParts = hostAndPort.split("/", 2);
        String authority = addressParts[0];
        resourceRoot = normalizeResourceRoot(addressParts.length == 2 ? addressParts[1] : "eSCL");
        if (!authority.matches("[0-9.]{7,15}(:[0-9]{1,5})?"))
            throw new IOException("Use a private IPv4 address with optional http(s)://, :port and eSCL path");
        String[] parts = authority.split(":", -1);
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
    String resourceRoot() { return resourceRoot; }
    @Override public void close() {
        closed = true;
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
    }

    /**
     * Performs the bounded, read-only handshake used before an automatically
     * discovered endpoint is accepted as a scanner. This is deliberately
     * separate from scan(), so discovery never creates a scan job.
     */
    public EsclProtocol.Capabilities probeCapabilities() throws IOException {
        EsclScanClient.Reply reply = exchange("GET", "/eSCL/ScannerCapabilities",
                null, null, EsclProtocol.MAX_XML_BYTES, 6000);
        if (reply.status != 200)
            throw new IOException("eSCL capabilities HTTP " + reply.status);
        return EsclProtocol.parseCapabilities(reply.body);
    }

    @Override public EsclScanClient.Reply exchange(String method, String path,
            String contentType, byte[] body, int maxBytes) throws IOException {
        return exchange(method, path, contentType, body, maxBytes, 90000);
    }

    private EsclScanClient.Reply exchange(String method, String path,
            String contentType, byte[] body, int maxBytes, int readTimeoutMillis) throws IOException {
        String servicePath = endpointPath(path);
        if (closed) throw new IOException("Scanner connection was cancelled");
        HttpURLConnection conn = (HttpURLConnection) new URL(origin + servicePath).openConnection();
        activeConnection = conn;
        if (closed) {
            activeConnection = null;
            conn.disconnect();
            throw new IOException("Scanner connection was cancelled");
        }
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(readTimeoutMillis);
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
            return new EsclScanClient.Reply(status, normalizeJobLocation(location), type, bytes);
        } finally {
            activeConnection = null;
            conn.disconnect();
        }
    }

    String endpointPath(String path) throws IOException {
        if (path == null || !path.startsWith("/eSCL/") || path.contains("..") || path.contains("?")
                || path.contains("#") || path.contains("\\"))
            throw new IOException("Unsafe eSCL endpoint");
        return resourceRoot + path.substring("/eSCL".length());
    }

    String normalizeJobLocation(String location) throws IOException {
        if (location == null || location.isEmpty()) return location;
        try {
            java.net.URI u = new java.net.URI(location.trim());
            if (u.getRawQuery() != null || u.getRawFragment() != null || u.getRawUserInfo() != null)
                return location;
            String path = u.getRawPath();
            if (path == null || !path.startsWith(resourceRoot + "/ScanJobs/")) return location;
            String canonicalPath = "/eSCL" + path.substring(resourceRoot.length());
            if (u.isAbsolute()) {
                java.net.URI expected = new java.net.URI(origin);
                if (!u.getScheme().equalsIgnoreCase(expected.getScheme())
                        || !u.getHost().equalsIgnoreCase(expected.getHost())
                        || effectivePort(u) != effectivePort(expected)) return location;
                return new java.net.URI(u.getScheme(), null, u.getHost(), u.getPort(),
                        canonicalPath, null, null).toString();
            }
            if (u.getRawAuthority() != null) return location;
            return canonicalPath;
        } catch (java.net.URISyntaxException e) {
            return location;
        }
    }

    private static int effectivePort(java.net.URI uri) {
        if (uri.getPort() != -1) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    static String normalizeResourceRoot(String value) throws IOException {
        if (value == null || value.trim().isEmpty()) value = "eSCL";
        String path = value.trim();
        while (path.startsWith("/")) path = path.substring(1);
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (path.isEmpty() || path.length() > 128 || path.contains("\\") || path.contains("?")
                || path.contains("#") || path.contains("%") || path.contains(":"))
            throw new IOException("Invalid eSCL resource path");
        String[] segments = path.split("/", -1);
        if (segments.length > 8) throw new IOException("Invalid eSCL resource path");
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                    || !segment.matches("[A-Za-z0-9._~-]{1,64}"))
                throw new IOException("Invalid eSCL resource path");
        }
        return "/" + path;
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
