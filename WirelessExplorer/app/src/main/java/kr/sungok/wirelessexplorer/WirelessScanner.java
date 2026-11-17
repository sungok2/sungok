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
import android.net.wifi.ScanResult.InformationElement;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.bluetooth.le.ScanRecord;
import android.util.SparseArray;

import java.util.List;
import java.util.Locale;

/**
 * Passive discovery of discoverable endpoints only.
 * Does not connect, pair, authenticate, interrogate ports or decode RF payloads.
 */
@SuppressWarnings("deprecation")
public final class WirelessScanner {
    public interface Listener {
        void onDevice(String protocol, String name, String id, String details, int rssi, boolean fresh);
        void onStatus(String message);
    }

    private static final int SESSION_MS = 120_000;
    private static final int WIFI_INTERVAL_MS = 35_000;
    private static final int NSD_INTERVAL_MS = 14_000;
    private static final String[] SERVICE_TYPES = {
            "_http._tcp.", "_ipp._tcp.", "_googlecast._tcp.",
            "_airplay._tcp.", "_workstation._tcp.", "_smb._tcp.",
            "_hap._tcp.", "_raop._tcp."
    };

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private BluetoothAdapter bluetooth;
    private BluetoothLeScanner ble;
    private WifiManager wifi;
    private WifiP2pManager p2p;
    private WifiP2pManager.Channel p2pChannel;
    private NsdManager nsd;
    private NsdManager.DiscoveryListener activeNsd;
    private ScanCallback bleCallback;
    private BroadcastReceiver receiver;
    private boolean running;

