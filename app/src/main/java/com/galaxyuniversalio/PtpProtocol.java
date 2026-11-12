package com.galaxyuniversalio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** ISO 15740 PTP-over-USB container parsing (not a generic scanner protocol). */
public final class PtpProtocol {
    public static final int COMMAND = 1, DATA = 2, RESPONSE = 3;
    public static final int OK = 0x2001;
    public static final int OPEN_SESSION = 0x1002, CLOSE_SESSION = 0x1003;
    public static final int GET_OBJECT_HANDLES = 0x1007, GET_OBJECT_INFO = 0x1008;
    public static final int GET_OBJECT = 0x1009;
    public static final int JPEG_FORMAT = 0x3801;
    public static final int MAX_IMAGE_BYTES = 16 * 1024 * 1024;
    public static final int MAX_HANDLES = 2048;

    private PtpProtocol() { }

    public static int u16(byte[] in, int pos) throws IOException {
        require(in, pos, 2);
        return (in[pos] & 255) | ((in[pos + 1] & 255) << 8);
    }
    public static long u32(byte[] in, int pos) throws IOException {
        require(in, pos, 4);
        return ((long) in[pos] & 255) | (((long) in[pos + 1] & 255) << 8)
                | (((long) in[pos + 2] & 255) << 16) | (((long) in[pos + 3] & 255) << 24);
    }
    private static void require(byte[] in, int start, int count) throws IOException {
        if (in == null || start < 0 || count < 0 || start > in.length - count)
            throw new IOException("PTP 데이터 길이 오류");
    }
    public static void put16(byte[] out, int i, int value) {
        out[i] = (byte) value;
        out[i + 1] = (byte) (value >>> 8);
    }
    public static void put32(byte[] out, int i, long value) {
        for (int n = 0; n < 4; n++) out[i + n] = (byte) (value >>> (n * 8));
    }
    public static byte[] command(int opcode, long tx, long... params) throws IOException {
        if (params.length > 5) throw new IOException("PTP 파라미터 초과");
        byte[] data = new byte[12 + params.length * 4];
        put32(data, 0, data.length);
        put16(data, 4, COMMAND);
        put16(data, 6, opcode);
        put32(data, 8, tx);
        for (int i = 0; i < params.length; i++) put32(data, 12 + 4 * i, params[i]);
        return data;
    }
    public static Header parseHeader(byte[] b) throws IOException {
        require(b, 0, 12);
        long n = u32(b, 0);
        if (n < 12 || n > MAX_IMAGE_BYTES + 12L) throw new IOException("PTP 컨테이너 크기 초과");
        int type = u16(b, 4), code = u16(b, 6);
        if (type != COMMAND && type != DATA && type != RESPONSE)
            throw new IOException("알 수 없는 PTP 컨테이너 유형");
        return new Header((int) n, type, code, u32(b, 8));
    }
    public static final class Header {
        public final int length, type, code;
        public final long transaction;
        Header(int length, int type, int code, long transaction) {
            this.length = length; this.type = type; this.code = code;
            this.transaction = transaction;
        }
    }
    public static List<Long> parseHandles(byte[] data) throws IOException {
        require(data, 0, 4);
        long count = u32(data, 0);
        if (count > MAX_HANDLES || count > (data.length - 4L) / 4L)
            throw new IOException("PTP 객체 목록이 비정상이거나 너무 많습니다.");
        List<Long> handles = new ArrayList<>((int) count);
        for (int i = 0; i < count; i++) handles.add(u32(data, 4 + i * 4));
        return handles;
    }
    public static ObjectInfo parseObjectInfo(byte[] data) throws IOException {
        require(data, 0, 12);
        return new ObjectInfo(u16(data, 4), u32(data, 8));
    }
    public static final class ObjectInfo {
        public final int format;
        public final long bytes;
        ObjectInfo(int format, long bytes) { this.format = format; this.bytes = bytes; }
        public boolean isSupportedJpeg() {
            return format == JPEG_FORMAT && bytes > 0 && bytes <= MAX_IMAGE_BYTES;
        }
    }
    public static boolean isJpeg(byte[] data) {
        // Check the SOI and EOI image markers. Not a full JPEG decoder or content integrity check.
        return data != null && data.length >= 4
                && (data[0] & 255) == 0xFF && (data[1] & 255) == 0xD8
                && (data[data.length - 2] & 255) == 0xFF
                && (data[data.length - 1] & 255) == 0xD9;
    }
}
