package com.galaxyuniversalio;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.ProgressBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintManager;
import android.print.PrintAttributes;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** M0.9: USB inventory, eSCL scan controls, accessible task-first UI, printer/PTP diagnostics. */
public final class MainActivity extends Activity {
    private static final int REQUEST_CREATE_REPORT = 1001;
    private static final int REQUEST_OPEN_RAW_JOB = 1002;
    private static final int REQUEST_OPEN_PDF = 1003;
    private static final int REQUEST_SAVE_PTP_JPEG = 1004;
    private static final int REQUEST_SAVE_ESCL_PDF = 1005;
    private static final int REQUEST_OPEN_IPP_PDF = 1006;
    private static final String STATE_IPP_DEVICE_NAME = "pending_ipp_device_name";
    private static final String STATE_IPP_VID = "pending_ipp_vid";
    private static final String STATE_IPP_PID = "pending_ipp_pid";
    private static final String STATE_SCAN_HOST = "pending_scan_host";
    private static final String STATE_SCAN_USB = "pending_scan_usb";
    private static final String STATE_SCAN_FEEDER = "pending_scan_feeder";
    private static final String STATE_SCAN_DEVICE = "pending_scan_device";
    private static final String STATE_SCAN_VID = "pending_scan_vid";
    private static final String STATE_SCAN_PID = "pending_scan_pid";
    private static final String STATE_PTP_DEVICE_NAME = "pending_ptp_device_name";
    private static final String STATE_PTP_VID = "pending_ptp_vid";
    private static final String STATE_PTP_PID = "pending_ptp_pid";
    private static final String STATE_PRINT_DEVICE_NAME = "pending_print_device_name";
    private static final String STATE_PRINT_VID = "pending_print_vid";
    private static final String STATE_PRINT_PID = "pending_print_pid";
    private static final String STATE_EXPORT_SNAPSHOT = "pending_export_snapshot";
    private static final String STATE_SERIAL = "include_serial";
    private static final String STATE_PAGE = "active_page";
    private static final String STATE_LAN_ADDRESS = "lan_address";
    private static final String STATE_FEEDER_SELECTED = "feed_selected";
    private static final String STATE_SHOW_REPORT = "show_report";


    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final AtomicBoolean scanInProgress = new AtomicBoolean(false);
    private volatile Thread scanWorker;
    private volatile EsclScanClient.Transport activeScanTransport;
    private volatile boolean scanCancellationRequested;
    private Button cancelScanButton;
    private ProgressBar scanProgress;
    private LinearLayout scanProgressCard;
    private ScrollView wholePage;
    private LinearLayout scanPanel, printPanel, devicePanel;
    private Button scanTabButton, printTabButton, devicesTabButton;
    private TextView usbHintView;
    private int activePage = 0;
    private boolean reportExpanded = false;

