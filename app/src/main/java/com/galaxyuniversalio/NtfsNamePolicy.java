package com.galaxyuniversalio;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Windows-interoperable NTFS file naming. Never silently replace characters. */
final class NtfsNamePolicy {
    private NtfsNamePolicy() { }

    static String requireValid(String name) throws IOException {
        if (name == null || name.isEmpty() || name.length() > 255) {
            throw new IOException("Invalid filename length (1–255 UTF-16 code units)");
        }
        if (".".equals(name) || "..".equals(name) ||
                name.endsWith(".") || name.endsWith(" ")) {
            throw new IOException("Windows filename must not end in a dot or space");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 32 || c == 127 || "\\/:*?\"<>|".indexOf(c) >= 0) {
                throw new IOException("Invalid Windows filename character");
            }
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= name.length() || !Character.isLowSurrogate(name.charAt(i + 1))) {
                    throw new IOException("Unpaired UTF-16 surrogate");
                }
                i++;
            } else if (Character.isLowSurrogate(c)) {
                throw new IOException("Unpaired UTF-16 surrogate");
            }
        }
        String stem = name.substring(0, name.indexOf('.') < 0 ? name.length() : name.indexOf('.'));
        String upper = stem.trim().toUpperCase(Locale.ROOT);
        if (upper.equals("CON") || upper.equals("PRN") || upper.equals("AUX") ||
                upper.equals("NUL") || upper.matches("(COM|LPT)([1-9¹²³])")) {
            throw new IOException("Reserved Windows device filename: " + stem);
        }
        return name;
    }

    static String caseFoldKey(String name) {
        return name.toUpperCase(Locale.ROOT);
    }

    static void requireDistinctNames(List<String> names) throws IOException {
        Set<String> keys = new HashSet<>();
        for (String name : names) {
            requireValid(name);
            if (!keys.add(caseFoldKey(name))) {
                throw new IOException("Conflicting selected names (case-insensitive): " + name);
            }
        }
    }

    /**
     * Fixed-length temporary name, so a valid 255-unit final filename can be staged.
     * UUID from SecureRandom, not wall-clock or a predictable timer.
     */
    static String temporaryName() {
        return ".guio-partial-" + UUID.randomUUID().toString();
    }
}
