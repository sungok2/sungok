package kr.sungok.wirelessexplorer;

/** Session timing is visible and unit-testable; none of these imply RF coverage. */
public final class ScanPlan {
    private ScanPlan() { }
    public static final long SESSION_MS = 180_000L;
    public static final long BLE_LOW_LATENCY_MS = 40_000L;
    public static final long CLASSIC_MAX_MS = 25_000L;
    public static final long P2P_RETRY_MS = 100_000L;
    public static final long WIFI_INTERVAL_MS = 35_000L;
    public static final long NSD_INTERVAL_MS = 13_500L;
    public static boolean valid() {
        return BLE_LOW_LATENCY_MS > 0
                && CLASSIC_MAX_MS >= 15_000
                && BLE_LOW_LATENCY_MS + CLASSIC_MAX_MS < SESSION_MS
                && P2P_RETRY_MS < SESSION_MS
                && WIFI_INTERVAL_MS >= 30_000;
    }
}
