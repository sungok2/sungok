package com.storyenglish.preview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Actual STFT spectral heatmaps; horizontal axis time, vertical axis frequency (110 Hz to 6 kHz). */
public final class SpectrogramView extends View {
    private float[][] reference, learner;
    private final Paint paint = new Paint(3);
    public SpectrogramView(Context context){super(context);setContentDescription("기준 음성과 사용자 녹음의 실제 스펙트로그램. 아래의 텍스트 점수와 함께 확인하세요.");}
    public void setSpectra(float[][] a,float[][] b){reference=a;learner=b;invalidate();}
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),Math.round(getResources().getDisplayMetrics().density*244));}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);
        float w=getWidth(),h=getHeight(),pad=dp(12),half=h/2;
        paint.setColor(0xff142238);canvas.drawRoundRect(new RectF(0,0,w,h),dp(12),dp(12),paint);
        drawPanel(canvas,reference,0,half,w,pad,"기준 음성 · TTS","#D6A85F");
        drawPanel(canvas,learner,half,half,w,pad,"내 목소리 · 마이크","#60A5FA");
        paint.setColor(0xff36445A);paint.setStrokeWidth(dp(1));canvas.drawLine(pad,half,w-pad,half,paint);
    }
    private void drawPanel(Canvas c,float[][] data,float top,float height,float width,float pad,String label,String palette){
        paint.setColor(0xffEDF2FA);paint.setTextSize(dp(13));c.drawText(label,pad,top+dp(19),paint);
        float y0=top+dp(27),panelH=height-dp(42),x0=pad,xW=width-2*pad;
        paint.setColor(0xff081420);c.drawRoundRect(new RectF(x0,y0,x0+xW,y0+panelH),dp(7),dp(7),paint);
        if(data==null||data.length==0){paint.setColor(0xff9AAAC4);paint.setTextSize(dp(12));c.drawText("기준 음성을 준비하거나 녹음을 완료하세요",x0+dp(9),y0+panelH/2,paint);return;}
        final int samples=data.length,bands=data[0].length;
        float cellW=xW/samples,cellH=panelH/bands;
        for(int t=0;t<samples;t++)for(int f=0;f<bands;f++){
            float v=data[t][f];
            int color=spectralColor(v,palette.equals("#D6A85F"));
            paint.setColor(color);
            float yy=y0+panelH-(f+1)*cellH;
            c.drawRect(x0+t*cellW,yy,x0+(t+1)*cellW+0.25f,yy+cellH+0.25f,paint);
        }
    }
    private static int spectralColor(float value,boolean gold){
        float v=Math.max(0,Math.min(1,value));
        int r=(int)(12+(gold?225:60)*v),g=(int)(24+(gold?131:155)*v),b=(int)(41+(gold?76:211)*v);
        return 0xff000000|(r<<16)|(g<<8)|b;
    }
    private float dp(int n){return n*getResources().getDisplayMetrics().density;}
}