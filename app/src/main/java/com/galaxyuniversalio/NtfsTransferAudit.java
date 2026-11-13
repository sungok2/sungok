package com.galaxyuniversalio;

import java.io.IOException;
import java.util.Locale;

/** Portable plain-text audit; records a read-back check, not a durability guarantee. */
final class NtfsTransferAudit {
    private final StringBuilder report = new StringBuilder();
    private int succeeded;
    private long byteCount;

    NtfsTransferAudit() {
        report.append("# Galaxy Universal I/O — NTFS SAF transfer validation\n");
        report.append("# SHA-256 is checked against the temporary and final provider URI.\n");
        report.append("# Not proof of media flush, NTFS journal integrity or safe ejection.\n");
        report.append("STATUS\tFILE\tBYTES\tSHA256\n");
    }

    void success(String name, SafVerifiedTransaction.Receipt result) throws IOException {
        NtfsNamePolicy.requireValid(name);
        if (result.bytes < 0 || result.sha256 == null ||
                !result.sha256.matches("[0-9a-f]{64}")) {
            throw new IOException("Invalid verification receipt");
        }
        report.append("VERIFIED\t").append(name).append('\t')
                .append(result.bytes).append('\t').append(result.sha256).append('\n');
        succeeded++;
        byteCount += result.bytes;
        if (byteCount < 0) throw new IOException("Transfer audit length overflow");
    }

    int count() { return succeeded; }
    long totalBytes() { return byteCount; }

    String finish(int planned, boolean allSucceeded, String error) {
        StringBuilder result = new StringBuilder(report);
        result.append("\nSUMMARY\t").append(allSucceeded ? "COMPLETE" : "PARTIAL")
                .append("\tVERIFIED_FILES=").append(succeeded)
                .append("\tREQUESTED_FILES=").append(planned)
                .append("\tBYTES=").append(byteCount).append('\n');
        if (error != null && !error.isEmpty()) {
            // No control characters or newlines in exported failure reason.
            result.append("NOTE\t").append(error.replaceAll("[\\p{Cntrl}]", " "))
                    .append('\n');
        }
        return result.toString();
    }
}
