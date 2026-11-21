package kr.sungok.wirelessexplorer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.net.Uri;

import static org.junit.Assert.*;

/**
 * Verifies actual main-screen interactions and custom visual draw paths.
 * Does not claim that emulator scan results represent real radio hardware.
 */
@RunWith(AndroidJUnit4.class)
public class DashboardUiInstrumentedTest {
    @Rule public ActivityTestRule<MainActivity> activityRule =
            new ActivityTestRule<>(MainActivity.class);

    private TextView findText(View root, String part) {
        if (root instanceof TextView) {
            TextView t = (TextView) root;
            if (t.getText() != null && t.getText().toString().contains(part)) return t;
            if (t.getHint() != null && t.getHint().toString().contains(part)) return t;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), part);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test public void dashboardHasUsableScanControlsAndExpandableUtilities() throws Throwable {
        MainActivity activity = activityRule.getActivity();
        ViewGroup root = activity.findViewById(android.R.id.content);
        assertNotNull(findText(root, "WIRELESS EXPLORER"));
        assertNotNull(findText(root, "주변 기기 탐색"));
        assertNotNull(findText(root, "탐색 시작"));
        assertNotNull(findText(root, "발견 기기"));
        assertNotNull(findText(root, "연결된 LAN도 조사"));
        assertNotNull(findText(root, "이름 · IP · MAC"));

        TextView tools = findText(root, "기록 · 검증 · 고급 설정");
        assertNotNull(tools);
        activityRule.runOnUiThread(tools::performClick);
        androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().waitForIdleSync();
        TextView backup = findText(root, "JSON 백업");
        assertNotNull(backup);
        assertEquals(View.VISIBLE, backup.getVisibility());
    }

    @Test public void optionalAudioToggleCanBeEnabledAndDisabled() throws Throwable {
        MainActivity activity = activityRule.getActivity();
        ViewGroup root = activity.findViewById(android.R.id.content);
        TextView sound = findText(root, "소리 ");
        assertNotNull(sound);
        // Known end state in case a previous test toggled the saved preference.
        activityRule.runOnUiThread(sound::performClick);
        androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().waitForIdleSync();
        assertTrue(sound.getText().toString().contains("소리 "));
        activityRule.runOnUiThread(sound::performClick);
    }

    @Test public void snapshotActualAppViewWithoutEmulatorLauncher() throws Throwable {
        MainActivity activity = activityRule.getActivity();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        activityRule.runOnUiThread(() -> {
            try {
                View decor = activity.getWindow().getDecorView();
                int width = decor.getWidth(), height = decor.getHeight();
                assertTrue("view must be laid out", width > 0 && height > 0);
                Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                decor.draw(new Canvas(image));
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, "WirelessExplorer-UI-view.png");
                    values.put(MediaStore.Downloads.MIME_TYPE, "image/png");
                    values.put(MediaStore.Downloads.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = activity.getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    assertNotNull("MediaStore item must be creatable", uri);
                    try (OutputStream stream = activity.getContentResolver().openOutputStream(uri)) {
                        assertNotNull(stream);
                        assertTrue("PNG serialization failed",
                                image.compress(Bitmap.CompressFormat.PNG, 100, stream));
                    }
                } else {
                    File out = new File(activity.getFilesDir(), "wireless-ui-view.png");
                    try (FileOutputStream stream = new FileOutputStream(out)) {
                        assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream));
                    }
                    assertTrue(out.length() > 1000L);
                }
                image.recycle();
            } catch (Throwable t) { failure.set(t); }
        });
        androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().waitForIdleSync();
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test public void scannerAndRssiGraphicsDrawWithoutLocationSimulation() throws Throwable {
        MainActivity activity = activityRule.getActivity();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        activityRule.runOnUiThread(() -> {
            try {
                ScanPulseView pulse = new ScanPulseView(activity);
                pulse.setScanning(false);
                pulse.layout(0, 0, 180, 180);
                Bitmap circleBitmap = Bitmap.createBitmap(180, 180, Bitmap.Config.ARGB_8888);
                pulse.draw(new Canvas(circleBitmap));
                assertTrue(pulse.getContentDescription().toString().contains("실제 기기 위치"));

                SignalPlotView graph = new SignalPlotView(activity);
                graph.setHistory("-95,-88,-69,-55,-61,-77");
                graph.layout(0, 0, 300, 80);
                Bitmap graphBitmap = Bitmap.createBitmap(300, 80, Bitmap.Config.ARGB_8888);
                graph.draw(new Canvas(graphBitmap));
                assertTrue(graph.getContentDescription().toString().contains("6개"));
            } catch (Throwable t) { failure.set(t); }
        });
        androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().waitForIdleSync();
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
