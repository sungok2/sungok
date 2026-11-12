package com.galaxyuniversalio;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbConstants;

import java.io.IOException;

/** USB Printer Class 1.1 adapter. Does not convert PDF, HWP or images into a printer PDL. */
public final class UsbPrinterTransport {
    private static final int TIMEOUT_MS = 3000;

    private UsbPrinterTransport() { }

    public static final class Selection {
        public final UsbInterface intf;
        public final UsbEndpoint bulkOut;
        public final boolean rawCapable;

        private Selection(UsbInterface intf, UsbEndpoint bulkOut) {
            this.intf = intf;
            this.bulkOut = bulkOut;
            this.rawCapable = UsbPrinterProtocol.permitsRawTransfer(
                    intf.getInterfaceClass(), intf.getInterfaceSubclass(),
                    intf.getInterfaceProtocol(), bulkOut != null);
        }
    }

    public static Selection select(UsbDevice device) {
        if (device == null) return null;
        Selection fallback = null;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            if (!UsbPrinterProtocol.isPrinterInterface(intf.getInterfaceClass(),
                    intf.getInterfaceSubclass(), intf.getInterfaceProtocol())) continue;
            UsbEndpoint out = null;
            for (int e = 0; e < intf.getEndpointCount(); e++) {
                UsbEndpoint ep = intf.getEndpoint(e);
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                        && ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    out = ep;
                    break;
                }
            }
            Selection current = new Selection(intf, out);
            if (current.rawCapable) return current;
            if (fallback == null) fallback = current;
        }
        return fallback;
    }

    public static String probe(UsbManager manager, UsbDevice device) throws IOException {
        Selection sel = select(device);
        if (sel == null) throw new IOException("USB Printer Class 인터페이스가 없습니다.");
        UsbDeviceConnection conn = openClaimed(manager, device, sel.intf);
        try {
            StringBuilder info = new StringBuilder();
            info.append("Printer interface id=").append(sel.intf.getId())
                    .append(" alt=").append(sel.intf.getAlternateSetting())
                    .append(" protocol=").append(sel.intf.getInterfaceProtocol())
                    .append("; rawBulkOut=").append(sel.rawCapable).append('\n');

            byte[] idBuffer = new byte[1024];
            int idIndex = (sel.intf.getId() << 8) | (sel.intf.getAlternateSetting() & 0xFF);
            int idCount = conn.controlTransfer(
                    UsbPrinterProtocol.REQUEST_TYPE_IN_CLASS_INTERFACE,
                    UsbPrinterProtocol.REQUEST_GET_DEVICE_ID, 0, idIndex,
                    idBuffer, idBuffer.length, TIMEOUT_MS);
            if (idCount < 0) {
                info.append("GET_DEVICE_ID: 전송 실패 또는 미지원\n");
            } else {
                try {
                    info.append("GET_DEVICE_ID: ")
                            .append(UsbPrinterProtocol.decodeDeviceId(idBuffer, idCount))
                            .append('\n');
                } catch (IOException e) {
                    info.append("GET_DEVICE_ID: ").append(e.getMessage()).append('\n');
                }
            }
            byte[] portStatus = new byte[1];
            int statusCount = conn.controlTransfer(
                    UsbPrinterProtocol.REQUEST_TYPE_IN_CLASS_INTERFACE,
                    UsbPrinterProtocol.REQUEST_GET_PORT_STATUS, 0, sel.intf.getId(),
                    portStatus, 1, TIMEOUT_MS);
            if (statusCount == 1) {
                info.append("GET_PORT_STATUS: ")
                        .append(UsbPrinterProtocol.describePortStatus(portStatus[0] & 0xFF));
            } else {
                info.append("GET_PORT_STATUS: 전송 실패 또는 응답 길이 오류");
            }
            return info.toString();
        } finally {
            releaseClose(conn, sel.intf);
        }
    }

    /** Sends an already encoded printer-language job (8 MiB max, preflighted separately). */
    public static int sendRaw(UsbManager manager, UsbDevice device, byte[] job)
            throws IOException {
        if (job == null || job.length == 0 || job.length > UsbPrinterProtocol.MAX_RAW_JOB_BYTES) {
            throw new IOException("RAW 인쇄 데이터 크기가 허용 범위를 벗어났습니다.");
        }
        Selection sel = select(device);
        if (sel == null || !sel.rawCapable || sel.bulkOut == null) {
            throw new IOException("지원되는 USB Printer Class 1/2 Bulk OUT이 없습니다.");
        }
        UsbDeviceConnection conn = openClaimed(manager, device, sel.intf);
        try {
            int offset = 0;
            while (offset < job.length) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("전송이 중단되었습니다.");
                int length = Math.min(UsbPrinterProtocol.CHUNK_BYTES, job.length - offset);
                // offset+length signature is available on Android API 26+.
                int sent = conn.bulkTransfer(sel.bulkOut, job, offset, length, TIMEOUT_MS);
                if (sent <= 0 || sent > length) {
                    throw new IOException("USB Bulk OUT 전송 실패 (offset=" + offset + ")");
                }
                offset += sent;
            }
            // USB transfer success does not prove paper was printed.
            return offset;
        } finally {
            releaseClose(conn, sel.intf);
        }
    }

    private static UsbDeviceConnection openClaimed(UsbManager manager, UsbDevice device,
                                                     UsbInterface intf) throws IOException {
        if (manager == null || device == null || !manager.hasPermission(device)) {
            throw new IOException("USB 접근 권한이 없습니다.");
        }
        UsbDeviceConnection conn;
        try { conn = manager.openDevice(device); }
        catch (RuntimeException e) { throw new IOException("USB 장치를 열 수 없습니다.", e); }
        if (conn == null) throw new IOException("USB 연결 생성 실패");
        try {
            if (!conn.claimInterface(intf, false)) {
                throw new IOException("USB 인터페이스를 점유할 수 없습니다 (force=false)");
            }
            if (intf.getAlternateSetting() != 0 && !conn.setInterface(intf)) {
                throw new IOException("USB 대체 인터페이스 설정 실패");
            }
        } catch (Exception e) {
            conn.close();
            throw e;
        }
        return conn;
    }

    private static void releaseClose(UsbDeviceConnection conn, UsbInterface intf) {
        try { conn.releaseInterface(intf); }
        finally { conn.close(); }
    }
}
