package kr.sungok.wirelessexplorer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Exact, user-supplied radio endpoint/service identifiers for controlled field
 * tests. No fuzzy device-name matching and no physical-device coverage claims.
 */
public final class KnownTargetCoverage {
    private KnownTargetCoverage() {}
    public static final int MAX_TARGETS = 60;
    public static final int MAX_INPUT = 10000;

    public static final class Target {
        public final String protocol;
        public final String id;
        public final String label;
        public final String key;
        Target(String protocol, String id, String label) {
            this.protocol = protocol;
            this.id = id;
            this.label = label;
            this.key = protocol + "|" + id;
        }
    }

    public static final class Result {
        public final List<Target> targets;
        public final List<String> errors;
        Result(List<Target> targets, List<String> errors) {
            this.targets = Collections.unmodifiableList(targets);
            this.errors = Collections.unmodifiableList(errors);
        }
    }

    public static Result parse(String raw) {
        List<Target> targets = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return new Result(targets, errors);
        if (raw.length() > MAX_INPUT) {
            errors.add("입력 길이가 " + MAX_INPUT + "자를 초과했습니다.");
            return new Result(targets, errors);
        }
        Set<String> seen = new HashSet<>();
        String[] lines = raw.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (targets.size() >= MAX_TARGETS) {
                errors.add("등록 가능한 최대 기준 항목은 " + MAX_TARGETS + "개입니다.");
                break;
            }
            String[] parts = line.split(",", 3);
            if (parts.length < 2) {
                errors.add((i + 1) + "행: 프로토콜,ID[,설명] 형식이 필요합니다.");
                continue;
            }
            String protocol = parts[0].trim().toUpperCase(Locale.ROOT);
            if (!protocol.equals("BLE") && !protocol.equals("BT")
                    && !protocol.equals("WIFI") && !protocol.equals("P2P")
                    && !protocol.equals("NSD") && !protocol.equals("UPNP")
                    && !protocol.equals("LAN")) {
                errors.add((i + 1) + "행: 지원하지 않는 프로토콜입니다.");
                continue;
            }
            String id = ObservationRules.normalizedId(protocol, parts[1]);
            if (id.isEmpty() || id.length() > 240) {
                errors.add((i + 1) + "행: 정확한 주소 또는 서비스 ID가 필요합니다.");
                continue;
            }
            String key = protocol + "|" + id;
            if (!seen.add(key)) {
                errors.add((i + 1) + "행: 중복된 기준 항목입니다.");
                continue;
            }
            String label = parts.length == 3
                    ? ObservationRules.sanitize(parts[2], 70) : id;
            targets.add(new Target(protocol, id, label.isEmpty() ? id : label));
        }
        return new Result(targets, errors);
    }

    public static int matchedCount(List<Target> expected, Set<String> observedKeys) {
        if (expected == null || observedKeys == null) return 0;
        int count = 0;
        for (Target t : expected) if (observedKeys.contains(t.key)) count++;
        return count;
    }

    public static String report(List<Target> expected, Set<String> currentSessionKeys) {
        if (expected == null || expected.isEmpty())
            return "[사용자 기준 탐색 검증]\\n기준 장치가 등록되지 않았습니다.\\n";
        int matched = matchedCount(expected, currentSessionKeys);
        StringBuilder b = new StringBuilder();
        b.append("[사용자 기준 탐색 검증]\\n");
        b.append("등록한 기준 엔드포인트: ").append(expected.size()).append("개\\n");
        b.append("이번 탐색에서 관찰: ").append(matched).append("개\\n");
        b.append("이번 탐색에서 미관찰: ").append(expected.size() - matched).append("개\\n");
        for (Target t : expected) {
            b.append(currentSessionKeys != null && currentSessionKeys.contains(t.key)
                    ? "[관찰] " : "[미관찰] ");
            b.append(t.protocol).append(' ').append(t.label)
                    .append(" (").append(t.id).append(")\\n");
        }
        b.append("주의: 사용자가 직접 입력한 정확한 무선 주소/서비스 ID 기준입니다. ");
        b.append("BLE 주소 임의화, 탐색 권한, 광고 비송신 등으로 놓칠 수 있으며 ");
        b.append("미관찰은 부재 증거가 아닙니다. 이름 유사성으로 자동 매칭하지 않습니다.\\n");
        return b.toString();
    }
}
