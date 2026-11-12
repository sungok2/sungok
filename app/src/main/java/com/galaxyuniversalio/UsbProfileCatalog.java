package com.galaxyuniversalio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Exact VID/PID lookup. Profiles are manually curated; a match is not proof of
 * successful USB transfers. The shipped catalog is intentionally empty.
 */
public final class UsbProfileCatalog {
    public static final class Entry {
        public final int vendorId;
        public final int productId;
        public final String vendor;
        public final String model;
        public final String category;
        public final String protocol;
        public final String evidenceLevel;
        public final String notes;

        private Entry(int vendorId, int productId, String vendor, String model,
                      String category, String protocol, String evidenceLevel, String notes) {
            this.vendorId = vendorId;
            this.productId = productId;
            this.vendor = vendor;
            this.model = model;
            this.category = category;
            this.protocol = protocol;
            this.evidenceLevel = evidenceLevel;
            this.notes = notes;
        }
    }

    private final Map<Long, Entry> entries;

    private UsbProfileCatalog(Map<Long, Entry> entries) {
        this.entries = Collections.unmodifiableMap(new HashMap<>(entries));
    }

    public static UsbProfileCatalog empty() {
        return new UsbProfileCatalog(Collections.emptyMap());
    }

    public int size() { return entries.size(); }

    public Entry find(int vendorId, int productId) {
        return entries.get(key(vendorId, productId));
    }

    public static UsbProfileCatalog load(Reader input) throws IOException {
        Map<Long, Entry> result = new HashMap<>();
        BufferedReader reader = new BufferedReader(input);
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.isEmpty() || line.startsWith("#") || line.equals(
                    "VID\tPID\tVENDOR\tMODEL\tCATEGORY\tPROTOCOL\tEVIDENCE\tNOTES")) continue;
            String[] fields = line.split("\t", -1);
            if (fields.length != 8) {
                throw new IOException("프로파일 형식 오류: " + lineNumber + "행 (8열 필요)");
            }
            int vid = parseHex(fields[0], lineNumber);
            int pid = parseHex(fields[1], lineNumber);
            String evidence = fields[6];
            if (!evidence.equals("discovered") && !evidence.equals("protocol-reviewed")
                    && !evidence.equals("hardware-tested")) {
                throw new IOException("프로파일 증거 수준 오류: " + lineNumber + "행");
            }
            long key = key(vid, pid);
            if (result.containsKey(key)) {
                throw new IOException("중복 VID:PID: " + lineNumber + "행");
            }
            result.put(key, new Entry(vid, pid, fields[2], fields[3],
                    fields[4], fields[5], evidence, fields[7]));
        }
        return new UsbProfileCatalog(result);
    }

    private static int parseHex(String text, int lineNumber) throws IOException {
        if (!text.matches("[0-9a-fA-F]{4}")) {
            throw new IOException("VID/PID는 4자리 16진수여야 합니다: " + lineNumber + "행");
        }
        return Integer.parseInt(text, 16);
    }

    private static long key(int vendorId, int productId) {
        return ((long) (vendorId & 0xFFFF) << 16) | (productId & 0xFFFFL);
    }
}
