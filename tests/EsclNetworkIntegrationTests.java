package com.galaxyuniversalio;

import java.io.IOException;
import java.util.List;

/** Connects to the included fake eSCL server on a private-LAN IP. */
public final class EsclNetworkIntegrationTests {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: <private IPv4:port> <jpeg|png>");
        String expectedMime = "image/" + args[1];
        if (!expectedMime.equals("image/jpeg") && !expectedMime.equals("image/png"))
            throw new IllegalArgumentException("Unsupported test image format");
        for (boolean feeder : new boolean[]{false,true}) {
            try (LanEsclTransport client = new LanEsclTransport(args[0])) {
                EsclProtocol.Capabilities capabilities = client.probeCapabilities();
                if (!capabilities.platen || !capabilities.feeder || capabilities.dpi < 1)
                    throw new AssertionError("Expected valid platen/ADF capabilities and resolution");
                System.out.println("PASS: capability probe parsed supported platen/ADF profiles at " + capabilities.dpi + " dpi");
                List<byte[]> scan = EsclScanClient.scan(client, feeder, null);
                int expected = feeder ? 2 : 1;
                if (scan.size() != expected) throw new AssertionError("Expected " + expected + " pages");
                for (byte[] bytes : scan)
                    if (!EsclProtocol.requireImage(bytes, expectedMime).equals(expectedMime))
                        throw new AssertionError("Unexpected negotiated format");
                System.out.println("PASS: mock " + (feeder?"ADF":"platen") + " eSCL " + expected + " " + expectedMime + " pages");
            }
        }
    }
}
