package kr.sungok.wirelessexplorer;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import static org.junit.Assert.*;

/** Real Android JSON restore, duplicate handling and provenance behavior. */
@RunWith(AndroidJUnit4.class)
public class ImportHistoryInstrumentedTest {
    @Test public void exportUninstallStyleRestoreDoesNotInventCurrentPresence() {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DeviceStore source = new DeviceStore(app);
        source.clear();
        try {
            source.beginSession();
            source.observe("BLE", "KnownBeacon", "02:11:22:33:44:55",
                    "BLE advertisement", -65, true);
            source.observe("NSD", "Printer", "_ipp._tcp./Office Printer",
                    "Local DNS-SD", 0, true);
            String json = source.asJson();

            // Simulate data loss after replacing an APK signed by a new debug key.
            source.clear();
            DeviceStore restored = new DeviceStore(app);
            DeviceStore.ImportResult outcome = restored.importJson(json);
            assertTrue(outcome.error, outcome.success());
            assertEquals(2, outcome.added);
            assertEquals(0, restored.countThisSession());
            assertEquals(0, restored.countLiveRadioEndpoints());
            assertEquals(0, restored.countLiveNetworkServices());
            assertEquals(2, new DeviceStore(app).total());
            List<DeviceRecord> records = restored.all();
            for (DeviceRecord d : records) {
                assertFalse(d.live);
                assertTrue(d.imported);
                assertTrue(d.confirmed);
            }

            DeviceStore.ImportResult again = restored.importJson(json);
            assertTrue(again.success());
            assertEquals(0, again.added);
            assertEquals(2, again.skipped);

            DeviceStore.ImportResult invalid = restored.importJson("{not-json}");
            assertFalse(invalid.success());
            assertEquals(2, restored.total());

            String badKey = json.replace("BLE|02:11:22:33:44:55",
                    "BLE|22:22:22:22:22:22");
            restored.clear();
            DeviceStore.ImportResult partial = restored.importJson(badKey);
            assertTrue(partial.success());
            assertEquals(1, partial.added);
            assertEquals(1, partial.skipped);
        } finally {
            new DeviceStore(app).clear();
        }
    }
}
