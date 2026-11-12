package com.galaxyuniversalio;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.io.StringReader;

/** Standalone JDK 17+ tests; Android SDK, Gradle, JUnit are not required. */
public final class HostTests {
    private static int checks;
    private HostTests() { }

    public static void main(String[] args) throws Exception {
        check(!UsbDeviceClassifier.classify(0, new int[] {0x08}).stillImage, "mass storage not image");
        check(!UsbDeviceClassifier.classify(0, new int[] {0x08}).printer, "mass storage not printer");
        check(UsbDeviceClassifier.classify(0x06, new int[0]).stillImage, "device-level still image");
        check(UsbDeviceClassifier.classify(0, new int[] {0x06}).stillImage, "interface still image");
        check(UsbDeviceClassifier.classify(0, new int[] {0x07}).printer, "interface printer");
        check(UsbDeviceClassifier.classify(0x07, null).printer, "device-level printer");
        check(UsbDeviceClassifier.classify(0xFF, new int[] {0x08}).vendorSpecific, "vendor device class");
        check(UsbDeviceClassifier.classify(0, new int[] {0xFF}).vendorSpecific, "vendor interface");
        check(UsbDeviceClassifier.classify(0, new int[] {0x06, 0x07}).description()
                .contains("Still Image + Printer"), "composite image/printer");
        check(UsbDeviceClassifier.classify(0, new int[] {0x06}).description()
                .contains("미검증"), "classification not support claim");
        check(UsbDeviceClassifier.endpointTypeName(0).equals("CONTROL"), "ep control");
        check(UsbDeviceClassifier.endpointTypeName(1).equals("ISOCHRONOUS"), "ep isochronous");
        check(UsbDeviceClassifier.endpointTypeName(2).equals("BULK"), "ep bulk");
        check(UsbDeviceClassifier.endpointTypeName(3).equals("INTERRUPT"), "ep interrupt");
        check(UsbDeviceClassifier.endpointTypeName(9).equals("UNKNOWN(9)"), "ep unknown");

        String header = "VID\tPID\tVENDOR\tMODEL\tCATEGORY\tPROTOCOL\tEVIDENCE\tNOTES\n";
        String profile = "ABCD\t0123\t가상 제조사\tTEST\tscanner\ttest-only\tdiscovered\t테스트 전용\n";
        UsbProfileCatalog catalog = UsbProfileCatalog.load(new StringReader("# test\n" + header + profile));
        check(catalog.size() == 1, "catalog one entry");
        check(catalog.find(0xABCD, 0x0123) != null, "exact profile found");
        check(catalog.find(0xABCD, 0x0124) == null, "no PID guess");
        check(catalog.find(0xABCD, 0x0123).vendor.equals("가상 제조사"), "utf8 field");
        check(catalog.find(0xABCD, 0x0123).evidenceLevel.equals("discovered"), "evidence stored");
        check(UsbProfileCatalog.load(new StringReader(header)).size() == 0, "empty catalog");
        check(UsbProfileCatalog.empty().find(1, 2) == null, "missing key");
        expectIOException("duplicate", header + profile + profile);
        expectIOException("invalid hex", header + profile.replace("ABCD", "ZZZZ"));
        expectIOException("invalid evidence", header + profile.replace("discovered", "unverified"));
        expectIOException("missing column", header + "ABCD\t0123\n");
        check(UsbPrinterProtocol.isPrinterInterface(7, 1, 1), "printer protocol 1");
        check(UsbPrinterProtocol.isPrinterInterface(7, 1, 2), "printer protocol 2");
        check(UsbPrinterProtocol.isPrinterInterface(7, 1, 3), "printer protocol 3 detected");
        check(!UsbPrinterProtocol.isPrinterInterface(7, 0, 1), "incorrect printer subclass blocked");
        check(!UsbPrinterProtocol.isPrinterInterface(6, 1, 1), "still image not a printer");
        check(UsbPrinterProtocol.isPrinterInterface(7, 1, 4), "IPP-over-USB printer identified");
        check(!UsbPrinterProtocol.permitsRawTransfer(7, 1, 4, true), "IPP-over-USB RAW blocked");
        check(UsbPrinterProtocol.permitsRawTransfer(7, 1, 1, true), "uni raw supported");
        check(UsbPrinterProtocol.permitsRawTransfer(7, 1, 2, true), "bi raw supported");
        check(!UsbPrinterProtocol.permitsRawTransfer(7, 1, 3, true), "1284.4 framing required");
        check(!UsbPrinterProtocol.permitsRawTransfer(7, 1, 1, false), "missing bulk OUT");
        byte[] encodedId = new byte[]{0, 16, 'M', 'F', 'G', ':', 'T', 'E', 'S', 'T', ';', 'C', 'M', 'D', ':', ';'};
        check(UsbPrinterProtocol.decodeDeviceId(encodedId, encodedId.length)
                .equals("MFG:TEST;CMD:;"), "IEEE1284 big-endian length decode");
        expectIdError(new byte[]{0, 12, 'M', 'F'}, 4, "truncated device ID");
        expectIdError(new byte[]{0, 0, 'X'}, 3, "short declared length");
        expectIdError(new byte[]{0}, 1, "short response length");
        check(UsbPrinterProtocol.describePortStatus(0x18).contains("paperEmpty=false"), "printer status paper bit");
        check(UsbPrinterProtocol.describePortStatus(0x20).contains("paperEmpty=true"), "paper empty bit");
        check(UsbPrinterProtocol.describePortStatus(0x18).contains("noError=true"), "printer no-error bit");
        check(UsbPrinterProtocol.isAcceptedRawFilename("A.PRN"), "uppercase PRN");
        check(UsbPrinterProtocol.isAcceptedRawFilename("b.pcl"), "PCL allowed");
        check(UsbPrinterProtocol.isAcceptedRawFilename("b.ps"), "PostScript allowed");
        check(!UsbPrinterProtocol.isAcceptedRawFilename("a.pdf"), "PDF blocked");
        check(!UsbPrinterProtocol.isAcceptedRawFilename("a.hwp"), "HWP blocked");
        check(!UsbPrinterProtocol.isAcceptedRawFilename(null), "unknown filename blocked");
        check(Arrays.equals(new byte[]{1,2,3}, UsbPrinterProtocol.readRawJob(
                new ByteArrayInputStream(new byte[]{1,2,3}))), "raw bytes intact");
        expectReadError(new byte[]{}, "empty raw file");
        expectReadError(new byte[UsbPrinterProtocol.MAX_RAW_JOB_BYTES + 1], "oversize raw file");
        byte[] limit = new byte[UsbPrinterProtocol.MAX_RAW_JOB_BYTES];
        check(UsbPrinterProtocol.readRawJob(new ByteArrayInputStream(limit)).length
                == UsbPrinterProtocol.MAX_RAW_JOB_BYTES, "exact RAW size limit");
        check(UsbPrinterProtocol.CHUNK_BYTES <= 8192, "bounded USB chunk size");
        System.out.println("PASS: " + checks + " host checks");
    }

    private static void expectIdError(byte[] data, int received, String reason) throws Exception {
        try { UsbPrinterProtocol.decodeDeviceId(data, received); }
        catch (IOException expected) { checks++; return; }
        throw new AssertionError("Expected invalid ID: " + reason);
    }

    private static void expectReadError(byte[] data, String reason) throws Exception {
        try { UsbPrinterProtocol.readRawJob(new ByteArrayInputStream(data)); }
        catch (IOException expected) { checks++; return; }
        throw new AssertionError("Expected invalid RAW input: " + reason);
    }

    private static void check(boolean good, String description) {
        if (!good) throw new AssertionError(description);
        checks++;
    }

    private static void expectIOException(String description, String source) throws Exception {
        try {
            UsbProfileCatalog.load(new StringReader(source));
            throw new AssertionError("Expected IOException: " + description);
        } catch (IOException expected) {
            checks++;
        }
    }
}
