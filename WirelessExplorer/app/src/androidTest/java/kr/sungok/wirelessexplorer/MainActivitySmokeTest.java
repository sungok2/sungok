package kr.sungok.wirelessexplorer;

import android.view.ViewGroup;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import static org.junit.Assert.*;

/** Offline UI launch and basic screen smoke; not a test of real radio scanning. */
@RunWith(AndroidJUnit4.class)
public class MainActivitySmokeTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);

    @Test public void opensWithoutRadioPermissionsAndShowsInventoryConsole() {
        MainActivity activity = rule.getActivity();
        assertNotNull(activity);
        assertNotNull(activity.getWindow());
        assertNotNull(activity.getWindow().getDecorView());
        ViewGroup content = activity.findViewById(android.R.id.content);
        assertNotNull(content);
        assertTrue(content.getChildCount() > 0);
    }
}
