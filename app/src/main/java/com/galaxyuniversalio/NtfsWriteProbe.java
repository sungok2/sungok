package com.galaxyuniversalio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Active, user-authorized write probe. It NEVER uses a user's existing filename.
 * It writes a random 8 KiB temporary file into the chosen DocumentsProvider folder,
 * verifies the provider can read it back, then explicitly deletes it.
 *
 * Successful result describes only the selected document tree at the time of testing.
 * It does not identify the underlying filesystem, test an actual NTFS driver, or
 * prove durability across USB removal.
 */
final class NtfsWriteProbe {
    static final int TEST_BYTES = 8192;

    interface Backend {
        void assertWritableDirectory() throws IOException;
        void journal(String temporaryName) throws IOException;
        String createTemporary(String temporaryName) throws IOException;
        void assertWritableFile(String documentId) throws IOException;
        OutputStream openWrite(String documentId) throws IOException;
        InputStream openRead(String documentId) throws IOException;
        boolean deleteTemporary(String documentId) throws IOException;
        boolean clearJournal();
        void cleanupError(Exception problem);
    }

    static final class Result {
        final int bytes;
        final String sha256;
        Result(int bytes, String sha256) {
            this.bytes=bytes;
            this.sha256=sha256;
        }
    }

    static Result execute(Backend backend, AtomicBoolean canceled) throws IOException {
        if (canceled != null && canceled.get()) throw new InterruptedIOException("Probe canceled before start");
        backend.assertWritableDirectory();
        byte[] payload = new byte[TEST_BYTES];
        new SecureRandom().nextBytes(payload);
        String temporaryName = NtfsNamePolicy.temporaryName();
        backend.journal(temporaryName);
        String id = null;
        IOException failure = null;
        Result result = null;
        boolean removed = false;
        try {
            id = backend.createTemporary(temporaryName);
            if (id == null) throw new IOException("Provider returned no document after create");
            backend.assertWritableFile(id);
            try (OutputStream out = backend.openWrite(id)) {
                if (out == null) throw new IOException("Provider did not open a writable stream");
                out.write(payload);
                out.flush();
            }
            if (canceled != null && canceled.get()) throw new InterruptedIOException("Probe canceled");
            VerifiedStreamCopy.Result expected =
                    VerifiedStreamCopy.hash(new ByteArrayInputStream(payload), canceled);
            VerifiedStreamCopy.Result actual;
            try (InputStream in = backend.openRead(id)) {
                if (in == null) throw new IOException("Provider did not open a readable stream");
                actual = VerifiedStreamCopy.hash(in, canceled);
            }
            VerifiedStreamCopy.verify(expected, actual);
            result = new Result(TEST_BYTES, actual.sha256);
        } catch (IOException e) {
            failure = e;
        } catch (RuntimeException e) {
            failure = new IOException("Provider exception: "+e.getMessage(),e);
        } finally {
            if (id != null) {
                try {
                    removed = backend.deleteTemporary(id);
                } catch (Exception e) {
                    backend.cleanupError(e);
                    if (failure == null) failure = new IOException("Temporary probe deletion failed",e);
                }
            }
            if (removed) {
                if (!backend.clearJournal()) {
                    IOException e = new IOException("Probe removed but recovery journal could not be cleared");
                    if (failure == null) failure = e;
                    else failure.addSuppressed(e);
                }
            } else {
                IOException e = new IOException("Probe file cleanup is uncertain; recovery required");
                backend.cleanupError(e);
                if (failure == null) failure = e;
                else failure.addSuppressed(e);
            }
        }
        if (failure != null) throw failure;
        return result;
    }
}
