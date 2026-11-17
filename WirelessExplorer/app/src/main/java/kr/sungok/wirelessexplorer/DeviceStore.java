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

    public int countLiveNetworkServices() {
        int count = 0;
        for (DeviceRecord d : records.values()) {
            if (d.live && !ObservationRules.isRadioEvidence(d.protocol)) count++;
        }
        return count;
    }

    /** The same BT/BLE address is only a duplicate *candidate*, never auto-merged. */
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
        StringBuilder b = new StringBuilder("protocol,kind,name,address,rssi,signal,details,firstSeen,lastReadAt,lastConfirmedAt,observations,thisSession,everConfirmed\n");
        for (DeviceRecord d : all()) {
            b.append(SignalRules.csv(d.protocol)).append(',')
                    .append(SignalRules.csv(ObservationRules.isRadioEvidence(d.protocol) ? "radio-endpoint" : "network-service")).append(',')
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
                    .append(d.confirmed).append('\n');
        }
        return b.toString();
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
