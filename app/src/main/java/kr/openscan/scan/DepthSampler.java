package kr.openscan.scan;

import android.media.Image;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Converts each valid aligned ARCore depth pixel into a colored 3D world point. */
public final class DepthSampler {
    private DepthSampler() {}
    public static boolean capture(Frame frame, Image depth, Image color, ScanModel model, ScanModel.Mode mode) {
        final int step=mode.pixelStep;
        final int cols=(depth.getWidth()+step-1)/step;
        final int rows=(depth.getHeight()+step-1)/step;
        int count=cols*rows;
        float[] tex=new float[count*2],cpu=new float[count*2];
        for(int y=0;y<rows;y++) for(int x=0;x<cols;x++) {
            int k=y*cols+x;
            tex[2*k]=(Math.min(depth.getWidth()-1,x*step)+0.5f)/depth.getWidth();
            tex[2*k+1]=(Math.min(depth.getHeight()-1,y*step)+0.5f)/depth.getHeight();
        }
        frame.transformCoordinates2d(Coordinates2d.TEXTURE_NORMALIZED,tex,Coordinates2d.IMAGE_PIXELS,cpu);
        CameraIntrinsics intr=frame.getCamera().getImageIntrinsics();
        float[] focal=intr.getFocalLength(),center=intr.getPrincipalPoint();
        int[] imgSize=intr.getImageDimensions();
        Pose pose=frame.getCamera().getPose();
        float[] xyz=new float[count*3],depthMeters=new float[count]; int[] colors=new int[count]; boolean[] valid=new boolean[count];
        Image.Plane dp=depth.getPlanes()[0];
        ByteBuffer db=dp.getBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int countValid=0;
        float[] cameraPoint=new float[3],worldPoint=new float[3];
        int maxColorWidth=color==null?0:color.getWidth(),maxColorHeight=color==null?0:color.getHeight();
        for(int y=0;y<rows;y++) for(int x=0;x<cols;x++) {
            int k=y*cols+x;
            int px=x*step,py=y*step;
            int offset=py*dp.getRowStride()+px*dp.getPixelStride();
            if(offset<0 || offset+1>=db.limit()) continue;
            int mm=Short.toUnsignedInt(db.getShort(offset));
            float z=mm/1000.0f;
            if(z<0.15f || z>mode.maxDepth) continue;
            float u=cpu[2*k],v=cpu[2*k+1];
            if(!Float.isFinite(u)||!Float.isFinite(v)||u<0||v<0||u>=imgSize[0]||v>=imgSize[1]) continue;
            // Image intrinsics use +Y down, ARCore 3D camera uses +Y up and -Z forward.
            cameraPoint[0]=(u-center[0])*z/focal[0];
            cameraPoint[1]=-(v-center[1])*z/focal[1];
            cameraPoint[2]=-z;
            pose.transformPoint(cameraPoint,0,worldPoint,0);
            xyz[3*k]=worldPoint[0]; xyz[3*k+1]=worldPoint[1]; xyz[3*k+2]=worldPoint[2];
            valid[k]=true; depthMeters[k]=z; countValid++;
            int ix=Math.round(u),iy=Math.round(v);
            colors[k]= color != null && ix>=0 && iy>=0 && ix<maxColorWidth && iy<maxColorHeight
                ? yuvRgb(color,ix,iy) : 0xAAAAAA;
        }
        if(countValid>8) model.addGrid(cols,rows,xyz,colors,valid,depthMeters);
        return countValid>8;
    }
    private static int yuvRgb(Image image,int x,int y) {
        Image.Plane[] p=image.getPlanes();
        if(p.length<3)return 0xAAAAAA;
        int Y=read(p[0],x,y),U=read(p[1],x/2,y/2),V=read(p[2],x/2,y/2);
        int c=Math.max(0,Y-16),d=U-128,e=V-128;
        int r=clip((298*c+409*e+128)>>8);
        int g=clip((298*c-100*d-208*e+128)>>8);
        int b=clip((298*c+516*d+128)>>8);
        return (r<<16)|(g<<8)|b;
    }
    private static int read(Image.Plane p,int x,int y) {
        ByteBuffer b=p.getBuffer();
        int idx=y*p.getRowStride()+x*p.getPixelStride();
        return (idx>=0 && idx<b.limit())?(b.get(idx)&255):128;
    }
    private static int clip(int v){return Math.max(0,Math.min(255,v));}
}
