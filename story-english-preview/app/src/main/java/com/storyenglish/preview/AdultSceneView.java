package com.storyenglish.preview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Original illustrative scene of two clothed adults discussing intimacy and boundaries. */
public final class AdultSceneView extends View {
    private int variant=0;
    private final Paint paint=new Paint(3);
    public AdultSceneView(Context ctx){super(ctx);setContentDescription("따뜻한 실내에 마주 앉은 성인 두 사람의 그림. 대화 주제는 친밀감, 동의와 건강입니다.");}
    public void setVariant(int n){variant=n;invalidate();}
    @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws);setMeasuredDimension(w,Math.round(getResources().getDisplayMetrics().density*196));}
    @Override protected void onDraw(Canvas c){super.onDraw(c);
        int checkpoint=c.save();c.scale(getWidth()/680f,getHeight()/300f);
        paint.setShader(new LinearGradient(0,0,680,300,new int[]{0xff1D1834,0xff593849,0xffB67564},null,Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0,0,680,300),18,18,paint);paint.setShader(null);
        // moonlight/window, warm ambient lamp, and lounge silhouettes.
        paint.setColor(0x778AD1D7);c.drawRoundRect(new RectF(420,18,646,225),13,13,paint);
        paint.setColor(0xff463D55);c.drawRect(430,27,636,216,paint);
        paint.setColor(0xff775968);c.drawCircle(529,103,50,paint);
        paint.setColor(0xffE3BDA4);c.drawCircle(529,103,32,paint);
        paint.setColor(0x44E7B9A3);c.drawCircle(175,116,77,paint);
        paint.setColor(0xff281C33);c.drawRoundRect(new RectF(80,224,638,292),29,29,paint);
        paint.setColor(0xff493043);c.drawRoundRect(new RectF(120,174,590,252),26,26,paint);
        // Adult at left: face, hair, clothing, bent arm.
        paint.setColor(0xff302636);c.drawOval(new RectF(215,100,320,220),paint);
        paint.setColor(0xffD8A58F);c.drawOval(new RectF(235,100,294,163),paint);
        paint.setColor(0xff271D2C);c.drawArc(new RectF(228,95,299,149),175,210,true,paint);
        paint.setColor(variant==2?0xff2C3D4C:0xff583D57);c.drawRoundRect(new RectF(214,163,336,282),28,28,paint);
        // Adult at right (different silhouette), bodies remain clothed.
        paint.setColor(0xff27263A);c.drawOval(new RectF(350,104,443,215),paint);
        paint.setColor(0xffAD8176);c.drawOval(new RectF(362,108,425,167),paint);
        paint.setColor(0xff221B2B);c.drawArc(new RectF(354,99,437,151),192,208,true,paint);
        paint.setColor(variant==1?0xff9A475B:0xff384F68);c.drawRoundRect(new RectF(335,166,467,280),27,27,paint);
        // A simple shared hand-holding pose, not sexual or nude.
        paint.setColor(0xffD8A58F);c.drawRoundRect(new RectF(295,204,374,216),8,8,paint);
        paint.setColor(0xffAD8176);c.drawCircle(366,210,10,paint);
        // soft foreground bloom
        paint.setColor(0x33E7BCA1);c.drawCircle(610,40,58,paint);
        paint.setColor(0xffF1D9C0);paint.setTextSize(14);
        c.restoreToCount(checkpoint);
    }
}