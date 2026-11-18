package kr.sungok.wirelessexplorer;

import static org.junit.Assert.*;
import org.junit.Test;

public class WifiConnectionEvidenceTest {
    @Test public void onlyValidNonRedactedConnectionBssidsAreEvidence() {
        assertTrue(WifiConnectionEvidence.usableBssid("78:A1:66:53:D9:1D"));
        assertTrue(WifiConnectionEvidence.usableBssid("02:55:11:33:44:66"));
        assertFalse(WifiConnectionEvidence.usableBssid("02:00:00:00:00:00"));
        assertFalse(WifiConnectionEvidence.usableBssid("00:00:00:00:00:00"));
        assertFalse(WifiConnectionEvidence.usableBssid("ff:ff:ff:ff:ff:ff"));
        assertFalse(WifiConnectionEvidence.usableBssid("unknown"));
        assertFalse(WifiConnectionEvidence.usableBssid(null));
    }

    @Test public void handlesQuotedAndRedactedSsidWithoutInventingName() {
        assertEquals("Home WiFi",WifiConnectionEvidence.displaySsid("\"Home WiFi\""));
        assertEquals("현재 연결된 Wi-Fi AP",
                WifiConnectionEvidence.displaySsid("<unknown ssid>"));
        assertEquals("현재 연결된 Wi-Fi AP",WifiConnectionEvidence.displaySsid(null));
        assertEquals("현재 연결된 Wi-Fi AP",WifiConnectionEvidence.displaySsid("\"\""));
    }
}
