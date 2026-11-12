package com.galaxyuniversalio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class UsbHttpPolicyTests {
    private static int checks;
    interface Task { void run() throws Exception; }
    static void accept(Task t) throws Exception { t.run(); checks++; }
    static void deny(Task t) throws Exception {
        boolean rejected = false;
        try { t.run(); } catch (IOException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("Unsafe request accepted at check " + checks);
        checks++;
    }
    static void check(String method, String path, String media, byte[] body, int limit)
            throws IOException {
        UsbHttpRequestPolicy.validate(method,path,media,body,limit);
    }
    public static void main(String[] args) throws Exception {
        byte[] xml = "<ScanSettings/>".getBytes(StandardCharsets.US_ASCII);
        accept(() -> check("GET", "/eSCL/ScannerCapabilities", null, null, 512*1024));
        accept(() -> check("GET", "/eSCL/ScanJobs/urn:uuid:ab-c/NextDocument", null, null, 8*1024*1024));
        accept(() -> check("POST", "/eSCL/ScanJobs", "text/xml", xml, 65536));
        accept(() -> check("POST", "/ipp/print", "application/ipp", xml, 65536));
        accept(() -> check("DELETE", "/eSCL/ScanJobs/id1", null, null, 1024));
        deny(() -> check("GET", "/eSCL/ScanJobs/id\r\nX: injected", null, null, 100));
        deny(() -> check("GET", "/eSCL/ScanJobs/id%0A", null, null, 100));
        deny(() -> check("GET", "/eSCL/../outside", null, null, 100));
        deny(() -> check("GET", "/eSCL//ScannerCapabilities", null, null, 100));
        deny(() -> check("GET", "/ipp/print", null, null, 100));
        deny(() -> check("POST", "/eSCL/ScanJobs", "text/xml\r\nX: evil", xml, 100));
        deny(() -> check("POST", "/eSCL/ScanJobs", "application/ipp", xml, 100));
        deny(() -> check("POST", "/ipp/print", "text/xml", xml, 100));
        deny(() -> check("POST", "/ipp/print", "application/ipp", null, 100));
        deny(() -> check("DELETE", "/eSCL/ScanJobs/a", null, xml, 100));
        deny(() -> check("POST", "/eSCL/ScanJobs/other", "text/xml", xml, 100));
        deny(() -> check("GET", "/eSCL/ScanJobs/a", null, null, -1));
        deny(() -> check("GET", "/eSCL/ScanJobs/a", null, null, 64*1024*1024+1));
        deny(() -> check("PUT", "/eSCL/ScanJobs/a", null, null, 100));
        deny(() -> check("GET", "/eSCL/ScanJobs/a?b=1", null, null, 100));
        deny(() -> check("GET", "/eSCL/ScanJobs/a#frag", null, null, 100));
        deny(() -> check("GET", "/eSCL/ScanJobs/a\\b", null, null, 100));
        deny(() -> check("GET", "/eSCL/ScanJobs/a", "image/jpeg", null, 100));
        deny(() -> check("POST", "/eSCL/ScanJobs", "text/xml", new byte[0], 100));
        deny(() -> check("GET", "/eSCL/ScanJobs/a", null, new byte[10*1024*1024+1], 100));
        System.out.println("PASS: " + checks + " USB HTTP preflight policy tests");
    }
}
