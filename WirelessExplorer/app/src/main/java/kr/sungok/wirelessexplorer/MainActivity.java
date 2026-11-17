package kr.sungok.wirelessexplorer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.widget.EditText;
import android.widget.CheckBox;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

/** Offline-first discovery console. Device count is explicitly endpoint count. */
public final class MainActivity extends Activity implements WirelessScanner.Listener {
    private static final int REQ_PERMS = 120;
    private static final int REQ_EXPORT = 121;
    private static final int BG = Color.rgb(11, 17, 32);
    private static final int SURFACE = Color.rgb(23, 33, 53);
    private static final int ACCENT = Color.rgb(64, 220, 184);
    private static final int WHITE = Color.rgb(241, 246, 252);
    private static final int SUBTLE = Color.rgb(159, 176, 197);
    private static final int BORDER = Color.rgb(48, 65, 89);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat time = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.KOREA);
    private DeviceStore store;
    private WirelessScanner scanner;
    private TextView status, counter, coverage, scanButton, sessionButton, progress, unknownButton;
    private CheckBox lanToggle;
    private LinearLayout list, filterBar;
    private String filter = "ALL";
    private String search = "";
    private String exportFormat = "csv";
    private boolean unknownOnly;
    private boolean extendedLan;
    private int visibleLimit = 60;
    private long sessionStartElapsed;
    private boolean sessionOnly = true;
    private boolean pendingStart;
    private boolean exportingJson;
    private boolean refreshScheduled;
    private boolean persistScheduled;
    private final Map<String,String> radioStates = new LinkedHashMap<>();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        store = new DeviceStore(this);
        scanner = new WirelessScanner(this, this);
        buildUi();
        render();
        if (!store.lastPersistenceError().isEmpty()) status.setText(store.lastPersistenceError());
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(18), dp(16), dp(18), dp(20));
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsets.Type.systemBars());
                root.setPadding(dp(18), dp(12) + insets.top,
                        dp(18), dp(20) + insets.bottom);
                return windowInsets;
            });
        }
        setContentView(root);
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        root.addView(scroller, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(page);

        TextView eyebrow = text("WIRELESS  /  EXPLORER", 12, ACCENT, true);
        eyebrow.setLetterSpacing(0.15f);
        page.addView(eyebrow);
        page.addView(space(7));
        TextView heading = text("주변 무선기기 탐색", 27, WHITE, true);
        page.addView(heading);
        page.addView(space(8));
        TextView subtitle = text("갤럭시 기반 · 로컬 기록 · 기기별 증거 중심 목록", 13, SUBTLE, false);
        page.addView(subtitle);
        page.addView(space(20));

        LinearLayout summary = panel();
        page.addView(summary);
        counter = text("무선 0건 · 서비스 0건 · 누적 0건", 18, WHITE, true);
        summary.addView(counter);
        summary.addView(space(7));
        TextView caution = text("실제 기기 대수가 아닌 무선 주소·네트워크 서비스 항목 수입니다. 중복 가능성을 고려하세요.", 12, SUBTLE, false);
        summary.addView(caution);
        summary.addView(space(11));
        status = text("준비 완료 · 탐색 시작을 눌러주세요", 13, ACCENT, false);
        summary.addView(status);
        summary.addView(space(8));
        progress = text("탐색 대기", 13, ACCENT, true);
        summary.addView(progress);
        summary.addView(space(12));
        coverage = text("BLE · 대기\nBT · 대기\nWIFI · 대기\nP2P · 대기\nNSD · 대기\nUPNP · 선택사항", 12, SUBTLE, false);
        coverage.setLineSpacing(dp(3), 1.1f);
        summary.addView(coverage);

        page.addView(space(12));
        lanToggle = new CheckBox(this);
        lanToggle.setText("UPnP 로컬 네트워크 확장 탐색 (요청 전송)");
        lanToggle.setTextColor(SUBTLE);
        lanToggle.setTextSize(13);
        lanToggle.setButtonTintList(ColorStateList.valueOf(ACCENT));
        lanToggle.setContentDescription("연결된 로컬 네트워크에 UPnP 표준 탐색 요청 전송 여부");
        lanToggle.setOnCheckedChangeListener((button, checked) -> extendedLan = checked);
        page.addView(lanToggle);
        page.addView(space(15));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(actions);
        scanButton = action("탐색 시작", true, () -> {
            if (scanner.isRunning()) stopSession();
            else requestAndStart();
        });
        actions.addView(scanButton, new LinearLayout.LayoutParams(0, dp(50), 1f));
        addHorizontalSpace(actions, 10);
        actions.addView(action("CSV 내보내기", false, () -> export(false)),
                new LinearLayout.LayoutParams(0, dp(50), 1f));

        page.addView(space(9));
        LinearLayout more = new LinearLayout(this);
        more.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(more);
        more.addView(action("JSON 내보내기", false, () -> export(true)),
                new LinearLayout.LayoutParams(0, dp(44), 1f));
        addHorizontalSpace(more, 10);
        more.addView(action("위치 설정 열기", false,
                () -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))),
                new LinearLayout.LayoutParams(0, dp(44), 1f));
        page.addView(space(9));
        page.addView(action("탐색 진단 보고서(.txt) 저장", false, this::exportReport),
                new LinearLayout.LayoutParams(-1, dp(44)));
        page.addView(space(9));
        page.addView(action("저장된 탐지 기록 전체 삭제", false, this::confirmDelete),
                new LinearLayout.LayoutParams(-1, dp(40)));
        page.addView(space(21));

        TextView section = text("발견 항목", 19, WHITE, true);
        page.addView(section);
        page.addView(space(10));
        EditText searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setInputType(InputType.TYPE_CLASS_TEXT);
        searchBox.setTextSize(14);
        searchBox.setTextColor(WHITE);
        searchBox.setHintTextColor(SUBTLE);
        searchBox.setHint("이름·주소·상세정보 검색");
        searchBox.setPadding(dp(14), dp(11), dp(14), dp(11));
        searchBox.setBackground(shape(SURFACE, 14, BORDER));
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                search = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                visibleLimit = 60;
                render();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        page.addView(searchBox, new LinearLayout.LayoutParams(-1, dp(46)));
        page.addView(space(9));
        sessionButton = action("이번 탐색만 표시", true, () -> {
            sessionOnly = !sessionOnly;
            sessionButton.setText(sessionOnly ? "이번 탐색만 표시" : "과거 탐지 포함");
            sessionButton.setBackground(shape(sessionOnly ? SURFACE : BORDER, 14, BORDER));
            render();
        });
        page.addView(sessionButton, new LinearLayout.LayoutParams(-1, dp(40)));
        page.addView(space(8));
        unknownButton = action("미확인 기기·미분류만 보기: 끔", false, () -> {
            unknownOnly = !unknownOnly;
            unknownButton.setText("미확인 기기·미분류만 보기: " + (unknownOnly ? "켬" : "끔"));
            visibleLimit = 60;
            render();
        });
        page.addView(unknownButton, new LinearLayout.LayoutParams(-1, dp(40)));
        page.addView(space(8));

        HorizontalScrollView filters = new HorizontalScrollView(this);
        filters.setHorizontalScrollBarEnabled(false);
        page.addView(filters);
        filterBar = new LinearLayout(this);
        filterBar.setOrientation(LinearLayout.HORIZONTAL);
        filters.addView(filterBar);
        buildFilters();
        page.addView(space(12));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        page.addView(list);

        page.addView(space(15));
        TextView footer = text("탐색 범위: BLE · Bluetooth Classic · Wi-Fi · Wi-Fi Direct · mDNS\n"
                + "확인할 수 없는 기기: 비송신 기기, 스텔스/전용 RF, 일부 비콘, 검색 비응답 기기\n"
                + "앱이 열려 있는 동안만 탐색하며 접속·페어링·데이터 업로드는 하지 않습니다.",
                12, SUBTLE, false);
        footer.setLineSpacing(dp(3), 1.1f);
        page.addView(footer);
    }

    private void buildFilters() {
        filterBar.removeAllViews();
        String[][] choices = {{"ALL","전체"}, {"BLE","BLE"}, {"BT","블루투스"},
                {"WIFI","Wi-Fi"}, {"P2P","Wi-Fi Direct"}, {"NSD","mDNS"}, {"UPNP","UPnP"}};
        for (String[] choice : choices) {
            String id = choice[0];
            boolean selected = id.equals(filter);
            TextView chip = text(choice[1], 13, selected ? BG : SUBTLE, selected);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(13), dp(9), dp(13), dp(9));
            chip.setBackground(shape(selected ? ACCENT : SURFACE, 999, selected ? ACCENT : BORDER));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(37));
            lp.rightMargin = dp(7);
            filterBar.addView(chip, lp);
            chip.setOnClickListener(v -> {
                filter = id;
                visibleLimit = 60;
                buildFilters();
                render();
            });
        }
    }

    private void requestAndStart() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            need(missing, Manifest.permission.BLUETOOTH_SCAN);
            need(missing, Manifest.permission.BLUETOOTH_CONNECT);
        }
        need(missing, Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) need(missing, Manifest.permission.NEARBY_WIFI_DEVICES);
        if (!missing.isEmpty()) {
            pendingStart = true;
            requestPermissions(missing.toArray(new String[0]), REQ_PERMS);
            return;
        }
        startSession();
    }

    private void need(List<String> list, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) list.add(permission);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode != REQ_PERMS || !pendingStart) return;
        pendingStart = false;
        if (grants.length == 0) {
            status.setText("권한 결과를 확인할 수 없습니다");
            return;
        }
        int denied = 0;
        for (int grant : grants) if (grant != PackageManager.PERMISSION_GRANTED) denied++;
        if (denied > 0) {
            Toast.makeText(this, "일부 권한 거부 · 허용된 무선 방식만 탐색합니다",
                    Toast.LENGTH_LONG).show();
        }
        startSession();
    }

    private void startSession() {
        store.beginSession();
        radioStates.clear();
        visibleLimit = 60;
        sessionStartElapsed = SystemClock.elapsedRealtime();
        scanner.setExtendedLan(extendedLan);
        lanToggle.setEnabled(false);
        sessionOnly = true;
        sessionButton.setText("이번 탐색만 표시");
        sessionButton.setBackground(shape(SURFACE, 14, BORDER));
        scanner.start();
        scanButton.setText("탐색 중지");
        status.setText("탐색 중 · 검색 결과 누적 수집");
        updateProgress();
        render();
    }

    private void stopSession() {
        scanner.stop();
        scanButton.setText("탐색 시작");
        lanToggle.setEnabled(true);
        if (!store.save()) status.setText(store.lastPersistenceError());
        updateProgress();
        render();
    }

    @Override public void onDevice(String protocol, String name, String id,
                                   String details, int rssi, boolean fresh) {
        store.observe(protocol, name, id, details, rssi, fresh);
        if (!persistScheduled) {
            persistScheduled = true;
            main.postDelayed(() -> {
                persistScheduled = false;
                store.save();
            }, 12_000);
        }
        if (!refreshScheduled) {
            refreshScheduled = true;
            main.postDelayed(() -> {
                refreshScheduled = false;
                render();
            }, 1600);
        }
    }

    @Override public void onStatus(String message) {
        if (status != null) status.setText(message);
        if (scanner != null && !scanner.isRunning() && scanButton != null) {
            scanButton.setText("탐색 시작");
            if (lanToggle != null) lanToggle.setEnabled(true);
            if (store != null && !store.save()) status.setText(store.lastPersistenceError());
        }
    }

    private void render() {
        if (counter == null || list == null) return;
        counter.setText("무선 " + store.countLiveRadioEndpoints() + "건  ·  로컬서비스 "
                + store.countLiveNetworkServices() + "건  ·  누적 " + store.total() + "건");
        renderCoverage();
        list.removeAllViews();
        int shown = 0;
        int matching = 0;
        for (DeviceRecord item : store.all()) {
            if (sessionOnly && !item.live) continue;
            if (!filter.equals("ALL") && !item.protocol.equals(filter)) continue;
            if (unknownOnly && !"이름 없는 기기".equals(item.name)
                    && !"숨김 Wi-Fi".equals(item.name)
                    && !SignalRules.category(item.protocol, item.name).contains("미분류")) continue;
            if (!search.isEmpty()) {
                String index = (item.name + " " + item.address + " " + item.details
                        + " " + item.protocol).toLowerCase(Locale.ROOT);
                if (!index.contains(search)) continue;
            }
            matching++;
            if (shown >= visibleLimit) continue;
            list.addView(card(item));
            list.addView(space(9));
            shown++;
        }
        if (matching == 0) {
            LinearLayout panel = panel();
            TextView t = text("탐지 결과가 없습니다.\n\n기기와 Wi-Fi·블루투스·위치 서비스를 켜고 탐색하세요. "
                    + "일부 기기는 검색 신호를 송출하지 않습니다.", 14, SUBTLE, false);
            t.setLineSpacing(dp(5), 1.05f);
            panel.addView(t);
            list.addView(panel);
        } else if (matching > shown) {
            final int remaining = matching - shown;
            TextView showMore = action("추가 60개 표시 · 남은 " + remaining + "개", false,
                    () -> { visibleLimit = Math.min(400, visibleLimit + 60); render(); });
            list.addView(showMore, new LinearLayout.LayoutParams(-1, dp(44)));
            list.addView(space(6));
            if (visibleLimit >= 400) list.addView(text(
                    "화면 표시 상한 400건 · 모든 결과는 CSV/JSON으로 내보낼 수 있습니다.",
                    12, SUBTLE, false));
        }
    }

    private View card(DeviceRecord d) {
        LinearLayout container = panel();
        container.setPadding(dp(15), dp(13), dp(15), dp(14));
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        container.addView(top);

        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        top.addView(titleStack, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView kind = text(d.protocol + "  /  " + SignalRules.category(d.protocol, d.name),
                11, ACCENT, true);
        titleStack.addView(kind);
        titleStack.addView(space(5));
        TextView title = text(d.name, 17, WHITE, true);
        title.setMaxLines(2);
        titleStack.addView(title);
        TextView signal = text(d.rssi < 0 ? d.rssi + " dBm\n" + SignalRules.strength(d.rssi) : "신호 미제공",
                12, d.rssi < 0 ? ACCENT : SUBTLE, true);
        signal.setGravity(Gravity.RIGHT);
        top.addView(signal);
        container.addView(space(10));
        TextView address = text(d.address, 12, SUBTLE, false);
        address.setTextIsSelectable(true);
        container.addView(address);
        container.addView(space(5));
        TextView detail = text(d.details, 12, SUBTLE, false);
        container.addView(detail);
        container.addView(space(8));
        String badge = d.live ? "이번 탐색에서 확인" :
                (d.confirmed ? "이전 탐색에서 확인" : "캐시 정보 · 현재 주변 미확인");
        String timestampLabel = d.lastConfirmedAt > 0L
                ? "최종 실탐지 " + time.format(new Date(d.lastConfirmedAt))
                : "캐시 수집 " + time.format(new Date(d.lastSeen));
        TextView meta = text(badge + "  ·  " + timestampLabel
                + "  ·  수집 " + d.observations + "회", 11, d.live ? ACCENT : SUBTLE, false);
        container.addView(meta);
        container.setOnLongClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("무선기기", d.name + "\n"
                        + d.protocol + " / " + d.address + "\n" + d.details));
                Toast.makeText(this, "기기 정보 복사됨", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        return container;
    }

    @Override public void onRadioState(String protocol, String state) {
        radioStates.put(protocol, state);
        renderCoverage();
    }

    private void renderCoverage() {
        if (coverage == null || store == null) return;
        StringBuilder b = new StringBuilder("무선 방식별 탐색 상태 · 이번 탐색에서 확인한 항목 수\n");
        String[] types = {"BLE", "BT", "WIFI", "P2P", "NSD", "UPNP"};
        List<DeviceRecord> items = store.all();
        for (String type : types) {
            int count = 0;
            for (DeviceRecord d : items) {
                if (d.live && type.equals(d.protocol)) count++;
            }
            b.append(type).append("  ").append(count).append("건  ·  ")
                    .append(radioStates.containsKey(type) ? radioStates.get(type) : "대기")
                    .append('\n');
        }
        coverage.setText(b.toString().trim());
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("탐지 기록 전체 삭제")
                .setMessage("저장된 모든 주소·서비스 탐지 이력이 삭제됩니다. 복구할 수 없습니다.")
                .setNegativeButton("취소", (dialog, which) -> { })
                .setPositiveButton("모두 삭제", (dialog, which) -> {
                    if (scanner.isRunning()) stopSession();
                    store.clear();
                    render();
                    Toast.makeText(this, "저장된 탐지 기록을 삭제했습니다",
                            Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private void updateProgress() {
        if (progress == null || scanner == null) return;
        if (!scanner.isRunning()) {
            progress.setText("탐색 종료 · 기록을 다시 확인하거나 보고서로 저장하세요");
            return;
        }
        long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - sessionStartElapsed);
        long secondsLeft = Math.max(0L, (ScanPlan.SESSION_MS - elapsed) / 1000L);
        progress.setText("탐색 진행 · 남은 시간 " + secondsLeft + "초  /  총 180초");
        main.postDelayed(this::updateProgress, 1000L);
    }

    private void exportReport() {
        exportFormat = "report";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "wireless-diagnostic-report.txt");
        startActivityForResult(intent, REQ_EXPORT);
    }

    private void export(boolean json) {
        exportingJson = json;
        exportFormat = json ? "json" : "csv";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType(json ? "application/json" : "text/csv");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, json ? "wireless-observations.json" : "wireless-observations.csv");
        startActivityForResult(intent, REQ_EXPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_EXPORT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("스트림을 열 수 없습니다");
            String content;
            if ("report".equals(exportFormat)) {
                content = InventoryReport.generate(
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(new Date()),
                        store.countLiveRadioEndpoints(), store.countLiveNetworkServices(),
                        store.total(), store.sharedBluetoothAddressCandidates(), radioStates,
                        "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")",
                        extendedLan);
            } else {
                content = exportingJson ? store.asJson() : store.asCsv();
            }
            out.write(content.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "탐색 기록 저장 완료", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "파일 저장 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onStop() {
        if (scanner != null && scanner.isRunning()) scanner.stop();
        if (store != null) store.save();
        super.onStop();
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (scanner != null) scanner.stop();
        super.onDestroy();
    }

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(16), dp(16), dp(16), dp(16));
        p.setBackground(shape(SURFACE, 17, BORDER));
        return p;
    }

    private GradientDrawable shape(int bg, int radius, int border) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(bg);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), border);
        return drawable;
    }

    private TextView action(String label, boolean primary, Runnable run) {
        TextView view = text(label, 14, primary ? BG : WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(shape(primary ? ACCENT : SURFACE, 13, primary ? ACCENT : BORDER));
        view.setOnClickListener(v -> run.run());
        return view;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private View space(int height) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return v;
    }

    private void addHorizontalSpace(LinearLayout p, int width) {
        View v = new View(this);
        p.addView(v, new LinearLayout.LayoutParams(dp(width), 1));
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
