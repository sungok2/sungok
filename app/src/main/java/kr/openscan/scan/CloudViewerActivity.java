package kr.openscan.scan;

import android.app.Activity;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Immersive point-cloud viewer with discoverable orbit and zoom controls. */
public final class CloudViewerActivity extends Activity {
    private GLSurfaceView surface;
    @Override public void onCreate(Bundle bundle){
        super.onCreate(bundle);
        getWindow().setStatusBarColor(UiKit.INK);getWindow().setNavigationBarColor(UiKit.INK);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(UiKit.INK);
        String path=getIntent().getStringExtra("scan_path");
        File scanDir=path==null?getFilesDir():new File(path);
        try{
            PointSet points=load(new File(scanDir,"model.ply"));
            if(points.size==0)throw new IOException("3D 포인트가 없습니다");
            ViewerRenderer renderer=new ViewerRenderer(points);
            surface=new GLSurfaceView(this);surface.setEGLContextClientVersion(2);
            surface.setPreserveEGLContextOnPause(true);surface.setRenderer(renderer);
            surface.setOnTouchListener(new View.OnTouchListener(){
                float lastX,lastY,lastDistance;
                boolean pinching;
                @Override public boolean onTouch(View v,MotionEvent ev){
                    int action=ev.getActionMasked();
                    if(action==MotionEvent.ACTION_DOWN){lastX=ev.getX();lastY=ev.getY();pinching=false;lastDistance=0;return true;}
                    if(action==MotionEvent.ACTION_POINTER_DOWN){pinching=true;lastDistance=0;return true;}
                    if(ev.getPointerCount()>=2){
                        float dist=(float)Math.hypot(ev.getX(0)-ev.getX(1),ev.getY(0)-ev.getY(1));
                        if(action==MotionEvent.ACTION_MOVE&&lastDistance>0)renderer.zoom(dist/lastDistance);
                        lastDistance=dist;pinching=true;return true;
                    }
                    if(action==MotionEvent.ACTION_POINTER_UP){lastDistance=0;pinching=true;return true;}
                    if(action==MotionEvent.ACTION_MOVE){
                        if(!pinching)renderer.rotate(ev.getX()-lastX,ev.getY()-lastY);
                        lastX=ev.getX();lastY=ev.getY();return true;
                    }
                    if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){pinching=false;lastDistance=0;}
                    return true;
                }
            });
            root.addView(surface,new FrameLayout.LayoutParams(-1,-1));
            LinearLayout overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);
            overlay.setPadding(UiKit.dp(this,16),UiKit.dp(this,14),UiKit.dp(this,16),UiKit.dp(this,16));
            overlay.setOnApplyWindowInsetsListener((v,insets)->{
                v.setPadding(UiKit.dp(this,16),UiKit.dp(this,12)+insets.getSystemWindowInsetTop(),
                    UiKit.dp(this,16),UiKit.dp(this,12)+insets.getSystemWindowInsetBottom());
                return insets;
            });
            root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
            LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
            Button back=UiKit.button(this,"‹",UiKit.PANEL,UiKit.TEXT,14);back.setTextSize(27);
            UiKit.accessibility(back,"뒤로 가기");
            head.addView(back,new LinearLayout.LayoutParams(UiKit.dp(this,48),UiKit.dp(this,48)));
            LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);
            TextView title=UiKit.text(this,"3D 미리보기",19,UiKit.TEXT,true);
            TextView subtitle=UiKit.text(this,String.format(Locale.KOREAN,"포인트 %,d개",points.size),12,UiKit.MUTED,false);
            titles.addView(title);titles.addView(subtitle);
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=UiKit.dp(this,12);
            head.addView(titles,tp);
            Button share=UiKit.button(this,"PLY 공유",UiKit.PANEL,UiKit.TEXT,14);
            head.addView(share,new LinearLayout.LayoutParams(UiKit.dp(this,94),UiKit.dp(this,46)));
            overlay.addView(head);
            View spacer=new View(this);overlay.addView(spacer,new LinearLayout.LayoutParams(1,0,1));
            LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(UiKit.dp(this,16),UiKit.dp(this,14),UiKit.dp(this,16),UiKit.dp(this,15));
            panel.setBackground(UiKit.fill(this,UiKit.PANEL,22));
            TextView instructions=UiKit.text(this,"한 손가락으로 회전  ·  두 손가락으로 확대",13,UiKit.TEXT,false);
            instructions.setGravity(Gravity.CENTER);panel.addView(instructions);
            LinearLayout tools=new LinearLayout(this);tools.setGravity(Gravity.CENTER);
            Button zoomOut=UiKit.button(this,"−",UiKit.SURFACE,UiKit.TEXT,13);
            Button reset=UiKit.button(this,"원래 보기",UiKit.ACCENT,UiKit.INK,13);
            Button zoomIn=UiKit.button(this,"+",UiKit.SURFACE,UiKit.TEXT,13);
            zoomOut.setTextSize(22);zoomIn.setTextSize(22);
            UiKit.accessibility(zoomOut,"축소");UiKit.accessibility(zoomIn,"확대");
            tools.addView(zoomOut,new LinearLayout.LayoutParams(UiKit.dp(this,58),UiKit.dp(this,48)));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,UiKit.dp(this,48),1);
            rp.leftMargin=UiKit.dp(this,8);rp.rightMargin=UiKit.dp(this,8);
            tools.addView(reset,rp);tools.addView(zoomIn,new LinearLayout.LayoutParams(UiKit.dp(this,58),UiKit.dp(this,48)));
            LinearLayout.LayoutParams toolp=new LinearLayout.LayoutParams(-1,-2);toolp.topMargin=UiKit.dp(this,12);
            panel.addView(tools,toolp);overlay.addView(panel);
            back.setOnClickListener(v->finish());share.setOnClickListener(v->ScanShare.share(this,new File(scanDir,"model.ply")));
            zoomIn.setOnClickListener(v->renderer.zoom(1.25f));zoomOut.setOnClickListener(v->renderer.zoom(0.8f));
            reset.setOnClickListener(v->renderer.resetView());
        }catch(Exception e){
            LinearLayout error=new LinearLayout(this);error.setOrientation(LinearLayout.VERTICAL);
            error.setGravity(Gravity.CENTER);error.setPadding(UiKit.dp(this,25),0,UiKit.dp(this,25),0);
            TextView title=UiKit.text(this,"3D 파일을 열 수 없습니다",21,UiKit.TEXT,true);
            title.setGravity(Gravity.CENTER);
            TextView detail=UiKit.text(this,e.getMessage()==null?"저장된 스캔을 다시 확인해 주세요":e.getMessage(),13,UiKit.MUTED,false);
            detail.setGravity(Gravity.CENTER);detail.setPadding(0,UiKit.dp(this,10),0,UiKit.dp(this,20));
            Button back=UiKit.button(this,"보관함으로 돌아가기",UiKit.ACCENT,UiKit.INK,14);
            error.addView(title);error.addView(detail);
            error.addView(back,new LinearLayout.LayoutParams(-1,UiKit.dp(this,50)));
            root.addView(error,new FrameLayout.LayoutParams(-1,-1));back.setOnClickListener(v->finish());
        }
        setContentView(root);
    }
    @Override protected void onResume(){super.onResume();if(surface!=null)surface.onResume();}
    @Override protected void onPause(){if(surface!=null)surface.onPause();super.onPause();}
    static final class PointSet {
        final FloatBuffer positions,colors;
        final int size;
        final float cx,cy,cz,radius;
        PointSet(FloatBuffer p,FloatBuffer c,int size,float cx,float cy,float cz,float radius){
            positions=p;colors=c;this.size=size;this.cx=cx;this.cy=cy;this.cz=cz;this.radius=radius;
        }
    }
    static PointSet load(File path) throws IOException {
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(path),131072))){
            String first=readAsciiLine(in);if(!"ply".equals(first))throw new IOException("PLY 헤더가 아님");
            String format=readAsciiLine(in);
            if(!"format binary_little_endian 1.0".equals(format))throw new IOException("이 앱은 binary little endian PLY를 지원합니다");
            int vertexCount=-1;String line;
            while((line=readAsciiLine(in))!=null){
                if(line.startsWith("element vertex "))vertexCount=Integer.parseInt(line.substring(15));
                if("end_header".equals(line))break;
            }
            if(vertexCount<0||vertexCount>ScanModel.MAX_VERTICES)throw new IOException("잘못된 점 수");
            int stride=Math.max(1,(vertexCount+89_999)/90_000),n=(vertexCount+stride-1)/stride;
            float[] p=new float[n*3],c=new float[n*3];int used=0;
            float minx=Float.POSITIVE_INFINITY,miny=minx,minz=minx;
            float maxx=Float.NEGATIVE_INFINITY,maxy=maxx,maxz=maxx;
            for(int i=0;i<vertexCount;i++){
                float x=Float.intBitsToFloat(Integer.reverseBytes(in.readInt()));
                float y=Float.intBitsToFloat(Integer.reverseBytes(in.readInt()));
                float z=Float.intBitsToFloat(Integer.reverseBytes(in.readInt()));
                int r=in.readUnsignedByte(),g=in.readUnsignedByte(),b=in.readUnsignedByte();
                if(i%stride!=0)continue;
                p[used*3]=x;p[used*3+1]=y;p[used*3+2]=z;
                c[used*3]=r/255f;c[used*3+1]=g/255f;c[used*3+2]=b/255f;
                minx=Math.min(x,minx);miny=Math.min(y,miny);minz=Math.min(z,minz);
                maxx=Math.max(x,maxx);maxy=Math.max(y,maxy);maxz=Math.max(z,maxz);used++;
            }
            float cx=(minx+maxx)*0.5f,cy=(miny+maxy)*0.5f,cz=(minz+maxz)*0.5f;
            float range=Math.max(maxx-minx,Math.max(maxy-miny,maxz-minz));float radius=Math.max(0.2f,range*0.5f);
            FloatBuffer pos=ByteBuffer.allocateDirect(used*12).order(ByteOrder.nativeOrder()).asFloatBuffer();
            FloatBuffer col=ByteBuffer.allocateDirect(used*12).order(ByteOrder.nativeOrder()).asFloatBuffer();
            for(int j=0;j<used;j++){
                pos.put(p[j*3]-cx).put(p[j*3+1]-cy).put(p[j*3+2]-cz);
                col.put(c[j*3]).put(c[j*3+1]).put(c[j*3+2]);
            }
            pos.position(0);col.position(0);
            return new PointSet(pos,col,used,cx,cy,cz,radius);
        }
    }
    static String readAsciiLine(DataInputStream in) throws IOException {
        StringBuilder s=new StringBuilder();int b;
        while((b=in.read())!=-1){if(b=='\n')return s.toString();if(b!='\r')s.append((char)b);if(s.length()>512)throw new IOException("PLY 헤더가 비정상적으로 깁니다");}
        return s.length()==0?null:s.toString();
    }
    static final class ViewerRenderer implements GLSurfaceView.Renderer {
        private final PointSet pts;
        private volatile float yaw=20,pitch=15,distanceScale=2.8f;
        private int program,position,color,mvp;
        private int viewportWidth=1,viewportHeight=1;
        ViewerRenderer(PointSet pts){this.pts=pts;}
        void resetView(){yaw=20;pitch=15;distanceScale=2.8f;}
        void rotate(float x,float y){yaw+=x*0.32f;pitch=Math.max(-89,Math.min(89,pitch+y*0.32f));}
        void zoom(float ratio){if(ratio>0.5f&&ratio<2f)distanceScale=Math.max(1.25f,Math.min(18f,distanceScale/ratio));}
        @Override public void onSurfaceCreated(GL10 gl,EGLConfig cfg){
            GLES20.glClearColor(0.055f,0.08f,0.12f,1);GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            String vs="uniform mat4 mvp;attribute vec3 aPoint;attribute vec3 aColor;varying vec3 vColor;"+
                "void main(){gl_Position=mvp*vec4(aPoint,1.);gl_PointSize=3.0;vColor=aColor;}";
            String fs="precision mediump float;varying vec3 vColor;void main(){gl_FragColor=vec4(vColor,1.);}";
            int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs);
            program=GLES20.glCreateProgram();GLES20.glAttachShader(program,v);GLES20.glAttachShader(program,f);GLES20.glLinkProgram(program);
            position=GLES20.glGetAttribLocation(program,"aPoint");color=GLES20.glGetAttribLocation(program,"aColor");mvp=GLES20.glGetUniformLocation(program,"mvp");
            GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);
        }
        static int shader(int type,String src){int id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,src);GLES20.glCompileShader(id);return id;}
        @Override public void onSurfaceChanged(GL10 gl,int w,int h){viewportWidth=w;viewportHeight=h;GLES20.glViewport(0,0,w,h);}
        @Override public void onDrawFrame(GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            float[] projection=new float[16],view=new float[16],model=new float[16],tmp=new float[16],result=new float[16];
            Matrix.perspectiveM(projection,0,55,viewportWidth/(float)Math.max(1,viewportHeight),0.05f,10000f);
            Matrix.setLookAtM(view,0,0,0,pts.radius*distanceScale+0.1f,0,0,0,0,1,0);
            Matrix.setIdentityM(model,0);Matrix.rotateM(model,0,pitch,1,0,0);Matrix.rotateM(model,0,yaw,0,1,0);
            Matrix.multiplyMM(tmp,0,view,0,model,0);Matrix.multiplyMM(result,0,projection,0,tmp,0);
            GLES20.glUseProgram(program);GLES20.glUniformMatrix4fv(mvp,1,false,result,0);
            pts.positions.position(0);pts.colors.position(0);
            GLES20.glEnableVertexAttribArray(position);GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,0,pts.positions);
            GLES20.glEnableVertexAttribArray(color);GLES20.glVertexAttribPointer(color,3,GLES20.GL_FLOAT,false,0,pts.colors);
            GLES20.glDrawArrays(GLES20.GL_POINTS,0,pts.size);
            GLES20.glDisableVertexAttribArray(position);GLES20.glDisableVertexAttribArray(color);
        }
    }
}
