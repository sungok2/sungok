package com.galaxyuniversalio;
import android.hardware.usb.*;
import java.util.Arrays;

/** Fake USB device tests: no actual Android SDK or hardware invoked. */
public final class PrinterTransportTests {
    private static int checks = 0;
    private static void ok(boolean b, String message) {
        if (!b) throw new AssertionError(message);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        UsbEndpoint bulk = new UsbEndpoint(2, 0);
        UsbInterface printer = new UsbInterface(4, 0, 7, 1, 2, bulk);
        UsbDevice device = new UsbDevice(new UsbInterface(0,0,8,6,80), printer);
        UsbManager manager = new UsbManager();
        ok(UsbPrinterTransport.select(device).rawCapable, "Printer class 2 selected");
        ok(UsbPrinterTransport.select(device).intf.getId() == 4, "USB interface id");
        manager.connection = new UsbDeviceConnection();
        String text = UsbPrinterTransport.probe(manager, device);
        ok(text.contains("MFG:FAKE;"), "printer 1284 ID");
        ok(text.contains("paperEmpty=false"), "printer status decoded");
        ok(manager.connection.released && manager.connection.closed, "probe closes connection");
        ok(manager.connection.requestType == 0xA1, "USB printer class request type");
        ok(manager.connection.wIndex == 4 && manager.connection.request == 1, "port status index");
        byte[] job = new byte[24000];
        for (int i=0; i<job.length; i++) job[i] = (byte)(i & 255);
        manager.connection = new UsbDeviceConnection();
        int sent = UsbPrinterTransport.sendRaw(manager, device, job);
        ok(sent == job.length, "raw all bytes accounted for");
        ok(Arrays.equals(job, manager.connection.sent.toByteArray()), "partial writes keep correct byte offset");
        ok(manager.connection.sendCalls > 3, "short writes retried");
        ok(manager.connection.released && manager.connection.closed, "raw closes connection");
        manager.connection = new UsbDeviceConnection();
        manager.connection.failBulk = true;
        try { UsbPrinterTransport.sendRaw(manager, device, job); throw new AssertionError("bulk failed should throw"); }
        catch (java.io.IOException expected) { checks++; }
        ok(manager.connection.closed, "bulk error closes");
        manager.connection = new UsbDeviceConnection();
        manager.connection.claimSuccess = false;
        try { UsbPrinterTransport.probe(manager, device); throw new AssertionError("claim failed should throw"); }
        catch (java.io.IOException expected) { checks++; }
        ok(manager.connection.closed, "claim failed closes");
        manager.permitted = false;
        try { UsbPrinterTransport.probe(manager, device); throw new AssertionError("permission fail expected"); }
        catch (java.io.IOException expected) { checks++; }
        manager.permitted = true;
        UsbDevice alternative = new UsbDevice(new UsbInterface(4,1,7,1,2,bulk));
        manager.connection = new UsbDeviceConnection();
        manager.connection.alternateSuccess = false;
        try { UsbPrinterTransport.probe(manager, alternative); throw new AssertionError("alternate setting should fail"); }
        catch (java.io.IOException expected) { checks++; }
        ok(manager.connection.closed, "printer alternate-setting failure closes");
        ok(UsbPrinterTransport.select(new UsbDevice(new UsbInterface(0,0,6,1,1))) == null,
                "non-printer should not raw transfer");
        System.out.println("PASS: " + checks + " printer transport fake-device checks");
    }
}
