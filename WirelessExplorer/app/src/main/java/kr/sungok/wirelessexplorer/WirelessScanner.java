package kr.sungok.wirelessexplorer;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.bluetooth.le.ScanRecord;
import android.util.SparseArray;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

/**
 * Discoverable radio endpoints + LAN service advertisement, with optional
 * user-enabled UPnP M-SEARCH on the currently connected local network.
 * Does not pair, authenticate, interrogate ports or decode RF payloads.
 */
@SuppressWarnings("deprecation")
public final class WirelessScanner {
    public interface Listener {
        void onDevice(String protocol, String name, String id, String details, int rssi, boolean fresh);
        void onStatus(String message);
        default void onRadioState(String protocol, String state) { }
    }

        private static final String[] SERVICE_TYPES = {
            "_http._tcp.", "_ipp._tcp.", "_googlecast._tcp.",
            "_airplay._tcp.", "_workstation._tcp.", "_smb._tcp.",
            "_hap._tcp.", "_raop._tcp.",
            "_device-info._tcp.", "_printer._tcp.", "_rtsp._tcp.", "_ssh._tcp."
    };

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ObservationThrottle bleGate = new ObservationThrottle();
    private BluetoothAdapter bluetooth;
    private BluetoothLeScanner ble;
    private WifiManager wifi;
    private WifiManager.MulticastLock multicastLock;
    private long sessionStartedElapsedUs;
    private WifiP2pManager p2p;
    private WifiP2pManager.Channel p2pChannel;
    private NsdManager nsd;
    private NsdManager.DiscoveryListener activeNsd;
    private boolean nsdStopping;
    private int nsdStopRetries;
    private int pendingNsdIndex = -1;
    private ScanCallback bleCallback;
    private BroadcastReceiver receiver;
    private volatile boolean running;
    private boolean bleScanning;
    private boolean classicPhase;
    private boolean extendedLan;
    private boolean lanProbeEnabled;
    private LanDiscovery lanDiscovery;
    private SsdpDiscovery ssdp;
    private int wifiRequests;
    private int wifiAccepted;
    private int wifiBroadcasts;
    private int wifiLastRows;
    private final Set<String> wifiFreshKeys = new HashSet<>();
    private boolean wifiConnected;
    private boolean wifiHadPermissionError;
    private volatile int generation;

