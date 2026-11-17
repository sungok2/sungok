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

    @Test public void wifiBandAndChannelBoundaries() {
        assertEquals("2.4 GHz", SignalRules.wifiBand(2412));
        assertEquals(1, SignalRules.wifiChannel(2412));
        assertEquals(14, SignalRules.wifiChannel(2484));
        assertEquals("5 GHz", SignalRules.wifiBand(5180));
        assertEquals(36, SignalRules.wifiChannel(5180));
        assertEquals("6 GHz", SignalRules.wifiBand(5955));
        assertEquals(1, SignalRules.wifiChannel(5955));
        assertEquals(0, SignalRules.wifiChannel(1234));
        assertEquals("대역 미분류", SignalRules.wifiBand(1234));
    }

    @Test public void categoriesRemainUncertain() {
        assertEquals("오디오 기기 추정", SignalRules.category("BLE", "Galaxy Buds"));
        assertEquals("미분류 무선기기", SignalRules.category("BT", ""));
        assertEquals("Wi-Fi 접속점", SignalRules.category("WIFI", ""));
    }

    @Test public void csvQuotesAreEscaped() {
        assertEquals("\"a\"\"b\"", SignalRules.csv("a\"b"));
        assertEquals("\"\"", SignalRules.csv(null));
        assertEquals("\"'=1+1\"", SignalRules.csv("=1+1"));
        assertEquals("\"'  @HYPERLINK\"", SignalRules.csv("  @HYPERLINK"));
    }
}
