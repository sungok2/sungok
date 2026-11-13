package com.galaxyuniversalio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class NtfsNamePolicyTests {
    private static int pass = 0;
    @FunctionalInterface interface Checked { void run() throws Exception; }
    private static void assertOk(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        ++pass;
    }
    private static void rejected(Checked action, String label) throws Exception {
        try { action.run(); }
        catch (IOException expected) { ++pass; return; }
        throw new AssertionError("Accepted unsafe value: " + label);
    }

    public static void main(String[] args) throws Exception {
        for (String name: Arrays.asList("보고서 한글.docx", "a.txt", ".hidden", "자료 01 (최종).pdf",
               "emoji-\uD83D\uDE42.txt", "a".repeat(255), "good.name.tar.gz")) {
            assertOk(name.equals(NtfsNamePolicy.requireValid(name)), "valid " + name);
        }
        List<String> invalid = Arrays.asList("", ".", "..", "bad.", "trail ", "/x",
            "ab\\cd", "why?", "quo\"te", "star*", "pipe|", "col:on",
            "CON", "con.txt", "AUX.md", "prn", "nul.jpeg", "COM1", "com9.xyz",
            "LPT3", "lpt9.txt", "COM\u00B9.pdf", "LPT\u00B2.txt", "COM\u00B3",
            "one\u0000two", "bell\u0007", "del\u007F", "\uD83D", "\uDE42",
            "z".repeat(256));
        for(String s:invalid) rejected(() -> NtfsNamePolicy.requireValid(s), s);
        rejected(() -> NtfsNamePolicy.requireValid(null), "null");
        rejected(() -> NtfsNamePolicy.requireDistinctNames(Arrays.asList("Readme.TXT","README.txt")), "same case");
        rejected(() -> NtfsNamePolicy.requireDistinctNames(Arrays.asList("가나.pdf","가나.pdf")), "same Hangul");
        NtfsNamePolicy.requireDistinctNames(Arrays.asList("a.txt","b.txt","자료.png"));
        ++pass;
        String t1=NtfsNamePolicy.temporaryName(),t2=NtfsNamePolicy.temporaryName();
        assertOk(!t1.equals(t2),"unique random UUID names");
        assertOk(t1.startsWith(".guio-partial-") && t1.length() < 60,"short temporary name");
        System.out.println("NTFS filename checks passed: "+pass);
    }
}
