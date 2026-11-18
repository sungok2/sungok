package kr.sungok.wirelessexplorer;

import org.junit.Test;
import static org.junit.Assert.*;

public class BleCompanyNamesTest {
    @Test public void officialSelectedCompanyIdsAreMappedAsAdvertiserIds() {
        assertEquals("Samsung Electronics", BleCompanyNames.forId(0x0075));
        assertEquals("Apple", BleCompanyNames.forId(0x004C));
        assertEquals("Microsoft", BleCompanyNames.forId(0x0006));
        assertEquals("Google", BleCompanyNames.forId(0x00E0));
        assertEquals("", BleCompanyNames.forId(0xFFFF));
        assertTrue(BleCompanyNames.label(0x0075).contains("기기 제조사 확정 아님"));
    }
}
