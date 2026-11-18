package kr.sungok.wirelessexplorer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prevents high-frequency BLE advertisements from flooding the UI thread.
 * First sightings are never suppressed. A later name or advertising metadata
 * change is surfaced after a short minimum interval; otherwise a sample is
 * delivered at least once per second for RSSI tracking.
 */
public final class ObservationThrottle {
    private static final int MAX_KEYS = 4096;
    private static final long MIN_INTERVAL_MS = 350;
    private static final long REGULAR_INTERVAL_MS = 1000;

    private static final class Seen {
        long lastDeliveredMs;
        int metadataHash;
        Seen(long now, int hash) { lastDeliveredMs = now; metadataHash = hash; }
    }

    private final LinkedHashMap<String, Seen> entries = new LinkedHashMap<>(256, .75f, true);

    public synchronized boolean accept(String id, String name, String details, long elapsedMs) {
        if (id == null || id.isEmpty()) return false;
        int hash = 31 * (name == null ? 0 : name.hashCode())
                + (details == null ? 0 : details.hashCode());
        Seen prev = entries.get(id);
        if (prev == null) {
            entries.put(id, new Seen(elapsedMs, hash));
            if (entries.size() > MAX_KEYS) {
                Iterator<Map.Entry<String, Seen>> iter = entries.entrySet().iterator();
                if (iter.hasNext()) { iter.next(); iter.remove(); }
            }
            return true;
        }
        long elapsed = elapsedMs - prev.lastDeliveredMs;
        if (elapsed >= 0 && elapsed < MIN_INTERVAL_MS) return false;
        if (elapsed >= 0 && elapsed < REGULAR_INTERVAL_MS && prev.metadataHash == hash)
            return false;
        prev.lastDeliveredMs = elapsedMs;
        prev.metadataHash = hash;
        return true;
    }

    public synchronized void clear() { entries.clear(); }
    public synchronized int size() { return entries.size(); }
}
