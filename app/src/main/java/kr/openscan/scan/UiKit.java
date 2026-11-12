package kr.openscan.scan;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/** Single accessible color/spacing system shared by the scanner, library and viewer. */
final class UiKit {
    static final int INK = 0xFF07121D;
    static final int PANEL = 0xF20D1B2A;
    static final int SURFACE = 0xFF15283A;
    static final int ACCENT = 0xFF43D5E5;
    static final int TEXT = 0xFFF4F8FB;
    static final int MUTED = 0xFFB8CBD7;
    static final int WARNING = 0xFFFFCA70;
    private UiKit() {}
    static int dp(Context c,float v){return (int)(v*c.getResources().getDisplayMetrics().density+0.5f);}
    static GradientDrawable fill(Context c,int color,float radius){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(c,radius));return g;
    }
    static GradientDrawable line(Context c,int color,float radius,int border){
        GradientDrawable g=fill(c,color,radius);g.setStroke(dp(c,1),border);return g;
    }
    static TextView text(Context c,String value,float sp,int color,boolean bold){
        TextView t=new TextView(c);t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(true);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    static Button button(Context c,String label,int background,int color,float radius){
        Button b=new Button(c);b.setText(label);b.setTextSize(15);b.setAllCaps(false);
        b.setTextColor(color);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setGravity(Gravity.CENTER);b.setBackground(fill(c,background,radius));
        b.setPadding(dp(c,12),0,dp(c,12),0);
        b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);
        return b;
    }
    static void accessibility(View v,String text){v.setContentDescription(text);}
}
