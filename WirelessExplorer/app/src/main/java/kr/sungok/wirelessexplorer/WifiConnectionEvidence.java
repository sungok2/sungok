package kr.sungok.wirelessexplorer;

/** Redacted/unknown Wi-Fi connection information must never be counted as an AP. */
public final class WifiConnectionEvidence {
    private WifiConnectionEvidence() {}

    public static boolean usableBssid(String bssid) {
        if (bssid == null || !bssid.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"))
            return false;
        return !"02:00:00:00:00:00".equalsIgnoreCase(bssid)
                && !"00:00:00:00:00:00".equalsIgnoreCase(bssid)
                && !"ff:ff:ff:ff:ff:ff".equalsIgnoreCase(bssid);
    }

    public static String displaySsid(String raw) {
        if (raw == null) return "현재 연결된 Wi-Fi AP";
        String ssid = raw.trim();
        if (ssid.length() >= 2 && ssid.startsWith("\"") && ssid.endsWith("\""))
            ssid = ssid.substring(1, ssid.length() - 1);
        ssid = ObservationRules.sanitize(ssid, 120);
        if (ssid.isEmpty() || "<unknown ssid>".equalsIgnoreCase(ssid))
            return "현재 연결된 Wi-Fi AP";
        return ssid;
    }
}
