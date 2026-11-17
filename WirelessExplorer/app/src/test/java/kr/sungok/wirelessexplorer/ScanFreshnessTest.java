package kr.sungok.wirelessexplorer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ScanFreshnessTest {
    @Test public void rejectCachedResultsEvenAfterSuccessfulBroadcast() {
        assertFalse(ScanFreshness.isWifiFresh(true, 199_000_000L, 200_000_000L, 204_000_000L));
        assertFalse(ScanFreshness.isWifiFresh(false, 202_000_000L, 200_000_000L, 204_000_000L));
    }
    @Test public void acceptTrulyNewResults() {
        assertTrue(ScanFreshness.isWifiFresh(true, 201_000_000L, 200_000_000L, 204_000_000L));
    }
    @Test public void rejectInvalidOrImplausibleTimestamps() {
        assertFalse(ScanFreshness.isWifiFresh(true, 0, 200, 500));
        assertFalse(ScanFreshness.isWifiFresh(true, 900_000_000L, 200_000_000L, 204_000_000L));
    }
}
