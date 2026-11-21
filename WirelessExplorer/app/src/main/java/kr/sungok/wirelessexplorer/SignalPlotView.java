package kr.sungok.wirelessexplorer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * A native, accessible RSSI sparkline. Input is bounded confirmed signal
 * history; never claim RSSI provides a calibrated location or distance.
 */
public final class SignalPlotView extends View {
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String history = "";
    private int[] samples = new int[0];
    private final Path plotPath = new Path();
    private int lineColor = Color.rgb(80, 210, 184);
    private int guideColor = Color.rgb(53, 76, 93);

    public SignalPlotView(Context context) {
        super(context);
        setContentDescription("수신 신호 강도 추이. 위쪽일수록 dBm이 강하며 거리나 위치를 나타내지 않습니다.");
    }

    public void setHistory(String raw) {
        history = SignalTrend.clean(raw);
        if (history.isEmpty()) {
            samples = new int[0];
        } else {
            String[] parts = history.split(",");
            int[] next = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try { next[i] = Integer.parseInt(parts[i]); }
                catch (NumberFormatException e) { next[i] = -100; }
            }
            samples = next;
        }
        setContentDescription("최근 " + samples.length
                + "개 수신 신호 강도 기록. 거리를 나타내지 않습니다.");
        invalidate();
    }

    public void setColors(int line, int guide) {
        lineColor = line;
        guideColor = guide;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (samples.length < 2) return;
        float density = getResources().getDisplayMetrics().density;
        float pad = density * 3;
        float width = getWidth() - 2 * pad;
        float height = getHeight() - 2 * pad;
        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(density);
        pen.setColor(guideColor);
        pen.setAlpha(130);
        for (int i = 1; i <= 3; i++)
            canvas.drawLine(pad, pad + i * height / 4f,
                    pad + width, pad + i * height / 4f, pen);

        plotPath.reset();
        for (int i = 0; i < samples.length; i++) {
            int sample = samples[i];
            float x = pad + width * i / Math.max(1, samples.length - 1);
            // (-35 dBm) near top; (-100 dBm) near bottom.
            float y = pad + height * (Math.max(0, Math.min(65, -35 - sample)) / 65f);
            if (i == 0) plotPath.moveTo(x, y);
            else plotPath.lineTo(x, y);
        }
        pen.setColor(lineColor);
        pen.setAlpha(240);
        pen.setStrokeWidth(density * 2.2f);
        pen.setStrokeCap(Paint.Cap.ROUND);
        pen.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(plotPath, pen);
    }
}
