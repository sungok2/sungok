package kr.sungok.wirelessexplorer;

import static org.junit.Assert.*;
import org.junit.Test;

public class LanTargetsTest {
    @Test public void boundsPrivate24ToItsRealNeighbors() {
        LanTargets.Range range = LanTargets.withinPrivateSubnet(
                new byte[]{(byte)192,(byte)168,1,50},24);
        assertEquals(253,range.addresses.size());
        assertTrue(range.addresses.contains("192.168.1.1"));
        assertTrue(range.addresses.contains("192.168.1.254"));
        assertFalse(range.addresses.contains("192.168.1.50"));
        assertFalse(range.addresses.contains("192.168.2.1"));
        assertFalse(range.limited);
    }

    @Test public void largeCorporateSubnetIsLimitedToLocal24() {
        LanTargets.Range range = LanTargets.withinPrivateSubnet(
                new byte[]{10,15,22,99},16);
        assertTrue(range.limited);
        assertEquals(253,range.addresses.size());
        assertTrue(range.addresses.contains("10.15.22.1"));
        assertFalse(range.addresses.contains("10.15.23.1"));
    }

    @Test public void refusesPublicAndUnsupportedNetworks() {
        assertEquals(0,LanTargets.withinPrivateSubnet(new byte[]{8,8,8,8},24).addresses.size());
        assertEquals(0,LanTargets.withinPrivateSubnet(null,24).addresses.size());
        assertEquals(0,LanTargets.withinPrivateSubnet(
                new byte[]{(byte)192,(byte)168,1,1},31).addresses.size());
    }

    @Test public void respectsSmallNetworks() {
        LanTargets.Range range = LanTargets.withinPrivateSubnet(
                new byte[]{(byte)172,16,8,1},29);
        assertEquals(5,range.addresses.size());
        assertTrue(range.addresses.contains("172.16.8.6"));
        assertFalse(range.addresses.contains("172.16.8.1"));
        assertFalse(range.addresses.contains("172.16.8.7"));
    }
}
