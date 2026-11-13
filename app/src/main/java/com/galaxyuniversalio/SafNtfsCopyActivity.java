package com.galaxyuniversalio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
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
import java.nio.charset.StandardCharsets;
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
    private static final int REQ_EXPORT_REPORT = 3104;
    private static final int REQ_WRITE_PROBE = 3105;
    private static final String RECOVERY_PREFS = "ntfs_transfer_journal_v1";
    private static final AtomicBoolean PROCESS_TRANSFER_ACTIVE = new AtomicBoolean(false);
    private static final int MAX_FILES = 128;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancel = new AtomicBoolean();
    private final ArrayList<Uri> sources = new ArrayList<>();
    private TextView status;
    private ProgressBar progress;
    private Button selectButton, cancelButton, recoveryButton, auditButton, probeButton;
    private volatile String lastAudit;
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

    private GradientDrawable surface(int color, int radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) d.setStroke(dp(1), strokeColor);
        return d;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void buildUi() {
        final int bg = 0xFFF6F8FC;
        final int navy = 0xFF122B51;
        final int slate = 0xFF53647B;
        final int teal = 0xFF0D7869;
        int pad = dp(18);

        LinearLayout column = new LinearLayout(this);
        column.setPadding(pad, dp(20), pad, dp(30));
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(bg);

        TextView heading = text("USB 파일 전송", 27, navy);
        heading.setPadding(dp(2), dp(4), 0, dp(8));
        column.addView(heading);

        TextView sub = text("Galaxy Universal I/O · MASTER 0.11", 12, slate);
        sub.setPadding(dp(2), 0, 0, dp(14));
        column.addView(sub);

        TextView badge = text("  NTFS 연결 제공자 연동  ·  루팅 불필요  ", 12, teal);
        badge.setBackground(surface(0xFFE8F6F1, 12, 0xFFC4E8DB));
        badge.setPadding(dp(10), dp(7), dp(10), dp(7));
        column.addView(badge);

        TextView section = text("시작하기", 17, navy);
        section.setPadding(dp(2), dp(20), 0, dp(10));
        column.addView(section);

        LinearLayout guide = new LinearLayout(this);
        guide.setOrientation(LinearLayout.VERTICAL);
        guide.setPadding(dp(15), dp(12), dp(15), dp(12));
        guide.setBackground(surface(0xFFFFFFFF, 15, 0xFFE1E7EF));

        TextView line1 = text("①  NTFS 읽기·쓰기 제공자에서 USB 연결", 14, navy);
        line1.setPadding(0, dp(4), 0, dp(8));
        guide.addView(line1);
        TextView line2 = text("②  USB 폴더 쓰기 진단으로 먼저 확인", 14, navy);
        line2.setPadding(0, 0, 0, dp(8));
        guide.addView(line2);
        TextView line3 = text("③  휴대전화 파일 선택 → USB 대상 폴더 선택", 14, navy);
        line3.setPadding(0, 0, 0, dp(4));
        guide.addView(line3);
        column.addView(guide);

        TextView help = text("삼성 '내 파일'의 NTFS 읽기 전용 폴더로는 쓰기가 불가능합니다. " +
                "별도 NTFS 읽기·쓰기 제공자를 먼저 연결해야 합니다.", 13, 0xFF8A501C);
        help.setPadding(dp(4), dp(10), dp(4), dp(9));
        column.addView(help);

        TextView showGuide = text("삼성 NTFS 읽기 전용 안내 자세히 보기", 13, 0xFF2459A6);
        showGuide.setPadding(dp(5), dp(5), dp(5), dp(10));
        showGuide.setOnClickListener(v -> explainSamsungReadOnly());
        column.addView(showGuide);

        probeButton = new Button(this);
        probeButton.setAllCaps(false);
        probeButton.setText("먼저: USB 쓰기 가능 여부 진단 (8KB)");
        probeButton.setTextSize(15);
        probeButton.setTextColor(0xFF145F56);
        probeButton.setBackground(surface(0xFFE4F4EE, 12, 0xFFB4DCCC));
        probeButton.setOnClickListener(v -> confirmProbe());
        column.addView(probeButton);

        TextView link = text("NTFS 연결 제공자 설치 안내 · 공식 프로젝트 열기", 13, 0xFF2459A6);
        link.setPadding(dp(6), dp(10), dp(6), dp(12));
        link.setOnClickListener(v -> {
            Intent browser = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/moylali/OTGMaster/releases"));
            startActivity(browser);
        });
        column.addView(link);

        selectButton = new Button(this);
        selectButton.setAllCaps(false);
        selectButton.setText("파일 선택하고 복사 시작");
        selectButton.setTextSize(16);
        selectButton.setTextColor(0xFFFFFFFF);
        selectButton.setPadding(dp(10), dp(13), dp(10), dp(13));
        selectButton.setBackground(surface(0xFF2459A6, 13, 0));
        selectButton.setOnClickListener(v -> pickSources());
        column.addView(selectButton);

        cancelButton = new Button(this);
        cancelButton.setAllCaps(false);
        cancelButton.setText("전송 중단");
        cancelButton.setTextSize(14);
        cancelButton.setTextColor(0xFFB5473F);
        cancelButton.setBackground(surface(0xFFFFF1F0, 12, 0xFFEEC5C0));
        cancelButton.setVisibility(View.GONE);
        cancelButton.setEnabled(false);
        cancelButton.setOnClickListener(v -> {
            cancel.set(true);
            cancelButton.setEnabled(false);
            showStatus("취소 요청됨. 현재 USB 입출력 종료 후 임시 파일 정리를 시도합니다.");
        });
        column.addView(cancelButton);

        recoveryButton = new Button(this);
        recoveryButton.setAllCaps(false);
        recoveryButton.setText("중단된 임시 파일 확인 및 정리");
        recoveryButton.setTextColor(0xFF7B4C0B);
        recoveryButton.setBackground(surface(0xFFFFEDD2, 12, 0xFFEFCB87));
        recoveryButton.setOnClickListener(v -> confirmRecovery());
        recoveryButton.setVisibility(View.GONE);
        column.addView(recoveryButton);

        auditButton = new Button(this);
        auditButton.setAllCaps(false);
        auditButton.setText("SHA-256 검증 보고서 내보내기");
        auditButton.setTextColor(navy);
        auditButton.setBackground(surface(0xFFE9EEF6, 12, 0xFFCAD6E8));
        auditButton.setVisibility(View.GONE);
        auditButton.setEnabled(false);
        auditButton.setOnClickListener(v -> exportAudit());
        column.addView(auditButton);

        TextView statusTitle = text("전송 상태", 16, navy);
        statusTitle.setPadding(dp(2), dp(20), 0, dp(9));
        column.addView(statusTitle);

        LinearLayout statusPanel = new LinearLayout(this);
        statusPanel.setOrientation(LinearLayout.VERTICAL);
        statusPanel.setPadding(dp(14), dp(12), dp(14), dp(13));
        statusPanel.setBackground(surface(0xFFFFFFFF, 15, 0xFFE1E7EF));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        statusPanel.addView(progress);

        status = text("파일을 선택하면 전송 상태가 표시됩니다.\n원본 파일은 삭제하지 않습니다.", 13, slate);
        status.setTextIsSelectable(true);
        status.setPadding(dp(2), dp(6), 0, dp(5));
        statusPanel.addView(status);
        column.addView(statusPanel);

        TextView caution = text("안전 안내\nNTFS를 읽기 전용으로 연결한 경우 이 앱으로 쓰기를 강제할 수 없습니다. " +
                "파일 검증이 끝나더라도 USB는 안전하게 분리하세요.", 12, 0xFF8A501C);
        caution.setPadding(dp(13), dp(12), dp(13), dp(12));
        caution.setBackground(surface(0xFFFFF4E4, 12, 0xFFF6DEB5));
        column.addView(caution);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
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

    private void explainSamsungReadOnly() {
        new AlertDialog.Builder(this)
                .setTitle("삼성 내 파일에서 NTFS 복사가 안 될 때")
                .setMessage("갤럭시 기본 파일 관리자가 NTFS USB를 읽기 전용으로 처리하면 " +
                        "파일을 보거나 휴대전화로 복사하는 기능과 달리 USB에 새 파일을 쓸 수 없습니다.\n\n" +
                        "해결 절차:\n1. NTFS USB의 중요한 파일을 PC에 백업\n" +
                        "2. NTFS 읽기·쓰기를 제공하는 별도 앱에서 USB를 연결\n" +
                        "3. 이 앱의 'USB 쓰기 가능 여부 진단'에서 그 앱의 USB 폴더 선택\n" +
                        "4. 8KB 검사 통과 후 실제 파일 복사\n\n" +
                        "진단 통과는 선택한 폴더의 쓰기 성공만 의미하며, NTFS 드라이버 자체를 이 앱이 가진 것은 아닙니다.")
                .setPositiveButton("확인", (dialog, which) -> {})
                .show();
    }

    private void pickSources() {
        if (copying || PROCESS_TRANSFER_ACTIVE.get()) {
            showStatus("다른 전송 작업이 아직 종료되지 않았습니다.");
            return;
        }
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
        if (requestCode == REQ_WRITE_PROBE) {
            beginWriteProbe(data);
            return;
        }
        if (requestCode == REQ_EXPORT_REPORT) {
            writeAuditDocument(data.getData());
            return;
        }
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

    private void confirmProbe() {
        if (copying || PROCESS_TRANSFER_ACTIVE.get()) {
            showStatus("전송 작업이 진행 중이므로 진단을 시작할 수 없습니다.");
            return;
        }
        if (readPending() != null) {
            showStatus("기존 임시 파일 복구 기록을 먼저 정리하세요.");
            refreshRecoveryUi();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("USB 쓰기 실험 안내")
                .setMessage("선택한 USB 폴더에 무작위 내용의 약 8KB 임시 파일을 만든 다음 " +
                        "재읽어 SHA-256을 검증하고 삭제합니다.\n\n" +
                        "기존 파일은 덮어쓰지 않습니다. USB 분리·전원 장애·파일 제공자 오류가 " +
                        "발생하면 임시 파일이 남을 수 있습니다. 중요한 자료를 백업한 USB에서만 시도하세요.\n\n" +
                        "주의: 휴대전화 내부 저장공간을 선택해도 NTFS 지원이 입증되지는 않습니다.")
                .setNegativeButton("취소", (dialog, which) -> {})
                .setPositiveButton("폴더 선택", (dialog, which) -> {
                    Intent choose = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    choose.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivityForResult(choose, REQ_WRITE_PROBE);
                }).show();
    }

    private void beginWriteProbe(Intent data) {
        Uri tree = data.getData();
        if (tree == null) return;
        int permissionFlags = data.getFlags() &
                (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        int required = Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        if ((permissionFlags & required) != required) {
            showStatus("선택한 대상의 읽기·쓰기 권한을 받지 못했습니다. 삼성 NTFS 읽기 전용 경로일 수 있습니다.");
            return;
        }
        if (copying || !PROCESS_TRANSFER_ACTIVE.compareAndSet(false, true)) {
            showStatus("다른 전송 또는 진단이 실행 중입니다.");
            return;
        }
        if (readPending() != null) {
            PROCESS_TRANSFER_ACTIVE.set(false);
            showStatus("복구 기록이 남아 있어 진단을 중단했습니다.");
            refreshRecoveryUi();
            return;
        }

        copying = true;
        cancel.set(false);
        selectButton.setEnabled(false);
        probeButton.setEnabled(false);
        recoveryButton.setEnabled(false);
        cancelButton.setVisibility(View.VISIBLE);
        cancelButton.setEnabled(true);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        showStatus("선택한 저장소 제공자와 쓰기 가능 여부를 검사합니다.");
        worker.execute(() -> {
            String report;
            try {
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree,
                        DocumentsContract.getTreeDocumentId(tree));
                final String treeId = DocumentsContract.getTreeDocumentId(tree);
                final String authority = tree.getAuthority();
                final boolean directory;
                final int flags;
                try (Cursor c = getContentResolver().query(parent,
                        new String[] {DocumentsContract.Document.COLUMN_MIME_TYPE,
                                DocumentsContract.Document.COLUMN_FLAGS},null,null,null)) {
                    if (c == null || !c.moveToFirst()) {
                        throw new IOException("대상 폴더의 기능 정보를 읽지 못했습니다.");
                    }
                    directory = DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(0));
                    flags = c.getInt(1);
                }
                NtfsProviderAdvisor.Assessment check =
                        NtfsProviderAdvisor.assess(authority, treeId, directory, flags);
                if (check.definitelyInternal) {
                    throw new IOException(check.title+"\n"+check.details);
                }
                if (!check.providerAllowsCreate) {
                    throw new IOException(check.title+"\\n"+check.details);
                }
                NtfsWriteProbe.Result result = NtfsWriteProbe.execute(new NtfsWriteProbe.Backend() {
                    @Override public void assertWritableDirectory() throws IOException {
                        ensureWritableDirectory(parent);
                    }
                    @Override public void journal(String partialName) throws IOException {
                        recordPending(tree, partialName, partialName);
                    }
                    @Override public String createTemporary(String name) throws IOException {
                        Uri created = DocumentsContract.createDocument(getContentResolver(),
                                parent,"application/octet-stream",name);
                        return created == null ? null : created.toString();
                    }
                    @Override public void assertWritableFile(String id) throws IOException {
                        requireDocumentWritable(Uri.parse(id));
                    }
                    @Override public OutputStream openWrite(String id) throws IOException {
                        return getContentResolver().openOutputStream(Uri.parse(id),"wt");
                    }
                    @Override public InputStream openRead(String id) throws IOException {
                        return getContentResolver().openInputStream(Uri.parse(id));
                    }
                    @Override public boolean deleteTemporary(String id) throws IOException {
                        return DocumentsContract.deleteDocument(getContentResolver(),Uri.parse(id));
                    }
                    @Override public boolean clearJournal() {return clearPending();}
                    @Override public void cleanupError(Exception e) {
                        android.util.Log.w("NtfsWriteProbe","Cleanup failed; journal retained",e);
                    }
                },cancel);
                report = "쓰기 진단 성공: " + result.bytes +
                        "바이트 임시 파일 기록·SHA-256 재읽기 검증·삭제 완료.\n" +
                        "선택한 제공자: " + (authority == null ? "알 수 없음" : authority) +
                        "\n" + check.details +
                        "\n실제 NTFS 형식과 안전 제거 이후 데이터 영속성은 확인되지 않았습니다.";
            } catch (Exception e) {
                report = "쓰기 진단 실패: " + e.getMessage() +
                        "\n" + NtfsFailureAdvice.nextStep(e.toString()) +
                        "\n임시 파일 복구 메뉴가 표시되면 확인 후 정리하세요.";
            } finally {
                copying = false;
                PROCESS_TRANSFER_ACTIVE.set(false);
            }
            final String finalReport = report;
            runOnUiThread(() -> {
                showStatus(finalReport);
                progress.setVisibility(View.GONE);
                selectButton.setEnabled(true);
                probeButton.setEnabled(true);
                cancelButton.setEnabled(false);
                cancelButton.setVisibility(View.GONE);
                refreshRecoveryUi();
            });
        });
    }

    private void beginCopy(Uri tree) {
        if (copying || !PROCESS_TRANSFER_ACTIVE.compareAndSet(false, true)) {
            showStatus("현재 다른 USB 전송이 실행 중입니다. 완료 또는 취소 후 다시 시도하세요.");
            return;
        }
        if (readPending() != null) {
            PROCESS_TRANSFER_ACTIVE.set(false);
            showStatus("이전 임시 전송 기록을 먼저 확인해야 새 전송을 시작할 수 있습니다.");
            refreshRecoveryUi();
            return;
        }
        copying = true;
        cancel.set(false);
        lastAudit = null;
        auditButton.setEnabled(false);
        selectButton.setEnabled(false);
        recoveryButton.setEnabled(false);
        probeButton.setEnabled(false);
        cancelButton.setEnabled(true);
        cancelButton.setVisibility(View.VISIBLE);
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        ArrayList<Uri> selection = new ArrayList<>(sources);
        worker.execute(() -> {
            int completed = 0;
            String failureSummary = null;
            NtfsTransferAudit audit = new NtfsTransferAudit();
            try {
                Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree,
                        DocumentsContract.getTreeDocumentId(tree));
                ensureWritableDirectory(parent);
                // A phone destination could pass write tests while not being the USB.
                NtfsProviderAdvisor.Assessment destination = NtfsProviderAdvisor.assess(
                        tree.getAuthority(), DocumentsContract.getTreeDocumentId(tree),
                        true, DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE);
                if (destination.definitelyInternal) {
                    throw new IOException("USB 폴더가 아니라 휴대전화 내부 저장공간을 선택했습니다.");
                }
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
                    SafVerifiedTransaction.Receipt receipt =
                            copyOne(tree, parent, source, fileName, mimeType, sourceSize);
                    audit.success(fileName, receipt);
                    completed++;
                }
                final int successful = completed;
                runOnUiThread(() -> showStatus("완료: " + successful +
                        "개 파일. 모든 파일을 읽어 SHA-256 검증했습니다. 원본은 그대로 유지합니다."));
            } catch (Exception e) {
                final int successful = completed;
                final String details = e.getClass().getSimpleName() + ": " + e.getMessage();
                failureSummary = details;
                runOnUiThread(() -> showStatus("중단: " + successful + "/" + selection.size() +
                        "개 완료. " + details + "\n" +
                        NtfsFailureAdvice.nextStep(details) +
                        "\n이미 저장된 파일은 자동 삭제하지 않습니다. 대상 파일과 복구 메뉴를 확인하세요."));
            } finally {
                lastAudit = audit.finish(selection.size(), completed == selection.size(),
                        failureSummary);
                copying = false;
                PROCESS_TRANSFER_ACTIVE.set(false);
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    selectButton.setEnabled(true);
                    probeButton.setEnabled(true);
                    cancelButton.setEnabled(false);
                    cancelButton.setVisibility(View.GONE);
                    auditButton.setEnabled(lastAudit != null);
                    auditButton.setVisibility(lastAudit == null ? View.GONE : View.VISIBLE);
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

    private SafVerifiedTransaction.Receipt copyOne(Uri tree, Uri parent, Uri source, String name,
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
                    @Override public InputStream openPublishedForRead(String id) throws IOException {
                        return getContentResolver().openInputStream(Uri.parse(id));
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
        return receipt;
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
        recoveryButton.setEnabled(!copying && !PROCESS_TRANSFER_ACTIVE.get() && old != null);
    }

    private void confirmRecovery() {
        Pending old = readPending();
        if (copying || PROCESS_TRANSFER_ACTIVE.get() || old == null) return;
        new AlertDialog.Builder(this)
                .setTitle("중단된 전송 파일 정리")
                .setMessage("이전 작업의 임시 파일 [" + old.partialName +
                        "]만 찾아 정리합니다. 실제 저장된 다른 파일과 원본은 삭제하지 않습니다." +
                        "\n대상 폴더를 다시 선택한 다음 삭제 여부를 한 번 더 확인합니다.")
                .setNeutralButton("기록만 해제", (dialog, which) -> confirmAbandonRecovery())
                .setNegativeButton("취소", (dialog, which) -> { })
                .setPositiveButton("대상 폴더 선택", (dialog, which) -> {
                    Intent choose = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    choose.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivityForResult(choose, REQ_RECOVER);
                }).show();
    }

    private void confirmAbandonRecovery() {
        Pending old = readPending();
        if (copying || PROCESS_TRANSFER_ACTIVE.get() || old == null) return;
        new AlertDialog.Builder(this)
                .setTitle("임시 파일을 삭제하지 않고 기록만 해제")
                .setMessage("USB가 분리됐다가 재연결되면 저장소 제공자의 폴더 주소가 바뀔 수 있습니다.\n\n" +
                        "이 기능은 USB 안의 파일을 전혀 삭제하지 않고 앱 내부의 복구 기록만 지웁니다." +
                        " 실제 USB에 임시 파일이나 이미 복사한 파일이 남아 있을 수 있으므로 " +
                        "PC 또는 연결 앱에서 파일들을 직접 확인해야 합니다.\n\n" +
                        "확인되지 않은 USB 파일이 있으면 '취소'를 선택하세요.")
                .setNegativeButton("취소", (dialog, which) -> {})
                .setPositiveButton("기록만 해제", (dialog, which) -> {
                    if (clearPending()) {
                        showStatus("앱의 복구 기록만 해제했습니다. USB에 남은 파일은 삭제·검증하지 않았습니다.");
                    } else {
                        showStatus("복구 기록 해제에 실패했습니다.");
                    }
                    refreshRecoveryUi();
                }).show();
    }

    private void handleRecoverySelection(Intent data) {
        if (PROCESS_TRANSFER_ACTIVE.get()) {
            showStatus("전송이 진행 중이므로 임시 파일 복구를 실행할 수 없습니다.");
            return;
        }
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
                // A rename may have succeeded even if its binder reply was lost.
                // Never assume absence of a staging filename implies the final copy is absent.
                boolean publishedMaybeExists = !old.partialName.equals(old.intendedName) &&
                        childNameExists(parent, old.intendedName);
                if (publishedMaybeExists) {
                    new AlertDialog.Builder(this)
                            .setTitle("최종 파일이 존재할 가능성")
                            .setMessage("임시 파일은 보이지 않지만 최종 파일명 [" + old.intendedName +
                                    "]이 발견됐습니다. 이름 변경이 이미 완료됐을 수 있습니다. " +
                                    "파일을 자동 삭제하지 않습니다. 파일 내용과 해시를 확인한 후에만 복구 기록을 지우세요.")
                            .setNegativeButton("기록 유지", (dialog, which) -> {})
                            .setPositiveButton("확인 후 기록 해제", (dialog, which) -> {
                                if (clearPending()) showStatus("확인 후 복구 기록만 해제했습니다. 기존 파일은 삭제하지 않았습니다.");
                                else showStatus("복구 기록 삭제 실패. 대상 파일은 건드리지 않았습니다.");
                                refreshRecoveryUi();
                            }).show();
                } else {
                    if (clearPending()) showStatus("남아 있는 임시 파일이 없어 기록만 정리했습니다.");
                    else showStatus("임시 파일은 없지만 복구 기록을 지우지 못했습니다.");
                    refreshRecoveryUi();
                }
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
        if (copying || !PROCESS_TRANSFER_ACTIVE.compareAndSet(false, true)) return;
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
                PROCESS_TRANSFER_ACTIVE.set(false);
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

    private void exportAudit() {
        if (copying || lastAudit == null) return;
        Intent choose = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        choose.addCategory(Intent.CATEGORY_OPENABLE);
        choose.setType("text/plain");
        choose.putExtra(Intent.EXTRA_TITLE, "GalaxyUniversalIO-M0.11-NTFS-SHA256.txt");
        choose.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(choose, REQ_EXPORT_REPORT);
    }

    private void writeAuditDocument(Uri destination) {
        if (destination == null || copying) return;
        final String content = lastAudit;
        if (content == null) return;
        auditButton.setEnabled(false);
        showStatus("검증 보고서를 저장하는 중입니다.");
        worker.execute(() -> {
            String finalStatus;
            try (OutputStream out = getContentResolver().openOutputStream(destination, "wt")) {
                if (out == null) throw new IOException("문서 제공자가 출력 스트림을 열지 않았습니다.");
                out.write(content.getBytes(StandardCharsets.UTF_8));
                out.flush();
                finalStatus = "검증 보고서를 TXT로 저장했습니다. USB 자료의 실제 영구 기록은 별도 확인이 필요합니다.";
            } catch (Exception e) {
                finalStatus = "검증 보고서 저장 실패: " + e.getMessage() +
                        "\n선택한 대상 파일에 일부 내용이 남아 있을 수 있습니다.";
            }
            String result = finalStatus;
            runOnUiThread(() -> {
                showStatus(result);
                auditButton.setEnabled(lastAudit != null);
            });
        });
    }

    private void requireDocumentWritable(Uri uri) throws IOException {
        try (Cursor c = getContentResolver().query(uri,
                new String[]{DocumentsContract.Document.COLUMN_FLAGS},null,null,null)) {
            if (c == null || !c.moveToFirst() ||
                    (c.getInt(0)&DocumentsContract.Document.FLAG_SUPPORTS_WRITE)==0) {
                throw new IOException("임시 파일이 기록을 지원하지 않습니다. NTFS 제공자 권한을 확인하세요.");
            }
        }
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
