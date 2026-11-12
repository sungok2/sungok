package com.galaxyuniversalio;

import java.io.IOException;

/** Fail-closed validation performed before ANY bytes are sent over the USB interface. */
public final class UsbHttpRequestPolicy {
    private UsbHttpRequestPolicy() { }

    public static void validate(String method, String path, String mediaType,
            byte[] body, int responseLimit) throws IOException {
        if (responseLimit < 0 || responseLimit > 64 * 1024 * 1024)
            throw new IOException("Invalid USB HTTP response size limit");
        if (method == null || path == null || path.length() > 256
                || !path.startsWith("/eSCL/") && !"/ipp/print".equals(path))
            throw new IOException("Unsupported USB HTTP endpoint");
        // Whitelist request-target characters. CR, LF, whitespace, question marks,
        // fragments, percent escapes and backslashes cannot enter the wire request.
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '/' || c == '_' || c == '-'
                    || c == '.' || c == ':'))
                throw new IOException("Unsafe USB HTTP request target");
        }
        if (path.contains("..") || path.contains("//"))
            throw new IOException("Unsafe USB HTTP path traversal");
        if (body != null && body.length > 10 * 1024 * 1024)
            throw new IOException("USB HTTP request body too large");
        if ("POST".equals(method)) {
            if (body == null || body.length == 0)
                throw new IOException("Empty USB HTTP POST body");
            if ("/ipp/print".equals(path)) {
                if (!"application/ipp".equals(mediaType))
                    throw new IOException("Invalid IPP content type");
            } else if ("/eSCL/ScanJobs".equals(path)) {
                if (!"text/xml".equals(mediaType))
                    throw new IOException("Invalid scan settings content type");
            } else throw new IOException("Unsupported USB POST endpoint");
        } else if ("GET".equals(method) || "DELETE".equals(method)) {
            if (body != null || mediaType != null || "/ipp/print".equals(path))
                throw new IOException("Unexpected USB HTTP request body or endpoint");
        } else throw new IOException("Unsupported USB HTTP method");
    }
}
