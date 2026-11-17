package kr.sungok.wirelessexplorer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Parse only SSDP response headers. Never follow LOCATION or HTTP links. */
public final class SsdpHeaders {
    private SsdpHeaders() { }

    public static Map<String, String> parse(String packet) {
        Map<String, String> values = new HashMap<>();
        if (packet == null || packet.length() > 8192) return values;
        String[] lines = packet.split("\\r?\\n", 70);
        if (lines.length == 0 || !lines[0].trim().matches("HTTP/1\\.[01] 200(?: .*)?")) return values;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon < 1) continue;
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            if (!name.equals("st") && !name.equals("usn") && !name.equals("server")
                    && !name.equals("location")) continue;
            String value = ObservationRules.sanitize(line.substring(colon + 1), 300);
            if (!value.isEmpty() && !values.containsKey(name)) values.put(name, value);
        }
        return values;
    }
}
