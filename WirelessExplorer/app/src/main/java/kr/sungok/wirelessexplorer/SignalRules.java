package kr.sungok.wirelessexplorer;

/** Pure signal metadata rules. RSSI cannot give a reliable absolute distance. */
public final class SignalRules {
    private SignalRules() {}

    public static String labelFor(String raw, String fallback) {
        if (raw == null) return fallback;
        String value = raw.trim();
        return value.isEmpty() ? fallback : value;
    }

    public static String strength(int rssi) {
        if (rssi == 0 || rssi > 0 || rssi < -127) return "미지원";
        if (rssi >= -55) return "매우 강함";
        if (rssi >= -70) return "강함";
        if (rssi >= -85) return "보통";
        return "약함";
    }

    public static String category(String protocol, String name) {
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (protocol.equals("WIFI")) return "Wi-Fi 접속점";
        if (protocol.equals("P2P")) return "Wi-Fi Direct 기기";
        if (protocol.equals("NSD")) return "로컬 네트워크 서비스";
        if (protocol.equals("UPNP")) return "UPnP 로컬 네트워크 서비스";
        if (protocol.equals("LAN")) return "LAN TCP 응답 주소(유·무선 미확인)";
        if (n.contains("watch") || n.contains("band")) return "웨어러블 추정";
        if (n.contains("buds") || n.contains("headphone") || n.contains("airpods")) return "오디오 기기 추정";
        if (n.contains("tv") || n.contains("cast")) return "미디어 기기 추정";
        return "미분류 무선기기";
    }

    public static String wifiBand(int mhz) {
        if (mhz >= 2400 && mhz <= 2500) return "2.4 GHz";
        if (mhz >= 4900 && mhz < 5925) return "5 GHz";
        if (mhz >= 5925 && mhz <= 7125) return "6 GHz";
        if (mhz >= 57000 && mhz <= 71000) return "60 GHz";
        return "대역 미분류";
    }

    public static int wifiChannel(int mhz) {
        if (mhz == 2484) return 14;
        if (mhz >= 2412 && mhz <= 2472 && (mhz - 2407) % 5 == 0)
            return (mhz - 2407) / 5;
        if (mhz >= 5000 && mhz < 5925 && (mhz - 5000) % 5 == 0)
            return (mhz - 5000) / 5;
        if (mhz >= 5955 && mhz <= 7115 && (mhz - 5950) % 5 == 0)
            return (mhz - 5950) / 5;
        return 0;
    }

    public static String csv(String value) {
        String s = value == null ? "" : value;
        // Device names and service labels are untrusted; protect CSV spreadsheet viewers.
        int first = 0;
        while (first < s.length() && Character.isWhitespace(s.charAt(first))) first++;
        if (first < s.length() && "=+-@".indexOf(s.charAt(first)) >= 0) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