    public WirelessScanner(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public boolean isRunning() { return running; }

    public void start() {
        if (running) return;
        running = true;
        report("탐색 시작 · 120초 동안 수신된 신호를 수집합니다");
        registerEvents();
        startBluetooth();
        wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        requestWifi();
        nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        startNsdCycle(0);
        startP2p();
        main.postDelayed(() -> {
            if (!running || bluetooth == null) return;
            try {
                if (bluetooth.isEnabled()) bluetooth.startDiscovery();
            } catch (SecurityException | IllegalStateException e) {
                report("일반 블루투스 검색 실패: " + e.getClass().getSimpleName());
            }
        }, 15_000);
        main.postDelayed(this::stop, SESSION_MS);
    }

    public void stop() {
        if (!running) return;
        running = false;
        main.removeCallbacksAndMessages(null);
        try { if (ble != null && bleCallback != null) ble.stopScan(bleCallback); }
        catch (SecurityException | IllegalStateException ignored) { }
        try { if (bluetooth != null && bluetooth.isDiscovering()) bluetooth.cancelDiscovery(); }
        catch (SecurityException | IllegalStateException ignored) { }
        stopNsd();
        try { if (receiver != null) context.unregisterReceiver(receiver); }
        catch (IllegalArgumentException ignored) { }
        receiver = null;
        report("탐색 종료 · 이번 탐색에서 발견한 항목은 별도 표시됩니다");
    }

    private void startBluetooth() {
        try {
            BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            bluetooth = manager == null ? null : manager.getAdapter();
            if (bluetooth == null) {
                report("블루투스 기능이 없는 단말입니다");
                return;
            }
            if (!bluetooth.isEnabled()) {
                report("블루투스가 꺼져 있습니다 · 설정에서 켜주세요");
                return;
            }
            ble = bluetooth.getBluetoothLeScanner();
            if (ble == null) return;
            bleCallback = new ScanCallback() {
                @Override public void onScanResult(int callbackType, ScanResult result) {
                    addBle(result);
                }

                @Override public void onBatchScanResults(List<ScanResult> results) {
                    for (ScanResult result : results) addBle(result);
                }

                @Override public void onScanFailed(int errorCode) {
                    report("BLE 스캔 실패 · Android 오류 코드 " + errorCode);
                }
            };
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            ble.startScan(null, settings, bleCallback);
        } catch (SecurityException | IllegalStateException e) {
            report("BLE 시작 실패 · 기기 권한이나 블루투스 상태를 확인하세요");
        }
    }

    private void addBle(ScanResult result) {
        if (!running || result == null || result.getDevice() == null) return;
        try {
            BluetoothDevice device = result.getDevice();
            String address = device.getAddress();
            ScanRecord record = result.getScanRecord();
            String name = record == null ? null : record.getDeviceName();
            if ((name == null || name.isEmpty()) && Build.VERSION.SDK_INT >= 31) {
                name = device.getName();
            }
            StringBuilder detail = new StringBuilder("BLE 광고");
            if (record != null) {
                SparseArray<byte[]> manufacturers = record.getManufacturerSpecificData();
                if (manufacturers != null && manufacturers.size() > 0) {
                    detail.append(" · 제조사 ID:");
                    for (int i = 0; i < Math.min(manufacturers.size(), 5); i++) {
                        detail.append(' ').append(String.format(Locale.ROOT, "0x%04X",
                                manufacturers.keyAt(i)));
                    }
                }
                if (record.getServiceUuids() != null && !record.getServiceUuids().isEmpty()) {
                    detail.append(" · 서비스 ").append(record.getServiceUuids().size()).append("개");
                }
            }
            publish("BLE", name, address, detail.toString(), result.getRssi(), true);
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
                    readWifi(fresh);
                } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    readClassic(intent);
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

    private void requestWifi() {
        if (!running || wifi == null) return;
        try {
            boolean accepted = wifi.startScan();
            if (!accepted) report("Wi-Fi 새 검색이 제한되었습니다 · 기존 결과만 확인");
            readWifi(false); // Explicitly labeled as cached, not a fresh discovery.
        } catch (SecurityException | IllegalStateException e) {
            report("Wi-Fi 탐색을 위해 위치 권한과 위치 서비스를 켜주세요");
        }
        main.postDelayed(this::requestWifi, WIFI_INTERVAL_MS);
    }

    private void readWifi(boolean fresh) {
        if (!running || wifi == null) return;
        try {
            List<android.net.wifi.ScanResult> results = wifi.getScanResults();
            if (results == null) return;
            for (android.net.wifi.ScanResult r : results) {
                if (r.BSSID == null || r.BSSID.isEmpty()) continue;
                String name = r.SSID == null || r.SSID.isEmpty() ? "숨김 Wi-Fi" : r.SSID;
                String detail = r.frequency + " MHz · " + r.capabilities
                        + (fresh ? " · 새 스캔" : " · 캐시 결과");
                publish("WIFI", name, r.BSSID, detail, r.level, fresh);
            }
        } catch (SecurityException | IllegalStateException e) {
            report("Wi-Fi 결과를 읽을 수 없습니다 · 위치 권한/서비스 확인");
        }
    }

    private void startP2p() {
        try {
            p2p = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
            if (p2p == null) return;
            p2pChannel = p2p.initialize(context, Looper.getMainLooper(), () -> {
                p2pChannel = null;
                report("Wi-Fi Direct 채널 종료");
            });
            if (p2pChannel == null) return;
            p2p.discoverPeers(p2pChannel, new WifiP2pManager.ActionListener() {
                @Override public void onSuccess() { readP2pPeers(); }
                @Override public void onFailure(int reason) {
                    report("Wi-Fi Direct 검색 불가 · 코드 " + reason);
                }
            });
        } catch (SecurityException | IllegalStateException e) {
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
                            "Wi-Fi Direct 응답 기기 · 연결 시도 없음", 0, true);
                }
            });
        } catch (SecurityException | IllegalStateException ignored) {
            report("Wi-Fi Direct 목록을 읽지 못했습니다");
        }
    }

    private void startNsdCycle(int index) {
        if (!running || nsd == null) return;
        stopNsd();
        String type = SERVICE_TYPES[index % SERVICE_TYPES.length];
        NsdManager.DiscoveryListener discovery = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String serviceType) { }
            @Override public void onServiceFound(NsdServiceInfo info) {
                if (!running || info == null) return;
                String unique = info.getServiceType() + "/" + info.getServiceName();
                publish("NSD", info.getServiceName(), unique,
                        "mDNS/DNS-SD 응답 · " + info.getServiceType()
                                + " · 동일 로컬 네트워크", 0, true);
            }
            @Override public void onServiceLost(NsdServiceInfo info) { }
            @Override public void onDiscoveryStopped(String serviceType) { }
            @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                report("mDNS 서비스 탐색 제한 · " + errorCode);
            }
            @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) { }
        };
        try {
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discovery);
            activeNsd = discovery;
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            report("mDNS 탐색 오류 · " + type);
        }
        main.postDelayed(() -> startNsdCycle(index + 1), NSD_INTERVAL_MS);
    }

    private void stopNsd() {
        if (nsd == null || activeNsd == null) return;
        try { nsd.stopServiceDiscovery(activeNsd); }
        catch (IllegalArgumentException | IllegalStateException ignored) { }
        activeNsd = null;
    }

    private void publish(String protocol, String name, String id,
                         String details, int rssi, boolean fresh) {
        if (id == null || id.isEmpty()) return;
        main.post(() -> {
            if (running) listener.onDevice(protocol, name, id, details, rssi, fresh);
        });
    }

    private void report(String message) {
        main.post(() -> listener.onStatus(message));
    }
}
