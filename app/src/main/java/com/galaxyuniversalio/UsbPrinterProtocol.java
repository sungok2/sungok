package com.galaxyuniversalio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** USB Printer Class 1.1 constants and pure-Java bounded parsing/preflight. */
public final class UsbPrinterProtocol {
    public static final int REQUEST_TYPE_IN_CLASS_INTERFACE = 0xA1;
    public static final int REQUEST_GET_DEVICE_ID = 0;
    public static final int REQUEST_GET_PORT_STATUS = 1;
    public static final int MAX_RAW_JOB_BYTES = 8 * 1024 * 1024;
    public static final int CHUNK_BYTES = 8192;

    private UsbPrinterProtocol() { }

    public static boolean isPrinterInterface(int deviceClass, int subclass, int protocol) {
        return deviceClass == 0x07 && subclass == 0x01 && protocol >= 1 && protocol <= 4;
    }

    /** Protocol 3 (IEEE 1284.4) and 4 (IPP-over-USB) require framing; not RAW. */
    public static boolean permitsRawTransfer(int deviceClass, int subclass, int protocol,
                                             boolean hasBulkOut) {
        return isPrinterInterface(deviceClass, subclass, protocol)
                && (protocol == 1 || protocol == 2) && hasBulkOut;
    }

    /** IEEE 1284 Device ID includes a two-byte big-endian total length. */
    public static String decodeDeviceId(byte[] bytes, int received) throws IOException {
        if (bytes == null || received < 2 || received > bytes.length) {
            throw new IOException("IEEE 1284 장치 ID 응답이 올바르지 않습니다.");
        }
        int declared = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        if (declared < 2 || declared > received) {
            throw new IOException("IEEE 1284 장치 ID 길이 오류/응답 잘림 (declared="
                    + declared + ", received=" + received + ")");
        }
        String input = new String(bytes, 2, declared - 2, StandardCharsets.ISO_8859_1);
        StringBuilder clean = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            clean.append(ch >= 0x20 && ch != 0x7f ? ch : ' ');
        }
        return clean.toString().trim();
    }

    public static String describePortStatus(int status) {
        if ((status & ~0xFF) != 0) throw new IllegalArgumentException("port status byte");
        boolean paperEmpty = (status & 0x20) != 0;
        boolean selected = (status & 0x10) != 0;
        boolean noError = (status & 0x08) != 0;
        return String.format(Locale.ROOT,
                "0x%02X (paperEmpty=%s, selected=%s, noError=%s; 장치 상태 비트이며 실제 인쇄 성공을 보장하지 않음)",
                status, paperEmpty, selected, noError);
    }

    public static boolean isAcceptedRawFilename(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".prn") || lower.endsWith(".pcl") || lower.endsWith(".ps");
    }

    /** Reads completely before sending any bytes, so oversize inputs cannot be partially printed. */
    public static byte[] readRawJob(InputStream in) throws IOException {
        if (in == null) throw new IOException("RAW 파일 스트림이 없습니다.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[CHUNK_BYTES];
        int count;
        int zeroReads = 0;
        while ((count = in.read(buffer)) != -1) {
            if (count == 0) {
                if (++zeroReads > 16) throw new IOException("RAW 입력 스트림이 응답하지 않습니다.");
                continue;
            }
            zeroReads = 0;
            if (out.size() > MAX_RAW_JOB_BYTES - count) {
                throw new IOException("RAW 파일 크기가 8 MiB 한도를 초과했습니다.");
            }
            out.write(buffer, 0, count);
        }
        if (out.size() == 0) throw new IOException("RAW 파일이 비어 있습니다.");
        return out.toByteArray();
    }
}
