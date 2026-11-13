package com.galaxyuniversalio;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Small, provider-independent transfer state machine. All destination changes are
 * routed through Backend, permitting deterministic fault injection in JVM tests.
 *
 * Guarantees only best-effort cleanup. Renames from arbitrary DocumentsProviders
 * are NOT guaranteed atomic or no-replace under concurrent external writers.
 */
final class SafVerifiedTransaction {
    interface Backend {
        void ensureFinalAbsent(String name) throws IOException;
        void journal(String temporaryName, String finalName) throws IOException;
        String createTemporary(String name) throws IOException;
        void requireWritableAndRenameable(String documentId) throws IOException;
        InputStream openSource() throws IOException;
        OutputStream openTemporaryForWrite(String documentId) throws IOException;
        InputStream openTemporaryForRead(String documentId) throws IOException;
        void beforeReadback();
        String rename(String documentId, String finalName) throws IOException;
        String displayName(String documentId) throws IOException;
        InputStream openPublishedForRead(String documentId) throws IOException;
        boolean deleteTemporary(String documentId) throws IOException;
        boolean clearJournal();
        void cleanupError(Exception e);
    }

    static final class Receipt {
        final long bytes;
        final String sha256;
        Receipt(long bytes, String sha256) {
            this.bytes = bytes;
            this.sha256 = sha256;
        }
    }

    static Receipt execute(Backend backend, String finalName, long expectedSize,
                           AtomicBoolean cancel, VerifiedStreamCopy.Progress progress) throws IOException {
        NtfsNamePolicy.requireValid(finalName);
        checkCancel(cancel);
        backend.ensureFinalAbsent(finalName);
        String tempName = NtfsNamePolicy.temporaryName();
        // Record an intention before any call that can mutate the destination.
        backend.journal(tempName, finalName);

        String temporary = null;
        boolean renamed = false;
        IOException primary = null;
        try {
            temporary = backend.createTemporary(tempName);
            if (temporary == null) throw new IOException("Provider refused temporary document");
            backend.requireWritableAndRenameable(temporary);

            VerifiedStreamCopy.Result written;
            try (InputStream in = backend.openSource();
                 OutputStream out = backend.openTemporaryForWrite(temporary)) {
                written = VerifiedStreamCopy.copy(in, out, cancel, progress);
            }
            if (expectedSize >= 0 && expectedSize != written.bytes) {
                throw new IOException("Source length changed during copy: expected " +
                        expectedSize + " bytes; copied " + written.bytes);
            }

            backend.beforeReadback();
            VerifiedStreamCopy.Result checked;
            try (InputStream in = backend.openTemporaryForRead(temporary)) {
                checked = VerifiedStreamCopy.hash(in, cancel);
            }
            VerifiedStreamCopy.verify(written, checked);
            checkCancel(cancel);
            backend.ensureFinalAbsent(finalName);
            String published = backend.rename(temporary, finalName);
            if (published == null) throw new IOException("Provider could not finalize the file");
            renamed = true;
            String actualName = backend.displayName(published);
            if (!finalName.equals(actualName)) {
                throw new IOException("Destination changed filename to '" + actualName +
                        "'. File was copied, but original name was not preserved.");
            }
            // A provider may replace the document ID during rename. Verify the final URI
            // too, rather than assuming the staging handle and published content match.
            VerifiedStreamCopy.Result finalReadback;
            try (InputStream in = backend.openPublishedForRead(published)) {
                finalReadback = VerifiedStreamCopy.hash(in, cancel);
            }
            VerifiedStreamCopy.verify(written, finalReadback);
            return new Receipt(written.bytes, written.sha256);
        } catch (IOException e) {
            primary = e;
            throw e;
        } finally {
            // Never delete a published file, even if the provider changed its name.
            if (renamed) {
                if (!backend.clearJournal()) {
                    IOException e = new IOException("Published file remains, but recovery journal could not be cleared");
                    if (primary != null) primary.addSuppressed(e);
                    else throw e;
                }
            } else if (temporary != null) {
                try {
                    if (backend.deleteTemporary(temporary)) {
                        if (!backend.clearJournal()) {
                            backend.cleanupError(new IOException("Temporary deleted but journal clear failed"));
                        }
                    } else {
                        backend.cleanupError(new IOException("Provider did not remove partial file"));
                    }
                } catch (Exception e) {
                    backend.cleanupError(e);
                }
            }
            // If createTemporary threw before returning an ID, keep the journal:
            // creation may have succeeded remotely despite the exception.
        }
    }

    private static void checkCancel(AtomicBoolean cancel) throws InterruptedIOException {
        if (cancel != null && cancel.get()) {
            throw new InterruptedIOException("Transfer canceled");
        }
    }

    private SafVerifiedTransaction() { }
}
