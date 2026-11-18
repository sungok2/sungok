package kr.sungok.wirelessexplorer;

/** UI diagnostics, never a claim that all nearby transmitters are discoverable. */
public final class ScanReadiness {
    private ScanReadiness() {}

    public static String describe(boolean hasBluetooth, boolean bluetoothEnabled,
                                  boolean hasWifi, boolean wifiEnabled,
                                  boolean locationEnabled, boolean preciseAllowed,
                                  boolean bluetoothPermission, boolean wifiPermission) {
        StringBuilder out = new StringBuilder("사전 점검\n");
        out.append("Bluetooth: ");
        if (!hasBluetooth) out.append("하드웨어 미지원");
        else if (!bluetoothPermission) out.append("주변 기기 권한 필요");
        else out.append(bluetoothEnabled ? "켜짐" : "꺼짐");
        out.append("\nWi-Fi: ");
        if (!hasWifi) out.append("하드웨어 미지원");
        else if (!wifiPermission) out.append("Wi-Fi 기기 권한 확인");
        else out.append(wifiEnabled ? "켜짐" : "꺼짐(스캔 가능 여부는 단말 설정에 따름)");
        out.append("\n위치 서비스: ").append(locationEnabled ? "켜짐" : "꺼짐 · Wi-Fi 검색 제한 가능");
        out.append("\n정확한 위치 권한: ").append(preciseAllowed ? "허용" : "미허용 · Wi-Fi 검색 결과 제한");
        return out.toString();
    }
}
