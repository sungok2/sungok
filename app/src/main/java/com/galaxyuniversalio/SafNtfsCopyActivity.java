package com.galaxyuniversalio;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SAF bridge for writable third-party USB DocumentsProviders, including OTG Master.
 *
 * This does NOT install an NTFS kernel driver or bypass a read-only system provider.
 * OTG Master must mount the NTFS partition read-write and expose its DocumentsProvider.
 * No source files are ever deleted. A destination is read back and SHA-256 checked
 * before its temporary filename is renamed to its final name.
 */
public final class SafNtfsCopyActivity extends Activity {
    private static final int REQ_SOURCES = 3101;
    private static final int REQ_TARGET = 3102;
    private static final int MAX_FILES = 128;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancel = new AtomicBoolean();
    private final ArrayList<Uri> sources = new ArrayList<>();
    private TextView status;
    private ProgressBar progress;
    private Button selectButton, cancelButton;
    private volatile boolean copying;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            ArrayList<String> saved = state.getStringArrayList("sources");
            if (saved != null) for (String value : saved) sources.add(Uri.parse(value));
        }
        buildUi();
    }

    private void buildUi() {
        int pad = dp(18);
        LinearLayout column = new LinearLayout(this);
        column.setPadding(pad, pad, pad, pad);
        column.setOrientation(LinearLayout.VERTICAL);

        TextView heading = new TextView(this);
        heading.setText("USB 파일 전송");
        heading.setTextSize(25);
        heading.setTextColor(0xFF142C51);
        column.addView(heading);

        TextView explanation = new TextView(this);
        explanation.setText("NTFS 포함 · 외부 저장소 제공자 연결\n\n" +
                "1. NTFS USB를 OTG Master 등 쓰기를 지원하는 앱에서 읽기·쓰기로 연결합니다.\n" +
                "2. 복사할 파일을 선택합니다(최대 128개).\n" +
                "3. Android 폴더 선택기에서 해당 USB의 대상 폴더를 선택합니다.\n\n" +
                "읽기 전용으로 연결된 USB에는 쓰지 않습니다. " +
                "이 기능은 NTFS 드라이버가 아닌 Android 파일 제공자 연동입니다.");
        explanation.setTextSize(14);
        explanation.setPadding(0, dp(12), 0, dp(16));
        column.addView(explanation);

        selectButton = new Button(this);
        selectButton.setAllCaps(false);
        selectButton.setText("파일 선택하고 복사");
        selectButton.setOnClickListener(v -> pickSources());
        column.addView(selectButton);

        cancelButton = new Button(this);
        cancelButton.setAllCaps(false);
        cancelButton.setText("현재 작업 취소");
        cancelButton.setEnabled(false);
        cancelButton.setOnClickListener(v -> {
            cancel.set(true);
            showStatus("취소 요청됨. 현재 USB 입출력 종료 후 정리합니다.");
        });
        column.addView(cancelButton);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        column.addView(progress);

        status = new TextView(this);
        status.setTextSize(14);
        status.setTextIsSelectable(true);
        status.setPadding(0, dp(16), 0, dp(16));
        column.addView(status);
        showStatus("원본 파일은 삭제하지 않습니다. 파일별 SHA-256 재검증 후 저장합니다.");

        TextView caution = new TextView(this);
        caution.setText("주의: Android 또는 다른 앱이 NTFS를 쓰기 불가로 연결했다면 " +
                "폴더 선택만으로 쓰기가 활성화되지 않습니다. " +
                "전송 도중 USB를 빼면 임시 파일이 남거나 데이터가 손상될 수 있습니다.");
        caution.setTextSize(12);
        caution.setTextColor(0xFF7B3E14);
        caution.setPadding(0, dp(20), 0, 0);
        column.addView(caution);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(column);
        setContentView(scroll);
    }

    private int dp(int n) {
        return (int) (getResources().getDisplayMetrics().density * n + 0.5f);
    }

    private void showStatus(String text) {
        if (status != null) status.setText(text);
    }

    private void pickSources() {
        if (copying) return;
        sources.clear();
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(pick, REQ_SOURCES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || copying) return;
        if (requestCode == REQ_SOURCES) {
            sources.clear();
            Set<String> distinct = new HashSet<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    if (uri != null && distinct.add(uri.toString())) sources.add(uri);
                }
            } else if (data.getData() != null) {
                sources.add(data.getData());
            }
            if (sources.isEmpty()) {
                showStatus("선택된 파일이 없습니다.");
                return;
            }
            if (sources.size() > MAX_FILES) {
                sources.clear();
                showStatus("안전을 위해 한 번에 최대 " + MAX_FILES + "개 파일만 선택해 주세요.");
                return;
            }
            Intent choose = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            choose.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            showStatus(sources.size() + "개 파일 선택됨. 쓰기가 가능한 NTFS USB 폴더를 선택하세요.");
            startActivityForResult(choose, REQ_TARGET);
        } else if (requestCode == REQ_TARGET) {
            Uri targetTree = data.getData();
            if (targetTree == null || sources.isEmpty()) {
                showStatus("대상 USB 폴더가 선택되지 않았습니다.");
                return;
            }
            final int granted = data.getFlags() &
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                // Persist when the provider allows it. Current activity grants suffice otherwise.
                getContentResolver().takePersistableUriPermission(targetTree, granted);
            } catch (SecurityException | IllegalArgumentException ignored) {
                // A transient permission may still be valid for this one transfer.
            }
            beginCopy(targetTree);
        }
    }

    private void beginCopy(Uri tree) {
        if (copying) return;
        copying = true;
        cancel.set(false);
        selectButton.setEnabled(false);
        cancelButton.setEnabled(true);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        ArrayList<Uri> selection = new ArrayList<>(sources);
        worker.execute(() -> {
            int completed = 0;
            try {
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree,
                        DocumentsContract.getTreeDocumentId(tree));
                ensureWritableDirectory(parent);
                for (Uri source : selection) {
                    if (cancel.get()) throw new InterruptedIOException("사용자 취소");
                    String fileName = safeName(getDisplayName(source));
                    String mimeType = getContentResolver().getType(source);
                    if (mimeType == null || mimeType.isEmpty()) mimeType = "application/octet-stream";
                    final int index = completed + 1;
                    final String currentName = fileName;
                    runOnUiThread(() -> showStatus(index + "/" + selection.size() +
                            " 복사 및 검증: " + currentName));
                    long sourceSize = getSourceLength(source);
                    runOnUiThread(() -> {
                        progress.setIndeterminate(sourceSize <= 0);
                        progress.setMax(1000);
                        progress.setProgress(0);
                    });
                    copyOne(parent, source, fileName, mimeType, sourceSize);
                    completed++;
                }
                final int successful = completed;
                runOnUiThread(() -> showStatus("완료: " + successful +
                        "개 파일. 모든 파일을 읽어 SHA-256 검증했습니다. 원본은 그대로 유지합니다."));
            } catch (Exception e) {
                final int successful = completed;
                final String details = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> showStatus("중단: " + successful + "/" + selection.size() +
                        "개 완료. " + details + "\n미완료 파일의 임시 데이터 정리를 시도했습니다."));
            } finally {
                copying = false;
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    selectButton.setEnabled(true);
                    cancelButton.setEnabled(false);
                });
            }
        });
    }

    private void ensureWritableDirectory(Uri parent) throws IOException {
        try (Cursor cursor = getContentResolver().query(parent,
                new String[] { DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_FLAGS }, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                throw new IOException("대상 폴더 정보를 읽지 못했습니다. USB 연결 앱을 확인하세요.");
            }
            String mime = cursor.getString(0);
            int flags = cursor.getInt(1);
            if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(mime) ||
                    (flags & DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) == 0) {
                throw new IOException("대상 제공자가 폴더 쓰기를 허용하지 않습니다. " +
                        "NTFS를 읽기·쓰기 모드로 다시 연결하세요.");
            }
        }
    }

    private boolean childNameExists(Uri parent, String name) throws IOException {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(parent,
                DocumentsContract.getDocumentId(parent));
        try (Cursor cursor = getContentResolver().query(children,
                new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME },
                null, null, null)) {
            if (cursor == null) throw new IOException("대상 폴더의 파일 목록을 읽지 못했습니다.");
            while (cursor.moveToNext()) {
                if (name.equalsIgnoreCase(cursor.getString(0))) return true;
            }
        }
        return false;
    }

    private void copyOne(Uri parent, Uri source, String name, String mimeType, long declaredSize) throws IOException {
        if (childNameExists(parent, name)) {
            throw new IOException("덮어쓰기 방지: 같은 이름의 대상 파일이 존재합니다: " + name);
        }
        // Keep under common 255-character filename limits, including the suffix.
        String fragment = name.length() > 170 ? name.substring(0, 170) : name;
        String partialName = "." + fragment + ".partial-" + Long.toHexString(System.nanoTime());
        Uri temporary = null;
        boolean renamed = false;
        try {
            temporary = DocumentsContract.createDocument(getContentResolver(),
                    parent, mimeType, partialName);
            if (temporary == null) throw new IOException("임시 대상 파일 생성이 거부되었습니다.");
            VerifiedStreamCopy.Result written;
            try (InputStream in = getContentResolver().openInputStream(source);
                 OutputStream out = getContentResolver().openOutputStream(temporary, "w")) {
                if (in == null || out == null) throw new IOException("입출력 스트림을 열지 못했습니다.");
                final long[] lastShownAt = {0L};
                written = VerifiedStreamCopy.copy(in, out, cancel, bytes -> {
                    long now = System.nanoTime();
                    if (now - lastShownAt[0] < 250_000_000L) return;
                    lastShownAt[0] = now;
                    runOnUiThread(() -> {
                        if (declaredSize > 0) {
                            int fraction = (int) Math.min(1000.0, bytes * 1000.0 / declaredSize);
                            progress.setProgress(fraction);
                        }
                        showStatus("복사: " + name + "\\n전송 " + bytes + "바이트" +
                                (declaredSize >= 0 ? " / " + declaredSize + "바이트" : ""));
                    });
                });
                if (declaredSize >= 0 && written.bytes != declaredSize) {
                    throw new IOException("원본 크기와 복사된 크기가 다릅니다: " +
                            written.bytes + " / " + declaredSize);
                }
            }
            runOnUiThread(() -> {
                progress.setIndeterminate(true);
                showStatus("SHA-256 재검증 중: " + name +
                        "\\nUSB 파일을 다시 읽어 기록 내용을 비교합니다.");
            });
            VerifiedStreamCopy.Result readBack;
            try (InputStream in = getContentResolver().openInputStream(temporary)) {
                if (in == null) throw new IOException("USB에 기록한 파일을 다시 읽지 못했습니다.");
                readBack = VerifiedStreamCopy.hash(in, cancel);
            }
            VerifiedStreamCopy.verify(written, readBack);
            if (cancel.get()) throw new InterruptedIOException("사용자 취소");
            if (childNameExists(parent, name)) {
                throw new IOException("검증 중 같은 이름의 파일이 생겼습니다. 덮어쓰지 않습니다.");
            }
            Uri finished = DocumentsContract.renameDocument(getContentResolver(), temporary, name);
            if (finished == null) throw new IOException("검증된 파일의 최종 이름 변경 실패");
            renamed = true;
            String actual = getDisplayName(finished);
            if (!name.equals(actual)) {
                throw new IOException("복사는 완료되었지만 제공자가 다른 이름으로 저장했습니다: " + actual);
            }
        } catch (SecurityException e) {
            throw new IOException("문서 제공자의 쓰기 또는 읽기 권한이 없습니다.", e);
        } catch (RuntimeException e) {
            throw new IOException("USB 문서 제공자 동작 실패: " + e.getMessage(), e);
        } finally {
            if (!renamed && temporary != null) {
                try {
                    DocumentsContract.deleteDocument(getContentResolver(), temporary);
                } catch (Exception cleanupFailure) {
                    android.util.Log.w("NtfsSafCopy", "Temporary file cleanup failed", cleanupFailure);
                }
            }
        }
    }

    private long getSourceLength(Uri source) {
        try (Cursor cursor = getContentResolver().query(source,
                new String[] { OpenableColumns.SIZE }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
        } catch (RuntimeException ignored) { }
        return -1;
    }

    private String getDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri,
                new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME },
                null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        } catch (Exception ignored) {}
        return uri.getLastPathSegment() == null ? "file" : uri.getLastPathSegment();
    }

    private static String safeName(String name) throws IOException {
        if (name == null || name.trim().isEmpty()) throw new IOException("파일명이 없습니다.");
        String fixed = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (fixed.isEmpty() || ".".equals(fixed) || "..".equals(fixed) ||
                fixed.length() > 240 || fixed.endsWith(".") || fixed.endsWith(" ")) {
            throw new IOException("NTFS에서 사용하기 어려운 파일명: " + name);
        }
        return fixed;
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        ArrayList<String> values = new ArrayList<>();
        for (Uri uri : sources) values.add(uri.toString());
        state.putStringArrayList("sources", values);
        super.onSaveInstanceState(state);
    }

    @Override
    public void onBackPressed() {
        if (copying) {
            cancel.set(true);
            showStatus("취소 요청됨. 현재 전송 종료 후 정리합니다.");
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        cancel.set(true);
        worker.shutdown();
        super.onDestroy();
    }
}
