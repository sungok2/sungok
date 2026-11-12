package com.galaxyuniversalio;

import android.hardware.usb.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Offline wire-protocol regression. No real scanner or Android SDK involved. */
public final class EsclHostTests {
    static int checks;
    static void check(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }
    interface Task { void run() throws Exception; }
    static void fails(Task task, String why) throws Exception {
        boolean failed = false;
        try { task.run(); } catch (IOException e) { failed = true; }
        check(failed, why);
    }
    static byte[] utf(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    static final String XML = "<?xml version='1.0'?><scan:ScannerCapabilities "
        + "xmlns:scan='http://schemas.hp.com/imaging/escl/2011/05/03' "
        + "xmlns:pwg='http://www.pwg.org/schemas/2010/12/sm'>"
        + "<scan:PlatenInputCaps><pwg:MaxWidth>2550</pwg:MaxWidth><pwg:MaxHeight>3508</pwg:MaxHeight>"
        + "<scan:ColorMode>RGB24</scan:ColorMode><pwg:DocumentFormat>image/jpeg</pwg:DocumentFormat>"
        + "<scan:XResolution>150</scan:XResolution><scan:XResolution>300</scan:XResolution>"
        + "</scan:PlatenInputCaps>"
        + "<scan:AdfSimplexInputCaps><pwg:MaxWidth>2400</pwg:MaxWidth>"
        + "<pwg:MaxHeight>3400</pwg:MaxHeight><scan:ColorMode>Grayscale8</scan:ColorMode>"
        + "<pwg:DocumentFormat>image/jpeg</pwg:DocumentFormat>"
        + "<scan:XResolution>150</scan:XResolution></scan:AdfSimplexInputCaps>"
        + "</scan:ScannerCapabilities>";
    static final byte[] JPEG = new byte[]{(byte)0xff,(byte)0xd8,4,3,2,(byte)0xff,(byte)0xd9};

    static final class Mock implements EsclScanClient.Transport {
        final List<String> requestPaths = new ArrayList<>();
        final Queue<EsclScanClient.Reply> responses = new ArrayDeque<>();
        boolean closed;
        @Override public String origin() { return "http://192.168.1.7:80"; }
        @Override public void close() { closed = true; }
        @Override public EsclScanClient.Reply exchange(String method,String path,String mime,byte[] body,int limit) {
            requestPaths.add(method+" "+path);
            if (responses.isEmpty()) throw new IllegalStateException("Missing mock response: "+method+" "+path);
            return responses.remove();
        }
        void add(int code,String loc,String mime,byte[] body) {
            responses.add(new EsclScanClient.Reply(code,loc,mime,body));
        }
    }

    static void protocol() throws Exception {
        EsclProtocol.Capabilities caps = EsclProtocol.parseCapabilities(utf(XML));
        check(caps.platen && caps.feeder, "platen/ADF parsed");
        check(caps.dpi == 300, "highest <= 600 supported");
        check(caps.height == 3508 && caps.width == 2480, "page region bounded");
        check(caps.colorMode.equals("RGB24"), "RGB24 priority");
        String settings = new String(EsclProtocol.settings(caps,true), StandardCharsets.UTF_8);
        check(settings.contains("<pwg:InputSource>Feeder</pwg:InputSource>"), "ADF command");
        check(settings.contains("<pwg:Height>3400</pwg:Height>"), "ADF-specific page length");
        check(settings.contains("<scan:XResolution>150</scan:XResolution>"), "ADF-specific resolution");
        check(settings.contains("<scan:ColorMode>Grayscale8</scan:ColorMode>"), "ADF-specific color mode");
        check(settings.contains("image/jpeg"), "only supported image format");
        check(EsclProtocol.jobPath("/eSCL/ScanJobs/job123","http://192.168.1.7:80")
                .equals("/eSCL/ScanJobs/job123"), "relative Location");
        check(EsclProtocol.jobPath("http://192.168.1.7/eSCL/ScanJobs/urn:uuid:abc",
                "http://192.168.1.7:80").contains("urn:uuid"), "same-origin absolute Location");
        fails(() -> EsclProtocol.jobPath("http://attacker.test/eSCL/ScanJobs/a",
                "http://192.168.1.7:80"), "Location SSRF");
        fails(() -> EsclProtocol.jobPath("/other/jobs/a","http://192.168.1.7"),"path escape");
        fails(() -> EsclProtocol.jobPath("/eSCL/ScanJobs/%2e%2e","http://192.168.1.7"),"encoded traversal");
        fails(() -> EsclProtocol.parseCapabilities(utf("<!DOCTYPE foo [<!ENTITY a SYSTEM 'file:///etc/passwd'>]><ScannerCapabilities>&a;</ScannerCapabilities>")),"XXE prevention");
        EsclProtocol.Capabilities pngCaps = EsclProtocol.parseCapabilities(
                utf(XML.replace("image/jpeg","image/png")));
        check(pngCaps.source(false).format.equals("image/png"), "PNG-only scanner supported");
        check(new String(EsclProtocol.settings(pngCaps,false), StandardCharsets.UTF_8)
                .contains("<pwg:DocumentFormat>image/png</pwg:DocumentFormat>"), "PNG negotiated");
        String emptyAdf = XML.replace("<scan:AdfSimplexInputCaps><pwg:MaxWidth>2400</pwg:MaxWidth>"
              + "<pwg:MaxHeight>3400</pwg:MaxHeight><scan:ColorMode>Grayscale8</scan:ColorMode>"
              + "<pwg:DocumentFormat>image/jpeg</pwg:DocumentFormat>"
              + "<scan:XResolution>150</scan:XResolution></scan:AdfSimplexInputCaps>",
              "<scan:AdfSimplexInputCaps/>");
        EsclProtocol.Capabilities unavailableAdf = EsclProtocol.parseCapabilities(utf(emptyAdf));
        check(!unavailableAdf.feeder, "Empty ADF capabilities not assumed compatible");
        fails(() -> EsclProtocol.settings(unavailableAdf,true), "Unsafe ADF fallback rejected");
        fails(() -> EsclProtocol.parseCapabilities(utf(XML.replace("image/jpeg","image/tiff"))), "TIFF-only rejected");
        fails(() -> EsclProtocol.parseCapabilities(new byte[0]),"empty capability response");
        EsclProtocol.requireJpeg(JPEG, "image/jpeg; charset=binary");
        checks++;
        fails(() -> EsclProtocol.requireJpeg(JPEG, "image/png"),"content-type mismatch");
        fails(() -> EsclProtocol.requireJpeg(new byte[]{1,2},"image/jpeg"),"corrupt JPEG");
        byte[] png = java.util.Base64.getDecoder().decode(
          "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFklEQVR4nGP8//8/AwMDEwMDAwMDAwAkBgMB/DXemwAAAABJRU5ErkJggg==");
        check(EsclProtocol.requireImage(png, "image/png").equals("image/png"), "PNG signature and CRC");
        check(EsclProtocol.imageMimeFromBytes(png).equals("image/png"), "PDF PNG detection");
        byte[] tampered = png.clone(); tampered[25] ^= 1;
        fails(() -> EsclProtocol.requireImage(tampered,"image/png"), "PNG CRC mismatch rejected");
        fails(() -> EsclProtocol.requireImage(png,"image/jpeg"), "PNG content-type spoof rejected");
    }

    static void scan() throws Exception {
        Mock mock = new Mock();
        mock.add(200,null,"text/xml",utf(XML));
        mock.add(201,"/eSCL/ScanJobs/doc1",null,new byte[0]);
        mock.add(200,null,"image/jpeg",JPEG);
        mock.add(200,null,null,new byte[0]); // DELETE job
        List<byte[]> scanned = EsclScanClient.scan(mock,false,n -> check(n == 1,"page callback"));
        check(scanned.size() == 1, "one platen page");
        check(mock.requestPaths.contains("DELETE /eSCL/ScanJobs/doc1"),"cleanup");

        Mock adf = new Mock();
        adf.add(200,null,"text/xml",utf(XML));
        adf.add(201,"http://192.168.1.7/eSCL/ScanJobs/doc2",null,new byte[0]);
        adf.add(200,null,"image/jpeg",JPEG);
        adf.add(200,null,"image/jpeg",JPEG);
        adf.add(404,null,null,new byte[0]);
        adf.add(200,null,null,new byte[0]);
        check(EsclScanClient.scan(adf,true,null).size() == 2,"ADF two pages and 404");
        check(adf.requestPaths.contains("GET /eSCL/ScanJobs/doc2/NextDocument"),"ADF job endpoint");

        // Regression: 12 pages is permitted, but a 13th must be rejected (no partial output).
        Mock twelve = new Mock();
        twelve.add(200,null,"text/xml",utf(XML));
        twelve.add(201,"/eSCL/ScanJobs/twelve",null,new byte[0]);
        for (int k=0;k<12;k++) twelve.add(200,null,"image/jpeg",JPEG);
        twelve.add(404,null,null,new byte[0]);
        twelve.add(204,null,null,new byte[0]);
        check(EsclScanClient.scan(twelve,true,null).size()==12,"ADF exactly 12 pages supported");
        check(twelve.requestPaths.contains("DELETE /eSCL/ScanJobs/twelve"),"12-page job cleanup");
        Mock thirteen = new Mock();
        thirteen.add(200,null,"text/xml",utf(XML));
        thirteen.add(201,"/eSCL/ScanJobs/thirteen",null,new byte[0]);
        for (int k=0;k<13;k++) thirteen.add(200,null,"image/jpeg",JPEG);
        thirteen.add(204,null,null,new byte[0]);
        fails(() -> EsclScanClient.scan(thirteen,true,null),"ADF 13th page rejected");
        check(thirteen.requestPaths.contains("DELETE /eSCL/ScanJobs/thirteen"),"13-page job cleanup");

        Mock bad = new Mock();
        bad.add(200,null,"text/xml",utf(XML));
        bad.add(201,"/eSCL/ScanJobs/doc3",null,new byte[0]);
        bad.add(200,null,"image/png",JPEG);
        bad.add(200,null,null,new byte[0]);
        fails(() -> EsclScanClient.scan(bad,false,null),"unwanted PNG rejected");
        check(bad.requestPaths.contains("DELETE /eSCL/ScanJobs/doc3"),"cleanup after failed scan");

        Mock empty = new Mock();
        empty.add(200,null,"text/xml",utf(XML));
        empty.add(201,"/eSCL/ScanJobs/doc4",null,new byte[0]);
        empty.add(404,null,null,new byte[0]);
        empty.add(200,null,null,new byte[0]);
        fails(() -> EsclScanClient.scan(empty,true,null),"empty ADF rejected");
        // Some ADF implementations signal clean end-of-job with 204 No Content.
        Mock noContent = new Mock();
        noContent.add(200,null,"text/xml",utf(XML));
        noContent.add(201,"/eSCL/ScanJobs/adf204",null,new byte[0]);
        noContent.add(200,null,"image/jpeg",JPEG);
        noContent.add(204,null,null,new byte[0]);
        noContent.add(204,null,null,new byte[0]);
        check(EsclScanClient.scan(noContent,true,null).size() == 1, "ADF 204 end accepted");
        check(noContent.requestPaths.contains("DELETE /eSCL/ScanJobs/adf204"), "204 cleanup");
        Mock empty204 = new Mock();
        empty204.add(200,null,"text/xml",utf(XML));
        empty204.add(201,"/eSCL/ScanJobs/empty204",null,new byte[0]);
        empty204.add(204,null,null,new byte[0]);
        empty204.add(204,null,null,new byte[0]);
        fails(() -> EsclScanClient.scan(empty204,true,null), "empty ADF 204 rejected");
        // An interrupted worker must not initiate an eSCL request.
        Mock preCancelled = new Mock();
        try {
            Thread.currentThread().interrupt();
            fails(() -> EsclScanClient.scan(preCancelled,false,null), "pre-cancelled scan blocked");
            check(preCancelled.requestPaths.isEmpty(), "pre-cancellation sends no network request");
        } finally { Thread.interrupted(); }
        // Interruption between ADF pages stops the job and attempts cleanup.
        Mock cancelledAdf = new Mock();
        cancelledAdf.add(200,null,"text/xml",utf(XML));
        cancelledAdf.add(201,"/eSCL/ScanJobs/stopjob",null,new byte[0]);
        cancelledAdf.add(200,null,"image/jpeg",JPEG);
        cancelledAdf.add(204,null,null,new byte[0]);
        try {
            fails(() -> EsclScanClient.scan(cancelledAdf,true,n -> Thread.currentThread().interrupt()),
                    "ADF cancellation stops between pages");
            check(cancelledAdf.requestPaths.contains("DELETE /eSCL/ScanJobs/stopjob"),
                    "ADF cancellation attempts server job cleanup");
        } finally { Thread.interrupted(); }
        Mock wrongStatus = new Mock();
        wrongStatus.add(401,null,"text/xml",utf(XML));
        fails(() -> EsclScanClient.scan(wrongStatus,true,null),"auth failed");
    }

    static void network() throws Exception {
        check(new LanEsclTransport("192.168.1.12").origin().equals("http://192.168.1.12:80"), "LAN 192.168");
        check(new LanEsclTransport("10.5.1.8:8080").origin().endsWith(":8080"),"LAN port");
        check(new LanEsclTransport("172.31.1.8").origin().contains("172.31.1.8"),"LAN 172.16/12");
        check(new LanEsclTransport("169.254.5.6").origin().contains("169.254.5.6"),"LAN link-local");
        check(new LanEsclTransport("https://192.168.1.15").origin()
                .equals("https://192.168.1.15:443"), "HTTPS default port");
        check(new LanEsclTransport("https://10.0.0.8:8443").origin()
                .equals("https://10.0.0.8:8443"), "HTTPS custom port");
        check(new LanEsclTransport("http://10.0.0.8:8080").origin()
                .equals("http://10.0.0.8:8080"), "Explicit HTTP supported");
        fails(() -> EsclProtocol.jobPath("http://10.0.0.8/eSCL/ScanJobs/a",
                "https://10.0.0.8"), "HTTPS downgrade Location rejected");
        for (String blocked : new String[]{"8.8.8.8", "127.0.0.1", "172.32.1.2", "192.168.1.5:0", "192.168.1.5:99999", "192.168.999.3", "example.org", "192.168.1.5/eSCL", "192.168.1.5@bad.org", "192.168.1.5:80#frag", "https://8.8.8.8", "https://192.168.1.5/evil", "ftp://10.1.1.1"})
            fails(() -> new LanEsclTransport(blocked),"LAN address rejection: " + blocked);
        fails(() -> LanEsclTransport.bounded(new ByteArrayInputStream(new byte[]{1,2,3}),2),"LAN byte limit");
        check(LanEsclTransport.bounded(new ByteArrayInputStream(JPEG),JPEG.length).length==JPEG.length,"LAN bounded read");
    }

    static byte[] join(byte[] a, byte[] b) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write(a);out.write(b);return out.toByteArray();
    }
    static UsbDevice printer() {
        return new UsbDevice(new UsbInterface(1,0,7,1,4,
                new UsbEndpoint(2,UsbConstants.USB_DIR_OUT),
                new UsbEndpoint(2,UsbConstants.USB_DIR_IN)));
    }
    static void usb() throws Exception {
        UsbManager manager = new UsbManager();
        manager.connection = new UsbDeviceConnection();
        UsbDevice printer = printer();
        check(UsbEsclHttpTransport.supports(printer),"IPP-over-USB profile");
        manager.permitted = false;
        fails(() -> UsbEsclHttpTransport.open(manager,printer),"USB permission gate");
        manager.permitted = true;
        byte[] response = utf("HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\nContent-Length: 3\r\n\r\nabc");
        manager.connection.incoming = response;
        try (UsbEsclHttpTransport t = UsbEsclHttpTransport.open(manager,printer)) {
            EsclScanClient.Reply r=t.exchange("GET","/eSCL/ScannerStatus",null,null,1024);
            check(r.status==200 && new String(r.body,StandardCharsets.US_ASCII).equals("abc"),"USB fixed length HTTP");
            check(manager.connection.sent.toString("US-ASCII").contains("Host: localhost"),"USB Host header");
            fails(() -> t.exchange("GET","/../../bad",null,null,1024),"USB endpoint security");
        }
        check(manager.connection.closed && manager.connection.released,"USB close/release");

        manager.connection = new UsbDeviceConnection();
        manager.connection.incoming = utf("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Type: text/xml\r\n\r\n3\r\nabc\r\n2\r\nde\r\n0\r\n\r\n");
        try (UsbEsclHttpTransport t = UsbEsclHttpTransport.open(manager,printer)) {
            check(new String(t.exchange("GET","/eSCL/ScannerStatus",null,null,10).body,StandardCharsets.US_ASCII).equals("abcde"),"USB chunks");
        }
        manager.connection = new UsbDeviceConnection();
        manager.connection.incoming = utf("HTTP/1.1 200 OK\r\nContent-Length: 100000\r\n\r\n");
        try (UsbEsclHttpTransport t = UsbEsclHttpTransport.open(manager,printer)) {
            fails(() -> t.exchange("GET","/eSCL/ScannerStatus",null,null,1024),"oversize HTTP response");
        }
        manager.connection = new UsbDeviceConnection();
        manager.connection.incoming = utf("HTTP/1.1 200 OK\r\n\r\n");
        try (UsbEsclHttpTransport t = UsbEsclHttpTransport.open(manager,printer)) {
            fails(() -> t.exchange("GET","/eSCL/ScannerStatus",null,null,1024),"unframed USB HTTP");
        }
        // An ambiguous length must be rejected at the actual USB exchange layer.
        manager.connection = new UsbDeviceConnection();
        manager.connection.incoming = utf("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n"
                + "Transfer-Encoding: chunked\r\n\r\n2\r\nok\r\n0\r\n\r\n");
        try (UsbEsclHttpTransport t = UsbEsclHttpTransport.open(manager,printer)) {
            fails(() -> t.exchange("GET","/eSCL/ScannerStatus",null,null,100),
                    "USB exchange rejects conflicting body framing");
        }
        manager.connection = new UsbDeviceConnection();
        manager.connection.incoming = utf("HTTP/1.1 204 No Content\r\nContent-Length: 1\r\n\r\nx");
        try (UsbEsclHttpTransport t = UsbEsclHttpTransport.open(manager,printer)) {
            fails(() -> t.exchange("DELETE","/eSCL/ScanJobs/x",null,null,100),
                    "USB exchange rejects malformed no-content response");
        }
        // Reject invalid preflight limits before sending anything to the printer.
        manager.connection = new UsbDeviceConnection();
        try (UsbEsclHttpTransport t = UsbEsclHttpTransport.open(manager,printer)) {
            fails(() -> t.exchange("GET","/eSCL/ScannerStatus",null,null,-1),
                    "response size checked before USB write");
            check(manager.connection.sent.size() == 0, "invalid limit writes zero USB bytes");
            check(!manager.connection.closed, "preflight rejects without tearing down valid connection");
        }
        // Malformed replies leave the stream unusable. Close before another command.
        manager.connection = new UsbDeviceConnection();
        manager.connection.incoming = utf("HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\nx");
        try (UsbEsclHttpTransport t = UsbEsclHttpTransport.open(manager,printer)) {
            fails(() -> t.exchange("GET","/eSCL/ScannerStatus",null,null,1024),
                    "truncated USB HTTP body rejected");
            check(manager.connection.closed && manager.connection.released,
                    "truncated reply closes and releases USB connection");
            fails(() -> t.exchange("GET","/eSCL/ScannerStatus",null,null,1024),
                    "failed USB session cannot be reused");
        }
        // Persistent USB connection must respect HTTP Connection: close.
        manager.connection = new UsbDeviceConnection();
        manager.connection.incoming = utf("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
        try (UsbEsclHttpTransport t = UsbEsclHttpTransport.open(manager,printer)) {
            check(t.exchange("GET","/eSCL/ScannerStatus",null,null,1024).status == 200,
                    "connection-close response parsed");
            check(manager.connection.closed, "USB transport closes when peer requests close");
        }
        // Reject malformed trailers instead of treating them as another response.
        manager.connection = new UsbDeviceConnection();
        manager.connection.incoming = utf("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"
                + "1\r\na\r\n0\r\nContent-Length: 100\r\n\r\n");
        try (UsbEsclHttpTransport t = UsbEsclHttpTransport.open(manager,printer)) {
            fails(() -> t.exchange("GET","/eSCL/ScannerStatus",null,null,1024),
                    "chunked framing trailer blocked at USB layer");
            check(manager.connection.closed, "framing trailer failure closes USB transport");
        }
        manager.connection = new UsbDeviceConnection();
        manager.connection.claimSuccess = false;
        fails(() -> UsbEsclHttpTransport.open(manager,printer),"USB interface claim failure");
        check(manager.connection.closed,"failed claim releases connection");

        // End-to-end eSCL job across the same USB HTTP stream: caps -> POST 201 -> JPEG -> DELETE.
        manager.connection = new UsbDeviceConnection();
        byte[] capabilities = utf(XML);
        byte[] rsp1 = utf("HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\nContent-Length: "
                + capabilities.length + "\r\n\r\n");
        byte[] rsp2 = utf("HTTP/1.1 201 Created\r\nLocation: /eSCL/ScanJobs/job01\r\nContent-Length: 0\r\n\r\n");
        byte[] rsp3 = utf("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: "
                + JPEG.length + "\r\n\r\n");
        byte[] rsp4 = utf("HTTP/1.1 204 No Content\r\n\r\n");
        manager.connection.incoming = join(join(join(join(join(rsp1, capabilities),rsp2),rsp3),JPEG),rsp4);
        try (UsbEsclHttpTransport t = UsbEsclHttpTransport.open(manager, printer)) {
            check(EsclScanClient.scan(t,false,null).size() == 1, "full USB job transaction");
            String sent = manager.connection.sent.toString("US-ASCII");
            check(sent.contains("POST /eSCL/ScanJobs HTTP/1.1"),"USB transmitted eSCL POST");
            check(sent.contains("DELETE /eSCL/ScanJobs/job01 HTTP/1.1"),"USB transaction cleanup");
        }
        check(manager.connection.closed, "full USB session closed");
        // ADF termination using 204 should also be valid over the USB HTTP stream.
        manager.connection = new UsbDeviceConnection();
        byte[] uc = utf(XML);
        manager.connection.incoming = join(join(join(join(join(join(
                utf("HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\nContent-Length: "
                        + uc.length + "\r\n\r\n"), uc),
                utf("HTTP/1.1 201 Created\r\nLocation: /eSCL/ScanJobs/adf\r\nContent-Length: 0\r\n\r\n")),
                utf("HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: "
                        + JPEG.length + "\r\n\r\n")), JPEG),
                utf("HTTP/1.1 204 No Content\r\n\r\n")),
                utf("HTTP/1.1 204 No Content\r\n\r\n"));
        try (UsbEsclHttpTransport t = UsbEsclHttpTransport.open(manager, printer)) {
            check(EsclScanClient.scan(t, true, null).size() == 1,
                    "full USB ADF 204 termination accepted");
        }
    }

    public static void main(String[] args) throws Exception {
        protocol(); scan(); network(); usb();
        System.out.println("PASS: " + checks + " eSCL protocol and USB/LAN mock checks");
    }
}
