package kr.sungok.wirelessexplorer;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import static org.junit.Assert.*;

/**
 * Real Android integration test: JSON/AtomicFile + cached vs newly observed
 * endpoints. The test uses app-private emulator storage, never external data.
 */
@RunWith(AndroidJUnit4.class)
public class DeviceStoreInstrumentedTest {
    @Test public void freshEvidenceAndLocalPersistenceAreCorrect() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DeviceStore store = new DeviceStore(app);
        store.clear();
        try {
            store.beginSession();
            store.observe("WIFI", "TestAP", "aa-bb-cc-dd-ee-ff",
                    "2.4 GHz", -72, false);
            assertEquals(1, store.total());
            assertEquals(0, store.countThisSession());
            assertEquals(0, store.countLiveRadioEndpoints());
            List<DeviceRecord> before = store.all();
            assertFalse(before.get(0).confirmed);
            assertEquals(0L, before.get(0).lastConfirmedAt);

            store.observe("WIFI", "TestAP", "AA:BB:CC:DD:EE:FF",
                    "2.4 GHz · CH1", -60, true);
            store.observe("NSD", "TestPrinter", "_ipp._tcp./TestPrinter",
                    "mDNS response", 0, true);
            assertEquals(2, store.total());
            assertEquals(1, store.countLiveRadioEndpoints());
            assertEquals(1, store.countLiveNetworkServices());
            store.observe("LAN", "LAN host", "192.168.1.15",
                    "TCP 80 responded", 0, true);
            assertEquals(1, store.countLiveLanHosts());
            assertEquals(1, store.countLiveNetworkServices());
            assertTrue(store.save());

            DeviceStore persisted = new DeviceStore(app);
            assertEquals(3, persisted.total());
            assertEquals(0, persisted.countThisSession());
            DeviceRecord wifi = null;
            for (DeviceRecord d : persisted.all()) {
                if ("WIFI".equals(d.protocol)) wifi = d;
            }
            assertNotNull(wifi);
            assertEquals("AA:BB:CC:DD:EE:FF", wifi.address);
            assertTrue(wifi.confirmed);
            assertTrue(wifi.lastConfirmedAt > 0);
            assertEquals(1, SignalTrend.sampleCount(wifi.signalTrace));
            long verifiedAt = wifi.lastConfirmedAt;
            long verifiedLastSeen = wifi.lastSeen;
            int verifiedRssi = wifi.rssi;
            String verifiedDetail = wifi.details;
            String verifiedTrace = wifi.signalTrace;
            // Re-reading Android's cache must not alter verified evidence.
            persisted.observe("WIFI", "StaleAP", "aa-bb-cc-dd-ee-ff",
                    "old cached results", -91, false);
            DeviceRecord same = null;
            for (DeviceRecord d : persisted.all()) if ("WIFI".equals(d.protocol)) same = d;
            assertNotNull(same);
            assertEquals(verifiedAt, same.lastConfirmedAt);
            assertEquals(verifiedLastSeen, same.lastSeen);
            assertEquals(verifiedRssi, same.rssi);
            assertEquals(verifiedDetail, same.details);
            assertEquals(verifiedTrace, same.signalTrace);
            assertEquals("TestAP", same.name);
            assertTrue(persisted.asCsv().contains("lastConfirmedAt"));
            assertTrue(persisted.asJson().contains("lastConfirmedAt"));

            assertTrue(persisted.save());
            // Simulate a power loss during the next atomic history write.
            AtomicFile atomic = new AtomicFile(new File(app.getFilesDir(), "observations.json"));
            FileOutputStream interrupted = atomic.startWrite();
            interrupted.write("not a valid complete JSON array".getBytes(StandardCharsets.UTF_8));
            atomic.failWrite(interrupted);
            DeviceStore recovered = new DeviceStore(app);
            assertEquals("AtomicFile rollback must preserve valid history", 3, recovered.total());
            recovered.clear();
            assertEquals(0, new DeviceStore(app).total());
        } finally {
            new DeviceStore(app).clear();
        }
    }
}
