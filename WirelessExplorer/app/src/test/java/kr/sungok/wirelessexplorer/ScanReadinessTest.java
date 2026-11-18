package kr.sungok.wirelessexplorer;

import static org.junit.Assert.*;
import org.junit.Test;

public class ScanReadinessTest {
    @Test public void missingPermissionsAreExplicit() {
        String status = ScanReadiness.describe(true, true, true, true,
                false, false, false, false);
        assertTrue(status.contains("주변 기기 권한 필요"));
        assertTrue(status.contains("Wi-Fi 기기 권한 확인"));
        assertTrue(status.contains("꺼짐 · Wi-Fi 검색 제한 가능"));
        assertTrue(status.contains("미허용"));
    }

    @Test public void hardwareUnsupportedIsNeverReportedAvailable() {
        String status = ScanReadiness.describe(false, false, false, false,
                true, true, true, true);
        assertTrue(status.contains("하드웨어 미지원"));
        assertFalse(status.contains("Bluetooth: 켜짐"));
    }
}
