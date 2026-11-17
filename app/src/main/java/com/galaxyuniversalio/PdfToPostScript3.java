package com.galaxyuniversalio;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** Rasterizes PDFs into PostScript 3 colorimage pages using the Android PDF renderer. */
public final class PdfToPostScript3 {
    private static final int MAX_RENDER_DIMENSION = 5000;

    private PdfToPostScript3() { }

    public static long render(ContentResolver resolver, Uri pdfUri, File output,
                              UsbPrinterProfile profile) throws IOException {
        if (resolver == null || pdfUri == null || output == null || profile == null
                || !"POSTSCRIPT3".equals(profile.pdl)) {
            throw new IOException("유효한 WF-C5390 PostScript 3 프로파일이 필요합니다.");
        }
        try (ParcelFileDescriptor descriptor = resolver.openFileDescriptor(pdfUri, "r");
             FileOutputStream file = new FileOutputStream(output);
             OutputStream bounded = new BoundedOutputStream(new BufferedOutputStream(file),
                     profile.maxJobBytes)) {
            if (descriptor == null) throw new IOException("PDF 파일을 열 수 없습니다.");
            try (PdfRenderer renderer = new PdfRenderer(descriptor)) {
                int pageCount = renderer.getPageCount();
                if (pageCount < 1 || pageCount > profile.maxPages) {
                    throw new IOException("페이지 수 제한 초과 (최대 " + profile.maxPages + "페이지)");
                }
                ascii(bounded, "%!PS-Adobe-3.0\n%%Creator: Galaxy Universal I/O\n"
                        + "%%Pages: " + pageCount + "\n%%LanguageLevel: 3\n%%EndComments\n");
                for (int i = 0; i < pageCount; i++) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("변환이 취소되었습니다.");
                    try (PdfRenderer.Page page = renderer.openPage(i)) {
                        int width = Math.max(1, (int) Math.ceil(page.getWidth() * profile.dpi / 72.0));
                        int height = Math.max(1, (int) Math.ceil(page.getHeight() * profile.dpi / 72.0));
                        if (width > MAX_RENDER_DIMENSION || height > MAX_RENDER_DIMENSION) {
                            throw new IOException("PDF 페이지 크기가 변환 한도를 초과합니다.");
                        }
                        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                        try {
                            new Canvas(bitmap).drawColor(Color.WHITE);
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                            emitPage(bounded, bitmap, i + 1, width, height,
                                    page.getWidth(), page.getHeight(), profile.dpi);
                        } finally {
                            bitmap.recycle();
                        }
                    }
                }
                ascii(bounded, "%%Trailer\n%%EOF\n");
            }
            bounded.flush();
            long bytes = output.length();
            if (bytes <= 0 || bytes > profile.maxJobBytes) throw new IOException("생성된 PDL 파일 크기 오류");
            return bytes;
        } catch (OutOfMemoryError e) {
            output.delete();
            throw new IOException("페이지 변환 메모리가 부족합니다. 더 작은 PDF로 다시 시도하세요.", e);
        } catch (IOException e) {
            output.delete();
            throw e;
        }
    }

    private static void emitPage(OutputStream out, Bitmap bitmap, int pageNumber,
                                 int width, int height, int pageWidthPoints,
                                 int pageHeightPoints, int dpi) throws IOException {
        ascii(out, String.format(Locale.US,
                "%%%%Page: %d %d\n<< /PageSize [%d %d] >> setpagedevice\n"
                        + "gsave %.6f %.6f scale\n/picstr %d string def\n"
                        + "/src currentfile /ASCIIHexDecode filter /FlateDecode filter def\n"
                        + "%d %d 8 [%d 0 0 -%d 0 %d] { src picstr readstring pop } false 3 colorimage\n",
                pageNumber, pageNumber, pageWidthPoints, pageHeightPoints,
                72.0 / dpi, 72.0 / dpi, width * 3, width, height, width, height, height));
        HexOutputStream hex = new HexOutputStream(out);
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        DeflaterOutputStream compressed = new DeflaterOutputStream(hex, deflater, 16 * 1024);
        try {
            int[] pixels = new int[width];
            byte[] row = new byte[width * 3];
            for (int y = 0; y < height; y++) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("변환이 취소되었습니다.");
                bitmap.getPixels(pixels, 0, width, 0, y, width, 1);
                for (int x = 0, p = 0; x < width; x++) {
                    int color = pixels[x];
                    row[p++] = (byte) ((color >>> 16) & 0xff);
                    row[p++] = (byte) ((color >>> 8) & 0xff);
                    row[p++] = (byte) (color & 0xff);
                }
                compressed.write(row);
            }
            compressed.finish();
            hex.finish();
            ascii(out, ">\ngrestore showpage\n");
        } finally {
            compressed.close();
            deflater.end();
        }
    }

    private static void ascii(OutputStream out, String value) throws IOException {
        out.write(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static final class BoundedOutputStream extends FilterOutputStream {
        private final long maximum;
        private long written;
        BoundedOutputStream(OutputStream out, long maximum) { super(out); this.maximum = maximum; }
        @Override public void write(int value) throws IOException {
            ensure(1); out.write(value); written++;
        }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            ensure(length); out.write(bytes, offset, length); written += length;
        }
        private void ensure(int length) throws IOException {
            if (length < 0 || written > maximum - length) throw new IOException("변환 결과가 크기 제한을 초과했습니다.");
        }
    }

    private static final class HexOutputStream extends FilterOutputStream implements Closeable {
        private static final byte[] HEX = "0123456789ABCDEF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        private int column;
        HexOutputStream(OutputStream out) { super(out); }
        @Override public void write(int value) throws IOException {
            int b = value & 0xff;
            out.write(HEX[b >>> 4]); out.write(HEX[b & 15]);
            column += 2;
            if (column >= 64) { out.write('\n'); column = 0; }
        }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            for (int i = 0; i < length; i++) write(bytes[offset + i]);
        }
        void finish() throws IOException { if (column != 0) { out.write('\n'); column = 0; } out.flush(); }
        @Override public void close() throws IOException { finish(); }
    }
}
