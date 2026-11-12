package com.galaxyuniversalio;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import java.io.IOException;
import java.util.Locale;

/** Direct IPP-over-USB PDF Print-Job for devices advertising application/pdf. */
public final class UsbIppPdfPrinter {
    private UsbIppPdfPrinter() { }

    public static String print(UsbManager manager, UsbDevice printer, byte[] pdf)
            throws IOException {
        // Validate PDF BEFORE connecting and sending any bytes.
        IppProtocol.printJob(2, pdf);
        try (UsbEsclHttpTransport usb = UsbEsclHttpTransport.open(manager, printer)) {
            EsclScanClient.Reply caps = usb.exchange("POST", "/ipp/print", "application/ipp",
                    IppProtocol.getPrinterAttributes(1), 256 * 1024);
            checkIppHttp(caps, "Get-Printer-Attributes");
            IppProtocol.Response attr = IppProtocol.parseResponse(caps.body, 1);
            if (attr.status > 0x00FF)
                throw new IOException(String.format(Locale.US,
                        "IPP capabilities request rejected: 0x%04X", attr.status));
            if (!attr.pdfSupported)
                throw new IOException("Printer does not advertise direct application/pdf support");
            EsclScanClient.Reply result = usb.exchange("POST", "/ipp/print", "application/ipp",
                    IppProtocol.printJob(2, pdf), 256 * 1024);
            checkIppHttp(result, "Print-Job");
            IppProtocol.Response accepted = IppProtocol.parseResponse(result.body, 2);
            if (accepted.status > 0x00FF)
                throw new IOException(String.format(Locale.US,
                        "IPP Print-Job rejected: 0x%04X", accepted.status));
            return String.format(Locale.US,
                    "IPP Print-Job accepted (0x%04X). Physical paper output not verified.",
                    accepted.status);
        }
    }

    private static void checkIppHttp(EsclScanClient.Reply r, String operation) throws IOException {
        if (r.status != 200) throw new IOException(operation + " HTTP " + r.status);
        if (r.mediaType == null || !r.mediaType.toLowerCase(Locale.ROOT)
                .split(";", 2)[0].trim().equals("application/ipp"))
            throw new IOException(operation + " returned non-IPP content type");
    }
}
