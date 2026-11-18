package kr.sungok.wirelessexplorer;

import org.json.JSONException;
import org.json.JSONObject;

public final class DeviceRecord {
    public final String key;
    public String protocol, name, address, details;
    public int rssi;
    public long firstSeen, lastSeen;
    public int observations;
    /** Latest 24 confirmed radio measurements; never created from scan caches. */
    public String signalTrace;
    public boolean live;
    /** True only after direct scan evidence, not merely a cached Wi-Fi result. */
    public boolean confirmed;
    /** 0 for a cached-only endpoint; never label this as an observed radio signal. */
    public long lastConfirmedAt;

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
        this.signalTrace = live ? SignalTrend.append("", rssi) : "";
        this.live = live;
        this.confirmed = live;
        this.lastConfirmedAt = live ? timestamp : 0L;
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
        j.put("signalTrace", SignalTrend.clean(signalTrace));
        j.put("live", live);
        j.put("confirmed", confirmed);
        j.put("lastConfirmedAt", lastConfirmedAt);
        j.put("kind", ObservationRules.isRadioEvidence(protocol) ? "radio-endpoint" : "network-service");
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
        d.signalTrace = SignalTrend.clean(j.optString("signalTrace", ""));
        d.confirmed = j.optBoolean("confirmed", j.optBoolean("live", false));
        d.lastConfirmedAt = j.optLong("lastConfirmedAt", 0L); // v0.2 did not retain verified time
        return d;
    }
}
