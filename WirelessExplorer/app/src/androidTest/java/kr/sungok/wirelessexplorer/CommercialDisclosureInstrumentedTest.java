package kr.sungok.wirelessexplorer;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Critical privacy regression: discovery permission requests cannot skip
 * the first-run explanation, and active LAN probing requires explicit consent.
 * Tests check default-deny behavior without actually sending network traffic.
 */
@RunWith(AndroidJUnit4.class)
public class CommercialDisclosureInstrumentedTest {
    @Rule public ActivityTestRule<MainActivity> activityRule =
            new ActivityTestRule<>(MainActivity.class);

    private TextView find(View view, String matching) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (text.getText() != null && text.getText().toString().contains(matching))
                return text;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = find(group.getChildAt(i), matching);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test public void scanRequiresProminentDisclosureBeforePermissions() throws Throwable {
        MainActivity activity = activityRule.getActivity();
        activity.getPreferences(Activity.MODE_PRIVATE).edit()
                .putBoolean("scan_disclosure_ack_v1", false).commit();
        TextView scan = find(activity.findViewById(android.R.id.content), "탐색 시작");
        assertNotNull(scan);
        activityRule.runOnUiThread(scan::performClick);
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertEquals("탐색 시작", scan.getText().toString());
        assertFalse(activity.getPreferences(Activity.MODE_PRIVATE)
                .getBoolean("scan_disclosure_ack_v1", false));
    }

    @Test public void lanProbeCannotBeEnabledWithoutAuthorizedNetworkConsent() throws Throwable {
        MainActivity activity = activityRule.getActivity();
        TextView candidate = find(activity.findViewById(android.R.id.content), "연결된 LAN도 조사");
        assertNotNull(candidate);
        assertTrue(candidate instanceof CheckBox);
        CheckBox toggle = (CheckBox) candidate;
        assertFalse(toggle.isChecked());
        activityRule.runOnUiThread(() -> toggle.setChecked(true));
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertFalse("LAN probing remains off until explicit confirmation", toggle.isChecked());
    }
}
