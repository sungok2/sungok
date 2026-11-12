package kr.openscan.scan;

import android.app.Activity;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
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
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Point-cloud viewer; no third-party viewer and no network required. */
public final class CloudViewerActivity extends Activity {
    @Override public void onCreate(Bundle bundle){
        super.onCreate(bundle);
        LinearLayout container=new LinearLayout(this);container.setOrientation(LinearLayout.VERTICAL);container.setBackgroundColor(0xFF141923);
        TextView label=new TextView(this);label.setText("3D 미리보기 · 드래그: 회전 · 두 손가락: 확대/축소");
        label.setTextSize(14);label.setTextColor(Color.WHITE);label.setPadding(15,17,10,17);container.addView(label);
        GLSurfaceView view=new GLSurfaceView(this);view.setEGLContextClientVersion(2);
        File f=new File(getIntent().getStringExtra("scan_path"),"model.ply");
        try{
            PointSet points=load(f);
            ViewerRenderer renderer=new ViewerRenderer(points);
            view.setRenderer(renderer);
            view.setOnTouchListener(new View.OnTouchListener(){
                float lastX,lastY,lastDistance;
                @Override public boolean onTouch(View v,MotionEvent event){
                    if(event.getPointerCount()>=2){
                        float dx=event.getX(0)-event.getX(1),dy=event.getY(0)-event.getY(1);
                        float distance=(float)Math.hypot(dx,dy);
                        if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&lastDistance>0)renderer.zoom(distance/lastDistance);
                        lastDistance=distance;
                    }else{
                        if(event.getActionMasked()==MotionEvent.ACTION_MOVE)renderer.rotate(event.getX()-lastX,event.getY()-lastY);
                        lastX=event.getX();lastY=event.getY();lastDistance=0;
                    }
                    return true;
                }
            });
            label.setText("3D 점군 "+points.size+"점 · 회전: 드래그 · 확대: 두 손가락");
            container.addView(view,new LinearLayout.LayoutParams(-1,0,1));
        }catch(Exception e){
            TextView error=new TextView(this);error.setText("3D 파일을 열지 못했습니다: "+e.getMessage());error.setTextColor(Color.WHITE);
            error.setPadding(20,20,20,20);container.addView(error);
        }
        setContentView(container);
    }
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
