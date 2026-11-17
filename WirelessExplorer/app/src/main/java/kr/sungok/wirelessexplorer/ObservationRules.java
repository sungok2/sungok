package kr.sungok.wirelessexplorer;

import java.util.Locale;

/**
 * Pure input normalization and evidence rules. Radio endpoint IDs are not
 * proof of a stable physical identity (BLE randomized addresses, multiple AP
 * BSSIDs, and DNS-SD service instances can belong to one physical device).
 */
public final class ObservationRules {
    private ObservationRules() { }

    public static String sanitize(String raw, int limit) {
        if (raw == null || limit <= 0) return "";
        StringBuilder b = new StringBuilder(Math.min(limit, raw.length()));
        for (int i = 0; i < raw.length() && b.length() < limit; i++) {
            char c = raw.charAt(i);
            if (Character.isISOControl(c)) {
                if (c == '\n' || c == '\r' || c == '\t') b.append(' ');
            } else {
                b.append(c);
            }
        }
        return b.toString().trim();
    }

    public static String normalizedId(String protocol, String id) {
        String cleaned = sanitize(id, 240);
        if (cleaned.isEmpty()) return "";
        if ("BLE".equals(protocol) || "BT".equals(protocol)
                || "WIFI".equals(protocol) || "P2P".equals(protocol)) {
            // Handle both 00:11:22... and 00-11-22... without changing opaque IDs.
            String candidate = cleaned.replace('-', ':');
            if (candidate.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")) {
                return candidate.toUpperCase(Locale.ROOT);
            }
        }
        return cleaned;
    }

    public static boolean isRadioEvidence(String protocol) {
        return "BLE".equals(protocol) || "BT".equals(protocol)
                || "WIFI".equals(protocol) || "P2P".equals(protocol);
    }

    /** BLE and Classic addresses may be shared, but equality is only a hint. */
    public static boolean sharedBluetoothAddressCandidate(String proto1, String id1,
                                                           String proto2, String id2) {
        if (proto1 == null || proto2 == null || id1 == null || id2 == null) return false;
        boolean differentRadio = ("BLE".equals(proto1) && "BT".equals(proto2))
                || ("BT".equals(proto1) && "BLE".equals(proto2));
        if (!differentRadio) return false;
        String a = normalizedId(proto1, id1);
        String b = normalizedId(proto2, id2);
        return a.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}") && a.equals(b);
    }
}
