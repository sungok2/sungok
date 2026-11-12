package com.galaxyuniversalio;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/** Deterministic host tests for framing ambiguity and private PDF staging. */
public final class ReliabilityTests {
    private static int checks;
    private interface Attempt { void run() throws Exception; }
    private static void ok(boolean result, String label) {
        checks++;
        if (!result) throw new AssertionError(label);
    }
    private static void reject(Attempt action, String label) throws Exception {
        boolean rejected = false;
        try { action.run(); } catch (IOException e) { rejected = true; }
        ok(rejected, label);
    }
    private static byte[] headers(String s) { return s.getBytes(StandardCharsets.ISO_8859_1); }
    private static UsbHttpResponseHeaders read(String s) throws IOException {
        return UsbHttpResponseHeaders.parse(headers(s));
    }
    private static void protocol() throws Exception {
        UsbHttpResponseHeaders regular = read("HTTP/1.1 200 OK\r\nContent-Length: 3\r\nContent-Type: image/png\r\n\r\n");
        ok(regular.status == 200 && regular.contentLength == 3 && !regular.chunked, "fixed length");
        ok("image/png".equals(regular.mediaType), "media type");
        UsbHttpResponseHeaders chunked = read("HTTP/1.1 201 Created\r\nTransfer-Encoding: chunked\r\nLocation: /eSCL/ScanJobs/a\r\n\r\n");
        ok(chunked.chunked && chunked.contentLength == -1 && chunked.status == 201, "chunked");
        ok("/eSCL/ScanJobs/a".equals(chunked.location), "location");
        UsbHttpResponseHeaders empty = read("HTTP/1.1 204 No Content\r\n\r\n");
        ok(empty.noBody && empty.contentLength == -1, "204 no body");
        ok(read("HTTP/1.0 304 Not Modified\r\nContent-Length: 0\r\n\r\n").noBody,"304 no body");
        ok(read("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").connectionClose,
            "Connection: close respected");
        reject(() -> read("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nContent-Encoding: gzip\r\n\r\n"),"gzip body rejected");
        reject(() -> read("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nContent-Encoding: identity\r\nContent-Encoding: identity\r\n\r\n"),"duplicate encoding rejected");
        reject(() -> read("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: upgrade\r\n\r\n"),"unsupported connection mode rejected");
        UsbHttpResponseHeaders.validateTrailer("X-Scan-ID: abc123");
        ok(true, "ordinary trailer accepted");
        reject(() -> UsbHttpResponseHeaders.validateTrailer("Content-Length: 100"), "framing trailer rejected");
        reject(() -> UsbHttpResponseHeaders.validateTrailer(" Transfer-Encoding: chunked"), "folded trailer rejected");
        reject(() -> UsbHttpResponseHeaders.validateTrailer("X-A: bad\nX-B: evil"), "injected trailer rejected");
        reject(() -> read("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Length: 5\r\n\r\n"),"TE+CL smuggling");
        reject(() -> read("HTTP/1.1 200 OK\r\nContent-Length: 3\r\nContent-Length: 4\r\n\r\n"),"duplicate CL");
        reject(() -> read("HTTP/1.1 200 OK\r\nTransfer-Encoding: gzip, chunked\r\n\r\n"),"invalid TE");
        reject(() -> read("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nTransfer-Encoding: chunked\r\n\r\n"),"duplicate TE");
        reject(() -> read("HTTP/1.1 200 OK\r\nContent-Length: +2\r\n\r\n"),"signed length");
        reject(() -> read("HTTP/1.1 200 OK\r\nContent-Length: 2147483648\r\n\r\n"),"overflow length");
        reject(() -> read("HTTP/1.1 200 OK\r\n\r\n"),"unframed 200");
        reject(() -> read("HTTP/1.1 204 No Content\r\nContent-Length: 1\r\n\r\n"),"illegal 204 body");
        reject(() -> read("HTTP/1.1 204 No Content\r\nTransfer-Encoding: chunked\r\n\r\n"),"illegal chunked 204");
        reject(() -> read("HTTP/1.1 200 OK\r\n Content-Length: 4\r\n\r\n"),"folded field");
        reject(() -> read("HTTP/1.1 200 OK\r\nContent-Length : 4\r\n\r\n"),"bad field name");
        reject(() -> read("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nLocation: /eSCL/a\r\nLocation: /eSCL/b\r\n\r\n"),"duplicate Location");
        reject(() -> read("HTTP/1.1 100 Continue\r\n\r\n"),"interim response");
        reject(() -> read("HTTP/2 200 OK\r\nContent-Length: 0\r\n\r\n"),"wrong HTTP version");
        reject(() -> read("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n"),"incomplete headers");
        reject(() -> read("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nX-Poison: bad\u0001\r\n\r\n"),"invalid header chars");
    }
    private static void staged() throws Exception {
        File cache = Files.createTempDirectory("guio-export-test-").toFile();
        try {
            byte[] pdf = "%PDF-1.7\nexample".getBytes(StandardCharsets.US_ASCII);
            ByteArrayOutputStream target = new ByteArrayOutputStream();
            long size = StagedPdfExport.write(cache, out -> out.write(pdf), () -> {
                ok(cache.list().length == 1, "staging complete before target opened");
                return target;
            });
            ok(size == pdf.length && Arrays.equals(target.toByteArray(), pdf), "successful spool and copy");
            ok(cache.list().length == 0, "spool removed on success");
            final int[] opened = {0};
            reject(() -> StagedPdfExport.write(cache, out -> {
                out.write(pdf);
                throw new IOException("encoding failed");
            }, () -> { opened[0]++; return target; }), "encoding failure");
            ok(opened[0] == 0 && cache.list().length == 0, "output untouched / spool cleaned on encode failure");
            reject(() -> StagedPdfExport.write(cache, out -> {}, () -> { opened[0]++; return target; }),"empty PDF rejected");
            ok(opened[0] == 0 && cache.list().length == 0,"no empty PDF in target");
            reject(() -> StagedPdfExport.write(cache, out -> out.write(pdf), () -> {
                opened[0]++; throw new IOException("provider unavailable");
            }), "provider failure");
            ok(cache.list().length == 0, "spool cleaned when provider fails");
            reject(() -> StagedPdfExport.write(cache, out -> out.write(pdf), () -> target, 4), "size limit");
            ok(cache.list().length == 0, "oversize spool removed");
            reject(() -> StagedPdfExport.write(cache, out -> out.write(pdf), () -> new OutputStream() {
                @Override public void write(int v) throws IOException { throw new IOException("out of space"); }
                @Override public void write(byte[] b, int o, int n) throws IOException { throw new IOException("out of space"); }
            }), "destination write failure");
            ok(cache.list().length == 0, "destination I/O failure cleanup");
            reject(() -> StagedPdfExport.write(cache, out -> out.write(pdf), () -> target, 0), "invalid limit");
            reject(() -> StagedPdfExport.write(new File(cache,"missing"), out -> out.write(pdf), () -> target), "invalid cache directory");
        } finally {
            cache.delete();
        }
    }
    public static void main(String[] args) throws Exception {
        protocol(); staged();
        System.out.println("PASS: " + checks + " USB HTTP framing and PDF staging tests");
    }
}
