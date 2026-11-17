package kr.sungok.wirelessexplorer;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScanPlanTest {
    @Test public void classicHasSeparateWindowAndBleResumes() {
        assertTrue(ScanPlan.valid());
        assertTrue(ScanPlan.BLE_LOW_LATENCY_MS + ScanPlan.CLASSIC_MAX_MS < ScanPlan.SESSION_MS);
        assertTrue(ScanPlan.P2P_RETRY_MS < ScanPlan.SESSION_MS);
    }
}
