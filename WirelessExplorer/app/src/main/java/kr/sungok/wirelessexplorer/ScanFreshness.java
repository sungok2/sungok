package kr.sungok.wirelessexplorer;

/**
 * Classify Wi-Fi scan result freshness without treating Android's cached APs
 * as direct evidence that an access point is presently nearby.
 */
public final class ScanFreshness {
    private ScanFreshness() {}

    /**
     * The Wi-Fi result timestamp is microseconds since boot.
     * A new-results broadcast is necessary but not sufficient: the scan list
     * may contain individual AP readings collected before this session.
     */
    public static boolean isWifiFresh(boolean scanResultsUpdated,
                                      long resultTimestampUs,
                                      long sessionStartedElapsedUs,
                                      long nowElapsedUs) {
        if (!scanResultsUpdated || resultTimestampUs <= 0 || sessionStartedElapsedUs <= 0) {
            return false;
        }
        return resultTimestampUs >= sessionStartedElapsedUs
                && resultTimestampUs <= nowElapsedUs + 2_000_000L;
    }
}
