package kr.openscan.scan;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** A photo set for optional external SfM: does not itself reconstruct from photos. */
public final class PhotoDataset {
    private static final int MAX_PHOTOS=150;
    private final File tempFolder;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private int queued=0;
    private boolean finished=false;
    private volatile int written=0;
    public PhotoDataset(File cacheDir) throws IOException {
        tempFolder=new File(cacheDir,"openscan_photos_"+System.nanoTime());
        if(!tempFolder.mkdirs())throw new IOException("사진 임시폴더 생성 실패");
    }
    /** Called only from the GL thread. Acquired Image must still be open. */
    public synchronized void enqueue(Frame frame, Image image) {
        if(finished || image==null || queued>=MAX_PHOTOS)return;
        final int width=image.getWidth(),height=image.getHeight();
        final byte[] nv21=convertNV21(image);
        final int n=++queued;
        CameraIntrinsics intr=frame.getCamera().getImageIntrinsics();
        Pose pose=frame.getCamera().getPose();
        final float[] t=pose.getTranslation(),q=pose.getRotationQuaternion(),fl=intr.getFocalLength(),pp=intr.getPrincipalPoint();
        final long timestamp=frame.getTimestamp();
        io.execute(()->{
            try {
                String filename=String.format(java.util.Locale.US,"frame_%04d.jpg",n);
                YuvImage yuv=new YuvImage(nv21,ImageFormat.NV21,width,height,null);
                File imageOut=new File(tempFolder,filename);
                try(FileOutputStream out=new FileOutputStream(imageOut)){
                    if(!yuv.compressToJpeg(new Rect(0,0,width,height),86,out))throw new IOException("JPEG 인코딩 실패");
                }
                JSONObject json=new JSONObject();
                json.put("image",filename);json.put("timestamp_ns",timestamp);
                json.put("width",width);json.put("height",height);
                json.put("camera_t_xyz_m",array(t));json.put("camera_q_xyzw",array(q));
                json.put("fx_fy",array(fl));json.put("cx_cy",array(pp));
                try(BufferedWriter log=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(tempFolder,"frames.jsonl"),true),StandardCharsets.UTF_8))){
                    log.write(json.toString());log.write('\n');
                }
                written++;
            }catch(Exception ignored){/* Missing individual photo does not invalidate mesh capture. */}
        });
    }
    private static JSONArray array(float[] values) throws org.json.JSONException {JSONArray arr=new JSONArray();for(float v:values)arr.put((double)v);return arr;}
    private static byte[] convertNV21(Image img){
        int w=img.getWidth(),h=img.getHeight();
        byte[] out=new byte[w*h*3/2];Image.Plane[] plane=img.getPlanes();
        ByteBuffer y=plane[0].getBuffer(),u=plane[1].getBuffer(),v=plane[2].getBuffer();
        for(int row=0;row<h;row++) for(int col=0;col<w;col++){
            int pos=row*plane[0].getRowStride()+col*plane[0].getPixelStride();
            out[row*w+col]=y.get(pos);
        }
        for(int row=0;row<h/2;row++)for(int col=0;col<w/2;col++){
            int vPos=row*plane[2].getRowStride()+col*plane[2].getPixelStride();
            int uPos=row*plane[1].getRowStride()+col*plane[1].getPixelStride();
            int dest=w*h+row*w+2*col;
            out[dest]=v.get(vPos);out[dest+1]=u.get(uPos);
        }
        return out;
    }
    public int count(){return written;}
    /** Waits for pending encodes then copies photos to the saved scan folder. */
    public synchronized int finish(File scanFolder) throws IOException,InterruptedException {
        if(finished)return written;
        finished=true;io.shutdown();
        if(!io.awaitTermination(100,TimeUnit.SECONDS))throw new IOException("사진 인코딩 완료 실패");
        if(written==0){discardFiles();return 0;}
        File destination=new File(scanFolder,"photos");
        if(!destination.mkdir())throw new IOException("저장 폴더를 만들 수 없습니다");
        File[] all=tempFolder.listFiles();
        if(all!=null)for(File file:all){
            File target=new File(destination,file.getName());
            if(!file.renameTo(target)){
                try(java.io.FileInputStream in=new java.io.FileInputStream(file);FileOutputStream out=new FileOutputStream(target)){
                    byte[] bytes=new byte[32768];int n;
                    while((n=in.read(bytes))>0)out.write(bytes,0,n);
                }
                file.delete();
            }
        }
        tempFolder.delete();return written;
    }
    public synchronized void discard(){
        if(finished)return;finished=true;io.shutdownNow();discardFiles();
    }
    private void discardFiles(){File[] f=tempFolder.listFiles();if(f!=null)for(File a:f)a.delete();tempFolder.delete();}
}