    private final List<UsbDevice> displayedDevices = new ArrayList<>();
    private UsbManager usbManager;
    private UsbProfileCatalog catalog = UsbProfileCatalog.empty();
    private TextView statusView;
    private TextView reportView;
    private Spinner deviceSpinner;
    private Button permissionButton;
    private Button printerProbeButton;
    private Button rawPrintButton;
    private Button ptpImportButton;
    private Button usbEsclButton;
    private Button ippPdfButton;
    private String pendingIppDeviceName;
    private int pendingIppVid, pendingIppPid;
    private EditText lanHostInput;
    private CheckBox feederCheck;
    private String pendingScanHost;
    private String pendingScanDevice;
    private int pendingScanVid, pendingScanPid;
    private boolean pendingScanUsb, pendingScanFeeder;
    private CheckBox serialCheckbox;
    private String latestReport = "";
    private String latestPrinterProbe = "";
    private String pendingPrintDeviceName;
    private int pendingPrintVid;
    private int pendingPrintPid;
    private String pendingPtpDeviceName;
    private int pendingPtpVid;
    private int pendingPtpPid;
    private String pendingExportReport;
    private String catalogWarning = "";
    private String notice = "";
    private boolean receiverRegistered;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && UsbPermissionReceiver.ACTION_RESULT.equals(intent.getAction())) {
                // The broadcast boolean itself is not a trusted permission oracle.
                String deviceName = intent.getStringExtra(UsbPermissionReceiver.EXTRA_DEVICE_NAME);
                UsbDevice device = null;
                try {
                    if (usbManager != null && deviceName != null) {
                        device = usbManager.getDeviceList().get(deviceName);
                    }
                } catch (RuntimeException ignored) { }
                boolean granted = device != null && usbManager.hasPermission(device);
                notice = granted ? "USB 접근 권한이 승인되었습니다."
                        : "USB 권한이 없거나 장치가 분리되었습니다.";
            } else {
                notice = "";
            }
            refreshInventory();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (state != null) {
            pendingExportReport = state.getString(STATE_EXPORT_SNAPSHOT);
            activePage = state.getInt(STATE_PAGE);
            reportExpanded = state.getBoolean(STATE_SHOW_REPORT, false);
            pendingPrintDeviceName = state.getString(STATE_PRINT_DEVICE_NAME);
            pendingPrintVid = state.getInt(STATE_PRINT_VID);
            pendingPrintPid = state.getInt(STATE_PRINT_PID);
            pendingPtpDeviceName = state.getString(STATE_PTP_DEVICE_NAME);
            pendingPtpVid = state.getInt(STATE_PTP_VID);
            pendingPtpPid = state.getInt(STATE_PTP_PID);
            pendingScanHost = state.getString(STATE_SCAN_HOST);
            pendingScanDevice = state.getString(STATE_SCAN_DEVICE);
            pendingScanVid = state.getInt(STATE_SCAN_VID);
            pendingScanPid = state.getInt(STATE_SCAN_PID);
            pendingScanUsb = state.getBoolean(STATE_SCAN_USB, false);
            pendingScanFeeder = state.getBoolean(STATE_SCAN_FEEDER, false);
            pendingIppDeviceName = state.getString(STATE_IPP_DEVICE_NAME);
            pendingIppVid = state.getInt(STATE_IPP_VID);
            pendingIppPid = state.getInt(STATE_IPP_PID);
        }
        try (InputStreamReader input = new InputStreamReader(
                getAssets().open("profiles.tsv"), StandardCharsets.UTF_8)) {
            catalog = UsbProfileCatalog.load(input);
        } catch (IOException e) {
            catalogWarning = "프로파일 목록을 읽지 못했습니다: " + e.getMessage();
        }
        buildUi();
        if (state != null) {
            serialCheckbox.setChecked(state.getBoolean(STATE_SERIAL, false));
            lanHostInput.setText(state.getString(STATE_LAN_ADDRESS));
            feederCheck.setChecked(state.getBoolean(STATE_FEEDER_SELECTED, false));
        }
        showPage(activePage);
    }

    private static final int INK = 0xFF152641;
    private static final int MUTED = 0xFF57677D;
    private static final int PRIMARY = 0xFF2058D4;
    private static final int BORDER = 0xFFDFE7F2;
    private static final int PALE = 0xFFF3F6FB;
    private static final int WHITE = 0xFFFFFFFF;

    private GradientDrawable rounded(int color, int stroke, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        if (stroke != 0) bg.setStroke(dp(1), stroke);
        return bg;
    }

    private LinearLayout vertical() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout card() {
        LinearLayout box = vertical();
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        box.setBackground(rounded(WHITE, BORDER, 18));
        return box;
    }

    private void addCard(LinearLayout parent, LinearLayout child) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        parent.addView(child, lp);
    }

    private TextView text(String content, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(content);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void addText(LinearLayout parent, String content, int size, int color, boolean bold, int bottom) {
        TextView t = text(content, size, color, bold);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(bottom);
        parent.addView(t, lp);
    }

    private void styleButton(Button button, boolean important) {
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setMinHeight(dp(50));
        button.setTextColor(important ? WHITE : INK);
        button.setBackgroundTintList(ColorStateList.valueOf(important ? PRIMARY : 0xFFE7EFFB));
    }

    private void addButton(LinearLayout parent, Button button) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        parent.addView(button, lp);
    }

    private void buildUi() {
        LinearLayout root = vertical();
        root.setPadding(dp(16), dp(14), dp(16), dp(24));
        root.setBackgroundColor(PALE);

        LinearLayout hero = vertical();
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(rounded(0xFF102955, 0, 18));
        addText(hero, "GALAXY  ·  UNIVERSAL I/O", 12, 0xFFBBD4FF, true, 8);
        addText(hero, "스캔부터 PDF까지", 24, WHITE, true, 5);
        addText(hero, "USB · 네트워크 스캔 · 인쇄 · 진단", 13, 0xFFD4E3FF, false, 0);
        addCard(root, hero);

        LinearLayout statusCard = card();
        addText(statusCard, "작업 상태", 12, MUTED, true, 4);
        statusView = text("시작할 준비가 되었습니다.", 14, INK, true);
        statusView.setMinHeight(dp(32));
        statusView.setContentDescription("현재 작업 상태");
        statusCard.addView(statusView);
        addCard(root, statusCard);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(4), dp(4), dp(4), dp(4));
        nav.setBackground(rounded(0xFFE7ECF5, 0, 15));
        scanTabButton = new Button(this);
        scanTabButton.setText("스캔");
        printTabButton = new Button(this);
        printTabButton.setText("인쇄");
        devicesTabButton = new Button(this);
        devicesTabButton.setText("장치 · 진단");
        Button[] tabs = {scanTabButton, printTabButton, devicesTabButton};
        for (int i = 0; i < tabs.length; i++) {
            final int selected = i;
            Button tab = tabs[i];
            tab.setAllCaps(false);
            tab.setMinHeight(dp(48));
            tab.setTextSize(13);
            tab.setOnClickListener(v -> showPage(selected));
            nav.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
        }
        addCard(root, nav);

        scanPanel = vertical();
        LinearLayout scanCard = card();
        addText(scanCard, "문서 스캔", 21, INK, true, 5);
        addText(scanCard, "스캐너에서 문서를 가져와 PDF로 저장합니다.", 13, MUTED, false, 18);
        addText(scanCard, "스캐너 네트워크 주소", 14, INK, true, 4);
        lanHostInput = new EditText(this);
        lanHostInput.setSingleLine(true);
        lanHostInput.setTextSize(15);
        lanHostInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        lanHostInput.setHint("예: 192.168.1.100:8080");
        lanHostInput.setContentDescription("네트워크 eSCL 스캐너의 사설 IPv4 주소");
        lanHostInput.setMinHeight(dp(54));
        scanCard.addView(lanHostInput, new LinearLayout.LayoutParams(-1, -2));
        feederCheck = new CheckBox(this);
        feederCheck.setText("자동 급지(ADF)로 여러 장 스캔");
        feederCheck.setTextSize(14);
        feederCheck.setTextColor(INK);
        feederCheck.setMinHeight(dp(48));
        scanCard.addView(feederCheck);
        Button lanScan = new Button(this);
        lanScan.setText("네트워크 스캔 → PDF 저장");
        styleButton(lanScan, true);
        lanScan.setOnClickListener(v -> chooseEsclPdfDestination(false));
        addButton(scanCard, lanScan);
        addText(scanCard, "스캐너와 같은 네트워크 연결 필요 · 평판 1장 또는 ADF 최대 12장 · JPEG/PNG 입력",
                12, MUTED, false, 0);
        addCard(scanPanel, scanCard);

        LinearLayout usbScanCard = card();
        addText(usbScanCard, "USB 케이블로 스캔", 17, INK, true, 6);
        addText(usbScanCard, "IPP-over-USB eSCL을 지원하는 기기에서만 이용할 수 있습니다. 장치·진단에서 USB 권한을 먼저 허용하세요.",
                13, MUTED, false, 8);
        usbEsclButton = new Button(this);
        usbEsclButton.setText("USB 스캔 → PDF 저장");
        styleButton(usbEsclButton, false);
        usbEsclButton.setOnClickListener(v -> chooseEsclPdfDestination(true));
        addButton(usbScanCard, usbEsclButton);
        addCard(scanPanel, usbScanCard);

        LinearLayout progressCard = card();
        addText(progressCard, "스캔 진행 상태", 15, INK, true, 6);
        scanProgress = new ProgressBar(this);
        scanProgress.setIndeterminate(true);
        scanProgress.setVisibility(View.GONE);
        progressCard.addView(scanProgress);
        cancelScanButton = new Button(this);
        cancelScanButton.setText("스캔 취소");
        styleButton(cancelScanButton, false);
        cancelScanButton.setVisibility(View.GONE);
        cancelScanButton.setEnabled(false);
        cancelScanButton.setOnClickListener(v -> cancelActiveScan());
        addButton(progressCard, cancelScanButton);
        addText(progressCard, "스캔 중 진행 상황과 오류는 위의 작업 상태에 표시됩니다.", 12, MUTED, false, 0);
        progressCard.setVisibility(View.GONE);
        scanProgressCard = progressCard;
        addCard(scanPanel, progressCard);
        addText(scanPanel, "일부 제조사 전용 스캐너는 이 앱에서 지원하지 않습니다. 민감한 문서는 신뢰할 수 있는 네트워크에서만 스캔하세요.",
                12, MUTED, false, 12);
        root.addView(scanPanel);

        printPanel = vertical();
        LinearLayout systemPrintCard = card();
        addText(systemPrintCard, "PDF 인쇄", 21, INK, true, 5);
        addText(systemPrintCard, "휴대전화에 설치된 인쇄 서비스를 사용합니다. 프린터와 인쇄 서비스를 먼저 설정하세요.",
                13, MUTED, false, 12);
        Button pdfPrint = new Button(this);
        pdfPrint.setText("PDF 파일 선택 후 인쇄");
        styleButton(pdfPrint, true);
        pdfPrint.setOnClickListener(v -> choosePdfForSystemPrint());
        addButton(systemPrintCard, pdfPrint);
        addCard(printPanel, systemPrintCard);
        LinearLayout directPrintCard = card();
        addText(directPrintCard, "고급 USB 직접 인쇄", 17, INK, true, 5);
        addText(directPrintCard, "USB 장치를 선택하고 권한을 허용한 뒤 이용하세요. 전송 성공과 실제 용지 출력은 다릅니다.",
                13, MUTED, false, 8);
        printerProbeButton = new Button(this);
        printerProbeButton.setText("프린터 상태 조회");
        styleButton(printerProbeButton, false);
        printerProbeButton.setOnClickListener(v -> probeSelectedPrinter());
        addButton(directPrintCard, printerProbeButton);
        rawPrintButton = new Button(this);
        rawPrintButton.setText("PRN / PCL / PS 파일 직접 전송");
        styleButton(rawPrintButton, false);
        rawPrintButton.setOnClickListener(v -> selectRawPrintDocument());
        addButton(directPrintCard, rawPrintButton);
        ippPdfButton = new Button(this);
        ippPdfButton.setText("USB IPP · PDF 직접 전송");
        styleButton(ippPdfButton, false);
        ippPdfButton.setOnClickListener(v -> chooseUsbIppPdf());
        addButton(directPrintCard, ippPdfButton);
        addCard(printPanel, directPrintCard);
        printPanel.setVisibility(View.GONE);
        root.addView(printPanel);

        devicePanel = vertical();
        LinearLayout deviceCard = card();
        addText(deviceCard, "USB 장치", 21, INK, true, 5);
        addText(deviceCard, "연결된 장치를 선택하고 접근 권한을 허용합니다.", 13, MUTED, false, 8);
        usbHintView = text("USB 연결을 확인하는 중입니다.", 13, MUTED, false);
        deviceCard.addView(usbHintView);
        deviceSpinner = new Spinner(this);
        deviceSpinner.setContentDescription("USB 장치 선택");
        deviceSpinner.setMinimumHeight(dp(52));
        deviceCard.addView(deviceSpinner);
        permissionButton = new Button(this);
        permissionButton.setText("선택한 USB 장치 접근 허용");
        styleButton(permissionButton, true);
        permissionButton.setOnClickListener(v -> requestSelectedDevicePermission());
        addButton(deviceCard, permissionButton);
        Button refresh = new Button(this);
        refresh.setText("장치 목록 새로고침");
        styleButton(refresh, false);
        refresh.setOnClickListener(v -> { notice = ""; refreshInventory(); });
        addButton(deviceCard, refresh);
        addCard(devicePanel, deviceCard);

        LinearLayout imageCard = card();
        addText(imageCard, "USB 카메라·이미지", 17, INK, true, 6);
        addText(imageCard, "PTP 호환 장치에서 첫 번째 JPEG 사진을 가져옵니다. 일반 문서 스캐너 기능과는 다릅니다.",
                13, MUTED, false, 6);
        ptpImportButton = new Button(this);
        ptpImportButton.setText("PTP JPEG 가져오기");
        styleButton(ptpImportButton, false);
        ptpImportButton.setOnClickListener(v -> choosePtpJpegDestination());
        addButton(imageCard, ptpImportButton);
        addCard(devicePanel, imageCard);

        LinearLayout reportCard = card();
        addText(reportCard, "장치 기술 진단", 17, INK, true, 5);
        addText(reportCard, "VID/PID, 인터페이스와 엔드포인트를 확인합니다. 개인 식별정보 공유에 주의하세요.",
                13, MUTED, false, 6);
        serialCheckbox = new CheckBox(this);
        serialCheckbox.setText("보고서에 시리얼번호 포함 (기본 꺼짐)");
        serialCheckbox.setTextSize(13);
        serialCheckbox.setMinHeight(dp(48));
        serialCheckbox.setOnCheckedChangeListener((v, checked) -> refreshInventory());
        reportCard.addView(serialCheckbox);
        Button toggleReport = new Button(this);
        toggleReport.setText("진단 상세 펼치기 / 접기");
        styleButton(toggleReport, false);
        toggleReport.setOnClickListener(v -> {
            reportExpanded = !reportExpanded;
            reportView.setVisibility(reportExpanded ? View.VISIBLE : View.GONE);
        });
        addButton(reportCard, toggleReport);
        Button export = new Button(this);
        export.setText("TXT 진단 보고서 저장");
        styleButton(export, false);
        export.setOnClickListener(v -> createReportDocument());
        addButton(reportCard, export);
        reportView = text("", 12, INK, false);
        reportView.setTextIsSelectable(true);
        reportView.setPadding(dp(8), dp(12), dp(8), dp(8));
        reportView.setVisibility(reportExpanded ? View.VISIBLE : View.GONE);
        reportCard.addView(reportView);
        addCard(devicePanel, reportCard);
        devicePanel.setVisibility(View.GONE);
        root.addView(devicePanel);

        deviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updatePermissionButton();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { updatePermissionButton(); }
        });
        wholePage = new ScrollView(this);
        wholePage.setFillViewport(true);
        wholePage.addView(root);
        setContentView(wholePage);
    }

    private void showPage(int page) {
        activePage = Math.max(0, Math.min(2, page));
        if (scanPanel == null) return;
        scanPanel.setVisibility(activePage == 0 ? View.VISIBLE : View.GONE);
        printPanel.setVisibility(activePage == 1 ? View.VISIBLE : View.GONE);
        devicePanel.setVisibility(activePage == 2 ? View.VISIBLE : View.GONE);
        Button[] tabs = {scanTabButton, printTabButton, devicesTabButton};
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == activePage;
            tabs[i].setBackgroundTintList(ColorStateList.valueOf(selected ? WHITE : 0xFFE7ECF5));
            tabs[i].setTextColor(selected ? PRIMARY : MUTED);
            tabs[i].setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            tabs[i].setContentDescription(tabs[i].getText() + (selected ? ", 선택됨" : ", 선택 가능"));
        }
        if (wholePage != null) wholePage.scrollTo(0, 0);
    }

    private void setScanUiBusy(boolean busy) {
        if (scanProgressCard != null) scanProgressCard.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (scanProgress != null) scanProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (cancelScanButton != null) {
            cancelScanButton.setEnabled(busy);
            cancelScanButton.setVisibility(busy ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbPermissionReceiver.ACTION_RESULT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
        }
        receiverRegistered = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshInventory();
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(usbReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    private void refreshInventory() {
        if (usbManager == null) {
            displayedDevices.clear();
            latestReport = "USB Host 서비스를 사용할 수 없습니다.\n";
            reportView.setText(latestReport);
            statusView.setText("USB Host 서비스 없음");
            updateDeviceSpinner();
            return;
        }

        List<UsbDevice> devices;
        try {
            Map<String, UsbDevice> deviceMap = usbManager.getDeviceList();
            devices = deviceMap == null ? new ArrayList<>() : new ArrayList<>(deviceMap.values());
            Collections.sort(devices, (a, b) -> Integer.compare(a.getDeviceId(), b.getDeviceId()));
        } catch (RuntimeException e) {
            displayedDevices.clear();
            latestReport = "USB 장치 목록을 가져오지 못했습니다: " + e.getClass().getSimpleName();
            reportView.setText(latestReport);
            statusView.setText("장치 검색 실패");
            updateDeviceSpinner();
            return;
        }
        displayedDevices.clear();
        displayedDevices.addAll(devices);
        updateDeviceSpinner();
        updateReport(devices);

        String status = devices.isEmpty()
                ? "USB 연결 없음 · 네트워크 문서 스캔 및 시스템 인쇄는 사용 가능"
                : "USB " + devices.size() + "개 연결 · 장치 정보를 확인했습니다.";
        if (usbHintView != null) usbHintView.setText(devices.isEmpty()
                ? "연결된 USB 장치가 없습니다. OTG 케이블 및 전원을 확인하세요."
                : "연결된 USB 장치 " + devices.size() + "개 · 장치를 선택하세요.");
        if (!notice.isEmpty()) status += "\n" + notice;
        if (!catalogWarning.isEmpty()) status += "\n" + catalogWarning;
        statusView.setText(status);
    }

    private void updateDeviceSpinner() {
        Object oldSelected = deviceSpinner.getSelectedItem();
        String oldName = oldSelected == null ? null : oldSelected.toString();
        List<String> labels = new ArrayList<>();
        for (UsbDevice device : displayedDevices) {
            labels.add(String.format(Locale.US, "%04X:%04X · %s",
                    device.getVendorId(), device.getProductId(), device.getDeviceName()));
        }
        if (labels.isEmpty()) labels.add("연결된 USB 장치 없음");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(adapter);
        if (oldName != null) {
            int index = labels.indexOf(oldName);
            if (index >= 0) deviceSpinner.setSelection(index);
        }
        updatePermissionButton();
    }

    private void updatePermissionButton() {
        int index = deviceSpinner.getSelectedItemPosition();
        if (usbManager == null || index < 0 || index >= displayedDevices.size()) {
            permissionButton.setEnabled(false);
            printerProbeButton.setEnabled(false);
            rawPrintButton.setEnabled(false);
            ptpImportButton.setEnabled(false);
            usbEsclButton.setEnabled(false);
            ippPdfButton.setEnabled(false);
            return;
        }
        try {
            UsbDevice device = displayedDevices.get(index);
            boolean hasPermission = usbManager.hasPermission(device);
            UsbPrinterTransport.Selection printer = UsbPrinterTransport.select(device);
            permissionButton.setEnabled(!hasPermission);
            printerProbeButton.setEnabled(hasPermission && printer != null);
            rawPrintButton.setEnabled(hasPermission && printer != null && printer.rawCapable);
            ptpImportButton.setEnabled(hasPermission && PtpUsbTransport.select(device) != null);
            usbEsclButton.setEnabled(hasPermission && UsbEsclHttpTransport.supports(device));
            ippPdfButton.setEnabled(hasPermission && UsbEsclHttpTransport.supports(device));
        } catch (RuntimeException e) {
            permissionButton.setEnabled(false);
            printerProbeButton.setEnabled(false);
            rawPrintButton.setEnabled(false);
            ptpImportButton.setEnabled(false);
            usbEsclButton.setEnabled(false);
            ippPdfButton.setEnabled(false);
        }
    }

    private void requestSelectedDevicePermission() {
        int index = deviceSpinner.getSelectedItemPosition();
        if (usbManager == null || index < 0 || index >= displayedDevices.size()) return;
        UsbDevice device = displayedDevices.get(index);
        try {
            if (usbManager.hasPermission(device)) {
                notice = "이 장치의 USB 접근 권한은 이미 승인되었습니다.";
                refreshInventory();
                return;
            }
            // Android 14+ requires explicit target for mutable PendingIntent.
            Intent callback = new Intent(this, UsbPermissionReceiver.class);
            callback.setAction(UsbPermissionReceiver.ACTION_PERMISSION);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    this, device.getDeviceId(), callback, flags);
            usbManager.requestPermission(device, permissionIntent);
            notice = "선택한 장치의 USB 권한을 요청했습니다. 시스템 대화상자를 확인하세요.";
            statusView.setText(notice);
        } catch (RuntimeException e) {
            notice = "USB 권한 요청 실패: " + e.getClass().getSimpleName();
            statusView.setText(notice);
        }
    }

    private void updateReport(List<UsbDevice> devices) {
        StringBuilder report = new StringBuilder(4096);
        report.append("Galaxy Universal I/O M0.9 진단 보고서\n");
        report.append("생성 시각: ").append(timestamp()).append('\n');
        report.append("단말: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        report.append("Android API: ").append(Build.VERSION.SDK_INT).append('\n');
        report.append("USB Host feature: ").append(getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_USB_HOST)).append('\n');
        report.append("USB 프로파일 수: ").append(catalog.size()).append('\n');
        report.append("감지 장치 수: ").append(devices.size()).append("\n\n");
        if (devices.isEmpty()) {
            report.append("연결된 USB 장치가 없습니다.\n");
            report.append("OTG 어댑터, 케이블, 장치·허브 전원을 확인하십시오.\n");
        }
        for (UsbDevice device : devices) {
            try {
                appendDevice(report, device);
            } catch (RuntimeException e) {
                report.append("장치 조회 중 오류: ")
                        .append(e.getClass().getSimpleName()).append('\n');
            }
        }
        report.append("\n[진단 범위 및 개인정보]\n");
        report.append("- 장치 기술자 조회는 자동으로 수행하며, 별도 사용자 명령으로 Printer Class 상태 조회가 가능합니다.\n");
        report.append("- RAW 전송은 명시적으로 선택한 .prn/.pcl/.ps 파일에 한정되며 문서 변환·인쇄 성공을 보장하지 않습니다.\n");
        report.append("- PTP 이미지 입력은 표준 Still Image 장치에서 JPEG 1개를 가져오는 제한적 기능입니다.\n");
        report.append("- Still Image 클래스는 카메라 등을 포함하므로 범용 문서 스캐너 지원의 증거가 아닙니다.\n");
        report.append("- PDF 시스템 인쇄는 설치된 Android Print Service에 의존하며 직접 USB 인쇄가 아닙니다.\n");
        report.append("- eSCL 스캔은 스캐너 기능·JPEG·지원 해상도·지원 원본을 확인한 후 사용자 명령에 따라 수행합니다.\n");
        report.append("- IPP-over-USB 프로토콜 4 장치만 USB eSCL 후보이며 기종별 호환성은 실물 시험이 필요합니다.\n");
        report.append("- PDF는 획득한 JPEG의 재구성 결과이며 OCR/이미지 보정은 지원하지 않습니다.\n");
        report.append("- USB IPP PDF 전송은 application/pdf 지원을 응답한 장치로 제한하며 인쇄 완료는 확인되지 않습니다.\n");
        report.append("- VID/PID와 클래스 정보만으로 호환성 여부를 확정할 수 없습니다.\n");
        report.append("- USB 경로 및 장치 식별정보가 포함됩니다. 외부 공유 시 확인하세요.\n");
        report.append("- 시리얼번호 포함: ").append(serialCheckbox.isChecked() ? "예" : "아니요").append('\n');
        if (!catalogWarning.isEmpty()) report.append("- ").append(catalogWarning).append('\n');
        if (!latestPrinterProbe.isEmpty()) report.append("\n[마지막 프린터 상태 조회]\n")
                .append(latestPrinterProbe).append('\n');
        latestReport = report.toString();
        reportView.setText(latestReport);
    }

    private void appendDevice(StringBuilder out, UsbDevice device) {
        int vid = device.getVendorId();
        int pid = device.getProductId();
        out.append("==================================================\n");
        out.append("VID:PID: ").append(String.format(Locale.US, "%04X:%04X", vid, pid)).append('\n');
        out.append("Device ID (일시적): ").append(device.getDeviceId()).append('\n');
        out.append("Device path: ").append(device.getDeviceName()).append('\n');
        out.append("Device class/subclass/protocol: ").append(String.format(Locale.US,
                "%02X/%02X/%02X", device.getDeviceClass(), device.getDeviceSubclass(),
                device.getDeviceProtocol())).append('\n');
        out.append("Manufacturer: ").append(safeString(device, true)).append('\n');
        out.append("Product: ").append(safeString(device, false)).append('\n');
        boolean granted = usbManager.hasPermission(device);
        out.append("Android USB permission: ").append(granted ? "granted" : "not granted")
                .append('\n');
        if (serialCheckbox.isChecked()) {
            String serial = "(권한 없음 또는 미제공)";
            if (granted) {
                try { serial = safe(device.getSerialNumber()); }
                catch (RuntimeException e) { serial = "(접근 실패)"; }
            }
            out.append("Serial number: ").append(serial).append('\n');
        }
        int count = device.getInterfaceCount();
        out.append("Interface count: ").append(count).append('\n');
        int[] classes = new int[count];
        for (int i = 0; i < count; i++) {
            UsbInterface intf = device.getInterface(i);
            classes[i] = intf.getInterfaceClass();
            out.append(String.format(Locale.US,
                    "  Interface %d: id=%d alt=%d class=%02X subclass=%02X protocol=%02X endpoints=%d\n",
                    i, intf.getId(), intf.getAlternateSetting(), intf.getInterfaceClass(),
                    intf.getInterfaceSubclass(), intf.getInterfaceProtocol(), intf.getEndpointCount()));
            for (int e = 0; e < intf.getEndpointCount(); e++) {
                UsbEndpoint endpoint = intf.getEndpoint(e);
                String direction = endpoint.getDirection() == UsbConstants.USB_DIR_IN ? "IN" : "OUT";
                out.append(String.format(Locale.US,
                        "    Endpoint %d: addr=%02X direction=%s type=%s maxPacket=%d interval=%d\n",
                        e, endpoint.getAddress(), direction,
                        UsbDeviceClassifier.endpointTypeName(endpoint.getType()),
                        endpoint.getMaxPacketSize(), endpoint.getInterval()));
            }
        }
        out.append("클래스 분류: ")
                .append(UsbDeviceClassifier.classify(device.getDeviceClass(), classes).description())
                .append('\n');
        UsbProfileCatalog.Entry match = catalog.find(vid, pid);
        if (match == null) {
            out.append("장치별 프로파일: 없음 (기능 호환성 미확인)\n");
        } else {
            out.append("장치별 프로파일: ").append(match.vendor).append(' ')
                    .append(match.model).append(" / category=").append(match.category)
                    .append(" / protocol=").append(match.protocol)
                    .append(" / evidence=").append(match.evidenceLevel)
                    .append(" / notes=").append(match.notes).append('\n');
        }
    }

    private static String safeString(UsbDevice device, boolean manufacturer) {
        try { return safe(manufacturer ? device.getManufacturerName() : device.getProductName()); }
        catch (RuntimeException e) { return "(접근 실패)"; }
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "(미제공)" : value;
    }

    private void createReportDocument() {
        if (latestReport.isEmpty()) refreshInventory();
        pendingExportReport = latestReport; // Freeze content before the external picker opens.
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "GalaxyUniversalIO-M0.9-report.txt");
        try { startActivityForResult(intent, REQUEST_CREATE_REPORT); }
        catch (RuntimeException e) {
            pendingExportReport = null;
            statusView.setText("문서 저장 선택기를 실행할 수 없습니다.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OPEN_IPP_PDF) {
            String path = pendingIppDeviceName;
            int vid = pendingIppVid, pid = pendingIppPid;
            pendingIppDeviceName = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && path != null)
                confirmUsbIppPdf(data.getData(), path, vid, pid);
            return;
        }
        if (requestCode == REQUEST_SAVE_ESCL_PDF) {
            boolean usb = pendingScanUsb;
            boolean feeder = pendingScanFeeder;
            String host = pendingScanHost, path = pendingScanDevice;
            int vid = pendingScanVid, pid = pendingScanPid;
            pendingScanHost = null;
            pendingScanDevice = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null)
                saveEsclPdf(data.getData(), usb, feeder, host, path, vid, pid);
            return;
        }
        if (requestCode == REQUEST_OPEN_PDF) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null)
                printPdfViaSystem(data.getData());
            return;
        }
        if (requestCode == REQUEST_SAVE_PTP_JPEG) {
            String path = pendingPtpDeviceName;
            int vid = pendingPtpVid, pid = pendingPtpPid;
            pendingPtpDeviceName = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && path != null)
                savePtpJpeg(data.getData(), path, vid, pid);
            return;
        }
        if (requestCode == REQUEST_OPEN_RAW_JOB) {
            String deviceName = pendingPrintDeviceName;
            int vid = pendingPrintVid;
            int pid = pendingPrintPid;
            pendingPrintDeviceName = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null
                    && deviceName != null) {
                confirmRawPrint(data.getData(), deviceName, vid, pid);
            }
            return;
        }
        if (requestCode != REQUEST_CREATE_REPORT) return;
        String snapshot = pendingExportReport;
        pendingExportReport = null;
        if (resultCode == RESULT_OK && data != null && data.getData() != null && snapshot != null) {
            writeReport(data.getData(), snapshot);
        }
    }

    private UsbDevice selectedDevice() {
        int index = deviceSpinner.getSelectedItemPosition();
        return index < 0 || index >= displayedDevices.size()
                ? null : displayedDevices.get(index);
    }

    private void probeSelectedPrinter() {
        UsbDevice device = selectedDevice();
        if (device == null || usbManager == null) return;
        UsbPrinterTransport.Selection sel = UsbPrinterTransport.select(device);
        if (sel == null || !usbManager.hasPermission(device)) {
            statusView.setText("Printer Class 장치와 USB 권한을 확인하세요.");
            return;
        }
        statusView.setText("프린터 상태 읽는 중 (USB 제어 전송)...");
        writer.execute(() -> {
            String result;
            try { result = UsbPrinterTransport.probe(usbManager, device); }
            catch (Exception e) { result = "프린터 상태 조회 실패: " + e.getMessage(); }
            String snapshot = result;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                latestPrinterProbe = String.format(Locale.US, "%04X:%04X / %s\n%s",
                        device.getVendorId(), device.getProductId(), device.getDeviceName(), snapshot);
                refreshInventory();
            });
        });
    }

    private void selectRawPrintDocument() {
        UsbDevice device = selectedDevice();
        if (device == null || usbManager == null || !usbManager.hasPermission(device)) return;
        UsbPrinterTransport.Selection printer = UsbPrinterTransport.select(device);
        if (printer == null || !printer.rawCapable) return;
        pendingPrintDeviceName = device.getDeviceName();
        pendingPrintVid = device.getVendorId();
        pendingPrintPid = device.getProductId();
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        try { startActivityForResult(picker, REQUEST_OPEN_RAW_JOB); }
        catch (RuntimeException e) {
            pendingPrintDeviceName = null;
            statusView.setText("RAW 파일 선택기를 실행할 수 없습니다.");
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) return cursor.getString(column);
            }
        } catch (RuntimeException ignored) { }
        return null;
    }

    private UsbDevice findMatchingPrinter(String path, int vid, int pid) {
        if (usbManager == null || path == null) return null;
        try {
            UsbDevice device = usbManager.getDeviceList().get(path);
            if (device == null || device.getVendorId() != vid || device.getProductId() != pid
                    || !usbManager.hasPermission(device)) return null;
            UsbPrinterTransport.Selection printer = UsbPrinterTransport.select(device);
            return printer != null && printer.rawCapable ? device : null;
        } catch (RuntimeException e) { return null; }
    }

    private void confirmRawPrint(Uri uri, String path, int vid, int pid) {
        String name = displayName(uri);
        if (!UsbPrinterProtocol.isAcceptedRawFilename(name)) {
            statusView.setText(".prn, .pcl, .ps RAW 파일만 허용됩니다. PDF/HWP는 직접 전송할 수 없습니다.");
            return;
        }
        if (findMatchingPrinter(path, vid, pid) == null) {
            statusView.setText("장치 연결 또는 USB 권한이 변경되었습니다. 다시 선택하세요.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("RAW 인쇄 전송 확인")
                .setMessage("선택한 파일: " + name + "\n\n인쇄 준비된 프린터 언어(PRN/PCL/PostScript)만 전송합니다. "
                        + "PDF/HWP/JPG 파일은 변환하지 않습니다. 프린터 언어가 일치하지 않으면 무반응·오출력할 수 있으며, "
                        + "USB 전송 완료는 실제 인쇄 성공을 뜻하지 않습니다.\n\n최대 8 MiB. 계속할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("전송", (dialog, which) -> sendRawSelected(uri, path, vid, pid))
                .show();
    }

    private void sendRawSelected(Uri uri, String path, int vid, int pid) {
        statusView.setText("RAW 데이터를 검사하는 중...");
        writer.execute(() -> {
            String result;
            try {
                byte[] job;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    job = UsbPrinterProtocol.readRawJob(input);
                }
                UsbDevice device = findMatchingPrinter(path, vid, pid);
                if (device == null) throw new IOException("전송 전에 장치 또는 권한이 변경되었습니다.");
                int sent = UsbPrinterTransport.sendRaw(usbManager, device, job);
                result = "USB RAW 전송 완료: " + sent + " bytes (용지 출력 성공 여부는 미확인)";
            } catch (Exception e) {
                result = "RAW 전송 실패: " + e.getMessage();
            }
            String snapshot = result;
            runOnUiThread(() -> { if (!isDestroyed()) statusView.setText(snapshot); });
        });
    }

    private void chooseUsbIppPdf() {
        UsbDevice device = selectedDevice();
        if (device == null || usbManager == null || !usbManager.hasPermission(device)
                || !UsbEsclHttpTransport.supports(device)) {
            statusView.setText("USB IPP-over-USB 장치와 사용자 권한이 필요합니다.");
            return;
        }
        pendingIppDeviceName = device.getDeviceName();
        pendingIppVid = device.getVendorId();
        pendingIppPid = device.getProductId();
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("application/pdf");
        try { startActivityForResult(picker, REQUEST_OPEN_IPP_PDF); }
        catch (RuntimeException e) {
            pendingIppDeviceName = null;
            statusView.setText("USB IPP PDF 파일 선택기를 열 수 없습니다.");
        }
    }

    private void confirmUsbIppPdf(Uri uri, String path, int vid, int pid) {
        new AlertDialog.Builder(this)
                .setTitle("USB IPP PDF 직접 전송")
                .setMessage("선택한 PDF를 USB IPP-over-USB 프린터에 전송할 수 있습니다. "
                        + "프린터가 application/pdf 형식을 지원한다고 응답한 경우에만 전송합니다. "
                        + "최대 8 MiB. 용지 출력·품질·페이지 성공 여부는 자동 확인할 수 없습니다. "
                        + "실제 용지가 출력될 수 있습니다. 계속할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("전송", (dialog, which) -> sendUsbIppPdf(uri,path,vid,pid))
                .show();
    }

    private void sendUsbIppPdf(Uri uri, String path, int vid, int pid) {
        statusView.setText("USB IPP PDF 준비 및 지원 형식 확인 중...");
        writer.execute(() -> {
            String message;
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    PdfSpoolPreflight.copy(in, output, () -> Thread.currentThread().isInterrupted());
                }
                byte[] bytes = output.toByteArray();
                if (bytes.length > IppProtocol.MAX_DIRECT_PDF_BYTES)
                    throw new IOException("USB IPP PDF는 8 MiB를 넘을 수 없습니다.");
                if (usbManager == null) throw new IOException("USB manager unavailable");
                UsbDevice device = usbManager.getDeviceList().get(path);
                if (device == null || device.getVendorId() != vid || device.getProductId() != pid
                        || !usbManager.hasPermission(device) || !UsbEsclHttpTransport.supports(device))
                    throw new IOException("IPP USB device disconnected or permission revoked");
                message = UsbIppPdfPrinter.print(usbManager, device, bytes);
            } catch (Exception e) { message = "USB IPP PDF 인쇄 요청 실패: " + e.getMessage(); }
            String finalMessage = message;
            runOnUiThread(() -> { if (!isDestroyed()) statusView.setText(finalMessage); });
        });
    }

    private void chooseEsclPdfDestination(boolean usb) {
        if (scanInProgress.get()) {
            statusView.setText("스캔이 진행 중입니다. 완료 또는 취소 후 다시 실행하세요.");
            return;
        }
        UsbDevice selected = selectedDevice();
        if (usb) {
            if (selected == null || usbManager == null || !usbManager.hasPermission(selected)
                    || !UsbEsclHttpTransport.supports(selected)) {
                statusView.setText("USB IPP-over-USB/eSCL 장치와 권한이 필요합니다.");
                return;
            }
            pendingScanDevice = selected.getDeviceName();
            pendingScanVid = selected.getVendorId();
            pendingScanPid = selected.getProductId();
        } else {
            pendingScanHost = lanHostInput.getText().toString().trim();
            try { new LanEsclTransport(pendingScanHost); }
            catch (IOException e) { statusView.setText("LAN 주소 오류: " + e.getMessage()); return; }
        }
        pendingScanUsb = usb;
        pendingScanFeeder = feederCheck.isChecked();
        if (!usb && !pendingScanHost.startsWith("https://")) {
            new AlertDialog.Builder(this)
                    .setTitle("암호화되지 않은 스캔 연결")
                    .setMessage("현재 LAN eSCL은 HTTP 연결입니다. 스캔한 문서가 Wi-Fi 또는 "
                            + "로컬 네트워크에서 암호화되지 않은 상태로 전송됩니다. "
                            + "신뢰할 수 있는 개인 네트워크에서만 사용하세요. 계속할까요?")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("계속", (dialog, which) -> launchEsclPdfSavePicker())
                    .show();
            return;
        }
        launchEsclPdfSavePicker();
    }

    private void launchEsclPdfSavePicker() {
        Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType("application/pdf");
        save.putExtra(Intent.EXTRA_TITLE, "eSCL-document.pdf");
        try { startActivityForResult(save, REQUEST_SAVE_ESCL_PDF); }
        catch (RuntimeException e) { statusView.setText("PDF 저장 선택기를 열 수 없습니다."); }
    }

    private void cancelActiveScan() {
        if (!scanInProgress.get()) return;
        scanCancellationRequested = true;
        Thread active = scanWorker;
        if (active != null) active.interrupt();
        cancelScanButton.setEnabled(false);
        EsclScanClient.Transport transport = activeScanTransport;
        if (transport != null) {
            try { transport.close(); } catch (Exception ignored) { }
        }
        statusView.setText("취소 요청됨 · 연결 종료 및 작업 정리 중");
    }

    private void saveEsclPdf(Uri destination, boolean usb, boolean feeder, String host,
            String path, int vid, int pid) {
        if (!scanInProgress.compareAndSet(false, true)) {
            statusView.setText("다른 스캔이 진행 중입니다.");
            return;
        }
        scanCancellationRequested = false;
        setScanUiBusy(true);
        statusView.setText("스캔 중 · 최대 12장 / 총 32MiB");
        try {
            writer.execute(() -> {
                scanWorker = Thread.currentThread();
                String outcome;
                try {
                    if (scanCancellationRequested) throw new IOException("스캔 취소됨");
                    try (EsclScanClient.Transport transport = openScannerTransport(usb, host, path, vid, pid)) {
                        activeScanTransport = transport;
                        if (scanCancellationRequested) throw new IOException("스캔 취소됨");
                        List<byte[]> pages = EsclScanClient.scan(transport, feeder, count ->
                            runOnUiThread(() -> { if (!isDestroyed() && !scanCancellationRequested)
                                statusView.setText("eSCL 문서 스캔: " + count + "페이지 수신"); }));
                        if (scanCancellationRequested || Thread.currentThread().isInterrupted())
                            throw new IOException("스캔 취소됨");
                        long bytes = StagedPdfExport.write(getCacheDir(),
                                sink -> ScannedPdfWriter.write(pages, sink),
                                () -> getContentResolver().openOutputStream(destination, "wt"));
                        outcome = "eSCL PDF 저장 완료: " + pages.size() + "페이지 / "
                                + bytes + " bytes (실물 호환성 별도 확인)";
                    }
                } catch (Exception e) {
                    outcome = scanCancellationRequested || Thread.currentThread().isInterrupted()
                            ? "스캔 취소됨. 저장 선택기에 만든 빈 파일은 파일 앱에서 삭제할 수 있습니다."
                            : "eSCL 스캔 실패: " + e.getMessage()
                                + " (저장 대상에 불완전한 파일이 남아 있을 수 있습니다)";
                } finally {
                    activeScanTransport = null;
                    scanWorker = null;
                    scanInProgress.set(false);
                }
                String result = outcome;
                runOnUiThread(() -> {
                    if (!isDestroyed()) {
                        setScanUiBusy(false);
                        statusView.setText(result);
                    }
                });
            });
        } catch (RuntimeException rejected) {
            scanInProgress.set(false);
            setScanUiBusy(false);
            statusView.setText("스캔 작업을 시작할 수 없습니다.");
        }
    }

    private EsclScanClient.Transport openScannerTransport(boolean usb, String host,
            String path, int vid, int pid) throws IOException {
        if (!usb) return new LanEsclTransport(host);
        if (usbManager == null || path == null) throw new IOException("USB scanner not selected");
        UsbDevice device = usbManager.getDeviceList().get(path);
        if (device == null || device.getVendorId() != vid || device.getProductId() != pid
                || !usbManager.hasPermission(device) || !UsbEsclHttpTransport.supports(device))
            throw new IOException("USB scanner disconnected or permission revoked");
        return UsbEsclHttpTransport.open(usbManager, device);
    }

    private void choosePdfForSystemPrint() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("application/pdf");
        try { startActivityForResult(picker, REQUEST_OPEN_PDF); }
        catch (RuntimeException e) { statusView.setText("PDF 선택기를 열 수 없습니다."); }
    }

    private void printPdfViaSystem(Uri uri) {
        try {
            PrintManager printer = (PrintManager) getSystemService(Context.PRINT_SERVICE);
            if (printer == null) throw new IllegalStateException("PrintManager 없음");
            printer.print("Galaxy Universal I/O - PDF",
                    new AndroidPdfPrintAdapter(this, uri), new PrintAttributes.Builder().build());
            statusView.setText("Android 인쇄 대화상자를 열었습니다. 출력 여부는 인쇄 서비스에서 확인하세요.");
        } catch (RuntimeException e) {
            statusView.setText("Android 시스템 인쇄를 시작하지 못했습니다: " + e.getClass().getSimpleName());
        }
    }

    private void choosePtpJpegDestination() {
        UsbDevice selected = selectedDevice();
        if (selected == null || usbManager == null || !usbManager.hasPermission(selected)
                || PtpUsbTransport.select(selected) == null) return;
        pendingPtpDeviceName = selected.getDeviceName();
        pendingPtpVid = selected.getVendorId();
        pendingPtpPid = selected.getProductId();
        Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType("image/jpeg");
        save.putExtra(Intent.EXTRA_TITLE, "PTP-import.jpg");
        try { startActivityForResult(save, REQUEST_SAVE_PTP_JPEG); }
        catch (RuntimeException e) {
            pendingPtpDeviceName = null;
            statusView.setText("JPEG 저장 선택기를 열 수 없습니다.");
        }
    }

    private void savePtpJpeg(Uri uri, String path, int vid, int pid) {
        statusView.setText("PTP JPEG 입력을 수행하는 중...");
        writer.execute(() -> {
            String result;
            try {
                UsbDevice device = usbManager.getDeviceList().get(path);
                if (device == null || device.getVendorId() != vid || device.getProductId() != pid
                        || !usbManager.hasPermission(device))
                    throw new IOException("PTP 장치 연결 또는 권한 변경");
                byte[] image = PtpUsbTransport.importFirstJpeg(usbManager, device);
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new IOException("이미지 저장 스트림이 없습니다.");
                    out.write(image);
                    out.flush();
                }
                result = "PTP JPEG 저장 완료: " + image.length + " bytes";
            } catch (Exception e) {
                result = "PTP JPEG 입력 실패: " + e.getMessage();
            }
            String snapshot = result;
            runOnUiThread(() -> { if (!isDestroyed()) statusView.setText(snapshot); });
        });
    }

    private void writeReport(Uri uri, String content) {
        writer.execute(() -> {
            String result;
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new IOException("출력 스트림을 열 수 없습니다.");
                output.write(content.getBytes(StandardCharsets.UTF_8));
                output.flush();
                result = "TXT 보고서 저장 완료";
            } catch (Exception e) {
                result = "보고서 저장 실패: " + e.getClass().getSimpleName();
            }
            String message = result;
            runOnUiThread(() -> { if (!isDestroyed()) statusView.setText(message); });
        });
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.KOREA).format(new Date());
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_EXPORT_SNAPSHOT, pendingExportReport);
        outState.putBoolean(STATE_SERIAL, serialCheckbox.isChecked());
        outState.putInt(STATE_PAGE, activePage);
        outState.putBoolean(STATE_SHOW_REPORT, reportExpanded);
        outState.putString(STATE_LAN_ADDRESS, lanHostInput.getText().toString());
        outState.putBoolean(STATE_FEEDER_SELECTED, feederCheck.isChecked());
        outState.putString(STATE_PRINT_DEVICE_NAME, pendingPrintDeviceName);
        outState.putInt(STATE_PRINT_VID, pendingPrintVid);
        outState.putInt(STATE_PRINT_PID, pendingPrintPid);
        outState.putString(STATE_PTP_DEVICE_NAME, pendingPtpDeviceName);
        outState.putInt(STATE_PTP_VID, pendingPtpVid);
        outState.putInt(STATE_PTP_PID, pendingPtpPid);
        outState.putString(STATE_SCAN_HOST, pendingScanHost);
        outState.putString(STATE_SCAN_DEVICE, pendingScanDevice);
        outState.putInt(STATE_SCAN_VID, pendingScanVid);
        outState.putInt(STATE_SCAN_PID, pendingScanPid);
        outState.putBoolean(STATE_SCAN_USB, pendingScanUsb);
        outState.putBoolean(STATE_SCAN_FEEDER, pendingScanFeeder);
        outState.putString(STATE_IPP_DEVICE_NAME, pendingIppDeviceName);
        outState.putInt(STATE_IPP_VID, pendingIppVid);
        outState.putInt(STATE_IPP_PID, pendingIppPid);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        scanCancellationRequested = true;
        Thread active = scanWorker;
        if (active != null) active.interrupt();
        EsclScanClient.Transport transport = activeScanTransport;
        if (transport != null) try { transport.close(); } catch (Exception ignored) { }
        writer.shutdownNow();
        super.onDestroy();
    }
}
