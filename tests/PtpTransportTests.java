package com.galaxyuniversalio;

import android.hardware.usb.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public final class PtpTransportTests {
    private static int checks;
    private static void ok(boolean v, String msg) { if (!v) throw new AssertionError(msg); checks++; }
    private static void expectFail(Throwing f) throws Exception {
        try { f.run(); throw new AssertionError("Should have failed"); }
        catch (IOException expected) { checks++; }
    }
    private interface Throwing { void run() throws Exception; }

    static final class FakeConnection extends UsbDeviceConnection {
        byte[] replies;
        int readOffset;
        int sentCount;
        FakeConnection(byte[] replies) { this.replies = replies; }
        @Override public int bulkTransfer(UsbEndpoint ep, byte[] data, int offset, int len, int timeout) {
            if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                if (readOffset == replies.length) return -1;
                int n = Math.min(len, Math.min(67, replies.length - readOffset));
                System.arraycopy(replies, readOffset, data, offset, n);
                readOffset += n;
                return n;
            }
            sentCount += len;
            return len;
        }
    }
    static byte[] frame(int type, int code, int tx, byte[] payload) {
        byte[] b = new byte[12 + payload.length];
        PtpProtocol.put32(b, 0, b.length);
        PtpProtocol.put16(b, 4, type); PtpProtocol.put16(b, 6, code);
        PtpProtocol.put32(b, 8, tx);
        System.arraycopy(payload,0,b,12,payload.length);
        return b;
    }
    static byte[] response(int tx, int code) { return frame(3, code, tx, new byte[0]); }
    static byte[] data(int tx, int opcode, byte[] body) { return frame(2, opcode, tx, body); }
    static byte[] script(boolean jpeg, boolean responseSuccess) {
        byte[] handles = new byte[8]; PtpProtocol.put32(handles,0,1); PtpProtocol.put32(handles,4,12);
        byte[] info = new byte[12]; PtpProtocol.put16(info,4,jpeg ? 0x3801 : 0x3000);
        PtpProtocol.put32(info,8,4);
        byte[] object = new byte[]{(byte) 0xFF, (byte)0xD8,(byte)0xFF,(byte)0xD9};
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.writeBytes(response(0,0x2001));
        stream.writeBytes(data(1,0x1007,handles)); stream.writeBytes(response(1,0x2001));
        stream.writeBytes(data(2,0x1008,info)); stream.writeBytes(response(2,responseSuccess ? 0x2001 : 0x2019));
        if (jpeg && responseSuccess) {
            stream.writeBytes(data(3,0x1009,object)); stream.writeBytes(response(3,0x2001));
        }
        stream.writeBytes(response(jpeg && responseSuccess ? 4 : 3,0x2001));
        return stream.toByteArray();
    }
    public static void main(String[] args) throws Exception {
        UsbEndpoint in = new UsbEndpoint(2,128), out = new UsbEndpoint(2,0);
        UsbDevice d = new UsbDevice(new UsbInterface(0,0,6,1,1,in,out));
        UsbDevice no = new UsbDevice(new UsbInterface(0,0,6,1,1,in));
        ok(PtpUsbTransport.select(d) != null, "PTP endpoints selected");
        ok(PtpUsbTransport.select(no) == null, "PTP requires both bulk endpoints");
        ok(PtpUsbTransport.select(new UsbDevice(new UsbInterface(0,0,6,1,2,in,out))) == null, "PTP protocol 2 excluded");
        ok(PtpUsbTransport.select(null) == null, "null PTP device excluded");
        UsbManager mgr = new UsbManager();
        FakeConnection connection = new FakeConnection(script(true,true));
        mgr.connection = connection;
        ok(Arrays.equals(PtpUsbTransport.importFirstJpeg(mgr,d), new byte[]{(byte)255,(byte)216,(byte)255,(byte)217}), "image imported");
        ok(connection.released && connection.closed, "PTP always close");
        ok(connection.sentCount > 50, "PTP session + commands issued");
        mgr.permitted = false;
        expectFail(() -> PtpUsbTransport.importFirstJpeg(mgr,d));
        mgr.permitted = true;
        mgr.connection = new FakeConnection(script(false,true));
        expectFail(() -> PtpUsbTransport.importFirstJpeg(mgr,d));
        ok(mgr.connection.closed && mgr.connection.released, "non-JPEG cleanup");
        mgr.connection = new FakeConnection(script(true,false));
        expectFail(() -> PtpUsbTransport.importFirstJpeg(mgr,d));
        ok(mgr.connection.closed, "PTP negative response cleanup");
        mgr.connection = new FakeConnection(script(true,true));
        mgr.connection.claimSuccess = false;
        expectFail(() -> PtpUsbTransport.importFirstJpeg(mgr,d));
        ok(mgr.connection.closed, "PTP claim fails close");
        UsbDevice alt = new UsbDevice(new UsbInterface(0,1,6,1,1,in,out));
        mgr.connection = new FakeConnection(script(true,true));
        mgr.connection.alternateSuccess = false;
        expectFail(() -> PtpUsbTransport.importFirstJpeg(mgr,alt));
        ok(mgr.connection.closed, "PTP alternate setting failure closes");
        mgr.connection = new FakeConnection(new byte[0]);
        expectFail(() -> PtpUsbTransport.importFirstJpeg(mgr,d));
        ok(mgr.connection.closed, "PTP timeout closes");
        System.out.println("PASS: " + checks + " PTP transport fake-device checks");
    }
}
