package com.galaxyuniversalio;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provider-neutral streaming copy and read-back SHA-256 verification.
 * No Android API: can be tested on a desktop JVM.
 */
final class VerifiedStreamCopy {
    private static final int BUFFER_SIZE = 64 * 1024;

    interface Progress {
        void onBytes(long bytes);
    }

    static final class Result {
        final long bytes;
        final String sha256;

        Result(long bytes, String sha256) {
            this.bytes = bytes;
            this.sha256 = sha256;
        }
    }

    private VerifiedStreamCopy() {}

    static Result copy(InputStream source, OutputStream destination,
                       AtomicBoolean cancel, Progress progress) throws IOException {
        if (destination == null) throw new IOException("Destination stream unavailable");
        Result result = read(source, destination, cancel, progress);
        destination.flush();
        checkCancel(cancel);
        return result;
    }

    static Result hash(InputStream source, AtomicBoolean cancel) throws IOException {
        return read(source, null, cancel, null);
    }

    static void verify(Result written, Result readBack) throws IOException {
        if (written.bytes != readBack.bytes || !written.sha256.equals(readBack.sha256)) {
            throw new IOException("SHA-256 or byte-count mismatch after reading back destination");
        }
    }

    private static Result read(InputStream source, OutputStream destination,
                               AtomicBoolean cancel, Progress progress) throws IOException {
        if (source == null) throw new IOException("Source stream unavailable");
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
        byte[] buf = new byte[BUFFER_SIZE];
        long total = 0;
        int consecutiveZeroReads = 0;
        while (true) {
            checkCancel(cancel);
            int count = source.read(buf);
            if (count < 0) break;
            if (count == 0) {
                if (++consecutiveZeroReads > 8) {
                    throw new IOException("Source stream repeatedly returned zero bytes");
                }
                continue;
            }
            consecutiveZeroReads = 0;
            if (Long.MAX_VALUE - total < count) throw new IOException("Byte count overflow");
            if (destination != null) destination.write(buf, 0, count);
            md.update(buf, 0, count);
            total += count;
            if (progress != null) progress.onBytes(total);
        }
        checkCancel(cancel);
        StringBuilder hex = new StringBuilder(64);
        for (byte b : md.digest()) {
            hex.append(Character.forDigit((b >>> 4) & 15, 16));
            hex.append(Character.forDigit(b & 15, 16));
        }
        return new Result(total, hex.toString());
    }

    private static void checkCancel(AtomicBoolean cancel) throws InterruptedIOException {
        if (cancel != null && cancel.get()) {
            throw new InterruptedIOException("Copy canceled before completion");
        }
    }
}
