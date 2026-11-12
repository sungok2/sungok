package com.galaxyuniversalio;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** RFC 8010 minimal IPP/1.1 codec for PDF-format guarded Print-Job. */
public final class IppProtocol {
    public static final int MAX_DIRECT_PDF_BYTES = 8 * 1024 * 1024;
    private static final String URI = "ipp://localhost/ipp/print";
    private IppProtocol() { }

    public static byte[] getPrinterAttributes(int id) throws IOException {
        return requestPrefix(0x000B, id, false, null);
    }

    public static byte[] printJob(int id, byte[] pdf) throws IOException {
        if (pdf == null || pdf.length < 8 || pdf.length > MAX_DIRECT_PDF_BYTES
                || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F' || pdf[4] != '-')
            throw new IOException("Invalid or oversized PDF");
        return requestPrefix(0x0002, id, true, pdf);
    }

    private static byte[] requestPrefix(int op, int requestId, boolean printing, byte[] pdf)
            throws IOException {
        if (requestId < 1) throw new IOException("Invalid IPP request ID");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(output);
        d.writeByte(1); d.writeByte(1); // IPP/1.1
        d.writeShort(op);
        d.writeInt(requestId);
        d.writeByte(0x01); // operation-attributes-tag
        attribute(d, 0x47, "attributes-charset", "utf-8");
        attribute(d, 0x48, "attributes-natural-language", "en");
        attribute(d, 0x45, "printer-uri", URI);
        if (printing) {
            attribute(d, 0x42, "requesting-user-name", "Galaxy");
            attribute(d, 0x42, "job-name", "Galaxy USB PDF");
            attribute(d, 0x49, "document-format", "application/pdf");
        } else {
            attribute(d, 0x44, "requested-attributes", "document-format-supported");
        }
        d.writeByte(0x03); // end-of-attributes
        if (pdf != null) d.write(pdf);
        d.flush();
        return output.toByteArray();
    }

    private static void attribute(DataOutputStream d, int tag, String name, String value)
            throws IOException {
        byte[] n = name.getBytes(StandardCharsets.US_ASCII);
        byte[] v = value.getBytes(StandardCharsets.UTF_8);
        d.writeByte(tag);
        d.writeShort(n.length); d.write(n);
        d.writeShort(v.length); d.write(v);
    }

    public static final class Response {
        public final int status;
        public final boolean pdfSupported;
        Response(int status, boolean pdfSupported) {
            this.status = status;
            this.pdfSupported = pdfSupported;
        }
    }

    /** Validate IPP request id and scan bounded attributes, including repeated name=0 values. */
    public static Response parseResponse(byte[] b, int requestId) throws IOException {
        if (b == null || b.length < 9 || b.length > 256 * 1024)
            throw new IOException("IPP response size invalid");
        int major = b[0] & 255;
        if (major < 1 || major > 2) throw new IOException("Unsupported IPP response version");
        int status = u16(b, 2);
        int id = ((b[4] & 255) << 24) | ((b[5] & 255) << 16)
                | ((b[6] & 255) << 8) | (b[7] & 255);
        if (id != requestId) throw new IOException("IPP request ID mismatch");
        int pos = 8;
        String prior = "";
        boolean supports = false;
        int attrs = 0;
        while (pos < b.length) {
            int tag = b[pos++] & 255;
            if (tag == 0x03) return new Response(status, supports);
            if (tag >= 0x01 && tag <= 0x0F) {
                prior = "";
                continue;
            }
            if (b.length - pos < 2) throw new IOException("Truncated IPP name length");
            int nlen = u16(b, pos); pos += 2;
            if (nlen > 1024 || nlen > b.length - pos)
                throw new IOException("Invalid IPP attribute name length");
            String name = nlen == 0 ? prior
                    : new String(b, pos, nlen, StandardCharsets.US_ASCII);
            pos += nlen;
            prior = name;
            if (b.length - pos < 2) throw new IOException("Truncated IPP value length");
            int vlen = u16(b, pos); pos += 2;
            if (vlen > b.length - pos) throw new IOException("Invalid IPP value length");
            if ("document-format-supported".equals(name) && tag == 0x49
                    && "application/pdf".equalsIgnoreCase(
                        new String(b, pos, vlen, StandardCharsets.US_ASCII))) supports = true;
            pos += vlen;
            if (++attrs > 4096) throw new IOException("Too many IPP attributes");
        }
        throw new IOException("Missing IPP end-of-attributes delimiter");
    }

    private static int u16(byte[] b, int o) {
        return ((b[o] & 255) << 8) | (b[o + 1] & 255);
    }
}
