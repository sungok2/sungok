package kr.sungok.wirelessexplorer;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
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
        String id = address == null ? "" : address.trim();
        if (id.isEmpty()) return;
        String key = protocol + "|" + id;
        long now = System.currentTimeMillis();
        DeviceRecord record = records.get(key);
        if (record == null) {
            record = new DeviceRecord(key, protocol,
                    SignalRules.labelFor(name, "이름 없는 기기"),
                    id, details == null ? "" : details, rssi, now, confirmedThisSession);
            records.put(key, record);
        } else {
            if (name != null && !name.trim().isEmpty()) record.name = name.trim();
            if (details != null && !details.trim().isEmpty()) record.details = details;
            if (rssi < 0) record.rssi = rssi;
            record.lastSeen = now;
            record.observations++;
            record.live = record.live || confirmedThisSession;
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

    public String asJson() {
        JSONArray array = new JSONArray();
        for (DeviceRecord item : all()) {
            try { array.put(item.toJson()); } catch (Exception ignored) { }
        }
        return array.toString(2);
    }

    public String asCsv() {
        StringBuilder b = new StringBuilder("protocol,name,address,rssi,signal,details,firstSeen,lastSeen,observations,thisSession\n");
        for (DeviceRecord d : all()) {
            b.append(SignalRules.csv(d.protocol)).append(',')
                    .append(SignalRules.csv(d.name)).append(',')
                    .append(SignalRules.csv(d.address)).append(',')
                    .append(d.rssi).append(',')
                    .append(SignalRules.csv(SignalRules.strength(d.rssi))).append(',')
                    .append(SignalRules.csv(d.details)).append(',')
                    .append(d.firstSeen).append(',')
                    .append(d.lastSeen).append(',')
                    .append(d.observations).append(',')
                    .append(d.live).append('\n');
        }
        return b.toString();
    }

    public void save() {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(asJson().getBytes(StandardCharsets.UTF_8));
            out.flush();
            if (!temp.renameTo(file)) {
                // File replacement fallback; no network or external storage involved.
                try (FileOutputStream direct = new FileOutputStream(file)) {
                    direct.write(asJson().getBytes(StandardCharsets.UTF_8));
                }
                temp.delete();
            }
        } catch (IOException ignored) {
            // The UI remains usable even when a storage write fails.
        }
    }

    private void load() {
        if (!file.exists()) return;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), 4_000_000L)];
            int count = 0;
            while (count < data.length) {
                int bytes = in.read(data, count, data.length - count);
                if (bytes == -1) break;
                count += bytes;
            }
            JSONArray array = new JSONArray(new String(data, 0, count, StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                JSONObject j = array.optJSONObject(i);
                if (j == null) continue;
                DeviceRecord record = DeviceRecord.fromJson(j);
                if (!record.key.isEmpty() && records.size() < LIMIT) records.put(record.key, record);
            }
        } catch (Exception ignored) {
            records.clear();
        }
    }
}
