package kr.sungok.wirelessexplorer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/**
 * A purely decorative scan progress symbol, not a spatial RF map.
 * Shows fixed concentric arcs; it never draws device locations or distances.
 */
public final class ScanPulseView extends View {
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private boolean scanning;
    private boolean reduceMotion;
    private int accent = Color.rgb(62, 221, 186);
    private int dim = Color.rgb(49, 83, 105);

    public ScanPulseView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("장식용 무선 탐색 애니메이션. 실제 기기 위치나 거리를 나타내지 않습니다.");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setColors(int active, int inactive) {
        accent = active;
        dim = inactive;
        invalidate();
    }

    public void setReduceMotion(boolean reduce) {
        reduceMotion = reduce;
        invalidate();
    }

    public void setScanning(boolean active) {
        scanning = active;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - getResources().getDisplayMetrics().density * 9;
        float stroke = getResources().getDisplayMetrics().density * 1.6f;
        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(stroke);
        for (int i = 1; i <= 3; i++) {
            float r = radius * i / 3f;
            pen.setColor(dim);
            pen.setAlpha(i == 3 ? 195 : 130);
            canvas.drawCircle(cx, cy, r, pen);
        }
        pen.setStrokeWidth(stroke * 1.5f);
        pen.setColor(accent);
        pen.setAlpha(220);
        float sweep = scanning && !reduceMotion
                ? ((SystemClock.uptimeMillis() % 3600L) / 10f) : -48f;
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(arc, sweep, scanning ? 76 : 46, false, pen);
        pen.setStyle(Paint.Style.FILL);
        pen.setColor(accent);
        pen.setAlpha(255);
        canvas.drawCircle(cx, cy, stroke * 3.1f, pen);
        if (scanning && !reduceMotion && isAttachedToWindow())
            postInvalidateDelayed(75);
    }
}
