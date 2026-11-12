package com.galaxyuniversalio;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** Imports a bounded JPEG from a standard PTP still-image class device. */
public final class PtpUsbTransport {
    private static final int TIMEOUT_MS = 4000;
    private static final int READ_BUFFER = 8192;
    private static final int OBJECT_SCAN_LIMIT = 16;

    private PtpUsbTransport() { }

    public static final class Selection {
        public final UsbInterface intf;
        public final UsbEndpoint in, out;
        Selection(UsbInterface intf, UsbEndpoint in, UsbEndpoint out) {
            this.intf = intf; this.in = in; this.out = out;
        }
    }

    public static Selection select(UsbDevice device) {
        if (device == null) return null;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface f = device.getInterface(i);
            if (f.getInterfaceClass() != 6 || f.getInterfaceSubclass() != 1
                    || f.getInterfaceProtocol() != 1) continue;
            UsbEndpoint in = null, out = null;
            for (int e = 0; e < f.getEndpointCount(); e++) {
                UsbEndpoint p = f.getEndpoint(e);
                if (p.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                if (p.getDirection() == UsbConstants.USB_DIR_IN) in = p;
                else if (p.getDirection() == UsbConstants.USB_DIR_OUT) out = p;
            }
            if (in != null && out != null) return new Selection(f, in, out);
        }
        return null;
    }

    public static byte[] importFirstJpeg(UsbManager manager, UsbDevice device) throws IOException {
        Selection sel = select(device);
        if (manager == null || device == null || sel == null || !manager.hasPermission(device))
            throw new IOException("USB PTP 이미지 장치 또는 접근 권한이 없습니다.");
        UsbDeviceConnection conn = manager.openDevice(device);
        if (conn == null) throw new IOException("USB PTP 연결을 열 수 없습니다.");
        boolean claimed = false;
        try {
            claimed = conn.claimInterface(sel.intf, false);
            if (!claimed) throw new IOException("PTP 인터페이스 점유 실패");
            if (sel.intf.getAlternateSetting() != 0 && !conn.setInterface(sel.intf))
                throw new IOException("PTP 대체 인터페이스 설정 실패");
            Session session = new Session(conn, sel);
            session.commandNoData(PtpProtocol.OPEN_SESSION, 1);
            try {
                byte[] handleData = session.commandWithData(PtpProtocol.GET_OBJECT_HANDLES,
                        0xFFFFFFFFL, 0, 0);
                List<Long> handles = PtpProtocol.parseHandles(handleData);
                int checked = 0;
                for (Long handle : handles) {
                    if (++checked > OBJECT_SCAN_LIMIT) break;
                    PtpProtocol.ObjectInfo info = PtpProtocol.parseObjectInfo(
                            session.commandWithData(PtpProtocol.GET_OBJECT_INFO, handle));
                    if (!info.isSupportedJpeg()) continue;
                    byte[] image = session.commandWithData(PtpProtocol.GET_OBJECT, handle);
                    if (image.length == info.bytes && PtpProtocol.isJpeg(image)) return image;
                }
                throw new IOException("첫 16개 PTP 객체 중 16MiB 이하 JPEG를 찾지 못했습니다.");
            } finally {
                try { session.commandNoData(PtpProtocol.CLOSE_SESSION); }
                catch (IOException ignored) { /* Best-effort session cleanup. */ }
            }
        } finally {
            if (claimed) { try { conn.releaseInterface(sel.intf); } catch (RuntimeException ignored) { } }
            conn.close();
        }
    }

    private static final class Session {
        private final UsbDeviceConnection conn;
        private final Selection sel;
        private final byte[] buffered = new byte[READ_BUFFER];
        private int pos, count;
        private long tx;
        Session(UsbDeviceConnection conn, Selection sel) { this.conn = conn; this.sel = sel; }

        void commandNoData(int opcode, long... args) throws IOException {
            long id = tx++;
            send(PtpProtocol.command(opcode, id, args));
            Container response = receive();
            checkResponse(response, id);
        }
        byte[] commandWithData(int opcode, long... args) throws IOException {
            long id = tx++;
            send(PtpProtocol.command(opcode, id, args));
            Container data = receive();
            if (data.header.type == PtpProtocol.RESPONSE) {
                checkResponse(data, id);
                throw new IOException("PTP 장치가 데이터를 반환하지 않았습니다.");
            }
            if (data.header.type != PtpProtocol.DATA || data.header.transaction != id
                    || data.header.code != opcode) throw new IOException("PTP 데이터 트랜잭션 불일치");
            Container response = receive();
            checkResponse(response, id);
            return data.body;
        }
        private static void checkResponse(Container c, long id) throws IOException {
            if (c.header.type != PtpProtocol.RESPONSE || c.header.transaction != id)
                throw new IOException("PTP 응답 트랜잭션 불일치");
            if (c.header.code != PtpProtocol.OK)
                throw new IOException("PTP 요청 오류 0x" + Integer.toHexString(c.header.code));
        }
        private void send(byte[] bytes) throws IOException {
            int offset = 0;
            while (offset < bytes.length) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("PTP 전송 중단");
                int n = conn.bulkTransfer(sel.out, bytes, offset,
                        Math.min(READ_BUFFER, bytes.length - offset), TIMEOUT_MS);
                if (n <= 0) throw new IOException("PTP OUT 전송 실패");
                offset += n;
            }
        }
        private void readExact(byte[] out, int offset, int length) throws IOException {
            int remaining = length;
            while (remaining > 0) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("PTP 수신 중단");
                if (pos == count) {
                    int n = conn.bulkTransfer(sel.in, buffered, 0, buffered.length, TIMEOUT_MS);
                    if (n <= 0) throw new IOException("PTP IN 응답 실패 또는 타임아웃");
                    pos = 0;
                    count = n;
                }
                int copy = Math.min(count - pos, remaining);
                System.arraycopy(buffered, pos, out, offset, copy);
                offset += copy; remaining -= copy; pos += copy;
            }
        }
        private Container receive() throws IOException {
            byte[] header = new byte[12];
            readExact(header, 0, 12);
            PtpProtocol.Header h = PtpProtocol.parseHeader(header);
            int remaining = h.length - 12;
            if (h.type == PtpProtocol.RESPONSE && remaining > 20)
                throw new IOException("PTP 응답 파라미터 크기 오류");
            byte[] bytes = new byte[remaining];
            readExact(bytes, 0, remaining);
            return new Container(h, bytes);
        }
    }
    private static final class Container {
        final PtpProtocol.Header header;
        final byte[] body;
        Container(PtpProtocol.Header h, byte[] body) { this.header = h; this.body = body; }
    }
}
