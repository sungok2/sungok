package kr.sungok.wirelessexplorer;

import android.content.Context;
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
    @Test public void freshEvidenceAndLocalPersistenceAreCorrect() {
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
            assertTrue(store.save());

            DeviceStore persisted = new DeviceStore(app);
            assertEquals(2, persisted.total());
            assertEquals(0, persisted.countThisSession());
            DeviceRecord wifi = null;
            for (DeviceRecord d : persisted.all()) {
                if ("WIFI".equals(d.protocol)) wifi = d;
            }
            assertNotNull(wifi);
            assertEquals("AA:BB:CC:DD:EE:FF", wifi.address);
            assertTrue(wifi.confirmed);
            assertTrue(wifi.lastConfirmedAt > 0);
            assertTrue(persisted.asCsv().contains("lastConfirmedAt"));
            assertTrue(persisted.asJson().contains("lastConfirmedAt"));

            persisted.clear();
            assertEquals(0, new DeviceStore(app).total());
        } finally {
            new DeviceStore(app).clear();
        }
    }
}
