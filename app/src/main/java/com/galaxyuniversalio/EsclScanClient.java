package com.galaxyuniversalio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Standards-based eSCL scan job state machine, transport-independent. */
public final class EsclScanClient {
    public interface Transport extends AutoCloseable {
        Reply exchange(String method, String path, String mediaType, byte[] body, int limit) throws IOException;
        String origin();
        @Override void close() throws IOException;
    }
    public interface Progress { void onPage(int number); }
    public static final class Reply {
        public final int status;
        public final String location;
        public final String mediaType;
        public final byte[] body;
        public Reply(int status, String location, String mediaType, byte[] body) {
            this.status = status;
            this.location = location;
            this.mediaType = mediaType;
            this.body = body;
        }
    }

    private EsclScanClient() { }

    /** Single platen page, or ADF up to 12 pages/32MiB, without saving partial PDFs. */
    public static List<byte[]> scan(Transport transport, boolean feeder, Progress progress)
            throws IOException {
        checkCancellation();
        Reply capabilities = transport.exchange("GET", "/eSCL/ScannerCapabilities", null, null,
                EsclProtocol.MAX_XML_BYTES);
        if (capabilities.status != 200) throw new IOException("eSCL capabilities HTTP " + capabilities.status);
        EsclProtocol.Capabilities caps = EsclProtocol.parseCapabilities(capabilities.body);
        checkCancellation();
        byte[] xml = EsclProtocol.settings(caps, feeder);
        Reply started = transport.exchange("POST", "/eSCL/ScanJobs", "text/xml", xml, 64 * 1024);
        if (started.status != 201) throw new IOException("eSCL ScanJobs HTTP " + started.status);
        String job = EsclProtocol.jobPath(started.location, transport.origin());
        List<byte[]> pages = new ArrayList<>();
        int total = 0;
        try {
            // ADF: query once more after the 12th page to distinguish a complete
            // 12-page job from a job that would otherwise be silently truncated.
            for (int i = 0; i < (feeder ? EsclProtocol.MAX_PAGES + 1 : 1); i++) {
                checkCancellation();
                Reply next = null;
                for (int retry = 0; retry < 30; retry++) {
                    checkCancellation();
                    next = transport.exchange("GET", job + "/NextDocument", null, null,
                            feeder && i == EsclProtocol.MAX_PAGES ? 1 : EsclProtocol.MAX_IMAGE_BYTES);
                    if (next.status != 503) break;
                    try { Thread.sleep(1000); }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Scan cancelled", e);
                    }
                }
                checkCancellation();
                if (next == null) throw new IOException("No scan response");
                if (next.status == 404 || next.status == 410 || (feeder && next.status == 204)) {
                    if (pages.isEmpty()) throw new IOException("No document returned by scanner");
                    break;
                }
                if (next.status != 200) throw new IOException("NextDocument HTTP " + next.status);
                if (feeder && i == EsclProtocol.MAX_PAGES)
                    throw new IOException("ADF contains more than 12 pages; output not saved");
                String returnedFormat = EsclProtocol.requireImage(next.body, next.mediaType);
                if (!returnedFormat.equals(caps.source(feeder).format))
                    throw new IOException("Scanner returned a different format than negotiated");
                total += next.body.length;
                if (total > EsclProtocol.MAX_TOTAL_BYTES)
                    throw new IOException("Scan exceeds 32MiB memory safety cap");
                pages.add(next.body);
                if (progress != null) progress.onPage(pages.size());
            }
            // All ADF pages must be accounted for; no partial PDF is produced.
            if (pages.isEmpty()) throw new IOException("No scanned pages");
            checkCancellation();
            return pages;
        } finally {
            // Always delete a job that might still be running (including error / single platen page).
            // Never let cleanup failure mask an already-valid scan or the original failure.
            try { transport.exchange("DELETE", job, null, null, 1024); }
            catch (IOException ignored) { }
        }
    }

    private static void checkCancellation() throws IOException {
        if (Thread.currentThread().isInterrupted())
            throw new IOException("Document scan interrupted");
    }
}
