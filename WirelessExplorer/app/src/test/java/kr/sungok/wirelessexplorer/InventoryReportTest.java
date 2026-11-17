package kr.sungok.wirelessexplorer;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class InventoryReportTest {
    @Test public void distinguishesRadioEntriesFromNetworkServices() {
        Map<String,String> states = new HashMap<>();
        states.put("BLE", "검색 중");
        String report = InventoryReport.generate("2026-11-17", 12, 4, 70,
                2, states, "Android", false);
        assertTrue(report.contains("무선 주소/접속점: 12건"));
        assertTrue(report.contains("네트워크 서비스: 4건"));
        assertTrue(report.contains("BLE: 검색 중"));
        assertTrue(report.contains("UPNP: 미실행/상태 미확인"));
        assertTrue(report.contains("UPnP 확장 요청 탐색: 비활성"));
        assertTrue(report.contains("실제 전자기기 수"));
    }
}
