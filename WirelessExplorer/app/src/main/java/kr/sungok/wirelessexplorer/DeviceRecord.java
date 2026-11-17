package kr.sungok.wirelessexplorer;

import org.json.JSONException;
import org.json.JSONObject;

public final class DeviceRecord {
    public final String key;
    public String protocol, name, address, details;
    public int rssi;
    public long firstSeen, lastSeen;
    public int observations;
    public boolean live;

    public DeviceRecord(String key, String protocol, String name, String address,
                        String details, int rssi, long timestamp, boolean live) {
        this.key = key;
        this.protocol = protocol;
        this.name = name;
        this.address = address;
        this.details = details;
        this.rssi = rssi;
        this.firstSeen = timestamp;
        this.lastSeen = timestamp;
        this.observations = 1;
        this.live = live;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("key", key);
        j.put("protocol", protocol);
        j.put("name", name);
        j.put("address", address);
        j.put("details", details);
        j.put("rssi", rssi);
        j.put("firstSeen", firstSeen);
        j.put("lastSeen", lastSeen);
        j.put("observations", observations);
        j.put("live", live);
        return j;
    }

    public static DeviceRecord fromJson(JSONObject j) {
        DeviceRecord d = new DeviceRecord(
                j.optString("key"), j.optString("protocol"),
                j.optString("name"), j.optString("address"),
                j.optString("details"), j.optInt("rssi", 0),
                j.optLong("firstSeen", System.currentTimeMillis()), false);
        d.lastSeen = j.optLong("lastSeen", d.firstSeen);
        d.observations = Math.max(1, j.optInt("observations", 1));
        return d;
    }
}
