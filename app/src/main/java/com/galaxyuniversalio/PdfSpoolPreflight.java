package com.galaxyuniversalio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Independent of Android: bounded PDF header check and cancellable spool copy. */
public final class PdfSpoolPreflight {
    public static final long MAX_PDF_BYTES = 32L * 1024 * 1024;

    public interface Cancellation { boolean isCancelled(); }
    private PdfSpoolPreflight() { }

    public static long copy(InputStream source, OutputStream destination, Cancellation cancellation)
            throws IOException {
        if (source == null || destination == null) throw new IOException("PDF 스트림이 없습니다.");
        byte[] header = new byte[5];
        int read = 0;
        while (read < header.length) {
            if (cancellation != null && cancellation.isCancelled()) throw new InterruptedIOException();
            int n = source.read(header, read, header.length - read);
            if (n < 0) throw new IOException("PDF 헤더가 너무 짧습니다.");
            if (n == 0) throw new IOException("PDF 스트림 응답 없음");
            read += n;
        }
        if (header[0] != '%' || header[1] != 'P' || header[2] != 'D'
                || header[3] != 'F' || header[4] != '-') {
            throw new IOException("PDF 파일 시그니처가 아닙니다.");
        }
        destination.write(header);
        long total = header.length;
        byte[] buf = new byte[16384];
        int n;
        while ((n = source.read(buf)) != -1) {
            if (cancellation != null && cancellation.isCancelled()) throw new InterruptedIOException();
            if (n == 0) throw new IOException("PDF 스트림 응답 없음");
            if (total > MAX_PDF_BYTES - n) throw new IOException("PDF 파일이 32 MiB 제한을 초과했습니다.");
            destination.write(buf, 0, n);
            total += n;
        }
        if (cancellation != null && cancellation.isCancelled()) throw new InterruptedIOException();
        destination.flush();
        return total;
    }

    public static final class InterruptedIOException extends IOException {
        public InterruptedIOException() { super("인쇄 작업이 취소되었습니다."); }
    }
}
