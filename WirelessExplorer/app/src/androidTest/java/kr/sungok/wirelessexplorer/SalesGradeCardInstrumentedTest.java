package kr.sungok.wirelessexplorer;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

/**
 * End-to-end visual-state regression using clearly synthetic local fixtures.
 * No fake devices are created in production app code.
 */
@RunWith(AndroidJUnit4.class)
public class SalesGradeCardInstrumentedTest {
    @Rule public ActivityTestRule<MainActivity> rule =
            new ActivityTestRule<>(MainActivity.class, false, false);

    private TextView findText(View view, String phrase) {
        if (view instanceof TextView) {
            TextView t = (TextView) view;
            if (t.getText() != null && t.getText().toString().contains(phrase)) return t;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), phrase);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test public void importedInventoryDisplaysProvenanceAndExpandableRssiEvidence() throws Throwable {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DeviceStore fixtures = new DeviceStore(context);
        fixtures.clear();
        try {
            fixtures.observe("BLE", "QA Sample Beacon", "AA:BB:CC:DD:EE:99",
                    "Synthetic fixture for UI instrumentation", -65, true);
            List<DeviceRecord> items = fixtures.all();
            assertEquals(1, items.size());
            items.get(0).signalTrace = "-91,-75,-65,-58,-62";
            assertTrue(fixtures.save());

            MainActivity activity = rule.launchActivity(
                    new Intent(context, MainActivity.class));
            ViewGroup root = activity.findViewById(android.R.id.content);
            TextView name = findText(root, "QA Sample Beacon");
            assertNotNull("name should be visible on record card", name);
            TextView past = findText(root, "과거 관찰");
            assertNotNull("past records must not be current RF", past);

            View v = name;
            while (v != null && !v.isClickable()) {
                android.view.ViewParent parent = v.getParent();
                v = parent instanceof View ? (View) parent : null;
            }
            assertNotNull("card must be clickable", v);
            final View clickable = v;
            rule.runOnUiThread(clickable::performClick);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNotNull(findText(root, "탐지 증거"));
            assertNotNull(findText(root, "최근 RSSI"));
            assertNotNull(findText(root, "Synthetic fixture"));
        } finally {
            fixtures.clear();
        }
    }
}
