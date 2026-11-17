package kr.sungok.wirelessexplorer;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class SignalRulesTest {
    @Test public void signalLabelsRespectUnknownAndBoundaries() {
        assertEquals("미지원", SignalRules.strength(0));
        assertEquals("매우 강함", SignalRules.strength(-55));
        assertEquals("강함", SignalRules.strength(-70));
        assertEquals("보통", SignalRules.strength(-85));
        assertEquals("약함", SignalRules.strength(-90));
    }

    @Test public void unknownNameIsNotGuessed() {
        assertEquals("미확인 기기", SignalRules.labelFor("   ", "미확인 기기"));
        assertEquals("미분류 무선기기", SignalRules.category("BLE", "???"));
    }

    @Test public void categoriesRemainUncertain() {
        assertEquals("오디오 기기 추정", SignalRules.category("BLE", "Galaxy Buds"));
        assertEquals("미분류 무선기기", SignalRules.category("BT", ""));
        assertEquals("Wi-Fi 접속점", SignalRules.category("WIFI", ""));
    }

    @Test public void csvQuotesAreEscaped() {
        assertEquals("\"a\"\"b\"", SignalRules.csv("a\"b"));
        assertEquals("\"\"", SignalRules.csv(null));
    }
}
