package com.galaxyuniversalio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Strict HTTP/1.x response framing for a persistent IPP-over-USB byte stream.
 * Reject ambiguous length/transfer-encoding combinations: the next eSCL
 * request must never consume leftover bytes from the previous response.
 */
public final class UsbHttpResponseHeaders {
    public final int status;
    public final String location;
    public final String mediaType;
    public final int contentLength;
    public final boolean chunked;
    public final boolean noBody;
    public final boolean connectionClose;

    private UsbHttpResponseHeaders(int status, String location, String mediaType,
            int contentLength, boolean chunked, boolean noBody, boolean connectionClose) {
        this.status = status;
        this.location = location;
        this.mediaType = mediaType;
        this.contentLength = contentLength;
        this.chunked = chunked;
        this.noBody = noBody;
        this.connectionClose = connectionClose;
    }

    public static UsbHttpResponseHeaders parse(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 16 || bytes.length > 16384)
            throw new IOException("USB HTTP headers size invalid");
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        if (!text.endsWith("\r\n\r\n")) throw new IOException("Incomplete USB HTTP headers");
        String[] lines = text.substring(0, text.length() - 4).split("\r\n", -1);
        if (lines.length == 0 || !lines[0].matches("HTTP/1\\.[01] [0-9]{3}( .*)?"))
            throw new IOException("Invalid USB HTTP status line");
        int status = Integer.parseInt(lines[0].substring(9, 12));
        if (status < 200 || status > 599) throw new IOException("Unsupported USB HTTP status " + status);
        String location = null, mediaType = null;
        int length = -1;
        boolean hasLength = false, chunked = false, hasEncoding = false;
        boolean close = false, seenConnection = false, seenContentEncoding = false;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty() || line.charAt(0) == ' ' || line.charAt(0) == '\t')
                throw new IOException("Malformed or folded USB HTTP header");
            int sep = line.indexOf(':');
            if (sep <= 0) throw new IOException("Malformed USB HTTP header");
            String key = line.substring(0, sep);
            if (!key.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]+"))
                throw new IOException("Invalid USB HTTP header name");
            String rawValue = line.substring(sep + 1);
            for (int c = 0; c < rawValue.length(); c++) {
                char ch = rawValue.charAt(c);
                if ((ch < 32 && ch != '\t') || ch == 127)
                    throw new IOException("Invalid USB HTTP header value");
            }
            String val = rawValue.trim();
            key = key.toLowerCase(Locale.US);
            if (key.equals("location")) {
                if (location != null) throw new IOException("Duplicate Location");
                location = val;
            } else if (key.equals("content-type")) {
                if (mediaType != null) throw new IOException("Duplicate Content-Type");
                mediaType = val;
            } else if (key.equals("content-length")) {
                if (hasLength || !val.matches("[0-9]{1,9}"))
                    throw new IOException("Ambiguous or invalid USB HTTP Content-Length");
                hasLength = true;
                try { length = Integer.parseInt(val); }
                catch (NumberFormatException e) { throw new IOException("Bad Content-Length", e); }
            } else if (key.equals("transfer-encoding")) {
                if (hasEncoding || !val.equalsIgnoreCase("chunked"))
                    throw new IOException("Unsupported or duplicate USB HTTP Transfer-Encoding");
                hasEncoding = true;
                chunked = true;
            } else if (key.equals("content-encoding")) {
                if (seenContentEncoding || !"identity".equalsIgnoreCase(val))
                    throw new IOException("Compressed or duplicate USB HTTP body encoding");
                seenContentEncoding = true;
            } else if (key.equals("connection")) {
                if (seenConnection || !("close".equalsIgnoreCase(val)
                        || "keep-alive".equalsIgnoreCase(val)))
                    throw new IOException("Ambiguous USB HTTP Connection header");
                seenConnection = true;
                close = "close".equalsIgnoreCase(val);
            }
        }
        if (hasLength && hasEncoding) throw new IOException("Conflicting USB HTTP body framing");
        boolean noBody = status == 204 || status == 304;
        if (noBody && (hasEncoding || length > 0))
            throw new IOException("Body forbidden for this USB HTTP status");
        if (!noBody && !hasEncoding && !hasLength)
            throw new IOException("Unframed USB HTTP body; cannot safely resync");
        return new UsbHttpResponseHeaders(status, location, mediaType, length, chunked, noBody, close);
    }

    /** Chunk trailers are allowed only if they cannot mutate HTTP framing. */
    public static void validateTrailer(String line) throws IOException {
        if (line == null || line.length() > 512 || line.isEmpty()
                || line.charAt(0) == ' ' || line.charAt(0) == '\t')
            throw new IOException("Invalid HTTP trailer");
        int colon = line.indexOf(':');
        if (colon <= 0 || !line.substring(0, colon)
                .matches("[A-Za-z0-9!#$%&'*+.^_`|~-]+"))
            throw new IOException("Invalid HTTP trailer name");
        String name = line.substring(0, colon).toLowerCase(Locale.US);
        if (name.equals("content-length") || name.equals("transfer-encoding")
                || name.equals("content-encoding") || name.equals("connection")
                || name.equals("host") || name.equals("trailer"))
            throw new IOException("Forbidden HTTP framing trailer");
        for (int i = colon + 1; i < line.length(); i++) {
            char c = line.charAt(i);
            if ((c < 32 && c != '\t') || c > 126)
                throw new IOException("Invalid HTTP trailer value");
        }
    }
}
