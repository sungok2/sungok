package kr.openscan.scan;

import android.media.Image;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import com.google.ar.core.Config;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.NotYetAvailableException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** OpenGL camera background and throttled depth collection, entirely on GL thread. */
public final class ArCameraRenderer implements GLSurfaceView.Renderer {
    public interface Reporter { void report(String text); }
    private final Reporter reporter;
    private volatile Session session;
    private volatile boolean recording;
    private volatile ScanModel model;
    private volatile ScanModel.Mode mode=ScanModel.Mode.OUTDOOR;
    private boolean depthSupported;
    private volatile boolean surfaceFirst=true;
    private volatile boolean subjectCrop=true;
    private final SubjectVolume subjectVolume=new SubjectVolume(1.1f);
    public void setSurfaceFirst(boolean value){surfaceFirst=value;}
    public boolean isSurfaceFirst(){return surfaceFirst;}
    public void setSubjectCrop(boolean value){subjectCrop=value;}
    private volatile PhotoDataset photos;
    private long lastPhotoTime;
    private int texId=0,program=0,positionSlot,uvSlot,textureSlot;
    private int width=1,height=1;
    private long lastCaptureMillis,lastReportMillis;
    private long lastPoseMillis;
    private final RawDepthFrameGate depthGate = new RawDepthFrameGate();
    private int rejectedFrames, rawAccepted, rawAvailable, rawReused, rawUnavailable;
    private volatile String captureHelp="카메라를 천천히 이동해 깊이를 수집해 주세요";
    public String getCaptureHelp() { return captureHelp; }
    private float[] lastPosePosition,lastPoseRotation;
    private final float[] corners={-1,-1,1,-1,-1,1,1,1};
    private final float[] textureUV=new float[8];
    private final FloatBuffer cornerBuffer=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final FloatBuffer uvBuffer=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
    public ArCameraRenderer(Reporter reporter){this.reporter=reporter;cornerBuffer.put(corners).position(0);}
    public void configure(Session s,boolean depth){this.session=s;this.depthSupported=depth;}
    public void scan(ScanModel next,ScanModel.Mode target,boolean enabled){
        if(this.model!=next){
            lastCaptureMillis=0;lastPoseMillis=0;lastPosePosition=null;lastPoseRotation=null;
            depthGate.reset();subjectVolume.reset();rejectedFrames=0;rawAccepted=0;rawAvailable=0;rawReused=0;rawUnavailable=0;
            captureHelp="카메라를 천천히 움직여 표면 깊이를 측정하세요";
        }
        this.model=next;this.mode=target;this.recording=enabled;
    }
    public void stop(){recording=false;}
    public void setPhotos(PhotoDataset photos){this.photos=photos;}
    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){
        GLES20.glClearColor(0.05f,0.07f,0.09f,1);
        int[] t=new int[1]; GLES20.glGenTextures(1,t,0);texId=t[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        String vs="attribute vec2 aPosition;attribute vec2 aUv;varying vec2 vUv;void main(){gl_Position=vec4(aPosition,0.,1.);vUv=aUv;}";
        String fs="#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES cameraTexture;varying vec2 vUv;void main(){gl_FragColor=texture2D(cameraTexture,vUv);}";
        int vert=compile(GLES20.GL_VERTEX_SHADER,vs),frag=compile(GLES20.GL_FRAGMENT_SHADER,fs);
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vert);GLES20.glAttachShader(program,frag);
        GLES20.glLinkProgram(program);GLES20.glDeleteShader(vert);GLES20.glDeleteShader(frag);
        positionSlot=GLES20.glGetAttribLocation(program,"aPosition");
        uvSlot=GLES20.glGetAttribLocation(program,"aUv");
        textureSlot=GLES20.glGetUniformLocation(program,"cameraTexture");
    }
    /** Avoid accumulating near-identical frames while allowing periodic depth refinement. */
    private boolean movedEnough(Frame f,long now){
        if(lastPosePosition==null || now-lastPoseMillis>1700)return true;
        com.google.ar.core.Pose pose=f.getCamera().getPose();
        float[] p=pose.getTranslation(),q=pose.getRotationQuaternion();
        float dx=p[0]-lastPosePosition[0],dy=p[1]-lastPosePosition[1],dz=p[2]-lastPosePosition[2];
        if(dx*dx+dy*dy+dz*dz>0.0016f)return true; // >= 4cm translation
        float dot=Math.abs(q[0]*lastPoseRotation[0]+q[1]*lastPoseRotation[1]
            +q[2]*lastPoseRotation[2]+q[3]*lastPoseRotation[3]);
        return dot<0.9986f; // ~6 degrees rotation
    }
    private static int compile(int kind,String shader){
        int s=GLES20.glCreateShader(kind);GLES20.glShaderSource(s,shader);GLES20.glCompileShader(s);return s;
    }
    @Override public void onSurfaceChanged(GL10 gl,int w,int h){width=w;height=h;GLES20.glViewport(0,0,w,h);}
    @Override public void onDrawFrame(GL10 gl){
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        Session s=session;
        if(s==null||texId==0)return;
        try {
            s.setCameraTextureName(texId);
            // Orientation read on each frame; handles rotation after an activity resumes.
            int rotation=MainActivity.displayRotation();
            s.setDisplayGeometry(rotation,width,height);
            Frame f=s.update();
            f.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                corners,Coordinates2d.TEXTURE_NORMALIZED,textureUV);
            uvBuffer.clear();uvBuffer.put(textureUV).position(0);
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texId);
            GLES20.glUniform1i(textureSlot,0);
            cornerBuffer.position(0);GLES20.glEnableVertexAttribArray(positionSlot);
            GLES20.glVertexAttribPointer(positionSlot,2,GLES20.GL_FLOAT,false,0,cornerBuffer);
            GLES20.glEnableVertexAttribArray(uvSlot);
            GLES20.glVertexAttribPointer(uvSlot,2,GLES20.GL_FLOAT,false,0,uvBuffer);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            GLES20.glDisableVertexAttribArray(positionSlot);GLES20.glDisableVertexAttribArray(uvSlot);
            long now=SystemClock.elapsedRealtime();
            if(recording && f.getCamera().getTrackingState()==TrackingState.TRACKING && model!=null && now-lastCaptureMillis>240 && movedEnough(f,now)) {
                lastCaptureMillis=now;
                if(depthSupported){
                    Image depth=null, confidence=null, rgb=null;
                    try {
                        if(surfaceFirst){
                            depth=f.acquireDepthImage16Bits(); // dense, interpolated scene depth
                        }else{
                            depth=f.acquireRawDepthImage16Bits(); // sparse measurement depth
                            confidence=f.acquireRawDepthConfidenceImage();
                        }
                        // Compare each raw depth frame only with the preceding raw depth timestamp.
                        // ARCore camera Frame timestamps need not equal raw depth timestamps.
                        rawAvailable++;
                        if(depthGate.shouldIntegrate(depth.getTimestamp())){
                            try {rgb=f.acquireCameraImage();}catch(NotYetAvailableException ignored){}
                            if(DepthSampler.capture(f,depth,confidence,rgb,model,mode,subjectCrop?subjectVolume:null)){
                                rawAccepted++;
                                com.google.ar.core.Pose pose=f.getCamera().getPose();
                                lastPosePosition=pose.getTranslation();lastPoseRotation=pose.getRotationQuaternion();lastPoseMillis=now;
                            }else rejectedFrames++;
                        }else rawReused++;
                        PhotoDataset photo=photos;
                        if(photo!=null && rgb!=null && now-lastPhotoTime>3000){
                            lastPhotoTime=now; photo.enqueue(f,rgb);
                        }
                    }catch(NotYetAvailableException ignored){rawUnavailable++;}
                    finally {if(rgb!=null)rgb.close();if(confidence!=null)confidence.close();if(depth!=null)depth.close();}
                }else{
                    // Accurate sparse feature points are already in ARCore world coordinates.
                    try(PointCloud cloud=f.acquirePointCloud()) {
                        FloatBuffer points=cloud.getPoints();
                        for(int j=0;j+3<points.limit();j+=4){
                            float conf=points.get(j+3);
                            if(conf>0.35f)model.addPoint(points.get(j),points.get(j+1),points.get(j+2),0xBBD1DD);
                        }
                        model.markFrame();
                    }
                    PhotoDataset photo=photos;
                    if(photo!=null && now-lastPhotoTime>3000){
                        Image rgb=null;
                        try{rgb=f.acquireCameraImage();lastPhotoTime=now;photo.enqueue(f,rgb);}
                        catch(NotYetAvailableException ignored){}
                        finally{if(rgb!=null)rgb.close();}
                    }
                }
                if(model.limitReached())recording=false;
            }
            if(now-lastReportMillis>800){
                lastReportMillis=now;
                ScanModel m=model;
                if(!depthSupported)captureHelp="기기가 깊이를 지원하지 않아 희소 특징점만 기록됩니다";
                else if(f.getCamera().getTrackingState()!=TrackingState.TRACKING)captureHelp="AR 추적 대기: 주변의 질감 있는 물체를 천천히 비춰주세요";
                else if(rawAccepted>0)captureHelp="깊이 프레임 "+rawAccepted+"개 수집 · "+(surfaceFirst?"표면 보간 중, 정확도 미검증":"원시 측정점 기록 중");
                else if(rawUnavailable>0 && rawAvailable==0)captureHelp="깊이 생성 대기: 빛을 밝게 하고 천천히 이동하세요 (대기 "+rawUnavailable+")";
                else if(rejectedFrames>0)captureHelp="깊이 샘플이 필터에 걸렸습니다: 사물과 0.5~2m 거리, 밝은 조명을 확인하세요";
                else if(rawReused>0)captureHelp="새 깊이 업데이트 대기 중: 다른 각도로 천천히 이동하세요";
                else captureHelp="포인트 수집 대기 중: 사물을 향해 0.5~2m에서 천천히 이동하세요";
                String tracking=f.getCamera().getTrackingState()==TrackingState.TRACKING?"추적 중":"추적 대기";
                String counts=m==null?"":String.format(java.util.Locale.KOREAN," · 점 %,d · 면 %,d · 프레임 %d",m.vertices(),m.triangles(),m.frames());
                String type=depthSupported?(surfaceFirst?"표면 우선(보간 깊이)":"원시 깊이(신뢰도 필터)"):"희소 포인트";
                String msg=tracking+" · "+type+counts
                    +(depthSupported && m!=null?" · 깊이 수집 "+rawAccepted+"/대기 "+rawUnavailable+"/제외 "+rejectedFrames:"")
                    +(m!=null && m.limitReached()?" · 용량 제한 도달":"");
                reporter.report(msg);
            }
        }catch(Exception ex){
            long now=SystemClock.elapsedRealtime();
            if(now-lastReportMillis>1500){lastReportMillis=now;reporter.report("ARCore: "+ex.getClass().getSimpleName());}
        }
    }
}
