package kr.sungok.wirelessexplorer;

import java.util.ArrayList;
import java.util.List;

/**
 * A short bounded history of confirmed dBm radio observations.
 * Bars show relative signal strength only; they are not distance estimates.
 */
public final class SignalTrend {
    private static final int LIMIT = 24;
    private static final char[] BARS = {'▁','▂','▃','▄','▅','▆','▇','█'};
    private SignalTrend() {}

    public static boolean valid(int rssi) {
        return rssi >= -127 && rssi <= -1;
    }

    public static String append(String history, int rssi) {
        if (!valid(rssi)) return clean(history);
        List<Integer> items = parse(history);
        if (items.size() >= LIMIT) items.remove(0);
        items.add(rssi);
        return join(items);
    }

    public static String clean(String history) {
        return join(parse(history));
    }

    public static int sampleCount(String history) {
        return parse(history).size();
    }

    public static int strongest(String history) {
        int value = -128;
        for (int sample : parse(history)) value = Math.max(value, sample);
        return value;
    }

    public static String render(String history) {
        List<Integer> items = parse(history);
        if (items.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (int sample : items) {
            // Fixed range for legibility; does not imply a calibrated measurement.
            int clamped = Math.max(-100, Math.min(-35, sample));
            int index = Math.min(7, Math.max(0, (clamped + 100) * 8 / 66));
            out.append(BARS[index]);
        }
        return out.toString();
    }

    private static List<Integer> parse(String raw) {
        List<Integer> result = new ArrayList<>();
        if (raw == null || raw.isEmpty() || raw.length() > 120) return result;
        String[] parts = raw.split(",", -1);
        int start = Math.max(0, parts.length - LIMIT);
        for (int i = start; i < parts.length; i++) {
            try {
                int sample = Integer.parseInt(parts[i].trim());
                if (valid(sample)) result.add(sample);
            } catch (NumberFormatException ignored) { }
        }
        return result;
    }

    private static String join(List<Integer> items) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) out.append(',');
            out.append(items.get(i));
        }
        return out.toString();
    }
}
