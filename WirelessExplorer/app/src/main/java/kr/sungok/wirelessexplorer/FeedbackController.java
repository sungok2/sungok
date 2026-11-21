package kr.sungok.wirelessexplorer;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * Minimal original system tone cues, never background audio playback.
 * Sound is opt-in; discovery-tone emission is throttled per session.
 */
public final class FeedbackController {
    private ToneGenerator tones;
    private boolean soundEnabled;
    private boolean hapticsEnabled;
    private long lastDiscoveryTone;
    private int tonesThisSession;

    public FeedbackController(Context context) {
        soundEnabled = context.getSharedPreferences("discovery-ui", Context.MODE_PRIVATE)
                .getBoolean("sound", false);
        hapticsEnabled = context.getSharedPreferences("discovery-ui", Context.MODE_PRIVATE)
                .getBoolean("haptic", true);
    }

    public boolean soundEnabled() { return soundEnabled; }
    public boolean hapticsEnabled() { return hapticsEnabled; }

    public void setSoundEnabled(Context context, boolean value) {
        soundEnabled = value;
        context.getSharedPreferences("discovery-ui", Context.MODE_PRIVATE)
                .edit().putBoolean("sound", value).apply();
        if (value) tone(ToneGenerator.TONE_PROP_ACK, 90);
        else releaseTone();
    }

    public void setHapticsEnabled(Context context, boolean value) {
        hapticsEnabled = value;
        context.getSharedPreferences("discovery-ui", Context.MODE_PRIVATE)
                .edit().putBoolean("haptic", value).apply();
    }

    public void start(View view) {
        tonesThisSession = 0;
        lastDiscoveryTone = 0L;
        haptic(view);
        tone(ToneGenerator.TONE_PROP_ACK, 80);
    }

    public void foundNewEndpoint() {
        long now = SystemClock.elapsedRealtime();
        if (tonesThisSession >= 8 || now - lastDiscoveryTone < 8500L)
            return;
        lastDiscoveryTone = now;
        tonesThisSession++;
        tone(ToneGenerator.TONE_PROP_BEEP, 45);
    }

    public void finish(View view) {
        haptic(view);
        tone(ToneGenerator.TONE_PROP_ACK, 110);
    }

    public void haptic(View view) {
        if (hapticsEnabled && view != null)
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void tone(int kind, int duration) {
        if (!soundEnabled) return;
        try {
            if (tones == null) tones = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 40);
            tones.startTone(kind, duration);
        } catch (RuntimeException ignored) { releaseTone(); }
    }

    private void releaseTone() {
        if (tones != null) {
            tones.release();
            tones = null;
        }
    }

    public void close() { releaseTone(); }
}
