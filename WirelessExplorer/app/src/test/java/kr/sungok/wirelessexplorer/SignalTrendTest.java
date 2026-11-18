package kr.sungok.wirelessexplorer;

import org.junit.Test;
import static org.junit.Assert.*;

public class SignalTrendTest {
    @Test public void boundsAndExcludesUnknownReadings() {
        String history = "";
        for (int i = 0; i < 100; i++) history = SignalTrend.append(history, -80);
        assertEquals(24, SignalTrend.sampleCount(history));
        assertEquals(-80, SignalTrend.strongest(history));
        assertEquals(24, SignalTrend.render(history).length());
        assertEquals(history, SignalTrend.append(history, 0));
        assertEquals("", SignalTrend.append("", 0));
    }

    @Test public void formatsRecentVariationsWithoutDistanceClaims() {
        String history = SignalTrend.append("", -105);
        history = SignalTrend.append(history, -65);
        history = SignalTrend.append(history, -35);
        assertEquals(3, SignalTrend.sampleCount(history));
        assertEquals(-35, SignalTrend.strongest(history));
        assertEquals("▁▅█", SignalTrend.render(history));
    }

    @Test public void sanitizesBrokenPersistentHistory() {
        assertEquals("-40,-60", SignalTrend.clean("abcd,0,-40,-150,-60"));
        assertEquals("", SignalTrend.clean("x".repeat(200)));
    }
}
