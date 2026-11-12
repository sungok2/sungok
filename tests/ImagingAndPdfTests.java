package com.galaxyuniversalio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public final class ImagingAndPdfTests {
    private static int checks;
    private static void ok(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        checks++;
    }
    private static void mustFail(Throwing action) throws Exception {
        try { action.run(); throw new AssertionError("Expected rejection"); }
        catch (IOException expected) { checks++; }
    }
    private interface Throwing { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        byte[] cmd = PtpProtocol.command(0x1002, 0, 1);
        ok(cmd.length == 16, "command length");
        ok(PtpProtocol.u32(cmd, 0) == 16, "command length LE");
        ok(PtpProtocol.u16(cmd, 4) == PtpProtocol.COMMAND, "command type LE");
        ok(PtpProtocol.u16(cmd, 6) == 0x1002, "command opcode");
        ok(PtpProtocol.u32(cmd, 12) == 1, "command session ID");
        ok(PtpProtocol.parseHeader(cmd).transaction == 0, "tx zero");
        mustFail(() -> PtpProtocol.command(0x1002, 1, 1,2,3,4,5,6));
        mustFail(() -> PtpProtocol.parseHeader(new byte[11]));
        byte[] badLength = cmd.clone(); PtpProtocol.put32(badLength, 0, 11);
        mustFail(() -> PtpProtocol.parseHeader(badLength));
        byte[] badType = cmd.clone(); PtpProtocol.put16(badType, 4, 99);
        mustFail(() -> PtpProtocol.parseHeader(badType));
        mustFail(() -> PtpProtocol.u32(cmd, -1));
        byte[] handles = new byte[12];
        PtpProtocol.put32(handles, 0, 2); PtpProtocol.put32(handles, 4, 5);
        PtpProtocol.put32(handles, 8, 0xFFFFFFFFL);
        List<Long> parsed = PtpProtocol.parseHandles(handles);
        ok(parsed.size() == 2 && parsed.get(1) == 0xFFFFFFFFL, "handles unsigned");
        mustFail(() -> PtpProtocol.parseHandles(new byte[3]));
        byte[] tooMany = handles.clone(); PtpProtocol.put32(tooMany, 0, 2049);
        mustFail(() -> PtpProtocol.parseHandles(tooMany));
        byte[] truncated = handles.clone(); PtpProtocol.put32(truncated, 0, 3);
        mustFail(() -> PtpProtocol.parseHandles(truncated));
        byte[] info = new byte[12]; PtpProtocol.put16(info, 4, 0x3801);
        PtpProtocol.put32(info, 8, 2345);
        ok(PtpProtocol.parseObjectInfo(info).isSupportedJpeg(), "JPEG object info");
        PtpProtocol.put32(info, 8, PtpProtocol.MAX_IMAGE_BYTES+1);
        ok(!PtpProtocol.parseObjectInfo(info).isSupportedJpeg(), "oversize JPEG rejected");
        PtpProtocol.put16(info, 4, 0x3000);
        ok(!PtpProtocol.parseObjectInfo(info).isSupportedJpeg(), "non JPEG rejected");
        mustFail(() -> PtpProtocol.parseObjectInfo(new byte[10]));
        ok(PtpProtocol.isJpeg(new byte[]{(byte)255,(byte)216,(byte)255,(byte)217}), "JPEG markers");
        ok(!PtpProtocol.isJpeg(new byte[]{0,1,2,3}), "non JPEG reject");
        ok(!PtpProtocol.isJpeg(null), "null JPEG reject");
        byte[] pdf = "%PDF-1.7\nexample-data".getBytes("US-ASCII");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        long total = PdfSpoolPreflight.copy(new ByteArrayInputStream(pdf), output, () -> false);
        ok(total == pdf.length && Arrays.equals(output.toByteArray(), pdf), "PDF copy exact");
        mustFail(() -> PdfSpoolPreflight.copy(new ByteArrayInputStream(new byte[0]),new ByteArrayOutputStream(), () -> false));
        mustFail(() -> PdfSpoolPreflight.copy(new ByteArrayInputStream("file".getBytes("US-ASCII")),new ByteArrayOutputStream(), () -> false));
        mustFail(() -> PdfSpoolPreflight.copy(new ByteArrayInputStream(pdf),new ByteArrayOutputStream(), () -> true));
        mustFail(() -> PdfSpoolPreflight.copy(null,new ByteArrayOutputStream(), null));
        mustFail(() -> PdfSpoolPreflight.copy(new ByteArrayInputStream(pdf),null, null));
        ok(PdfSpoolPreflight.MAX_PDF_BYTES == 33554432, "PDF size limit");
        System.out.println("PASS: " + checks + " PTP codec + PDF spool checks");
    }
}
