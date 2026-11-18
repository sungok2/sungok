package kr.sungok.wirelessexplorer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Limit authorized, optional LAN checks to private IPv4 addresses within the
 * active Wi-Fi interface's own subnet. Broad networks are clamped to the
 * /24 containing the phone and scan at most 253 other hosts.
 */
public final class LanTargets {
    private LanTargets() { }

    public static final class Range {
        public final List<String> addresses;
        public final String description;
        public final boolean limited;
        Range(List<String> addresses, String description, boolean limited) {
            this.addresses = Collections.unmodifiableList(addresses);
            this.description = description;
            this.limited = limited;
        }
    }

    public static Range withinPrivateSubnet(byte[] local, int prefix) {
        if (local == null || local.length != 4 || prefix < 8 || prefix > 30)
            return new Range(new ArrayList<>(), "IPv4 인터페이스/서브넷 정보 없음", false);
        long ip = pack(local);
        if (!isPrivate(ip))
            return new Range(new ArrayList<>(), "사설 IPv4 네트워크가 아닙니다", false);
        int usedPrefix = Math.max(24, prefix);
        boolean limited = usedPrefix != prefix;
        long mask = (0xFFFFFFFFL << (32 - usedPrefix)) & 0xFFFFFFFFL;
        long base = ip & mask;
        long last = base | (~mask & 0xFFFFFFFFL);
        List<String> out = new ArrayList<>();
        for (long candidate = base + 1; candidate < last && out.size() < 253; candidate++) {
            if (candidate == ip || !isPrivate(candidate)) continue;
            out.add(format(candidate));
        }
        return new Range(out, format(base) + "/" + usedPrefix
                + (limited ? " (큰 네트워크의 일부만 검사)" : ""), limited);
    }

    public static boolean isPrivate(long ip) {
        int a = (int) ((ip >>> 24) & 255);
        int b = (int) ((ip >>> 16) & 255);
        return a == 10 || (a == 172 && b >= 16 && b <= 31)
                || (a == 192 && b == 168);
    }

    private static long pack(byte[] b) {
        return ((b[0] & 255L) << 24) | ((b[1] & 255L) << 16)
                | ((b[2] & 255L) << 8) | (b[3] & 255L);
    }

    private static String format(long ip) {
        return ((ip >>> 24) & 255) + "." + ((ip >>> 16) & 255)
                + "." + ((ip >>> 8) & 255) + "." + (ip & 255);
    }
}