    public WirelessScanner(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public boolean isRunning() { return running; }

    /** Opt-in only: UPnP discovery sends a standard M-SEARCH multicast on the connected LAN. */
    public void setExtendedLan(boolean enabled) {
        if (!running) extendedLan = enabled;
    }

    /** Explicit opt-in only: small, bounded TCP handshakes to the active local Wi-Fi subnet. */
    public void setLanProbeEnabled(boolean enabled) {
        if (!running) lanProbeEnabled = enabled;
    }

    public void start() {
        if (running) return;
        running = true;
        generation++;
        bleGate.clear();
        wifiRequests = wifiAccepted = wifiBroadcasts = wifiLastRows = 0;
        wifiFreshKeys.clear();
        wifiConnected = wifiHadPermissionError = false;
        sessionStartedElapsedUs = SystemClock.elapsedRealtimeNanos() / 1000L;
        report("탐색 시작 · BLE/Classic 분리 및 Wi-Fi·LAN 증거 수집 (180초)");
        registerEvents();
        radio("BLE", "시작 준비");
        radio("BT", "시작 준비");
        radio("WIFI", "시작 준비");
        radio("P2P", "시작 준비");
        radio("NSD", "시작 준비");
        radio("UPNP", extendedLan ? "확장 검색 준비" : "비활성(선택사항)");
        radio("LAN", lanProbeEnabled ? "주소 조사 준비" : "비활성(사용자 선택)");
        startBluetooth();
        // Classic inquiry can compete with BLE; scan the radios in phases.
        main.postDelayed(this::beginClassicPhase, ScanPlan.BLE_LOW_LATENCY_MS);
        main.postDelayed(this::retryP2p, ScanPlan.P2P_RETRY_MS);
        wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            try {
                // Older Android versions need a lock for reliable mDNS multicast reception.
                multicastLock = wifi.createMulticastLock("WirelessExplorer-mdns");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            } catch (SecurityException | IllegalStateException ignored) {
                report("mDNS 멀티캐스트 수신 제한 · 일부 서비스가 누락될 수 있습니다");
            }
        }
        if (wifi == null) radio("WIFI", "Wi-Fi 기능 미지원");
        readConnectedWifi();
        requestWifi();
        nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) radio("NSD", "서비스 탐색 미지원");
        startNsdCycle(0);
        startP2p();
        if (extendedLan) {
            ssdp = new SsdpDiscovery(context);
            main.postDelayed(this::discoverSsdp, 8_000);
            main.postDelayed(this::discoverSsdp, 100_000);
        }
        if (lanProbeEnabled) {
            lanDiscovery = new LanDiscovery();
            main.postDelayed(this::discoverLan, 2_500L);
        }
        main.postDelayed(this::stop, ScanPlan.SESSION_MS);
    }

    public void stop() {
        if (!running) return;
        running = false;
        generation++;
        main.removeCallbacksAndMessages(null);
        bleGate.clear();
        if (lanDiscovery != null) lanDiscovery.stop();
        lanDiscovery = null;
         radio("WIFI", wifiSummary() + " · 탐색 종료");
        radio("P2P", "탐색 종료 · Wi-Fi Direct 수신 가능 기기만");
        radio("NSD", "탐색 종료 · 동일 LAN 광고 서비스만");
        radio("BLE", "탐색 종료");
        radio("BT", "탐색 종료 · 발견 가능 모드 기기만");
        if (lanProbeEnabled) radio("LAN", "탐색 종료 · 발견 항목은 목록에서 확인");
        classicPhase = false;
        bleScanning = false;
        if (ssdp != null) ssdp.stop();
        ssdp = null;
        try { if (ble != null && bleCallback != null) ble.stopScan(bleCallback); }
        catch (SecurityException | IllegalStateException ignored) { }
        try { if (bluetooth != null && bluetooth.isDiscovering()) bluetooth.cancelDiscovery(); }
        catch (SecurityException | IllegalStateException ignored) { }
        pendingNsdIndex = -1;
        stopNsd();
        if (p2p != null && p2pChannel != null) {
            try {
                p2p.stopPeerDiscovery(p2pChannel, new WifiP2pManager.ActionListener() {
                    @Override public void onSuccess() { }
                    @Override public void onFailure(int reason) { }
                });
            } catch (SecurityException | IllegalStateException ignored) { }
        }
        p2pChannel = null;
        try { if (multicastLock != null && multicastLock.isHeld()) multicastLock.release(); }
        catch (RuntimeException ignored) { }
        multicastLock = null;
        try { if (receiver != null) context.unregisterReceiver(receiver); }
        catch (IllegalArgumentException ignored) { }
        receiver = null;
        report("탐색 종료 · 이번 탐색에서 발견한 항목은 별도 표시됩니다");
    }

    private void discoverSsdp() {
        if (!running || !extendedLan || ssdp == null) return;
        final int token = generation;
        radio("UPNP", "로컬 네트워크 검색 중(요청 전송)");
        ssdp.discover(new SsdpDiscovery.Callback() {
            @Override public void found(String name, String id, String details) {
                if (running && generation == token) {
                    publish("UPNP", name, id, details, 0, true);
                }
            }
            @Override public void status(String message) {
                if (running && generation == token) radio("UPNP", message);
            }
        });
    }

    private void startBluetooth() {
        try {
            BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            bluetooth = manager == null ? null : manager.getAdapter();
            if (bluetooth == null) {
                report("블루투스 기능이 없는 단말입니다");
                radio("BLE", "하드웨어 미지원");
                radio("BT", "하드웨어 미지원");
                return;
            }
            if (!bluetooth.isEnabled()) {
                report("블루투스가 꺼져 있습니다 · 설정에서 켜주세요");
                radio("BLE", "블루투스 꺼짐");
                radio("BT", "블루투스 꺼짐");
                return;
            }
            ble = bluetooth.getBluetoothLeScanner();
            if (ble == null) {
                radio("BLE", "BLE 수신기 미지원");
                return;
            }
            final int callbackGeneration = generation;
            bleCallback = new ScanCallback() {
                @Override public void onScanResult(int callbackType, ScanResult result) {
                    if (callbackGeneration == generation) addBle(result);
                }

                @Override public void onBatchScanResults(List<ScanResult> results) {
                    if (callbackGeneration == generation) {
                        for (ScanResult result : results) addBle(result);
                    }
                }

                @Override public void onScanFailed(int errorCode) {
                    main.post(() -> {
                        if (callbackGeneration != generation || !running) return;
                        bleScanning = false;
                        radio("BLE", "실패 / 코드 " + errorCode);
                        report("BLE 스캔 실패 · Android 오류 코드 " + errorCode);
                    });
                }
            };
            scanBle(ScanSettings.SCAN_MODE_LOW_LATENCY);
        } catch (SecurityException | IllegalStateException e) {
            radio("BLE", "시작 실패 / 권한 확인");
            report("BLE 시작 실패 · 기기 권한이나 블루투스 상태를 확인하세요");
        }
    }


    private void scanBle(int mode) {
        if (!running || ble == null || bleCallback == null) return;
        try {
            if (bleScanning) {
                ble.stopScan(bleCallback);
                bleScanning = false;
            }
            ble.startScan(null, new ScanSettings.Builder().setScanMode(mode).build(), bleCallback);
            bleScanning = true;
            radio("BLE", mode == ScanSettings.SCAN_MODE_LOW_LATENCY
                    ? "집중 탐색" : "균형 탐색");
        } catch (SecurityException | IllegalStateException e) {
            bleScanning = false;
            radio("BLE", "검색 실패 / 블루투스 또는 권한 확인");
        }
    }

    private void beginClassicPhase() {
        if (!running || bluetooth == null) return;
        try {
            if (!bluetooth.isEnabled()) {
                radio("BT", "블루투스 꺼짐");
                return;
            }
            classicPhase = true;
            if (ble != null && bleScanning && bleCallback != null) {
                ble.stopScan(bleCallback);
                bleScanning = false;
                radio("BLE", "일반 BT 검색 중 일시중단");
            }
            boolean accepted = bluetooth.startDiscovery();
            radio("BT", accepted ? "기기 검색 중" : "검색 시작 거부");
            if (!accepted) {
                endClassicPhase();
                return;
            }
            main.postDelayed(this::endClassicPhase, ScanPlan.CLASSIC_MAX_MS);
        } catch (SecurityException | IllegalStateException e) {
            radio("BT", "시작 실패 / 권한 또는 단말 상태 확인");
            endClassicPhase();
        }
    }

    private void endClassicPhase() {
        if (!running || !classicPhase) return;
        classicPhase = false;
        try {
            if (bluetooth != null && bluetooth.isDiscovering()) bluetooth.cancelDiscovery();
        } catch (SecurityException | IllegalStateException ignored) { }
        radio("BT", "검색 단계 완료");
        scanBle(ScanSettings.SCAN_MODE_BALANCED);
    }

    private void retryP2p() {
        if (!running || p2p == null || p2pChannel == null) return;
        discoverP2p();
    }

    private void discoverP2p() {
        if (!running || p2p == null || p2pChannel == null) return;
        try {
            p2p.discoverPeers(p2pChannel, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() {
                    if (running) radio("P2P", "검색 중 · 피어 목록 변경 이벤트 대기");
                }
                @Override public void onFailure(int reason) {
                    if (running) radio("P2P", "검색 실패 / 코드 " + reason);
                }
            });
        } catch (SecurityException | IllegalStateException e) {
            radio("P2P", "검색 권한/상태 제한");
        }
    }

    private void addBle(ScanResult result) {
        if (!running || result == null || result.getDevice() == null) return;
        try {
            BluetoothDevice device = result.getDevice();
            String address = device.getAddress();
            ScanRecord record = result.getScanRecord();
            String name = record == null ? null : record.getDeviceName();
            if (name == null || name.isEmpty()) name = device.getName();
            StringBuilder detail = new StringBuilder("BLE 광고");
            if (record != null) {
                int tx = record.getTxPowerLevel();
                if (tx != Integer.MIN_VALUE) detail.append(" · 광고 TX ").append(tx).append(" dBm");
                SparseArray<byte[]> manufacturers = record.getManufacturerSpecificData();
                if (manufacturers != null && manufacturers.size() > 0) {
                    detail.append(" · 제조사 ID:");
                    for (int i = 0; i < Math.min(manufacturers.size(), 5); i++) {
                        int id = manufacturers.keyAt(i);
                        detail.append(' ').append(String.format(Locale.ROOT, "0x%04X", id));
                        String company = BleCompanyNames.forId(id);
                        if (!company.isEmpty()) {
                            detail.append(" (").append(company)
                                    .append(" 광고 회사 ID · 기기 제조사 확정 아님)");
                            if (name == null || name.isEmpty())
                                name = "광고 회사 ID: " + company;
                        }
                    }
                }
                if (record.getServiceUuids() != null && !record.getServiceUuids().isEmpty()) {
                    detail.append(" · 서비스 ").append(record.getServiceUuids().size()).append("개");
                    int services = Math.min(2, record.getServiceUuids().size());
                    for (int i = 0; i < services; i++) {
                        detail.append(" [").append(record.getServiceUuids().get(i)).append("]");
                    }
                }
            }
            String evidence = detail.toString();
            if (bleGate.accept(address, name, evidence, SystemClock.elapsedRealtime())) {
                publish("BLE", name, address, evidence, result.getRssi(), true);
            }
        } catch (SecurityException ignored) {
            report("BLE 기기 정보 접근 권한이 부족합니다");
        }
    }

    private void registerEvents() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                if (!running || intent == null) return;
                String action = intent.getAction();
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                    boolean fresh = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                    wifiBroadcasts++;
                    readWifi(fresh);
                    readConnectedWifi();
                } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    readClassic(intent);
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    radio("BT", "검색 완료");
                    if (classicPhase) endClassicPhase();
                } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                    readP2pPeers();
                }
            }
        };
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
    }

    private void readClassic(Intent intent) {
        try {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null) return;
            short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (short) 0);
            publish("BT", device.getName(), device.getAddress(),
                    "Bluetooth Classic · 검색에 응답한 기기", rssi, true);
        } catch (SecurityException ignored) {
            report("일반 블루투스 기기 정보 접근 제한");
        }
    }

    /** A connected access point is evidence of an association, not a new RF scan. */
    private void readConnectedWifi() {
        if (!running || wifi == null) return;
        try {
            WifiInfo info = null;
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                android.net.Network network = cm.getActiveNetwork();
                NetworkCapabilities caps = network == null
                        ? null : cm.getNetworkCapabilities(network);
                if (Build.VERSION.SDK_INT >= 29 && caps != null
                        && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        && caps.getTransportInfo() instanceof WifiInfo) {
                    info = (WifiInfo) caps.getTransportInfo();
                }
            }
            if (info == null) info = wifi.getConnectionInfo(); // Old device compatibility.
            if (info == null) return;
            String bssid = info.getBSSID();
            if (!usableBssid(bssid)) return;
            String ssid = info.getSSID();
            if (ssid != null && ssid.length() >= 2
                    && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length() - 1);
            }
            if (ssid == null || ssid.isEmpty() || "<unknown ssid>".equalsIgnoreCase(ssid))
                ssid = "현재 연결된 Wi-Fi AP";
            int freq = info.getFrequency();
            int channel = SignalRules.wifiChannel(freq);
            String detail = "현재 연결 중인 공유기 · Wi-Fi 연결 정보"
                    + (freq > 0 ? " · " + SignalRules.wifiBand(freq) + " "
                    + freq + " MHz" + (channel > 0 ? " CH" + channel : "") : "")
                    + " · 새로운 AP 스캔 결과와 별도 검증";
            wifiConnected = true;
            publish("WIFI", ssid, bssid, detail, info.getRssi(), true);
            radio("WIFI", wifiSummary());
        } catch (SecurityException | IllegalStateException e) {
            wifiHadPermissionError = true;
            radio("WIFI", "연결 정보 접근 제한 · 정확한 위치 권한 확인");
        }
    }

    private static boolean usableBssid(String bssid) {
        return bssid != null && bssid.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")
                && !"02:00:00:00:00:00".equals(bssid)
                && !"00:00:00:00:00:00".equals(bssid)
                && !"ff:ff:ff:ff:ff:ff".equalsIgnoreCase(bssid);
    }

    private String wifiSummary() {
        if (wifiHadPermissionError)
            return "Wi-Fi 결과 접근 제한 · 정확한 위치 권한/위치 서비스 확인";
        return "AP 목록 " + wifiLastRows + "개"
                + " · 신규 AP " + wifiFreshKeys.size() + "개"
                + " · 요청 허용 " + wifiAccepted + "/" + wifiRequests
                + " · 결과 알림 " + wifiBroadcasts
                + (wifiConnected ? " · 연결 AP 확인" : " · 연결 AP 미확인")
                + (wifiLastRows == 0 && !wifiConnected
                    ? " · Wi-Fi 연결/스캔 제한 확인 필요" : "");
    }

    private void requestWifi() {
        if (!running || wifi == null) return;
        try {
            wifiRequests++;
            boolean accepted = wifi.startScan();
            if (accepted) wifiAccepted++;
            readWifi(false); // Cached records are not newly sighted radio APs.
            readConnectedWifi(); // Separate source: currently associated AP.
            if (!accepted) radio("WIFI", wifiSummary() + " · 새 검색 요청 제한");
            else radio("WIFI", wifiSummary());
        } catch (SecurityException | IllegalStateException e) {
            wifiHadPermissionError = true;
            radio("WIFI", "Wi-Fi 스캔 차단 · 위치 권한/서비스 및 운영체제 설정 확인");
        }
        if (running) main.postDelayed(this::requestWifi, ScanPlan.WIFI_INTERVAL_MS);
    }

    private void readWifi(boolean updated) {
        if (!running || wifi == null) return;
        try {
            List<android.net.wifi.ScanResult> results = wifi.getScanResults();
            if (results == null) {
                wifiLastRows = 0;
                radio("WIFI", wifiSummary() + " · 결과 목록 null");
                return;
            }
            int valid = 0;
            int newRows = 0;
            long nowUs = SystemClock.elapsedRealtimeNanos() / 1000L;
            for (android.net.wifi.ScanResult r : results) {
                if (!usableBssid(r.BSSID)) continue;
                valid++;
                String name = r.SSID == null || r.SSID.isEmpty() ? "숨김 Wi-Fi" : r.SSID;
                boolean fresh = ScanFreshness.isWifiFresh(
                        updated, r.timestamp, sessionStartedElapsedUs, nowUs);
                if (fresh) { newRows++; wifiFreshKeys.add(r.BSSID); }
                int channel = SignalRules.wifiChannel(r.frequency);
                String detail = SignalRules.wifiBand(r.frequency) + " / "
                        + r.frequency + " MHz"
                        + (channel > 0 ? " · CH " + channel : "")
                        + " · " + r.capabilities
                        + (fresh ? " · 이번 탐색 AP 스캔" :
                        " · 과거 스캔 캐시(현재 주변 미확인)");
                publish("WIFI", name, r.BSSID, detail, r.level, fresh);
            }
            wifiLastRows = valid;
            radio("WIFI", wifiSummary() +
                    (updated && newRows == 0 ? " · 갱신 알림이나 신규 AP 없음" : ""));
        } catch (SecurityException | IllegalStateException e) {
            wifiHadPermissionError = true;
            radio("WIFI", "getScanResults 접근 제한 · 정확한 위치 권한/위치 서비스 확인");
        }
    }

    private void discoverLan() {
        if (!running || !lanProbeEnabled || lanDiscovery == null) return;
        final int token = generation;
        lanDiscovery.discover(context, new LanDiscovery.Callback() {
            @Override public void found(String ip, String description) {
                if (running && token == generation)
                    publish("LAN", "LAN 응답 " + ip, ip, description, 0, true);
            }

            @Override public void status(String message) {
                if (running && token == generation)
                    radio("LAN", message);
            }
        });
    }

    private void startP2p() {
        try {
            p2p = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
            if (p2p == null) { radio("P2P", "기능 미지원"); return; }
            p2pChannel = p2p.initialize(context, Looper.getMainLooper(), () -> {
                p2pChannel = null;
                radio("P2P", "채널 종료");
                report("Wi-Fi Direct 채널 종료");
            });
            if (p2pChannel == null) { radio("P2P", "채널 미연결"); return; }
            discoverP2p();
        } catch (SecurityException | IllegalStateException e) {
            radio("P2P", "권한 또는 기능 제한");
            report("Wi-Fi Direct 검색 권한이나 기능이 제한되었습니다");
        }
    }

    private void readP2pPeers() {
        if (!running || p2p == null || p2pChannel == null) return;
        try {
            p2p.requestPeers(p2pChannel, peers -> {
                if (!running || peers == null) return;
                for (WifiP2pDevice d : peers.getDeviceList()) {
                    publish("P2P", d.deviceName, d.deviceAddress,
                            "Wi-Fi Direct 피어 목록 업데이트 · 현재 존재 확인에는 한계 · 연결 없음", 0, true);
                }
            });
        } catch (SecurityException | IllegalStateException ignored) {
            report("Wi-Fi Direct 목록을 읽지 못했습니다");
        }
    }

    /**
     * Android NSD stop is asynchronous. Do not start a new service-type scan
     * until onDiscoveryStopped confirms the old listener was released.
     */
    private void startNsdCycle(int index) {
        if (!running || nsd == null) return;
        if (activeNsd != null) {
            pendingNsdIndex = index;
            stopNsd();
            return;
        }
        pendingNsdIndex = -1;
        String type = SERVICE_TYPES[index % SERVICE_TYPES.length];
        final int callbackGeneration = generation;
        NsdManager.DiscoveryListener discovery = new NsdManager.DiscoveryListener() {
            private boolean current() {
                return running && generation == callbackGeneration
                        && activeNsd == this;
            }

            @Override public void onDiscoveryStarted(String serviceType) {
                if (current()) radio("NSD", "검색 중 · " + serviceType);
            }

            @Override public void onServiceFound(NsdServiceInfo info) {
                if (!current() || info == null) return;
                String unique = info.getServiceType() + "/" + info.getServiceName();
                publish("NSD", info.getServiceName(), unique,
                        "mDNS/DNS-SD 응답 · " + info.getServiceType()
                                + " · 연결된 로컬 네트워크", 0, true);
            }

            @Override public void onServiceLost(NsdServiceInfo info) { }

            @Override public void onDiscoveryStopped(String serviceType) {
                if (activeNsd != this) return;
                activeNsd = null;
                nsdStopping = false;
                nsdStopRetries = 0;
                int nextIndex = pendingNsdIndex;
                pendingNsdIndex = -1;
                // A stop from an older scan can finish after a user restarts.
                // Start the new session's pending index, not the old token.
                if (running && nextIndex >= 0)
                    main.post(() -> { if (running) startNsdCycle(nextIndex); });
            }

            @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                if (activeNsd != this) return;
                activeNsd = null;
                nsdStopping = false;
                int next = pendingNsdIndex;
                pendingNsdIndex = -1;
                if (running && generation == callbackGeneration)
                    radio("NSD", "서비스 검색 시작 실패 · " + errorCode);
                if (running && next >= 0) main.post(() -> startNsdCycle(next));
            }

            @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                if (activeNsd != this) return;
                nsdStopping = false;
                // Retry a bounded number of times. Do not overlap discoveries.
                if (++nsdStopRetries <= 2) {
                    main.postDelayed(() -> {
                        if (activeNsd == this) stopNsd();
                    }, 1800L);
                } else {
                    pendingNsdIndex = -1;
                    if (running) radio("NSD", "이전 검색 종료 실패 · " + errorCode);
                }
            }
        };
        try {
            activeNsd = discovery;
            nsdStopRetries = 0;
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discovery);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            if (activeNsd == discovery) activeNsd = null;
            radio("NSD", "서비스 검색 예외 · " + e.getClass().getSimpleName());
        }
        main.postDelayed(() -> startNsdCycle(index + 1), ScanPlan.NSD_INTERVAL_MS);
    }

    private void stopNsd() {
        if (nsd == null || activeNsd == null || nsdStopping) return;
        try {
            nsdStopping = true;
            nsd.stopServiceDiscovery(activeNsd);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            nsdStopping = false;
            pendingNsdIndex = -1;
            radio("NSD", "서비스 종료 실패 · " + e.getClass().getSimpleName());
        }
    }

    private void publish(String protocol, String name, String id,
                         String details, int rssi, boolean fresh) {
        if (id == null || id.isEmpty()) return;
        final int token = generation;
        main.post(() -> {
            if (running && generation == token)
                listener.onDevice(protocol, name, id, details, rssi, fresh);
        });
    }

    private void report(String message) {
        final int token = generation;
        main.post(() -> {
            if (generation == token) listener.onStatus(message);
        });
    }

    private void radio(String protocol, String state) {
        final int token = generation;
        main.post(() -> {
            if (generation == token) listener.onRadioState(protocol, state);
        });
    }
}
