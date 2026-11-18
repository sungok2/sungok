package kr.sungok.wirelessexplorer;

import static org.junit.Assert.*;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class KnownTargetCoverageTest {
    @Test public void exactEndpointMatchesNeverUseFuzzyNames() {
        String raw = "# Expected endpoints\n"
            + "ble,aa-bb-cc-dd-ee-ff,Known Tag\n"
            + "WIFI,11:22:33:44:55:66,Office AP\n"
            + "NSD,_ipp._tcp./Printer,Printer Service\n";
        KnownTargetCoverage.Result result = KnownTargetCoverage.parse(raw);
        assertEquals(3, result.targets.size());
        assertTrue(result.errors.isEmpty());
        Set<String> actual = new HashSet<>();
        actual.add("BLE|AA:BB:CC:DD:EE:FF");
        actual.add("WIFI|AA:BB:CC:DD:EE:FF");
        assertEquals(1, KnownTargetCoverage.matchedCount(result.targets, actual));
        String report = KnownTargetCoverage.report(result.targets, actual);
        assertTrue(report.contains("이번 탐색에서 관찰: 1개"));
        assertTrue(report.contains("[미관찰] WIFI"));
        assertTrue(report.contains("미관찰은 부재 증거가 아닙니다"));
    }

    @Test public void rejectsBadRowsAndExactDuplicates() {
        KnownTargetCoverage.Result result = KnownTargetCoverage.parse(
            "BLE,aa:bb:cc:dd:ee:ff\n"
            + "ble,AA-BB-CC-DD-EE-FF\n"
            + "FAKE,hello\n"
            + "no-comma\n"
            + "WIFI,  \n");
        assertEquals(1, result.targets.size());
        assertEquals(4, result.errors.size());
    }

    @Test public void emptyInputIsAllowedButOverlongInputIsRejected() {
        assertTrue(KnownTargetCoverage.parse("").errors.isEmpty());
        assertTrue(KnownTargetCoverage.parse("x".repeat(10001)).targets.isEmpty());
        assertFalse(KnownTargetCoverage.parse("x".repeat(10001)).errors.isEmpty());
    }
}
