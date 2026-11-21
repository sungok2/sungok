package kr.sungok.wirelessexplorer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.content.res.Resources;
import android.content.res.Configuration;
import android.widget.ProgressBar;
import android.graphics.PorterDuff;
import android.text.TextUtils;
import java.util.Collections;
import java.util.Comparator;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
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
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

/** Offline-first discovery console. Device count is explicitly endpoint count. */
public final class MainActivity extends Activity implements WirelessScanner.Listener {
    private static final int REQ_PERMS = 120;
    private static final int REQ_EXPORT = 121;
    private static final int REQ_IMPORT = 122;
    private static final int BG = Color.rgb(8, 17, 30);
    private static final int SURFACE = Color.rgb(18, 32, 49);
    private static final int ACCENT = Color.rgb(65, 211, 185);
    private static final int WHITE = Color.rgb(246, 250, 253);
    private static final int SUBTLE = Color.rgb(160, 179, 198);
    private static final int BORDER = Color.rgb(41, 62, 81);

    private static final int BLUE = Color.rgb(125, 168, 248);
    private static final int GOLD = Color.rgb(239, 195, 115);
    private static final int MUTED = Color.rgb(125, 150, 170);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat time = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.KOREA);
    private DeviceStore store;
    private WirelessScanner scanner;
    private TextView status, counter, coverage, scanButton, sessionButton, progress, unknownButton, targetSummary;
    private TextView heroNumber, soundButton, sortRecent, sortStrong, sortName;
    private ProgressBar sessionProgress;
    private ScanPulseView radar;
    private FeedbackController feedback;
    private final Set<String> expandedKeys = new HashSet<>();
    private int sortMode = 0;
    private boolean reduceMotion;
    private CheckBox lanToggle, lanProbeToggle;
    private LinearLayout advancedPanel;
    private TextView coverageToggle, toolToggle, readiness;
    private boolean coverageExpanded;
    private boolean toolsExpanded;
    private LinearLayout list, filterBar;
    private String filter = "ALL";
    private String search = "";
    private String exportFormat = "csv";
    private boolean unknownOnly;
    private boolean extendedLan;
    private boolean lanProbeEnabled;
    private String knownTargetInput = "";
    private KnownTargetCoverage.Result knownTargets;
    private int visibleLimit = 60;
    private long sessionStartElapsed;
    private boolean sessionOnly = false;
    private final Runnable countdownTick = this::updateProgress;
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
        feedback = new FeedbackController(this);
        reduceMotion = android.provider.Settings.Global.getFloat(getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        knownTargetInput = getPreferences(MODE_PRIVATE).getString("known_targets", "");
        knownTargets = KnownTargetCoverage.parse(knownTargetInput);
        buildUi();
        render();
        refreshReadiness();
        if (!store.lastPersistenceError().isEmpty()) status.setText(store.lastPersistenceError());
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(14), dp(16), dp(16));
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsets.Type.systemBars());
                root.setPadding(dp(16), dp(10) + insets.top,
                        dp(16), dp(16) + insets.bottom);
                return windowInsets;
            });
        }
        setContentView(root);
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setBackgroundColor(BG);
        root.addView(scroller, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);
        scroller.addView(page);

        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(toolbar);
        TextView monogram = text("W", 19, BG, true);
        monogram.setGravity(Gravity.CENTER);
        monogram.setBackground(shape(ACCENT, 13, ACCENT));
        toolbar.addView(monogram, new LinearLayout.LayoutParams(dp(42), dp(42)));
        addHorizontalSpace(toolbar, 11);
        LinearLayout appName = new LinearLayout(this);
        appName.setOrientation(LinearLayout.VERTICAL);
        toolbar.addView(appName, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView brand = text("WIRELESS EXPLORER", 11, ACCENT, true);
        brand.setLetterSpacing(.09f);
        appName.addView(brand);
        appName.addView(space(3));
        appName.addView(text("주변 기기 탐색", landscape ? 21 : 23, WHITE, true));

        soundButton = action(feedback.soundEnabled() ? "소리 켬" : "소리 끔", false, () -> {
            feedback.setSoundEnabled(this, !feedback.soundEnabled());
            soundButton.setText(feedback.soundEnabled() ? "소리 켬" : "소리 끔");
            soundButton.setContentDescription("탐색 음향 피드백 "
                    + (feedback.soundEnabled() ? "사용 중, 탭하여 끄기" : "사용 안 함, 탭하여 켜기"));
        });
        soundButton.setTextSize(12);
        soundButton.setContentDescription("탐색 음향 피드백 "
                + (feedback.soundEnabled() ? "사용 중, 탭하여 끄기" : "사용 안 함, 탭하여 켜기"));
        toolbar.addView(soundButton, new LinearLayout.LayoutParams(dp(84), dp(48)));
        page.addView(space(12));

        LinearLayout summary = panel();
        summary.setPadding(dp(15), dp(14), dp(15), dp(13));
        page.addView(summary);
        LinearLayout upper = new LinearLayout(this);
        upper.setGravity(Gravity.CENTER_VERTICAL);
        upper.setOrientation(LinearLayout.HORIZONTAL);
        summary.addView(upper);
        radar = new ScanPulseView(this);
        radar.setColors(ACCENT, BORDER);
        radar.setReduceMotion(reduceMotion);
        upper.addView(radar, new LinearLayout.LayoutParams(dp(106), dp(109)));
        addHorizontalSpace(upper, 8);
        LinearLayout numbers = new LinearLayout(this);
        numbers.setOrientation(LinearLayout.VERTICAL);
        upper.addView(numbers, new LinearLayout.LayoutParams(0, -2, 1f));
        numbers.addView(text("이번 탐색 · 무선 엔드포인트", 12, SUBTLE, false));
        numbers.addView(space(4));
        heroNumber = text("0", 36, WHITE, true);
        numbers.addView(heroNumber);
        numbers.addView(space(5));
        counter = text("LAN 0 · 서비스 0 · 누적 0", 12, SUBTLE, false);
        numbers.addView(counter);
        summary.addView(space(10));

        status = text("탐색 준비 완료", 13, WHITE, true);
        status.setMaxLines(3);
        summary.addView(status);
        summary.addView(space(6));
        progress = text("탐색을 시작해 주변 기기 목록을 만드세요", 12, SUBTLE, false);
        summary.addView(progress);
        summary.addView(space(10));
        sessionProgress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        sessionProgress.setIndeterminate(false);
        sessionProgress.setMax(180);
        sessionProgress.setProgress(0);
        sessionProgress.setProgressTintList(ColorStateList.valueOf(ACCENT));
        sessionProgress.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
        summary.addView(sessionProgress, new LinearLayout.LayoutParams(-1, dp(5)));
        summary.addView(space(9));

        targetSummary = text("", 12, GOLD, false);
        targetSummary.setVisibility(View.GONE);
        summary.addView(targetSummary);

        coverageToggle = text("진단 보기  ·  BLE / Wi-Fi / LAN 상태", 12, BLUE, true);
        coverageToggle.setPadding(0, dp(8), 0, dp(8));
        coverageToggle.setMinHeight(dp(48));
        coverageToggle.setGravity(Gravity.CENTER_VERTICAL);
        coverageToggle.setContentDescription("탐색 방식별 상태 및 권한 진단 펼치기");
        coverageToggle.setOnClickListener(v -> {
            coverageExpanded = !coverageExpanded;
            coverage.setVisibility(coverageExpanded ? View.VISIBLE : View.GONE);
            readiness.setVisibility(coverageExpanded ? View.VISIBLE : View.GONE);
            coverageToggle.setText(coverageExpanded
                    ? "진단 접기  ·  상태 확인 완료" : "진단 보기  ·  BLE / Wi-Fi / LAN 상태");
            coverageToggle.setContentDescription(coverageExpanded
                    ? "탐색 방식별 상태 및 권한 진단 접기" : "탐색 방식별 상태 및 권한 진단 펼치기");
            if (coverageExpanded) refreshReadiness();
        });
        summary.addView(coverageToggle);
        coverage = text("탐색을 시작하면 각 방식의 상태를 표시합니다.", 12, SUBTLE, false);
        coverage.setLineSpacing(dp(4), 1.1f);
        coverage.setVisibility(View.GONE);
        summary.addView(coverage);
        readiness = text("", 12, SUBTLE, false);
        readiness.setLineSpacing(dp(3), 1.1f);
        readiness.setVisibility(View.GONE);
        summary.addView(readiness);

        page.addView(space(11));
        LinearLayout scanActions = new LinearLayout(this);
        scanActions.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(scanActions);
        scanButton = action("탐색 시작", true, () -> {
            if (scanner.isRunning()) stopSession();
            else requestAndStart();
        });
        scanButton.setTextSize(16);
        scanActions.addView(scanButton, new LinearLayout.LayoutParams(0, dp(54), 1.5f));
        addHorizontalSpace(scanActions, 9);
        scanActions.addView(action("결과 저장", false, () -> export(false)),
                new LinearLayout.LayoutParams(0, dp(54), 1f));

        page.addView(space(9));
        LinearLayout lanRow = new LinearLayout(this);
        lanRow.setGravity(Gravity.CENTER_VERTICAL);
        lanRow.setPadding(dp(9), dp(4), dp(7), dp(4));
        lanRow.setBackground(shape(SURFACE, 12, BORDER));
        page.addView(lanRow);
        lanProbeToggle = new CheckBox(this);
        lanProbeToggle.setText(R.string.lan_probe_toggle);
        lanProbeToggle.setTextColor(WHITE);
        lanProbeToggle.setTextSize(13);
        lanProbeToggle.setButtonTintList(ColorStateList.valueOf(ACCENT));
        lanProbeToggle.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) {
                lanProbeEnabled = false;
                return;
            }
            if (lanProbeEnabled) return;
            // Active network ownership is not observable through Android APIs.
            // Require explicit informed authorization before traffic is generated.
            lanProbeToggle.setChecked(false);
            new AlertDialog.Builder(this)
                    .setTitle("LAN 탐색 권한 확인")
                    .setMessage("현재 연결된 Wi-Fi의 사설 IP 주소 최대 253개에 "
                            + "제한된 TCP 연결 요청 및 UPnP 탐색 요청을 전송합니다.\n\n"
                            + "네트워크에 기기 검색 트래픽이 발생합니다. "
                            + "본인이 소유하거나 조사 허가를 받은 네트워크에서만 사용하세요. "
                            + "TCP 응답이 없는 기기는 표시되지 않을 수 있습니다.")
                    .setNegativeButton("취소", (d, w) -> { })
                    .setPositiveButton("허가된 네트워크 · 활성화", (d, w) -> {
                        lanProbeEnabled = true;
                        lanProbeToggle.setChecked(true);
                    })
                    .show();
        });
        lanProbeToggle.setContentDescription("선택 기능: 연결된 허가된 사설 Wi-Fi LAN에서 "
                + "제한된 TCP 및 UPnP 탐색 요청 전송");
        lanRow.addView(lanProbeToggle, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView lanInfo = text("유·무선 포함 · 선택", 11, MUTED, false);
        lanRow.addView(lanInfo);
        page.addView(space(16));

        LinearLayout resultsTitle = new LinearLayout(this);
        resultsTitle.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(resultsTitle);
        resultsTitle.addView(text("발견 기기", 19, WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView legend = text("주소·서비스 기준", 11, SUBTLE, false);
        resultsTitle.addView(legend);
        page.addView(space(9));

        EditText searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setInputType(InputType.TYPE_CLASS_TEXT);
        searchBox.setTextSize(14);
        searchBox.setTextColor(WHITE);
        searchBox.setHintTextColor(SUBTLE);
        searchBox.setHint("이름 · IP · MAC · 제조사 ID 검색");
        searchBox.setContentDescription("발견 기기 이름 주소 상세정보 검색");
        searchBox.setPadding(dp(15), dp(10), dp(14), dp(10));
        searchBox.setBackground(shape(SURFACE, 12, BORDER));
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence x, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence x, int a, int b, int c) {
                search = x == null ? "" : x.toString().trim().toLowerCase(Locale.ROOT);
                visibleLimit = 60;
                render();
            }
            @Override public void afterTextChanged(Editable x) { }
        });
        page.addView(searchBox, new LinearLayout.LayoutParams(-1, dp(48)));
        page.addView(space(9));

        HorizontalScrollView filters = new HorizontalScrollView(this);
        filters.setHorizontalScrollBarEnabled(false);
        page.addView(filters);
        filterBar = new LinearLayout(this);
        filterBar.setOrientation(LinearLayout.HORIZONTAL);
        filters.addView(filterBar);
        buildFilters();
        page.addView(space(9));

        LinearLayout sortingRow = new LinearLayout(this);
        sortingRow.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(sortingRow);
        sortRecent = sortButton("최신순", 0);
        sortStrong = sortButton("신호순", 1);
        sortName = sortButton("이름순", 2);
        sortingRow.addView(sortRecent, new LinearLayout.LayoutParams(0, dp(48), 1f));
        addHorizontalSpace(sortingRow, 6);
        sortingRow.addView(sortStrong, new LinearLayout.LayoutParams(0, dp(48), 1f));
        addHorizontalSpace(sortingRow, 6);
        sortingRow.addView(sortName, new LinearLayout.LayoutParams(0, dp(48), 1f));
        page.addView(space(9));

        LinearLayout toggleRow = new LinearLayout(this);
        toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(toggleRow);
        sessionButton = action("과거 포함", false, () -> {
            sessionOnly = !sessionOnly;
            sessionButton.setText(sessionOnly ? "이번 탐색만" : "과거 포함");
            render();
        });
        sessionButton.setTextSize(12);
        toggleRow.addView(sessionButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        addHorizontalSpace(toggleRow, 8);
        unknownButton = action("미확인만: 끔", false, () -> {
            unknownOnly = !unknownOnly;
            unknownButton.setText(unknownOnly ? "미확인만: 켬" : "미확인만: 끔");
            visibleLimit = 60;
            render();
        });
        unknownButton.setTextSize(12);
        toggleRow.addView(unknownButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        page.addView(space(12));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        page.addView(list);

        page.addView(space(14));
        toolToggle = action("기록 · 검증 · 고급 설정  ▾", false, () -> {
            toolsExpanded = !toolsExpanded;
            advancedPanel.setVisibility(toolsExpanded ? View.VISIBLE : View.GONE);
            toolToggle.setText(toolsExpanded
                    ? "기록 · 검증 · 고급 설정  ▴" : "기록 · 검증 · 고급 설정  ▾");
        });
        page.addView(toolToggle, new LinearLayout.LayoutParams(-1, dp(48)));
        advancedPanel = new LinearLayout(this);
        advancedPanel.setOrientation(LinearLayout.VERTICAL);
        advancedPanel.setVisibility(View.GONE);
        advancedPanel.setPadding(0, dp(9), 0, dp(5));
        page.addView(advancedPanel);

        lanToggle = new CheckBox(this);
        lanToggle.setText(R.string.lan_opt_in);
        lanToggle.setTextSize(13);
        lanToggle.setTextColor(SUBTLE);
        lanToggle.setButtonTintList(ColorStateList.valueOf(ACCENT));
        lanToggle.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) {
                extendedLan = false;
                return;
            }
            if (extendedLan) return;
            lanToggle.setChecked(false);
            new AlertDialog.Builder(this)
                    .setTitle("UPnP 요청 전송 확인")
                    .setMessage("현재 연결된 허가된 Wi-Fi LAN에 SSDP 서비스 탐색 메시지를 "
                            + "전송합니다. 다른 네트워크의 허가 없이 사용하면 안 됩니다.")
                    .setNegativeButton("취소", (d, w) -> { })
                    .setPositiveButton("허가된 LAN · 활성화", (d, w) -> {
                        extendedLan = true;
                        lanToggle.setChecked(true);
                    })
                    .show();
        });
        lanToggle.setContentDescription("선택 기능: 연결된 사설 Wi-Fi에서 UPnP 서비스 검색 요청");
        advancedPanel.addView(lanToggle);
        advancedPanel.addView(space(8));
        CheckBox haptics = new CheckBox(this);
        haptics.setText("버튼 진동 피드백");
        haptics.setTextSize(13);
        haptics.setTextColor(SUBTLE);
        haptics.setButtonTintList(ColorStateList.valueOf(ACCENT));
        haptics.setChecked(feedback.hapticsEnabled());
        haptics.setOnCheckedChangeListener((button, checked) ->
                feedback.setHapticsEnabled(this, checked));
        advancedPanel.addView(haptics, new LinearLayout.LayoutParams(-1, dp(48)));
        advancedPanel.addView(space(8));
        LinearLayout exportRow = new LinearLayout(this);
        exportRow.setOrientation(LinearLayout.HORIZONTAL);
        exportRow.addView(action("JSON 백업", false, () -> export(true)),
                new LinearLayout.LayoutParams(0, dp(48), 1f));
        addHorizontalSpace(exportRow, 8);
        exportRow.addView(action("TXT 진단 보고서", false, this::exportReport),
                new LinearLayout.LayoutParams(0, dp(48), 1f));
        advancedPanel.addView(exportRow);
        advancedPanel.addView(space(8));
        advancedPanel.addView(action("JSON 기록 복원", false, this::importHistory),
                new LinearLayout.LayoutParams(-1, dp(48)));
        advancedPanel.addView(space(8));
        advancedPanel.addView(action("검증 기준 기기 관리", false, this::editKnownTargets),
                new LinearLayout.LayoutParams(-1, dp(48)));
        advancedPanel.addView(space(8));
        advancedPanel.addView(action("위치 서비스 설정", false,
                () -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))),
                new LinearLayout.LayoutParams(-1, dp(48)));
        advancedPanel.addView(space(8));
        advancedPanel.addView(action("모든 탐지 기록 삭제", false, this::confirmDelete),
                new LinearLayout.LayoutParams(-1, dp(48)));
        advancedPanel.addView(space(8));
        advancedPanel.addView(action("개인정보 · 탐색 권한 안내", false, this::showPrivacy),
                new LinearLayout.LayoutParams(-1, dp(48)));

        page.addView(space(17));
        TextView footer = text("탐색은 앱이 열려 있는 동안에만 진행합니다.\n"
                + "표시되는 것은 Android가 확인한 주소·서비스입니다. 실제 물리 기기 수, "
                + "위치 또는 전수 탐지 결과가 아닙니다.\n"
                + "서버 전송 없음 · 로그인 없음 · LAN 연결 요청은 사용자 선택",
                11, MUTED, false);
        footer.setLineSpacing(dp(4), 1f);
        page.addView(footer);
        page.addView(space(14));
        styleSortButtons();
    }

    private TextView sortButton(String label, int mode) {
        TextView chip = action(label, false, () -> {
            sortMode = mode;
            styleSortButtons();
            render();
        });
        chip.setTextSize(12);
        return chip;
    }

    private void styleSortButtons() {
        if (sortRecent == null || sortStrong == null || sortName == null) return;
        TextView[] controls = {sortRecent, sortStrong, sortName};
        for (int i = 0; i < controls.length; i++) {
            boolean selected = i == sortMode;
            controls[i].setTextColor(selected ? ACCENT : SUBTLE);
            controls[i].setBackground(shape(selected ? Color.rgb(27, 62, 66)
                    : SURFACE, 11, selected ? ACCENT : BORDER));
            controls[i].setSelected(selected);
        }
    }

    private void buildFilters() {
        filterBar.removeAllViews();
        String[][] choices = {{"ALL","전체"}, {"BLE","BLE"}, {"BT","블루투스"},
                {"WIFI","Wi-Fi"}, {"LAN","LAN 장치"}, {"P2P","Wi-Fi Direct"}, {"NSD","mDNS"}, {"UPNP","UPnP"}};
        for (String[] choice : choices) {
            String id = choice[0];
            boolean selected = id.equals(filter);
            int matchingRecords = 0;
            for (DeviceRecord record : store.all()) {
                if ((!sessionOnly || record.live)
                        && ("ALL".equals(id) || id.equals(record.protocol)))
                    matchingRecords++;
            }
            TextView chip = text(choice[1] + " " + matchingRecords,
                    12, selected ? BG : SUBTLE, selected);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(9), dp(14), dp(9));
            chip.setBackground(new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(BORDER),
                    shape(selected ? ACCENT : SURFACE, 999, selected ? ACCENT : BORDER), null));
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setContentDescription(choice[1] + " 기록 " + matchingRecords
                    + "건 필터" + (selected ? ", 선택됨" : ""));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(48));
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

    @Override protected void onResume() {
        super.onResume();
        refreshReadiness();
    }

    /** This preflight diagnoses missing discoverability prerequisites; it does not enable GPS. */
    private void refreshReadiness() {
        if (readiness == null) return;
        boolean hasBluetooth = false;
        boolean bluetoothOn = false;
        boolean btPermission = Build.VERSION.SDK_INT < 31
                || (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED);
        try {
            BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            hasBluetooth = adapter != null;
            bluetoothOn = btPermission && adapter != null && adapter.isEnabled();
        } catch (RuntimeException ignored) { }

        boolean hasWifi = false;
        boolean wifiOn = false;
        try {
            WifiManager manager = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            hasWifi = manager != null;
            wifiOn = manager != null && manager.isWifiEnabled();
        } catch (RuntimeException ignored) { }

        boolean locationOn = false;
        try {
            LocationManager location = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (location != null) {
                locationOn = Build.VERSION.SDK_INT >= 28
                        ? location.isLocationEnabled()
                        : (location.isProviderEnabled(LocationManager.GPS_PROVIDER)
                        || location.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
            }
        } catch (RuntimeException ignored) { }
        boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean wifiPermission = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
                == PackageManager.PERMISSION_GRANTED;
        boolean activeWifi = false;
        boolean activeVpn = false;
        try {
            ConnectivityManager connectivity = (ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity != null && connectivity.getActiveNetwork() != null) {
                NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(
                        connectivity.getActiveNetwork());
                if (capabilities != null) {
                    activeWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                    activeVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
                }
            }
        } catch (RuntimeException ignored) { }
        readiness.setText(getString(R.string.readiness_with_network,
                ScanReadiness.describe(hasBluetooth, bluetoothOn, hasWifi,
                        wifiOn, locationOn, fine, btPermission, wifiPermission),
                activeVpn ? getString(R.string.active_lan_vpn)
                        : activeWifi ? getString(R.string.active_lan_wifi)
                        : getString(R.string.active_lan_none)));
    }

    private void requestAndStart() {
        if (!getPreferences(MODE_PRIVATE).getBoolean("scan_disclosure_ack_v1", false)) {
            new AlertDialog.Builder(this)
                    .setTitle("무선기기 탐색 · 개인정보 안내")
                    .setMessage("탐색하려면 Android의 블루투스·Wi-Fi 및 정확한 위치 권한이 필요할 수 있습니다.\n\n"
                            + "이 앱은 주변에서 방송된 기기 이름, 블루투스/Wi-Fi 주소, "
                            + "수신 신호 강도, 발견 시각을 내 휴대전화에 기록합니다. "
                            + "주소는 주변 사람의 기기를 가리킬 수 있는 민감한 정보입니다.\n\n"
                            + "위치 권한은 Android 무선 검색을 위해 요청하며, GPS 좌표는 저장하지 않습니다. "
                            + "기본 탐색은 외부 서버로 데이터를 전송하지 않습니다.\n\n"
                            + "LAN TCP/UPnP 조사는 기본 꺼짐이고 별도로 선택해야 합니다. "
                            + "파일 내보내기는 사용자가 직접 결정합니다.")
                    .setNegativeButton("나중에", (dialog, which) ->
                            status.setText("권한 사전 안내 확인 전 · 탐색 시작 취소"))
                    .setPositiveButton("이해하고 계속", (dialog, which) -> {
                        getPreferences(MODE_PRIVATE).edit()
                                .putBoolean("scan_disclosure_ack_v1", true).apply();
                        requestAndStart();
                    })
                    .show();
            return;
        }
        refreshReadiness();
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            need(missing, Manifest.permission.BLUETOOTH_SCAN);
            need(missing, Manifest.permission.BLUETOOTH_CONNECT);
        }
        need(missing, Manifest.permission.ACCESS_COARSE_LOCATION);
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
        refreshReadiness();
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
        main.removeCallbacks(countdownTick);
        scanner.setExtendedLan(extendedLan || lanProbeEnabled);
        scanner.setLanProbeEnabled(lanProbeEnabled);
        lanToggle.setEnabled(false);
        lanProbeToggle.setEnabled(false);
        // Display old/cached records explicitly labeled rather than hiding them.
        sessionOnly = false;
        sessionButton.setText("과거 포함");
        sessionButton.setBackground(shape(SURFACE, 14, BORDER));
        scanner.start();
        radar.setScanning(true);
        feedback.start(scanButton);
        sessionProgress.setProgress(0);
        scanButton.setText("탐색 중지");
        status.setText("탐색 중 · 검색 결과 누적 수집");
        refreshReadiness();
        updateProgress();
        render();
    }

    private void stopSession() {
        scanner.stop();
        radar.setScanning(false);
        feedback.finish(scanButton);
        main.removeCallbacks(countdownTick);
        scanButton.setText("탐색 시작");
        lanToggle.setEnabled(true);
        lanProbeToggle.setEnabled(true);
        if (!store.save()) status.setText(store.lastPersistenceError());
        updateProgress();
        render();
    }

    @Override public void onDevice(String protocol, String name, String id,
                                   String details, int rssi, boolean fresh) {
        boolean newEvidence = fresh && !store.wasLiveThisSession(protocol, id);
        store.observe(protocol, name, id, details, rssi, fresh);
        if (newEvidence) feedback.foundNewEndpoint();
        if (!persistScheduled) {
            persistScheduled = true;
            main.postDelayed(() -> {
                persistScheduled = false;
                if (!store.save() && status != null)
                    status.setText(store.lastPersistenceError());
            }, 12_000);
        }
        if (!refreshScheduled) {
            refreshScheduled = true;
            main.postDelayed(() -> {
                refreshScheduled = false;
                render();
            }, 2300);
        }
    }

    @Override public void onStatus(String message) {
        if (status != null) status.setText(message);
        if (scanner != null && !scanner.isRunning() && scanButton != null) {
            if ("탐색 중지".contentEquals(scanButton.getText())) feedback.finish(scanButton);
            scanButton.setText("탐색 시작");
            if (radar != null) radar.setScanning(false);
            if (lanToggle != null) lanToggle.setEnabled(true);
            if (lanProbeToggle != null) lanProbeToggle.setEnabled(true);
            if (store != null && !store.save()) status.setText(store.lastPersistenceError());
        }
    }

    private void render() {
        if (counter == null || list == null) return;
        int rf = store.countLiveRadioEndpoints();
        int lan = store.countLiveLanHosts();
        int services = store.countLiveNetworkServices();
        heroNumber.setText(String.valueOf(rf));
        String countText = "LAN " + lan + "  ·  서비스 " + services
                + "  ·  누적 " + store.total();
        int overlap = store.sharedBluetoothAddressCandidates();
        counter.setText(overlap > 0
                ? countText + "\nBT/BLE 같은 주소 " + overlap + "쌍 · 동일 기기 미확정"
                : countText);
        if (radar != null) radar.setScanning(scanner != null && scanner.isRunning());
        renderCoverage();
        updateTargetCoverage();
        buildFilters();

        List<DeviceRecord> devices = store.all();
        if (sortMode == 1) {
            Collections.sort(devices, (a, b) -> {
                int signalA = a.rssi < 0 ? a.rssi : -999;
                int signalB = b.rssi < 0 ? b.rssi : -999;
                int order = Integer.compare(signalB, signalA);
                return order != 0 ? order : Long.compare(b.lastSeen, a.lastSeen);
            });
        } else if (sortMode == 2) {
            Collections.sort(devices, (a, b) -> {
                int compare = a.name.compareToIgnoreCase(b.name);
                return compare != 0 ? compare : Long.compare(b.lastSeen, a.lastSeen);
            });
        }
        list.removeAllViews();
        int shown = 0, matching = 0;
        for (DeviceRecord item : devices) {
            if (sessionOnly && !item.live) continue;
            if (!"ALL".equals(filter) && !filter.equals(item.protocol)) continue;
            if (unknownOnly && !item.name.equals("이름 없는 기기")
                    && !item.name.equals("숨김 Wi-Fi")
                    && !SignalRules.category(item.protocol, item.name).contains("미분류"))
                continue;
            if (!search.isEmpty()) {
                String searchIndex = (item.name + " " + item.address + " "
                        + item.details + " " + item.protocol).toLowerCase(Locale.ROOT);
                if (!searchIndex.contains(search)) continue;
            }
            matching++;
            if (shown >= visibleLimit) continue;
            list.addView(card(item));
            list.addView(space(9));
            shown++;
        }
        if (matching == 0) {
            LinearLayout empty = panel();
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPadding(dp(22), dp(24), dp(22), dp(24));
            TextView icon = text("◎", 40, BLUE, false);
            icon.setGravity(Gravity.CENTER);
            empty.addView(icon);
            empty.addView(space(8));
            TextView heading = text("조건에 맞는 항목이 없습니다", 16, WHITE, true);
            heading.setGravity(Gravity.CENTER);
            empty.addView(heading);
            empty.addView(space(9));
            String explanation = "검색 조건을 초기화하거나 새 탐색을 시작하세요. "
                    + "모든 기기가 무선 광고를 보내는 것은 아닙니다.";
            if ("WIFI".equals(filter))
                explanation = "Wi-Fi 스캔 결과가 제한됐을 수 있습니다. "
                        + "연결된 공유기 정보를 확인하고 '진단 보기'에서 AP 목록·요청·알림을 점검하세요.";
            else if ("LAN".equals(filter))
                explanation = "현재 Wi-Fi에 연결한 뒤 '연결된 LAN도 조사'를 켜세요. "
                        + "TCP 응답이 없는 기기는 표시되지 않습니다.";
            else if ("BT".equals(filter))
                explanation = "일반 블루투스 검색은 기기가 검색 가능한 상태여야 결과가 나타날 수 있습니다.";
            TextView hint = text(explanation, 12, SUBTLE, false);
            hint.setGravity(Gravity.CENTER);
            hint.setLineSpacing(dp(3), 1.08f);
            empty.addView(hint);
            list.addView(empty);
        } else if (matching > shown) {
            final int remaining = matching - shown;
            if (visibleLimit < 400) {
                TextView more = action("더 보기  ·  " + remaining + "건 남음", false, () -> {
                    visibleLimit = Math.min(400, visibleLimit + 60);
                    render();
                });
                list.addView(more, new LinearLayout.LayoutParams(-1, dp(46)));
                list.addView(space(6));
            }
            if (visibleLimit >= 400)
                list.addView(text("화면에는 최대 400건을 표시합니다. 전체는 CSV/JSON으로 저장하세요.",
                        12, SUBTLE, false));
        }
    }

    private int protocolTint(String protocol) {
        if ("BLE".equals(protocol)) return ACCENT;
        if ("WIFI".equals(protocol) || "P2P".equals(protocol)) return BLUE;
        if ("LAN".equals(protocol)) return GOLD;
        if ("BT".equals(protocol)) return Color.rgb(174, 155, 238);
        return MUTED;
    }

    private String displayProtocol(String protocol) {
        switch (protocol) {
            case "BLE": return "BLE 광고";
            case "BT": return "Bluetooth";
            case "WIFI": return "Wi-Fi AP";
            case "LAN": return "LAN TCP";
            case "P2P": return "Wi-Fi Direct";
            case "NSD": return "mDNS 서비스";
            case "UPNP": return "UPnP 서비스";
            default: return protocol;
        }
    }

    private String protocolMonogram(String protocol) {
        switch (protocol) {
            case "BLE": return "B";
            case "BT": return "BT";
            case "WIFI": return "W";
            case "LAN": return "IP";
            case "P2P": return "P";
            case "NSD": return "D";
            case "UPNP": return "U";
            default: return "•";
        }
    }

    private void copyDevice(DeviceRecord d) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    "Wireless Explorer: " + d.name, d.name + "\n" + d.protocol + " / "
                            + d.address + "\n" + d.details));
            Toast.makeText(this, "기기 정보가 복사됐습니다", Toast.LENGTH_SHORT).show();
        }
    }

    private View card(DeviceRecord d) {
        LinearLayout container = panel();
        container.setPadding(dp(14), dp(13), dp(14), dp(13));
        container.setClickable(true);
        container.setFocusable(true);
        container.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(BORDER), shape(SURFACE, 17, BORDER), null));
        boolean expanded = expandedKeys.contains(d.key);
        int tint = protocolTint(d.protocol);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        container.addView(header);

        TextView icon = text(protocolMonogram(d.protocol), 13, tint, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(Color.rgb(28, 44, 63), 13, BORDER));
        header.addView(icon, new LinearLayout.LayoutParams(dp(43), dp(43)));
        addHorizontalSpace(header, 11);

        LinearLayout naming = new LinearLayout(this);
        naming.setOrientation(LinearLayout.VERTICAL);
        header.addView(naming, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView category = text(displayProtocol(d.protocol), 11, tint, true);
        naming.addView(category);
        naming.addView(space(4));
        String displayedName = ("이름 없는 기기".equals(d.name)) ? "이름 미확인" : d.name;
        TextView title = text(displayedName, 15, WHITE, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        naming.addView(title);
        naming.addView(space(4));
        TextView address = text(d.address, 12, SUBTLE, false);
        address.setMaxLines(1);
        address.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        naming.addView(address);

        LinearLayout signalColumn = new LinearLayout(this);
        signalColumn.setOrientation(LinearLayout.VERTICAL);
        signalColumn.setGravity(Gravity.END);
        header.addView(signalColumn);
        TextView strength = text(d.rssi < 0 ? d.rssi + " dBm" : "—",
                13, d.rssi < 0 ? tint : SUBTLE, true);
        strength.setGravity(Gravity.END);
        signalColumn.addView(strength);
        signalColumn.addView(space(3));
        TextView level = text(d.rssi < 0 ? SignalRules.strength(d.rssi) : "신호 미제공",
                11, SUBTLE, false);
        level.setGravity(Gravity.END);
        signalColumn.addView(level);

        container.addView(space(10));
        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        container.addView(foot);
        String evidence = d.live ? "● 이번 탐색" :
                d.imported ? "○ 가져온 기록" :
                d.confirmed ? "○ 과거 관찰" : "○ 캐시 · 미확인";
        TextView provenance = text(evidence, 11, d.live ? ACCENT : SUBTLE, true);
        foot.addView(provenance, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView trailing = text(expanded ? "상세 접기  ⌃" : "상세 보기  ⌄",
                11, BLUE, true);
        foot.addView(trailing);

        if (expanded) {
            View divider = new View(this);
            divider.setBackgroundColor(BORDER);
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(-1, dp(1));
            divLp.topMargin = dp(13);
            divLp.bottomMargin = dp(12);
            container.addView(divider, divLp);

            container.addView(text("탐지 증거", 11, tint, true));
            container.addView(space(5));
            TextView details = text(d.details, 12, SUBTLE, false);
            details.setTextIsSelectable(true);
            details.setLineSpacing(dp(4), 1.1f);
            container.addView(details);

            if (store.hasSharedBluetoothAddressCandidate(d)) {
                container.addView(space(8));
                container.addView(text("BLE/일반 BT 주소 동일 후보 · 같은 물리 기기인지는 미확인",
                        11, GOLD, false));
            }

            if (SignalTrend.sampleCount(d.signalTrace) >= 2) {
                container.addView(space(12));
                container.addView(text("최근 RSSI  ·  "
                        + SignalTrend.sampleCount(d.signalTrace) + "개 샘플",
                        11, WHITE, true));
                container.addView(space(7));
                SignalPlotView plot = new SignalPlotView(this);
                plot.setColors(tint, BORDER);
                plot.setHistory(d.signalTrace);
                container.addView(plot, new LinearLayout.LayoutParams(-1, dp(56)));
                container.addView(space(4));
                container.addView(text("상대적인 신호 변화입니다. 거리·방향 아님",
                        11, MUTED, false));
            }

            container.addView(space(11));
            String when = d.lastConfirmedAt > 0L
                    ? "확인 시각  " + time.format(new Date(d.lastConfirmedAt))
                    : d.confirmed ? "과거 관찰 시간 미검증"
                    : "캐시 기록  " + time.format(new Date(d.lastSeen));
            container.addView(text(when + "  ·  관찰 " + d.observations + "회",
                    11, SUBTLE, false));
            if (d.imported) {
                container.addView(space(5));
                container.addView(text("외부 JSON에서 가져옴 · 현재 존재 여부는 확인되지 않음",
                        11, GOLD, false));
            }
            container.addView(space(12));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            container.addView(row);
            TextView copy = action("정보 복사", false, () -> copyDevice(d));
            copy.setTextSize(12);
            row.addView(copy, new LinearLayout.LayoutParams(0, dp(45), 1f));
            addHorizontalSpace(row, 9);
            TextView pin = action("검증 기준 등록", false, () -> addKnownTarget(d));
            pin.setTextSize(12);
            row.addView(pin, new LinearLayout.LayoutParams(0, dp(45), 1f));
        }
        container.setContentDescription(displayProtocol(d.protocol) + ", " + displayedName
                + ", 주소 " + d.address + ", "
                + (d.live ? "이번 탐색에서 관찰" : "현재 탐색에서 미확인")
                + ", " + (expanded ? "상세 접기" : "상세 펼치기"));
        container.setOnClickListener(v -> {
            if (!expandedKeys.add(d.key)) expandedKeys.remove(d.key);
            render();
        });
        container.setOnLongClickListener(v -> {
            copyDevice(d);
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
        String[] types = {"BLE", "BT", "WIFI", "LAN", "P2P", "NSD", "UPNP"};
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

    /** Exact benchmark only; names do not establish device identity. */
    private Set<String> sessionKeys() {
        Set<String> keys = new HashSet<>();
        for (DeviceRecord d : store.all()) if (d.live) keys.add(d.key);
        return keys;
    }

    private void updateTargetCoverage() {
        if (targetSummary == null) return;
        int expectedCount = knownTargets == null ? 0 : knownTargets.targets.size();
        if (expectedCount == 0) {
            targetSummary.setVisibility(View.GONE);
            return;
        }
        int matched = KnownTargetCoverage.matchedCount(knownTargets.targets, sessionKeys());
        targetSummary.setText(getString(R.string.target_coverage_summary, expectedCount, matched));
        targetSummary.setVisibility(View.VISIBLE);
    }

    private void addKnownTarget(DeviceRecord d) {
        String nextLine = d.protocol + "," + d.address + ","
                + ObservationRules.sanitize(d.name.replace(',', ' '), 70);
        String candidate = knownTargetInput.isEmpty()
                ? nextLine : knownTargetInput + "\n" + nextLine;
        KnownTargetCoverage.Result parsed = KnownTargetCoverage.parse(candidate);
        if (!parsed.errors.isEmpty()) {
            Toast.makeText(this, parsed.errors.get(0), Toast.LENGTH_LONG).show();
            return;
        }
        saveKnownTargets(candidate, parsed);
        Toast.makeText(this, "검증 기준에 등록됨 · 다음 탐색에서 확인하세요", Toast.LENGTH_LONG).show();
    }

    private void saveKnownTargets(String raw, KnownTargetCoverage.Result parsed) {
        knownTargetInput = raw;
        knownTargets = parsed;
        getPreferences(MODE_PRIVATE).edit().putString("known_targets", raw).apply();
        updateTargetCoverage();
    }

    private void editKnownTargets() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(knownTargetInput);
        input.setTextSize(13);
        input.setTextColor(WHITE);
        input.setHintTextColor(SUBTLE);
        input.setHint("BLE,AA:BB:CC:DD:EE:FF,내 비콘\n"
                + "WIFI,11:22:33:44:55:66,테스트 공유기\n"
                + "NSD,_ipp._tcp./프린터명,프린터 서비스");
        input.setMinLines(5);
        input.setMaxLines(11);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(16), dp(5), dp(16), dp(5));
        wrapper.addView(input, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("검증 기준 엔드포인트")
                .setMessage("줄마다 프로토콜,정확한 주소/서비스 ID,설명 순서로 입력합니다."
                        + "\n지원: BLE, BT, WIFI, P2P, NSD, UPNP. 최대 60개."
                        + "\n등록은 로컬에만 저장되며 탐색 성능 시험 기준입니다.")
                .setView(wrapper)
                .setNegativeButton("취소", (d, which) -> { })
                .setPositiveButton("저장", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String raw = input.getText().toString();
                    KnownTargetCoverage.Result parsed = KnownTargetCoverage.parse(raw);
                    if (!parsed.errors.isEmpty()) {
                        input.setError(parsed.errors.get(0));
                        return;
                    }
                    saveKnownTargets(raw, parsed);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void showPrivacy() {
        new AlertDialog.Builder(this)
                .setTitle("개인정보 · 탐색 기능 안내")
                .setMessage("Wireless Explorer v0.9.0\n\n"
                        + "• BLE·Bluetooth·Wi-Fi 주소와 기기명, RSSI, 검색 시각을 기기 내부에 저장합니다.\n"
                        + "• 서버·광고·계정·원격 분석 기능이 없습니다.\n"
                        + "• 위치 권한은 Android 무선 검색 API에 필요합니다. GPS 좌표를 저장하거나 전송하지 않습니다.\n"
                        + "• LAN TCP/UPnP 탐색은 사용자가 직접 켠 경우에만 허가된 사설 Wi-Fi에 요청을 보냅니다.\n"
                        + "• 소리 알림은 기본 꺼짐입니다. 무음 모드를 존중하고 새 발견 알림을 제한합니다.\n"
                        + "• CSV·JSON 내보내기는 사용자가 고른 파일에만 기록됩니다.\n"
                        + "• 외부로 내보낸 기록은 주소 등의 민감정보를 포함할 수 있으므로 공유에 주의하세요.\n"
                        + "• 실제 물리적 기기 수, 위치, 전수 탐지 여부는 확인하지 않습니다.")
                .setPositiveButton("확인", (dialog, which) -> { })
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("탐지 기록 전체 삭제")
                .setMessage("저장된 모든 탐지 기록과 등록한 검증 기준 주소를 삭제합니다. 복구할 수 없습니다.")
                .setNegativeButton("취소", (dialog, which) -> { })
                .setPositiveButton("모두 삭제", (dialog, which) -> {
                    if (scanner.isRunning()) stopSession();
                    store.clear();
                    knownTargetInput = "";
                    knownTargets = KnownTargetCoverage.parse("");
                    getPreferences(MODE_PRIVATE).edit().remove("known_targets").apply();
                    render();
                    Toast.makeText(this, "저장된 탐지 기록을 삭제했습니다",
                            Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private void updateProgress() {
        if (progress == null || scanner == null) return;
        if (!scanner.isRunning()) {
            progress.setText("탐색 종료 · 발견 기록을 확인하거나 내보내세요");
            if (sessionProgress != null) sessionProgress.setProgress(180);
            if (radar != null) radar.setScanning(false);
            return;
        }
        long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - sessionStartElapsed);
        long secondsLeft = Math.max(0L, (ScanPlan.SESSION_MS - elapsed) / 1000L);
        progress.setText(getString(R.string.scan_progress_active, secondsLeft));
        if (sessionProgress != null)
            sessionProgress.setProgress((int) Math.min(180L, elapsed / 1000L));
        main.removeCallbacks(countdownTick);
        main.postDelayed(countdownTick, 1000L);
    }

    private void exportReport() {
        exportFormat = "report";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "wireless-diagnostic-report.txt");
        startActivityForResult(intent, REQ_EXPORT);
    }

    private void importHistory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQ_IMPORT);
    }

    private void handleImport(Intent data) {
        Uri uri = data.getData();
        if (uri == null) return;
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("파일을 읽을 수 없습니다");
            byte[] chunk = new byte[8192];
            int count;
            while ((count = in.read(chunk)) != -1) {
                if (bytes.size() + count > 6_000_000)
                    throw new IOException("파일 크기 6MB 제한 초과");
                bytes.write(chunk, 0, count);
            }
            DeviceStore.ImportResult imported = store.importJson(
                    new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            if (!imported.success()) {
                Toast.makeText(this, "복원 실패 · " + imported.error, Toast.LENGTH_LONG).show();
                return;
            }
            sessionOnly = false;
            sessionButton.setText("과거 포함");
            render();
            Toast.makeText(this, "복원 성공 " + imported.added
                    + "건 · 중복/무효 제외 " + imported.skipped + "건",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "JSON 파일 복원 오류: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
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
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_IMPORT) {
            handleImport(data);
            return;
        }
        if (requestCode != REQ_EXPORT) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("스트림을 열 수 없습니다");
            String content;
            if ("report".equals(exportFormat)) {
                content = InventoryReport.generate(
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(new Date()),
                        store.countLiveRadioEndpoints(), store.countLiveLanHosts(),
                        store.countLiveNetworkServices(), store.total(),
                        store.sharedBluetoothAddressCandidates(), radioStates,
                        "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")",
                        extendedLan || lanProbeEnabled);
                content += "\n" + KnownTargetCoverage.report(knownTargets.targets, sessionKeys());
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
        boolean wasScanning = scanner != null && scanner.isRunning();
        if (wasScanning) scanner.stop();
        if (radar != null) radar.setScanning(false);
        if (wasScanning && scanButton != null) {
            scanButton.setText("탐색 시작");
            if (progress != null)
                progress.setText("앱이 백그라운드로 이동해 탐색을 종료했습니다");
        }
        if (lanToggle != null) lanToggle.setEnabled(true);
        if (lanProbeToggle != null) lanProbeToggle.setEnabled(true);
        main.removeCallbacks(countdownTick);
        if (store != null && !store.save() && status != null)
            status.setText(store.lastPersistenceError());
        super.onStop();
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (scanner != null) scanner.stop();
        if (feedback != null) feedback.close();
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
        view.setClickable(true);
        view.setFocusable(true);
        view.setMinHeight(dp(48));
        view.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(primary ? Color.rgb(70, 158, 139) : BORDER),
                shape(primary ? ACCENT : SURFACE, 12, primary ? ACCENT : BORDER),
                null));
        view.setOnClickListener(v -> {
            if (feedback != null && view != scanButton) feedback.haptic(view);
            run.run();
        });
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
