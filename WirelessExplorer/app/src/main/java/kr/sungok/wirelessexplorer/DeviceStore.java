package kr.sungok.wirelessexplorer;

import android.content.Context;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A local-only ledger of radio endpoints, not a count of physical devices. */
public final class DeviceStore {
    private static final int LIMIT = 3000;
    private static final long MAX_JSON_FILE_BYTES = 6_000_000L;
    private String lastPersistenceError = "";
    private final File file;
    private final Map<String, DeviceRecord> records = new LinkedHashMap<>();

    public DeviceStore(Context context) {
        file = new File(context.getFilesDir(), "observations.json");
        load();
    }

    public void beginSession() {
        for (DeviceRecord record : records.values()) record.live = false;
    }

    public void observe(String protocol, String name, String address, String details,
                        int rssi, boolean confirmedThisSession) {
        String id = ObservationRules.normalizedId(protocol, address);
        if (id.isEmpty()) return;
        String key = protocol + "|" + id;
        String safeName = ObservationRules.sanitize(name, 120);
        String safeDetails = ObservationRules.sanitize(details, 440);
        long now = System.currentTimeMillis();
        DeviceRecord record = records.get(key);
        if (record == null) {
            record = new DeviceRecord(key, protocol,
                    SignalRules.labelFor(safeName, "이름 없는 기기"),
                    id, safeDetails, rssi, now, confirmedThisSession);
            records.put(key, record);
        } else {
            // Cached results must neither refresh verified evidence nor make
            // a previously observed endpoint appear more recently present.
            if (confirmedThisSession) {
                if (!safeName.isEmpty()) record.name = safeName;
                if (!safeDetails.isEmpty()) record.details = safeDetails;
                if (rssi < 0) record.rssi = rssi;
                record.lastSeen = now;
                record.lastConfirmedAt = now;
                if (SignalTrend.valid(rssi) && now - record.lastTrendSampleAt >= 1000L) {
                    record.signalTrace = SignalTrend.append(record.signalTrace, rssi);
                    record.lastTrendSampleAt = now;
                }
            } else if (!record.confirmed) {
                // Cache-only record keeps the time of its *first local import*.
                // Re-reading Android's cache does not create a new observation.
                if ("이름 없는 기기".equals(record.name) && !safeName.isEmpty())
                    record.name = safeName;
            }
            if (confirmedThisSession) record.observations++;
            record.live = record.live || confirmedThisSession;
            record.confirmed = record.confirmed || confirmedThisSession;
        }
        if (records.size() > LIMIT) {
            String oldest = records.values().stream()
                    .min(Comparator.comparingLong(d -> d.lastSeen))
                    .map(d -> d.key).orElse(null);
            if (oldest != null) records.remove(oldest);
        }
    }

    public List<DeviceRecord> all() {
        List<DeviceRecord> list = new ArrayList<>(records.values());
        list.sort((a, b) -> Long.compare(b.lastSeen, a.lastSeen));
        return list;
    }

    /** Used only for low-frequency audio: newly encountered session evidence. */
    public boolean wasLiveThisSession(String protocol, String rawId) {
        String key = protocol + "|" + ObservationRules.normalizedId(protocol, rawId);
        DeviceRecord found = records.get(key);
        return found != null && found.live;
    }

    public int countThisSession() {
        int total = 0;
        for (DeviceRecord d : records.values()) if (d.live) total++;
        return total;
    }

    public int total() { return records.size(); }

    public int countLiveRadioEndpoints() {
        int count = 0;
        for (DeviceRecord d : records.values()) {
            if (d.live && ObservationRules.isRadioEvidence(d.protocol)) count++;
        }
        return count;
    }

    /** Hosts with an actual allowed-network TCP response, possibly wired. */
    public int countLiveLanHosts() {
        int count = 0;
        for (DeviceRecord d : records.values())
            if (d.live && "LAN".equals(d.protocol)) count++;
        return count;
    }

    public int countLiveNetworkServices() {
        int count = 0;
        for (DeviceRecord d : records.values()) {
            if (d.live && ("NSD".equals(d.protocol) || "UPNP".equals(d.protocol))) count++;
        }
        return count;
    }

