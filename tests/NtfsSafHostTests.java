package com.galaxyuniversalio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NtfsSafHostTests {
    private static int passed;

    private static void ok(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        passed++;
    }

    private static void fails(IoAction action, String label) {
        try {
            action.run();
            throw new AssertionError("Expected IOException: " + label);
        } catch (IOException expected) {
            passed++;
        }
    }

    interface IoAction { void run() throws IOException; }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        AtomicBoolean cancel = new AtomicBoolean(false);
        byte[] input = bytes("NTFS 복사 / Hangul / 12345");
        ByteArrayOutputStream destination = new ByteArrayOutputStream();
        VerifiedStreamCopy.Result written = VerifiedStreamCopy.copy(
                new ByteArrayInputStream(input), destination, cancel, count -> {});
        ok(Arrays.equals(input, destination.toByteArray()), "bytes match");
        ok(written.bytes == input.length, "byte count");
        VerifiedStreamCopy.Result readBack = VerifiedStreamCopy.hash(
                new ByteArrayInputStream(destination.toByteArray()), cancel);
        VerifiedStreamCopy.verify(written, readBack);
        passed++;
        fails(() -> VerifiedStreamCopy.verify(written,
                VerifiedStreamCopy.hash(new ByteArrayInputStream(bytes("wrong")), cancel)),
                "changed bytes rejected");
        fails(() -> VerifiedStreamCopy.verify(written,
                VerifiedStreamCopy.hash(new ByteArrayInputStream(new byte[0]), cancel)),
                "truncated bytes rejected");
        ByteArrayOutputStream empty = new ByteArrayOutputStream();
        VerifiedStreamCopy.Result emptyResult = VerifiedStreamCopy.copy(
                new ByteArrayInputStream(new byte[0]), empty, cancel, count -> {});
        ok(emptyResult.bytes == 0 && empty.size() == 0, "empty files supported");

        byte[] large = new byte[1024 * 1024 + 111];
        for (int i = 0; i < large.length; i++) large[i] = (byte) (i * 17);
        ByteArrayOutputStream largeDestination = new ByteArrayOutputStream();
        VerifiedStreamCopy.Result largeResult = VerifiedStreamCopy.copy(
                new ByteArrayInputStream(large), largeDestination, cancel, count -> {});
        VerifiedStreamCopy.verify(largeResult,
                VerifiedStreamCopy.hash(new ByteArrayInputStream(largeDestination.toByteArray()), cancel));
        passed++;
        ok(Arrays.equals(large, largeDestination.toByteArray()), "multi-block copy");

        AtomicBoolean stopped = new AtomicBoolean(true);
        fails(() -> VerifiedStreamCopy.copy(new ByteArrayInputStream(input),
                new ByteArrayOutputStream(), stopped, null), "pre-cancel");
        AtomicBoolean midStop = new AtomicBoolean();
        fails(() -> VerifiedStreamCopy.copy(new ByteArrayInputStream(large),
                new ByteArrayOutputStream(), midStop, count -> midStop.set(true)),
                "mid-copy cancel");
        fails(() -> VerifiedStreamCopy.hash(new ByteArrayInputStream(input), stopped),
                "verify cancel");

        OutputStream failing = new OutputStream() {
            @Override public void write(int b) throws IOException { throw new IOException("device disconnected"); }
            @Override public void write(byte[] b, int off, int len) throws IOException {
                throw new IOException("device disconnected");
            }
        };
        fails(() -> VerifiedStreamCopy.copy(new ByteArrayInputStream(input),
                failing, cancel, null), "destination I/O error");
        InputStream zeroForever = new InputStream() {
            @Override public int read() { return 0; }
            @Override public int read(byte[] b, int off, int len) { return 0; }
        };
        fails(() -> VerifiedStreamCopy.hash(zeroForever, cancel), "zero-read livelock rejected");
        InputStream broken = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("read failure"); }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                throw new IOException("read failure");
            }
        };
        fails(() -> VerifiedStreamCopy.hash(broken, cancel), "source I/O error");
        System.out.println("NTFS SAF host checks passed: " + passed);
    }
}
