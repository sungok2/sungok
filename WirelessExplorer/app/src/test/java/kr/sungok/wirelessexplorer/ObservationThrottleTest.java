package kr.sungok.wirelessexplorer;

import org.junit.Test;
import static org.junit.Assert.*;

public class ObservationThrottleTest {
    @Test public void firstSightingsNeverSuppressedAndRepeatsCoalesced() {
        ObservationThrottle gate = new ObservationThrottle();
        assertTrue(gate.accept("BLE|AA", "Tag", "a", 10000));
        assertTrue(gate.accept("BLE|BB", "Tag", "a", 10001));
        assertFalse(gate.accept("BLE|AA", "Tag", "a", 10100));
        assertFalse(gate.accept("BLE|AA", "Tag", "a", 10400));
        assertTrue(gate.accept("BLE|AA", "Tag", "a", 11000));
    }

    @Test public void metadataChangesReachUiWithoutFlooding() {
        ObservationThrottle gate = new ObservationThrottle();
        assertTrue(gate.accept("AA", "", "adv0", 100));
        assertFalse(gate.accept("AA", "New Name", "adv1", 300));
        assertTrue(gate.accept("AA", "New Name", "adv1", 500));
        assertFalse(gate.accept("AA", "New Name", "adv1", 750));
        assertTrue(gate.accept("AA", "New Name", "adv1", 1500));
    }

    @Test public void resetsAndHandlesClockDiscontinuity() {
        ObservationThrottle gate = new ObservationThrottle();
        assertFalse(gate.accept(null, "", "", 0));
        assertTrue(gate.accept("AA", "", "", 100000));
        assertTrue(gate.accept("AA", "", "", 10));
        gate.clear();
        assertEquals(0, gate.size());
        assertTrue(gate.accept("AA", "", "", 10));
    }

    @Test public void boundedUnderAddressRandomization() {
        ObservationThrottle gate = new ObservationThrottle();
        for (int i = 0; i < 5000; i++) assertTrue(gate.accept("ID"+i, "", "", i));
        assertEquals(4096, gate.size());
    }
}
