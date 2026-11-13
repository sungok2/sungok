package com.galaxyuniversalio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
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
import java.util.List;
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
    private static final int REQ_RECOVER = 3103;
    private static final String RECOVERY_PREFS = "ntfs_transfer_journal_v1";
    private static final int MAX_FILES = 128;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancel = new AtomicBoolean();
    private final ArrayList<Uri> sources = new ArrayList<>();
    private TextView status;
    private ProgressBar progress;
    private Button selectButton, cancelButton, recoveryButton;
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

        recoveryButton = new Button(this);
        recoveryButton.setAllCaps(false);
        recoveryButton.setText("중단된 임시 전송 파일 정리");
        recoveryButton.setOnClickListener(v -> confirmRecovery());
        column.addView(recoveryButton);

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
        refreshRecoveryUi();
    }

    private int dp(int n) {
        return (int) (getResources().getDisplayMetrics().density * n + 0.5f);
    }

    private void showStatus(String text) {
        if (status != null) status.setText(text);
    }

    private void pickSources() {
        if (copying) return;
        if (readPending() != null) {
            showStatus("지난 전송의 임시 파일 기록이 남아 있습니다. 먼저 복구 메뉴를 확인하세요.");
            refreshRecoveryUi();
            return;
        }
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
        if (requestCode == REQ_RECOVER) {
            handleRecoverySelection(data);
            return;
        }
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
            final int required = Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            if ((granted & required) != required) {
                showStatus("대상 폴더에 읽기와 쓰기 권한이 모두 필요합니다. NTFS 마운트 상태를 확인하세요.");
                return;
            }
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
        if (readPending() != null) {
            showStatus("이전 임시 전송 기록을 먼저 확인해야 새 전송을 시작할 수 있습니다.");
            refreshRecoveryUi();
            return;
        }
        copying = true;
        cancel.set(false);
        selectButton.setEnabled(false);
        recoveryButton.setEnabled(false);
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
                // Validate the entire batch before touching any destination files.
                List<String> plannedNames = new ArrayList<>();
                for (Uri source : selection) {
                    plannedNames.add(NtfsNamePolicy.requireValid(getDisplayName(source)));
                }
                NtfsNamePolicy.requireDistinctNames(plannedNames);
                for (Uri source : selection) {
                    if (cancel.get()) throw new InterruptedIOException("사용자 취소");
                    String fileName = plannedNames.get(completed);
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
                    copyOne(tree, parent, source, fileName, mimeType, sourceSize);
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
                    refreshRecoveryUi();
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

    private void copyOne(Uri tree, Uri parent, Uri source, String name,
                         String mimeType, long declaredSize) throws IOException {
        final long[] lastShown = {0L};
        SafVerifiedTransaction.Receipt receipt = SafVerifiedTransaction.execute(
                new SafVerifiedTransaction.Backend() {
                    @Override public void ensureFinalAbsent(String requested) throws IOException {
                        if (childNameExists(parent, requested)) {
                            throw new IOException("덮어쓰기 방지: 대상에 같은 이름이 있습니다: " + requested);
                        }
                    }
                    @Override public void journal(String temporaryName, String requested) throws IOException {
                        recordPending(tree, temporaryName, requested);
                    }
                    @Override public String createTemporary(String temporaryName) throws IOException {
                        Uri created = DocumentsContract.createDocument(getContentResolver(), parent,
                                mimeType, temporaryName);
                        if (created == null) return null;
                        String actualName = getDisplayName(created);
                        if (!actualName.equals(temporaryName) &&
                                !actualName.startsWith(temporaryName + ".")) {
                            // The URI is valid, so return it; next capability check will run.
                            // Any remainder is covered by deleteTemporary in execute's finally.
                            android.util.Log.w("NtfsSafCopy", "Provider modified staging filename");
                        }
                        return created.toString();
                    }
                    @Override public void requireWritableAndRenameable(String id) throws IOException {
                        requireRenameAndWriteSupport(Uri.parse(id));
                    }
                    @Override public InputStream openSource() throws IOException {
                        return getContentResolver().openInputStream(source);
                    }
                    @Override public OutputStream openTemporaryForWrite(String id) throws IOException {
                        // "w" is not guaranteed to truncate; "wt" requests truncation.
                        return getContentResolver().openOutputStream(Uri.parse(id), "wt");
                    }
                    @Override public InputStream openTemporaryForRead(String id) throws IOException {
                        return getContentResolver().openInputStream(Uri.parse(id));
                    }
                    @Override public void beforeReadback() {
                        runOnUiThread(() -> {
                            progress.setIndeterminate(true);
                            showStatus("SHA-256 재검증 중: " + name +
                                    "\nUSB 파일을 처음부터 다시 읽어 기록 내용을 비교합니다.");
                        });
                    }
                    @Override public String rename(String id, String requested) throws IOException {
                        Uri result = DocumentsContract.renameDocument(getContentResolver(),
                                Uri.parse(id), requested);
                        return result == null ? null : result.toString();
                    }
                    @Override public String displayName(String id) {
                        return getDisplayName(Uri.parse(id));
                    }
                    @Override public boolean deleteTemporary(String id) throws IOException {
                        return DocumentsContract.deleteDocument(getContentResolver(), Uri.parse(id));
                    }
                    @Override public boolean clearJournal() {
                        return clearPending();
                    }
                    @Override public void cleanupError(Exception e) {
                        android.util.Log.w("NtfsSafCopy", "Partial cleanup incomplete; journal retained", e);
                    }
                }, name, declaredSize, cancel, bytes -> {
                    long now = System.nanoTime();
                    if (now - lastShown[0] < 250_000_000L) return;
                    lastShown[0] = now;
                    runOnUiThread(() -> {
                        if (declaredSize > 0) {
                            progress.setProgress((int) Math.min(1000.0,
                                    bytes * 1000.0 / declaredSize));
                        }
                        showStatus("복사: " + name + "\n전송 " + bytes + "바이트" +
                                (declaredSize >= 0 ? " / " + declaredSize : ""));
                    });
                });
        android.util.Log.i("NtfsSafCopy", "Verified bytes=" + receipt.bytes +
                " sha256=" + receipt.sha256 + " (actual durable media flush not guaranteed)");
    }

    private static final class Pending {
        final String tree, partialName, intendedName;
        Pending(String tree, String partialName, String intendedName) {
            this.tree = tree;
            this.partialName = partialName;
            this.intendedName = intendedName;
        }
    }

    private Pending readPending() {
        SharedPreferences p = getSharedPreferences(RECOVERY_PREFS, MODE_PRIVATE);
        String tree = p.getString("tree", null);
        String partial = p.getString("partial", null);
        String intended = p.getString("name", null);
        if (tree == null || partial == null || intended == null) return null;
        return new Pending(tree, partial, intended);
    }

    private void recordPending(Uri tree, String partial, String intended) throws IOException {
        if (readPending() != null) throw new IOException("Unresolved earlier transfer");
        boolean saved = getSharedPreferences(RECOVERY_PREFS, MODE_PRIVATE).edit()
                .putString("tree", tree.toString())
                .putString("partial", partial)
                .putString("name", intended)
                .commit();
        if (!saved) throw new IOException("Could not durably save partial-transfer recovery record");
    }

    private boolean clearPending() {
        return getSharedPreferences(RECOVERY_PREFS, MODE_PRIVATE).edit().clear().commit();
    }

    private void refreshRecoveryUi() {
        if (recoveryButton == null) return;
        Pending old = readPending();
        recoveryButton.setVisibility(old == null ? View.GONE : View.VISIBLE);
        recoveryButton.setEnabled(!copying && old != null);
    }

    private void confirmRecovery() {
        Pending old = readPending();
        if (copying || old == null) return;
        new AlertDialog.Builder(this)
                .setTitle("중단된 전송 파일 정리")
                .setMessage("이전 작업의 임시 파일 [" + old.partialName +
                        "]만 찾아 정리합니다. 실제 저장된 다른 파일과 원본은 삭제하지 않습니다." +
                        "\n대상 폴더를 다시 선택한 다음 삭제 여부를 한 번 더 확인합니다.")
                .setNegativeButton("취소", (dialog, which) -> { })
                .setPositiveButton("대상 폴더 선택", (dialog, which) -> {
                    Intent choose = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    choose.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivityForResult(choose, REQ_RECOVER);
                }).show();
    }

    private void handleRecoverySelection(Intent data) {
        Pending old = readPending();
        Uri chosen = data.getData();
        if (old == null || chosen == null) return;
        if (!old.tree.equals(chosen.toString())) {
            showStatus("이전 전송과 다른 대상 폴더입니다. 삭제하지 않았습니다.");
            return;
        }
        final int needed = Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        if ((data.getFlags() & needed) != needed) {
            showStatus("복구하려면 기존 대상 폴더의 읽기·쓰기 권한이 필요합니다.");
            return;
        }
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(chosen,
                DocumentsContract.getTreeDocumentId(chosen));
        final Uri match;
        final String displayName;
        try {
            Uri childListing = DocumentsContract.buildChildDocumentsUriUsingTree(parent,
                    DocumentsContract.getDocumentId(parent));
            Uri found = null;
            String actual = null;
            int matching = 0;
            try (Cursor cursor = getContentResolver().query(childListing,
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                if (cursor == null) throw new IOException("대상 파일 목록을 읽을 수 없습니다.");
                while (cursor.moveToNext()) {
                    String documentId = cursor.getString(0);
                    String name = cursor.getString(1);
                    // Allow MIME-appended suffix only. Never match unrelated filenames.
                    if (name != null && (name.equals(old.partialName) ||
                            name.startsWith(old.partialName + "."))) {
                        matching++;
                        actual = name;
                        found = DocumentsContract.buildDocumentUriUsingTree(parent, documentId);
                    }
                }
            }
            if (matching == 0) {
                if (clearPending()) showStatus("남아 있는 임시 파일이 없어 기록을 정리했습니다.");
                else showStatus("임시 파일은 없지만 복구 기록을 지우지 못했습니다.");
                refreshRecoveryUi();
                return;
            }
            if (matching != 1) {
                showStatus("임시 파일 후보가 여러 개여서 자동 정리하지 않았습니다.");
                return;
            }
            match = found;
            displayName = actual;
        } catch (Exception e) {
            showStatus("임시 파일 검사 실패: " + e.getMessage());
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("임시 파일 삭제 확인")
                .setMessage("이 파일 하나만 삭제합니다:\n" + displayName +
                        "\n\n원래 저장하려던 이름: " + old.intendedName)
                .setNegativeButton("삭제 안 함", (dialog, which) -> { })
                .setPositiveButton("임시 파일 삭제", (dialog, which) ->
                        deleteStagedRecoveryFile(match)).show();
    }

    private void deleteStagedRecoveryFile(Uri item) {
        if (copying) return;
        copying = true;
        selectButton.setEnabled(false);
        recoveryButton.setEnabled(false);
        cancelButton.setEnabled(false);
        progress.setIndeterminate(true);
        progress.setVisibility(View.VISIBLE);
        worker.execute(() -> {
            String message;
            try {
                boolean removed = DocumentsContract.deleteDocument(getContentResolver(), item);
                if (!removed) throw new IOException("USB 제공자가 임시 파일 삭제에 실패했습니다.");
                if (!clearPending()) throw new IOException("삭제 후 복구 기록 저장에 실패했습니다.");
                message = "임시 파일을 삭제했습니다. 기존 원본 파일은 변경하지 않았습니다.";
            } catch (Exception e) {
                message = "복구 실패: " + e.getMessage() +
                        "\n기록이 남아 있으므로 연결 상태를 확인하고 다시 시도하세요.";
            } finally {
                copying = false;
            }
            final String resultMessage = message;
            runOnUiThread(() -> {
                showStatus(resultMessage);
                progress.setVisibility(View.GONE);
                selectButton.setEnabled(true);
                refreshRecoveryUi();
            });
        });
    }

    private void requireRenameAndWriteSupport(Uri uri) throws IOException {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[] { DocumentsContract.Document.COLUMN_FLAGS }, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                throw new IOException("생성된 USB 파일의 기능 정보를 확인할 수 없습니다.");
            }
            int flags = cursor.getInt(0);
            int required = DocumentsContract.Document.FLAG_SUPPORTS_WRITE |
                    DocumentsContract.Document.FLAG_SUPPORTS_RENAME;
            if ((flags & required) != required) {
                throw new IOException("USB 파일 제공자가 쓰기 또는 이름 변경을 지원하지 않습니다.");
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