    /** The same BT/BLE address is only a duplicate *candidate*, never auto-merged. */
    public boolean hasSharedBluetoothAddressCandidate(DeviceRecord d) {
        if (d == null || !d.live) return false;
        String companion = "BLE".equals(d.protocol) ? "BT"
                : "BT".equals(d.protocol) ? "BLE" : "";
        if (companion.isEmpty()) return false;
        DeviceRecord other = records.get(companion + "|" + d.address);
        return other != null && other.live;
    }

    public int sharedBluetoothAddressCandidates() {
        int count = 0;
        for (DeviceRecord d : records.values()) {
            if (!d.live || !"BLE".equals(d.protocol)) continue;
            DeviceRecord other = records.get("BT|" + d.address);
            if (other != null && other.live &&
                    ObservationRules.sharedBluetoothAddressCandidate(
                            d.protocol, d.address, other.protocol, other.address)) count++;
        }
        return count;
    }

    public String lastPersistenceError() { return lastPersistenceError; }

    /** Remove every observation, including cached entries, from app-private storage. */
    public void clear() {
        records.clear();
        new AtomicFile(file).delete();
        lastPersistenceError = "";
    }

    public String asJson() {
        JSONArray array = new JSONArray();
        for (DeviceRecord item : all()) {
            try { array.put(item.toJson()); } catch (Exception ignored) { }
        }
        return array.toString();
    }

    public String asCsv() {
        StringBuilder b = new StringBuilder("protocol,kind,name,address,rssi,signal,details,firstSeen,lastReadAt,lastConfirmedAt,observations,thisSession,everConfirmed,signalTrace,importedHistory\n");
        for (DeviceRecord d : all()) {
            b.append(SignalRules.csv(d.protocol)).append(',')
                    .append(SignalRules.csv(ObservationRules.isRadioEvidence(d.protocol) ? "radio-endpoint" : ("LAN".equals(d.protocol) ? "network-host" : "network-service"))).append(',')
                    .append(SignalRules.csv(d.name)).append(',')
                    .append(SignalRules.csv(d.address)).append(',')
                    .append(d.rssi).append(',')
                    .append(SignalRules.csv(SignalRules.strength(d.rssi))).append(',')
                    .append(SignalRules.csv(d.details)).append(',')
                    .append(d.firstSeen).append(',')
                    .append(d.lastSeen).append(',')
                    .append(d.lastConfirmedAt).append(',')
                    .append(d.observations).append(',')
                    .append(d.live).append(',')
                    .append(d.confirmed).append(',')
                    .append(SignalRules.csv(SignalTrend.clean(d.signalTrace))).append(',')
                    .append(d.imported).append('\n');
        }
        return b.toString();
    }

    public static final class ImportResult {
        public final int added;
        public final int skipped;
        public final String error;
        ImportResult(int added, int skipped, String error) {
            this.added = added;
            this.skipped = skipped;
            this.error = error;
        }
        public boolean success() { return error.isEmpty(); }
    }

