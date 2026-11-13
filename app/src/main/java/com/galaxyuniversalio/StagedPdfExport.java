package com.galaxyuniversalio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Generates a PDF into private app cache before opening the user destination.
 * This avoids creating a partially generated PDF when bitmap/PDF rendering
 * fails. Final SAF copy is NOT transactional: provider or I/O failures may
 * still leave a partial destination file.
 */
public final class StagedPdfExport {
    public interface Encoder { void encode(OutputStream sink) throws IOException; }
    public interface Target { OutputStream open() throws IOException; }
    public static final long MAX_SPOOL_BYTES = 96L * 1024 * 1024;
    private StagedPdfExport() { }

    public static long write(File privateCacheDirectory, Encoder encoder, Target target)
            throws IOException {
        return write(privateCacheDirectory, encoder, target, MAX_SPOOL_BYTES);
    }

    // Package-private bound permits small, deterministic host-only limit tests.
    static long write(File privateCacheDirectory, Encoder encoder, Target target,
            long maxSpoolBytes) throws IOException {
        if (privateCacheDirectory == null || !privateCacheDirectory.isDirectory()
                || encoder == null || target == null || maxSpoolBytes <= 0
                || maxSpoolBytes > MAX_SPOOL_BYTES)
            throw new IOException("Invalid staged PDF destination");
        File temp = File.createTempFile("guio-pdf-", ".part", privateCacheDirectory);
        try {
            if (Thread.currentThread().isInterrupted())
                throw new IOException("PDF export interrupted");
            try (OutputStream output = new LimitedOutputStream(new FileOutputStream(temp),
                    maxSpoolBytes)) {
                encoder.encode(output);
            }
            long size = temp.length();
            if (size == 0 || size > maxSpoolBytes)
                throw new IOException("Missing or oversized staged PDF");
            // Do not open/truncate the user destination until PDF rendering succeeds.
            if (Thread.currentThread().isInterrupted())
                throw new IOException("PDF export interrupted");
            try (InputStream input = new FileInputStream(temp);
                    OutputStream out = target.open()) {
                if (out == null) throw new IOException("No output stream from document provider");
                byte[] buffer = new byte[32768];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted())
                        throw new IOException("PDF export interrupted");
                    out.write(buffer, 0, n);
                }
                out.flush();
            }
            return size;
        } finally {
            // App-private temporary page data must not be left on storage.
            if (!temp.delete() && temp.exists()) temp.deleteOnExit();
        }
    }

    private static final class LimitedOutputStream extends FilterOutputStream {
        private final long limit;
        private long written;
        LimitedOutputStream(OutputStream delegate, long limit) {
            super(delegate); this.limit = limit;
        }
        @Override public void write(int b) throws IOException {
            if (written >= limit) throw new IOException("PDF cache size limit exceeded");
            out.write(b); written++;
        }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            if (length < 0 || offset < 0 || offset > bytes.length - length)
                throw new IndexOutOfBoundsException();
            if (length > limit - written) throw new IOException("PDF cache size limit exceeded");
            out.write(bytes, offset, length); written += length;
        }
    }
}