package kr.sungok.wirelessexplorer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Explicitly opt-in, non-invasive, bounded local-network discovery.
 * TCP connection handshake only; no login, payload, port enumeration,
 * broadcast flooding, external address, or discovery outside active Wi-Fi.
 *
 * Only endpoints responding on a tiny set of common service ports appear.
 * Silence is NOT evidence that the IP address has no device.
 */
public final class LanDiscovery {
    public interface Callback {
        void found(String ip, String description);
        void status(String message);
    }

    // Common admin/web, SMB, print and casting service entry points.
    private static final int[] PORTS = {80, 443, 445, 9100, 631, 8008, 22};
    private static final int CONNECT_TIMEOUT_MS = 180;
    private static final int THREADS = 12;

    private volatile boolean cancelled;
    private volatile ExecutorService workers;
    private volatile Thread coordinator;

    public synchronized void discover(Context context, Callback callback) {
        if (coordinator != null && coordinator.isAlive()) {
            callback.status("LAN 검색이 이미 진행 중입니다");
            return;
        }
        cancelled = false;
        coordinator = new Thread(() -> run(context.getApplicationContext(), callback),
                "WirelessExplorer-LAN-Discovery");
        coordinator.setDaemon(true);
        coordinator.start();
    }

    private void run(Context context, Callback callback) {
        ExecutorService pool = null;
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network network = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities capabilities = network == null ? null
                    : cm.getNetworkCapabilities(network);
            if (capabilities == null
                    || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                callback.status("LAN 검색 불가 · 활성 Wi-Fi 연결이 필요하며 VPN에서는 실행하지 않습니다");
                return;
            }
            LinkProperties props = cm.getLinkProperties(network);
            if (props == null) {
                callback.status("LAN 검색 불가 · 현재 Wi-Fi의 IP 정보를 읽을 수 없습니다");
                return;
            }
            LanTargets.Range chosen = null;
            for (LinkAddress link : props.getLinkAddresses()) {
                InetAddress address = link.getAddress();
                if (!(address instanceof Inet4Address)) continue;
                LanTargets.Range candidate = LanTargets.withinPrivateSubnet(
                        address.getAddress(), link.getPrefixLength());
                if (!candidate.addresses.isEmpty()) {
                    chosen = candidate;
                    break;
                }
            }
            if (chosen == null) {
                callback.status("LAN 검색 불가 · 현재 연결에 사설 IPv4 서브넷이 없습니다");
                return;
            }
            final LanTargets.Range target = chosen;
            callback.status("LAN 주소 조사 시작 · " + target.description
                    + " · 최대 " + target.addresses.size() + "개");
            pool = Executors.newFixedThreadPool(THREADS);
            workers = pool;
            AtomicInteger completed = new AtomicInteger();
            AtomicInteger hits = new AtomicInteger();
            CountDownLatch countdown = new CountDownLatch(target.addresses.size());
            for (String ip : target.addresses) {
                pool.submit(() -> {
                    try {
                        if (cancelled || Thread.currentThread().isInterrupted()) return;
                        List<Integer> openPorts = new ArrayList<>();
                        for (int port : PORTS) {
                            if (cancelled || Thread.currentThread().isInterrupted()) return;
                            try (Socket socket = network.getSocketFactory().createSocket()) {
                                socket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS);
                                openPorts.add(port);
                                // Limit work on a responding host; two corroborating ports max.
                                if (openPorts.size() == 2) break;
                            } catch (Exception ignored) { }
                        }
                        if (!cancelled && !openPorts.isEmpty()) {
                            hits.incrementAndGet();
                            callback.found(ip, "TCP 접속 응답 " + openPorts
                                    + " · 같은 LAN 주소(유선/무선 여부 미확인)"
                                    + " · 서비스 응답만 확인, 로그인/데이터 전송 없음");
                        }
                    } finally {
                        int done = completed.incrementAndGet();
                        if (!cancelled && done % 32 == 0)
                            callback.status("LAN 조사 " + done + "/"
                                    + target.addresses.size()
                                    + " · 응답한 주소 " + hits.get() + "개");
                        countdown.countDown();
                    }
                });
            }
            boolean complete = countdown.await(42, TimeUnit.SECONDS);
            if (!cancelled) callback.status("LAN "
                    + (complete ? "조사 완료" : "시간 제한 종료")
                    + " · 검사 " + completed.get() + "/" + target.addresses.size()
                    + " · TCP 응답 주소 " + hits.get()
                    + "개 (응답 없다고 기기가 없는 것은 아님)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (SecurityException | IllegalStateException e) {
            if (!cancelled) callback.status("LAN 접근 제한 · " + e.getClass().getSimpleName());
        } finally {
            if (pool != null) pool.shutdownNow();
            workers = null;
        }
    }

    public void stop() {
        cancelled = true;
        ExecutorService pool = workers;
        if (pool != null) pool.shutdownNow();
        Thread thread = coordinator;
        if (thread != null) thread.interrupt();
    }
}