    /**
     * Import a user-selected export, not an arbitrary unbounded file.
     * Validate everything first, never mark imported records as current-session
     * observations, and roll back added entries if atomic persistence fails.
     */
    public ImportResult importJson(String json) {
        if (json == null || json.length() > MAX_JSON_FILE_BYTES)
            return new ImportResult(0, 0, "복원 파일이 비어 있거나 크기 제한을 초과했습니다.");
        final JSONArray array;
        try {
            array = new JSONArray(json);
        } catch (Exception e) {
            return new ImportResult(0, 0, "JSON 배열 형식의 탐지 기록이 아닙니다.");
        }
        if (array.length() > LIMIT)
            return new ImportResult(0, 0, "복원 파일에 허용 범위보다 많은 기록이 있습니다.");
        int skipped = 0;
        long now = System.currentTimeMillis();
        long maxTime = now + 86_400_000L;
        Map<String, DeviceRecord> staging = new LinkedHashMap<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject j = array.optJSONObject(i);
            if (j == null) { skipped++; continue; }
            String protocol = j.optString("protocol", "");
            if (!"BLE".equals(protocol) && !"BT".equals(protocol)
                    && !"WIFI".equals(protocol) && !"P2P".equals(protocol)
                    && !"NSD".equals(protocol) && !"UPNP".equals(protocol)
                    && !"LAN".equals(protocol)) {
                skipped++;
                continue;
            }
            String id = ObservationRules.normalizedId(protocol, j.optString("address", ""));
            String key = protocol + "|" + id;
            String sourceKey = j.optString("key", "");
            boolean keyValid = key.equals(sourceKey)
                    || (protocol + "|" + j.optString("address", "")).equals(sourceKey);
            if (id.isEmpty() || id.length() > 240 || records.containsKey(key)
                    || staging.containsKey(key) || !keyValid) {
                skipped++;
                continue;
            }
            long first = j.optLong("firstSeen", 0L);
            long last = j.optLong("lastSeen", 0L);
            if (first <= 0L || first > maxTime || last < first || last > maxTime) {
                skipped++;
                continue;
            }
            String name = ObservationRules.sanitize(j.optString("name", ""), 120);
            String detail = ObservationRules.sanitize(j.optString("details", ""), 440);
            int rssi = j.optInt("rssi", 0);
            if (!SignalTrend.valid(rssi)) rssi = 0;
            DeviceRecord d = new DeviceRecord(key, protocol,
                    SignalRules.labelFor(name, "이름 없는 기기"), id, detail,
                    rssi, first, false);
            d.lastSeen = last;
            d.observations = Math.max(1, Math.min(10_000_000, j.optInt("observations", 1)));
            d.confirmed = j.optBoolean("confirmed", false);
            d.lastConfirmedAt = j.optLong("lastConfirmedAt", 0L);
            if (d.lastConfirmedAt < 0L || d.lastConfirmedAt > maxTime
                    || d.lastConfirmedAt > d.lastSeen) d.lastConfirmedAt = 0L;
            d.signalTrace = SignalTrend.clean(j.optString("signalTrace", ""));
            d.lastTrendSampleAt = 0L;
            d.live = false;   // Never claim an imported endpoint is present now.
            d.imported = true;
            staging.put(key, d);
        }
        if (records.size() + staging.size() > LIMIT)
            return new ImportResult(0, skipped, "저장 용량 초과 · 기록을 백업하고 기존 이력을 정리하세요.");
        for (Map.Entry<String, DeviceRecord> entry : staging.entrySet()) {
            records.put(entry.getKey(), entry.getValue());
        }
        if (!save()) {
            for (String key : staging.keySet()) records.remove(key);
            return new ImportResult(0, skipped, "기록 저장 실패 · " + lastPersistenceError);
        }
        return new ImportResult(staging.size(), skipped, "");
    }

    public boolean save() {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream stream = null;
        try {
            byte[] data = asJson().getBytes(StandardCharsets.UTF_8);
            if (data.length > MAX_JSON_FILE_BYTES) {
                lastPersistenceError = "기록이 허용 용량을 초과했습니다. 먼저 내보내기를 사용하세요.";
                return false;
            }
            stream = atomic.startWrite();
            stream.write(data);
            atomic.finishWrite(stream);
            lastPersistenceError = "";
            return true;
        } catch (IOException | RuntimeException e) {
            if (stream != null) atomic.failWrite(stream);
            lastPersistenceError = "내부 저장 실패 (" + e.getClass().getSimpleName() + ")";
            return false;
        }
    }

    private void load() {
        // AtomicFile may have a valid .bak while the base file is absent after
        // an interrupted update. Always allow AtomicFile.openRead() to recover.
        AtomicFile atomic = new AtomicFile(file);
        File backup = new File(file.getPath() + ".bak");
        if (!file.exists() && !backup.exists()) return;
        if (file.length() > MAX_JSON_FILE_BYTES || backup.length() > MAX_JSON_FILE_BYTES) {
            lastPersistenceError = "저장 파일의 용량이 안전 제한을 초과했습니다.";
            return;
        }
        try (FileInputStream in = atomic.openRead();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = in.read(chunk)) != -1) {
                if (out.size() + count > MAX_JSON_FILE_BYTES)
                    throw new IOException("History file exceeds safe limit");
                out.write(chunk, 0, count);
            }
            JSONArray array = new JSONArray(new String(out.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                JSONObject j = array.optJSONObject(i);
                if (j == null) continue;
                DeviceRecord record = DeviceRecord.fromJson(j);
                if (!record.key.isEmpty() && records.size() < LIMIT) records.put(record.key, record);
            }
        } catch (Exception e) {
            records.clear();
            lastPersistenceError = "기존 탐색 기록을 열 수 없습니다: " + e.getClass().getSimpleName();
        }
    }
}
