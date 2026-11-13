package com.galaxyuniversalio;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Direct eSCL HTTP/1.1 over USB Printer Class=7/SubClass=1/Protocol=4.
 *  Bounded wire parser supports fixed-length and chunked responses.
 *  Not a general-purpose ipp-usb proxy or a guarantee of device interoperability.
 */
public final class UsbEsclHttpTransport implements EsclScanClient.Transport {
    private static final int TIMEOUT_MS = 90000;
    private final UsbDeviceConnection connection;
    private final UsbInterface usbInterface;
    private final UsbEndpoint in;
    private final UsbEndpoint out;
    private final byte[] input = new byte[8192];
    private int begin = 0;
    private int end = 0;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private UsbEsclHttpTransport(UsbDeviceConnection conn, UsbInterface intf,
            UsbEndpoint in, UsbEndpoint out) {
        this.connection = conn;
        this.usbInterface = intf;
        this.in = in;
        this.out = out;
    }

    public static boolean supports(UsbDevice device) {
        if (device == null) return false;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (iface.getInterfaceClass() != 7 || iface.getInterfaceSubclass() != 1
                    || iface.getInterfaceProtocol() != 4) continue;
            if (endpoints(iface) != null) return true;
        }
        return false;
    }

    private static UsbEndpoint[] endpoints(UsbInterface iface) {
        UsbEndpoint in = null, out = null;
        for (int j = 0; j < iface.getEndpointCount(); j++) {
            UsbEndpoint e = iface.getEndpoint(j);
            if (e.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
            if (e.getDirection() == UsbConstants.USB_DIR_IN) in = e;
            if (e.getDirection() == UsbConstants.USB_DIR_OUT) out = e;
        }
        return (in != null && out != null) ? new UsbEndpoint[] {in, out} : null;
    }

    public static UsbEsclHttpTransport open(UsbManager manager, UsbDevice device) throws IOException {
        if (manager == null || device == null || !manager.hasPermission(device))
            throw new IOException("USB scanner permission missing");
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (iface.getInterfaceClass() != 7 || iface.getInterfaceSubclass() != 1
                    || iface.getInterfaceProtocol() != 4) continue;
            UsbEndpoint[] eps = endpoints(iface);
            if (eps == null) continue;
            UsbDeviceConnection conn = manager.openDevice(device);
            if (conn == null) throw new IOException("Cannot open USB scanner");
            try {
                if (!conn.claimInterface(iface, false))
                    throw new IOException("Cannot claim IPP-over-USB interface");
                if (iface.getAlternateSetting() != 0 && !conn.setInterface(iface))
                    throw new IOException("Cannot select IPP-over-USB alternate interface");
                return new UsbEsclHttpTransport(conn, iface, eps[0], eps[1]);
            } catch (IOException | RuntimeException e) {
                try { conn.releaseInterface(iface); } catch (Exception ignored) { }
                conn.close();
                throw e;
            }
        }
        throw new IOException("No Class 7/SubClass 1/Protocol 4 Bulk IN/OUT interface");
    }

    @Override public String origin() { return "http://localhost"; }

    @Override public synchronized EsclScanClient.Reply exchange(String method, String path,
            String mediaType, byte[] body, int limit) throws IOException {
        if (closed.get()) throw new IOException("USB scanner is closed");
        UsbHttpRequestPolicy.validate(method, path, mediaType, body, limit);
        StringBuilder req = new StringBuilder();
        req.append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                .append("Host: localhost\r\n")
                .append("Accept: image/jpeg, image/png, text/xml, application/xml, application/ipp\r\n")
                .append("Connection: keep-alive\r\n");
        if (body != null) {
            if (body.length > 10 * 1024 * 1024 || mediaType == null)
                throw new IOException("Oversized or untyped request");
            req.append("Content-Type: ").append(mediaType).append("\r\n")
                    .append("Content-Length: ").append(body.length).append("\r\n");
        } else if ("POST".equals(method)) req.append("Content-Length: 0\r\n");
        req.append("\r\n");
        byte[] header = req.toString().getBytes(StandardCharsets.US_ASCII);
        // Once a write begins, an IOException can leave the reply stream at an
        // unknown offset. Never reuse that USB connection for another command.
        try {
            send(header);
            if (body != null) send(body);
            UsbHttpResponseHeaders head = UsbHttpResponseHeaders.parse(readHeaders());
            byte[] content;
            if (head.noBody) content = new byte[0];
            else if (head.chunked) content = readChunked(limit);
            else content = readExact(head.contentLength, limit);
            EsclScanClient.Reply result = new EsclScanClient.Reply(
                    head.status, head.location, head.mediaType, content);
            if (head.connectionClose) close();
            return result;
        } catch (IOException | RuntimeException failure) {
            try { close(); } catch (RuntimeException ignored) { }
            throw failure;
        }
    }

    private void send(byte[] bytes) throws IOException {
        int off = 0;
        while (off < bytes.length) {
            int n = connection.bulkTransfer(out, bytes, off, Math.min(8192, bytes.length - off),
                    TIMEOUT_MS);
            if (n <= 0) throw new IOException("USB Bulk OUT failed");
            off += n;
        }
    }

    private int readByte() throws IOException {
        if (begin == end) {
            int n = connection.bulkTransfer(in, input, 0, input.length, TIMEOUT_MS);
            if (n <= 0) throw new IOException("USB Bulk IN failed or timed out");
            begin = 0;
            end = n;
        }
        return input[begin++] & 255;
    }

    private byte[] readHeaders() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int progress = 0;
        while (out.size() < 16384) {
            int b = readByte();
            out.write(b);
            progress = (progress == 0 && b == '\r') || (progress == 2 && b == '\r')
                    ? progress + 1 : (progress == 1 || progress == 3) && b == '\n'
                    ? progress + 1 : b == '\r' ? 1 : 0;
            if (progress == 4) return out.toByteArray();
        }
        throw new IOException("Oversized USB HTTP response headers");
    }

    private byte[] readExact(int size, int limit) throws IOException {
        if (size > limit) throw new IOException("USB scan exceeds response size limit");
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(size, 8192));
        for (int i = 0; i < size; i++) out.write(readByte());
        return out.toByteArray();
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (line.size() < 512) {
            int c = readByte();
            if (c == '\r') {
                if (readByte() != '\n') throw new IOException("Malformed USB chunk line");
                return line.toString("US-ASCII");
            }
            line.write(c);
        }
        throw new IOException("Oversized USB chunk line");
    }

    private byte[] readChunked(int limit) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        int chunks = 0;
        while (++chunks < 10000) {
            String line = readLine();
            int semi = line.indexOf(';');
            String raw = (semi < 0 ? line : line.substring(0, semi)).trim();
            long size;
            try { size = Long.parseLong(raw, 16); }
            catch (NumberFormatException e) { throw new IOException("Bad USB HTTP chunk size", e); }
            if (size < 0 || size > limit - result.size())
                throw new IOException("USB scan chunks exceed response size limit");
            if (size == 0) {
                for (int t = 0; t < 16; t++) {
                    String trailer = readLine();
                    if (trailer.isEmpty()) return result.toByteArray();
                    UsbHttpResponseHeaders.validateTrailer(trailer);
                }
                throw new IOException("Too many USB HTTP trailers");
            }
            for (int j = 0; j < size; j++) result.write(readByte());
            if (readByte() != '\r' || readByte() != '\n')
                throw new IOException("Invalid USB HTTP chunk delimiter");
        }
        throw new IOException("Too many USB HTTP chunks");
    }

    @Override public void close() {
        // Do not acquire exchange()'s monitor: UI cancellation must be able
        // to close the USB connection while a bulkTransfer is blocked.
        if (!closed.compareAndSet(false, true)) return;
        try { connection.releaseInterface(usbInterface); } catch (RuntimeException ignored) { }
        connection.close();
    }
}
