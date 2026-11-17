package kr.sungok.wirelessexplorer;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Optional, LAN-only UPnP/SSDP discovery. Does not connect to found URLs. */
public final class SsdpDiscovery {
    public interface Callback {
        void found(String name, String id, String details);
        void status(String message);
    }

    private static final byte[] REQUEST = (
            "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: ssdp:all\r\n\r\n").getBytes(StandardCharsets.US_ASCII);

    private volatile DatagramSocket active;
    private volatile boolean cancelled;

    public void discover(Callback callback) {
        cancelled = false;
        Thread thread = new Thread(() -> {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                active = socket;
                socket.setSoTimeout(700);
                InetAddress multicast = InetAddress.getByName("239.255.255.250");
                DatagramPacket query = new DatagramPacket(REQUEST, REQUEST.length, multicast, 1900);
                socket.send(query);
                // One burst only; avoids noisy repeated UPnP traffic.
                long deadline = System.nanoTime() + 3_200_000_000L;
                byte[] bytes = new byte[4096];
                int responses = 0;
                while (!cancelled && System.nanoTime() < deadline && responses < 120) {
                    DatagramPacket answer = new DatagramPacket(bytes, bytes.length);
                    try {
                        socket.receive(answer);
                    } catch (SocketTimeoutException ignored) {
                        continue;
                    }
                    if (cancelled || answer.getAddress() == null) break;
                    InetAddress source = answer.getAddress();
                    // Responses must originate from directly local/private or link-local addresses.
                    if (!source.isSiteLocalAddress() && !source.isLinkLocalAddress()) continue;
                    String payload = new String(answer.getData(), answer.getOffset(),
                            answer.getLength(), StandardCharsets.UTF_8);
                    Map<String, String> values = SsdpHeaders.parse(payload);
                    if (values.isEmpty()) continue;
                    String id = values.get("usn");
                    String st = values.get("st");
                    if (id == null || id.isEmpty()) id = source.getHostAddress() + "/" + st;
                    String server = values.get("server");
                    String name = (server == null || server.isEmpty()) ? st : server;
                    if (name == null || name.isEmpty()) name = "UPnP 서비스";
                    String detail = "UPnP/SSDP · " + source.getHostAddress()
                            + " · ST: " + (st == null ? "미제공" : st)
                            + (values.containsKey("location") ? " · 설명 주소 표시 전용(접속 안 함)" : "");
                    callback.found(name, id, detail);
                    responses++;
                }
                if (!cancelled) callback.status("UPnP 검색 완료");
            } catch (Exception e) {
                if (!cancelled) callback.status("UPnP 검색 제한: " + e.getClass().getSimpleName());
            } finally {
                if (socket != null) socket.close();
                if (active == socket) active = null;
            }
        }, "WirelessExplorer-SSDP");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        cancelled = true;
        DatagramSocket socket = active;
        if (socket != null) socket.close();
    }
}
