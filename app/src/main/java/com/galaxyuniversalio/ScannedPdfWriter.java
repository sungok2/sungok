package com.galaxyuniversalio;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/** JPEG/PNG pages to A4 PDF using platform APIs (no external PDF dependency). */
public final class ScannedPdfWriter {
    private ScannedPdfWriter() { }

    public static void write(List<byte[]> imagePages, OutputStream output) throws IOException {
        if (imagePages == null || imagePages.isEmpty() || imagePages.size() > EsclProtocol.MAX_PAGES
                || output == null) throw new IOException("Invalid PDF pages or output");
        PdfDocument pdf = new PdfDocument();
        try {
            int pageNo = 0;
            for (byte[] bytes : imagePages) {
                if (Thread.currentThread().isInterrupted())
                    throw new IOException("PDF generation interrupted");
                EsclProtocol.imageMimeFromBytes(bytes);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                if (opts.outWidth <= 0 || opts.outHeight <= 0 || opts.outWidth > 30000
                        || opts.outHeight > 30000) throw new IOException("Invalid scanned-image dimensions");
                opts.inSampleSize = 1;
                while ((long) Math.ceil((double) opts.outWidth / opts.inSampleSize)
                        * Math.ceil((double) opts.outHeight / opts.inSampleSize) > 3_000_000L)
                    opts.inSampleSize *= 2;
                opts.inJustDecodeBounds = false;
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                if (bitmap == null) throw new IOException("Image decode failed");
                try {
                    PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(595, 842, ++pageNo).create();
                    PdfDocument.Page page = pdf.startPage(info);
                    Canvas canvas = page.getCanvas();
                    canvas.drawColor(0xFFFFFFFF);
                    float maxW = 555f, maxH = 802f;
                    float scale = Math.min(maxW / bitmap.getWidth(), maxH / bitmap.getHeight());
                    float width = bitmap.getWidth() * scale, height = bitmap.getHeight() * scale;
                    float left = (595f - width) / 2f;
                    float top = (842f - height) / 2f;
                    canvas.drawBitmap(bitmap, new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                            new RectF(left, top, left + width, top + height),
                            new Paint(Paint.FILTER_BITMAP_FLAG));
                    pdf.finishPage(page);
                } finally { bitmap.recycle(); }
            }
            pdf.writeTo(output);
            output.flush();
        } finally { pdf.close(); }
    }
}
