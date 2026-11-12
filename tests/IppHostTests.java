package com.galaxyuniversalio;

import android.hardware.usb.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** RFC8010 minimal IPP and fake USB PDF spool tests. */
public final class IppHostTests {
    private static int tests;
    private static void ok(boolean b, String message) {
        tests++;
        if (!b) throw new AssertionError(message);
    }
    private interface Checked { void run() throws Exception; }
    private static void fail(Checked c, String message) throws Exception {
        boolean did = false;
        try { c.run(); } catch (IOException e) { did = true; }
        ok(did, message);
    }
    private static byte[] getReply(int id, boolean pdf, int status) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(buf);
        d.writeByte(1); d.writeByte(1); d.writeShort(status); d.writeInt(id);
        d.writeByte(4); // printer-attributes group
        String name="document-format-supported";
        byte[] n = name.getBytes(StandardCharsets.US_ASCII);
        String format= pdf ? "application/pdf" : "image/pwg-raster";
        byte[] v=format.getBytes(StandardCharsets.US_ASCII);
        d.writeByte(0x49);d.writeShort(n.length);d.write(n);d.writeShort(v.length);d.write(v);
        // A second format with name-length 0 (multi-valued attribute)
        if (pdf) {
            byte[] another = "image/pwg-raster".getBytes(StandardCharsets.US_ASCII);
            d.writeByte(0x49);d.writeShort(0);d.writeShort(another.length);d.write(another);
        }
        d.writeByte(3);
        return buf.toByteArray();
    }
    private static byte[] http(byte[] ipp) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(("HTTP/1.1 200 OK\r\nContent-Type: application/ipp\r\nContent-Length: "
                + ipp.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        b.write(ipp);
        return b.toByteArray();
    }
    private static byte[] join(byte[] a, byte[] b)throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();o.write(a);o.write(b);return o.toByteArray();
    }
    private static UsbDevice printer() {
        return new UsbDevice(new UsbInterface(0,0,7,1,4,
                new UsbEndpoint(2,UsbConstants.USB_DIR_IN),
                new UsbEndpoint(2,UsbConstants.USB_DIR_OUT)));
    }

    public static void main(String[] args) throws Exception {
        byte[] get=IppProtocol.getPrinterAttributes(1);
        ok(get[0]==1 && get[1]==1,"IPP 1.1 encoding");
        ok(get[2]==0 && get[3]==0x0b,"Get-Printer-Attributes op");
        ok(get[get.length-1]==3,"attributes end marker");
        ok(new String(get,StandardCharsets.ISO_8859_1).contains("document-format-supported"),"capabilities request field");
        byte[] pdf="%PDF-1.7\nhello world!\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        byte[] print=IppProtocol.printJob(2,pdf);
        ok(print[2]==0 && print[3]==2,"Print-Job op");
        ok(print.length>pdf.length,"IPP attributes and payload");
        ok(print[print.length-pdf.length]=='%' && print[print.length-1]=='F',"payload exact appended");
        ok(new String(print,StandardCharsets.ISO_8859_1).contains("application/pdf"),"PDF document MIME");
        fail(()->IppProtocol.printJob(1,new byte[0]),"zero PDF reject");
        fail(()->IppProtocol.printJob(1,"HELLO WORLD".getBytes(StandardCharsets.US_ASCII)),"non-PDF reject");
        fail(()->IppProtocol.getPrinterAttributes(0),"zero IPP request ID");
        IppProtocol.Response attr=IppProtocol.parseResponse(getReply(1,true,0),1);
        ok(attr.status==0 && attr.pdfSupported,"supports PDF");
        ok(!IppProtocol.parseResponse(getReply(1,false,0),1).pdfSupported,"does not advertise PDF");
        ok(IppProtocol.parseResponse(getReply(1,true,0x0402),1).status==0x0402,"IPP printer rejects");
        fail(()->IppProtocol.parseResponse(getReply(1,true,0),2),"request ID mismatched");
        fail(()->IppProtocol.parseResponse(new byte[8],1),"short IPP reply");
        byte[] noEnd=getReply(1,true,0);
        fail(()->IppProtocol.parseResponse(java.util.Arrays.copyOf(noEnd,noEnd.length-1),1),"missing IPP end marker");
        ok(UsbEsclHttpTransport.supports(printer()),"USB IPP interface");
        UsbManager m = new UsbManager();
        m.connection = new UsbDeviceConnection();
        byte[] reply1 = http(getReply(1,true,0));
        byte[] reply2 = http(getReply(2,false,0));
        m.connection.incoming=join(reply1,reply2);
        String accepted = UsbIppPdfPrinter.print(m,printer(),pdf);
        ok(accepted.contains("accepted"),"fake printer accepted IPP PDF job");
        String requests=m.connection.sent.toString("ISO-8859-1");
        ok(requests.contains("POST /ipp/print HTTP/1.1"),"IPP HTTP endpoint");
        ok(requests.contains("Content-Type: application/ipp"),"IPP HTTP mime");
        ok(requests.contains("%PDF-1.7"),"spooled original PDF");
        ok(m.connection.closed && m.connection.released,"USB released after IPP print");
        m.connection = new UsbDeviceConnection();
        m.connection.incoming = http(getReply(1,false,0));
        fail(()->UsbIppPdfPrinter.print(m,printer(),pdf),"device that does not advertise PDF blocked");
        ok(!m.connection.sent.toString("ISO-8859-1").contains("%PDF-"),"PDF not sent when unsupported");
        fail(()->UsbIppPdfPrinter.print(m,printer(),new byte[]{1,2}),"invalid PDF blocked before USB");
        System.out.println("PASS: " + tests + " IPP protocol + fake USB print checks");
    }
}
