package kr.sungok.wirelessexplorer;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Map;

public class SsdpHeadersTest {
    @Test public void parsesValidSsdpResponseWithoutFollowingUrls() {
        String packet = "HTTP/1.1 200 OK\r\nST: upnp:rootdevice\r\n"
            + "USN: uuid:abc::upnp:rootdevice\r\nSERVER: TestDevice/1.0\r\n"
            + "LOCATION: http://192.168.1.2:1400/description.xml\r\n\r\n";
        Map<String,String> parsed = SsdpHeaders.parse(packet);
        assertEquals("upnp:rootdevice", parsed.get("st"));
        assertEquals("uuid:abc::upnp:rootdevice", parsed.get("usn"));
        assertTrue(parsed.get("location").contains("192.168.1.2"));
    }

    @Test public void acceptsHttp10ButNotFailureResponses() {
        assertEquals("uuid:legacy", SsdpHeaders.parse(
                "HTTP/1.0 200 OK\r\nUSN: uuid:legacy\r\n").get("usn"));
        assertTrue(SsdpHeaders.parse(
                "HTTP/1.1 403 Forbidden\r\nUSN: uuid:fake\r\n").isEmpty());
    }

    @Test public void ignoresRequestsAndOverlongPackets() {
        assertTrue(SsdpHeaders.parse("M-SEARCH * HTTP/1.1\r\nST:ssdp:all\r\n").isEmpty());
        assertTrue(SsdpHeaders.parse("x".repeat(8200)).isEmpty());
    }

    @Test public void ignoresHeaderInjection() {
        Map<String,String> parsed = SsdpHeaders.parse(
            "HTTP/1.1 200 OK\r\nLOCATION: http://192.168.0.2/\r\nBAD: fake\r\n");
        assertFalse(parsed.containsKey("bad"));
        assertEquals("http://192.168.0.2/", parsed.get("location"));
    }
}
