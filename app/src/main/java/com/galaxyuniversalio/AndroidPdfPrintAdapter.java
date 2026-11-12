package com.galaxyuniversalio;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Hands an existing PDF to Android Print Framework; NOT a direct USB printer driver. */
public final class AndroidPdfPrintAdapter extends PrintDocumentAdapter {
    private final Context context;
    private final Uri pdfUri;
    private final ExecutorService workers = Executors.newSingleThreadExecutor();

    public AndroidPdfPrintAdapter(Context context, Uri pdfUri) {
        this.context = context.getApplicationContext();
        this.pdfUri = pdfUri;
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                         CancellationSignal cancellation, LayoutResultCallback callback, Bundle extras) {
        if (cancellation.isCanceled()) { callback.onLayoutCancelled(); return; }
        PrintDocumentInfo info = new PrintDocumentInfo.Builder("GalaxyUniversalIO-PDF.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build();
        callback.onLayoutFinished(info, true);
    }

    @Override
    public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                        CancellationSignal cancellation, WriteResultCallback callback) {
        // Copying an existing PDF cannot implement arbitrary page selection without a PDF renderer.
        if (pages == null || pages.length != 1 || !PageRange.ALL_PAGES.equals(pages[0])) {
            try { destination.close(); } catch (IOException ignored) { }
            callback.onWriteFailed("부분 페이지 인쇄는 미지원입니다. 전체 페이지를 선택하세요.");
            return;
        }
        try {
            workers.execute(() -> {
                try (InputStream input = context.getContentResolver().openInputStream(pdfUri);
                     OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(destination)) {
                    PdfSpoolPreflight.copy(input, output, cancellation::isCanceled);
                    if (cancellation.isCanceled()) callback.onWriteCancelled();
                    else callback.onWriteFinished(new PageRange[] {PageRange.ALL_PAGES});
                } catch (PdfSpoolPreflight.InterruptedIOException e) {
                    callback.onWriteCancelled();
                } catch (IOException | RuntimeException e) {
                    callback.onWriteFailed("PDF 인쇄 데이터 준비 실패: " + e.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException e) {
            try { destination.close(); } catch (IOException ignored) { }
            callback.onWriteFailed("인쇄 작업을 실행할 수 없습니다.");
        }
    }

    @Override
    public void onFinish() {
        workers.shutdown();
    }
}
