package kr.sungok.wirelessexplorer;

import org.junit.Test;
import static org.junit.Assert.*;

public class ObservationRulesTest {
    @Test public void canonicalMacAndOpaqueServicesRemainDistinct() {
        assertEquals("AA:BB:CC:DD:EE:FF",
                ObservationRules.normalizedId("BLE", "aa-bb-cc-dd-ee-ff"));
        assertEquals("AA:BB:CC:DD:EE:FF",
                ObservationRules.normalizedId("BT", "Aa:Bb:Cc:Dd:Ee:Ff"));
        assertEquals("_ipp._tcp./My Printer",
                ObservationRules.normalizedId("NSD", " _ipp._tcp./My Printer "));
    }

    @Test public void preventControlCharactersFromEnteringRecords() {
        assertEquals("Unit name w spaces", ObservationRules.sanitize(
                "Unit\u0000 name\tw\nspaces", 100));
        assertEquals("12345", ObservationRules.sanitize("123456789", 5));
        assertEquals("", ObservationRules.normalizedId("BLE", "  \n "));
    }

    @Test public void doNotMergePhysicalDevicesWithoutEvidence() {
        assertTrue(ObservationRules.sharedBluetoothAddressCandidate(
                "BLE", "aa:bb:cc:dd:ee:ff", "BT", "AA-BB-CC-DD-EE-FF"));
        assertFalse(ObservationRules.sharedBluetoothAddressCandidate(
                "WIFI", "aa:bb:cc:dd:ee:ff", "BLE", "AA-BB-CC-DD-EE-FF"));
        assertFalse(ObservationRules.sharedBluetoothAddressCandidate(
                "BT", "11:22:33:44:55:66", "BLE", "AA-BB-CC-DD-EE-FF"));
        assertFalse(ObservationRules.isRadioEvidence("NSD"));
        assertTrue(ObservationRules.isRadioEvidence("WIFI"));
    }
}
