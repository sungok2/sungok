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
        if (n.contains("watch") || n.contains("band")) return "웨어러블 추정";
        if (n.contains("buds") || n.contains("headphone") || n.contains("airpods")) return "오디오 기기 추정";
        if (n.contains("tv") || n.contains("cast")) return "미디어 기기 추정";
        return "미분류 무선기기";
    }

    public static String csv(String value) {
        String s = value == null ? "" : value;
        // Device names and service labels are untrusted; protect CSV spreadsheet viewers.
        String leading = s.stripLeading();
        if (!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
