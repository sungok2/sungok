package kr.openscan.scan;

import android.media.Image;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Precision scanner: confidence-gated RAW depth (not smoothed/invented pixels).
 * Depth coordinates use texture intrinsics scaled to the depth image, following
 * Google's ARCore Raw Depth codelab. CPU-image coordinates are ONLY for YUV color.
 */
public final class DepthSampler {
    private DepthSampler() {}

    public static boolean capture(Frame frame, Image depth, Image confidence, Image color,
                                  ScanModel model, ScanModel.Mode mode, SubjectVolume subjectVolume) {
        if (depth == null || (confidence != null && (depth.getWidth() != confidence.getWidth()
                || depth.getHeight() != confidence.getHeight()))) return false;
        final int dw=depth.getWidth(), dh=depth.getHeight();
        final int step=mode.pixelStep;
        final int cols=(dw+step-1)/step, rows=(dh+step-1)/step;
        final int count=cols*rows;
        final int minConfidence=mode==ScanModel.Mode.OBJECT?95:mode==ScanModel.Mode.ROOM?85:75;
        Image.Plane dp=depth.getPlanes()[0],cp=confidence==null?null:confidence.getPlanes()[0];
        ByteBuffer db=dp.getBuffer().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer cb=cp==null?null:cp.getBuffer().duplicate();

        // RAW depth samples are in the TEXTURE camera coordinate frame, not the
        // unrotated CPU IMAGE frame. Never unproject them with CPU-image intrinsics.
        CameraIntrinsics ti=frame.getCamera().getTextureIntrinsics();
        int[] td=ti.getImageDimensions();float[] focal=ti.getFocalLength(),center=ti.getPrincipalPoint();
        if(td[0]<=0 || td[1]<=0 || focal[0]<=0 || focal[1]<=0)return false;
        final float[] calibration=DepthGeometry.scaleIntrinsics(focal[0],focal[1],center[0],center[1],
                td[0],td[1],dw,dh);
        if(!Float.isFinite(calibration[0]+calibration[1]+calibration[2]+calibration[3]))return false;

        // Candidate depth estimate from the central region. In OBJECT mode reject
        // background geometry when a smaller foreground subject is in the center.
        int[] nearby=new int[Math.min(2048,count)];int nc=0;
        if(mode==ScanModel.Mode.OBJECT){
            for(int yy=rows/3; yy<rows*2/3 && nc<nearby.length; yy++)for(int xx=cols/3;xx<cols*2/3 && nc<nearby.length;xx++){
                int px=xx*step, py=yy*step;
                int mm=millimeters(db,dp,px,py);
                int cf=cp==null?255:confidence(cb,cp,px,py);
                if(cf>=minConfidence && mm>=300 && mm<=2500)nearby[nc++]=mm;
            }
        }
        float subjectDepth=Float.NaN;
        if(nc>=12){Arrays.sort(nearby,0,nc);subjectDepth=nearby[nc/3]/1000f;}
        // The subject centre is anchored once in ARCore world space to reduce
        // the accumulation of similarly distant background across viewpoints.
        Pose pose=frame.getCamera().getPose();
        if(mode==ScanModel.Mode.OBJECT && subjectVolume!=null && !subjectVolume.isInitialized()
                && Float.isFinite(subjectDepth)) {
            float[] centre=new float[3];
            pose.transformPoint(new float[]{0f,0f,-subjectDepth},0,centre,0);
            subjectVolume.initializeIfNeeded(centre[0],centre[1],centre[2]);
        }

        // Coordinate map is for YUV color ONLY. For geometry use texture intrinsics
        // as above; applying a CPU-image coordinate rotation to the depth pixels
        // would deform/mirror the resulting world geometry.
        float[] tex=new float[count*2],cpu=new float[count*2];
        for(int y=0;y<rows;y++)for(int x=0;x<cols;x++){
            int k=y*cols+x,px=Math.min(dw-1,x*step),py=Math.min(dh-1,y*step);
            tex[k*2]=(px+0.5f)/dw;tex[k*2+1]=(py+0.5f)/dh;
        }
        if(color!=null)frame.transformCoordinates2d(Coordinates2d.TEXTURE_NORMALIZED,tex,
                                                     Coordinates2d.IMAGE_PIXELS,cpu);
        float[] xyz=new float[count*3],meters=new float[count];
        int[] colors=new int[count];boolean[] valid=new boolean[count];
        float[] point=new float[3],world=new float[3];int n=0;
        int iw=color==null?0:color.getWidth(),ih=color==null?0:color.getHeight();
        for(int y=0;y<rows;y++)for(int x=0;x<cols;x++){
            int k=y*cols+x,px=x*step,py=y*step;
            if(mode==ScanModel.Mode.OBJECT && (px<dw*0.12f||px>=dw*0.88f||py<dh*0.12f||py>=dh*0.88f))continue;
            int cf=cp==null?255:confidence(cb,cp,px,py);
            if(cf<minConfidence)continue;
            int mm=millimeters(db,dp,px,py);float z=mm*0.001f;
            if(z<0.3f||z>mode.maxDepth)continue;
            // Full Depth is interpolated; discard discontinuities before creating faces.
            if(cp==null && !coherentDepth(db,dp,px,py,dw,dh,step,z))continue;
            if(mode==ScanModel.Mode.OBJECT && Float.isFinite(subjectDepth)
                    && (z<subjectDepth-0.32f || z>subjectDepth+0.48f))continue;
            DepthGeometry.unproject(px+0.5f,py+0.5f,z,calibration,point);
            pose.transformPoint(point,0,world,0);
            if(!Float.isFinite(world[0]+world[1]+world[2]))continue;
            if(mode==ScanModel.Mode.OBJECT && subjectVolume!=null
                    && !subjectVolume.contains(world[0],world[1],world[2]))continue;
            xyz[k*3]=world[0];xyz[k*3+1]=world[1];xyz[k*3+2]=world[2];
            valid[k]=true;meters[k]=z;n++;
            int iu=Math.round(cpu[k*2]),iv=Math.round(cpu[k*2+1]);
            colors[k]=color!=null && iu>=0 && iv>=0 && iu<iw && iv<ih
                    ?yuvRgb(color,iu,iv):0xB1BEC5;
        }
        if(n<12)return false;
        model.addGrid(cols,rows,xyz,colors,valid,meters);
        return true;
    }
    /** Suppress depth discontinuities generated by interpolation, without assuming geometric truth. */
    private static boolean coherentDepth(ByteBuffer buffer,Image.Plane plane,int x,int y,
                                         int w,int h,int step,float z) {
        float tolerance=Math.max(0.09f,z*0.095f);
        int compatible=0;
        for(int dy=-step;dy<=step;dy+=step)for(int dx=-step;dx<=step;dx+=step){
            if(dx==0&&dy==0)continue;
            int xx=x+dx,yy=y+dy;
            if(xx<0||xx>=w||yy<0||yy>=h)continue;
            int neighbour=millimeters(buffer,plane,xx,yy);
            if(neighbour>0 && Math.abs(neighbour*0.001f-z)<=tolerance)compatible++;
        }
        return compatible>=2;
    }
    private static int millimeters(ByteBuffer b,Image.Plane p,int x,int y){
        int pos=y*p.getRowStride()+x*p.getPixelStride();
        return pos>=0 && pos+1<b.limit()?Short.toUnsignedInt(b.getShort(pos)):0;
    }
    private static int confidence(ByteBuffer b,Image.Plane p,int x,int y){
        int pos=y*p.getRowStride()+x*p.getPixelStride();
        return pos>=0&&pos<b.limit()?(b.get(pos)&255):0;
    }
    private static int yuvRgb(Image img,int x,int y){
        Image.Plane[] p=img.getPlanes();if(p.length<3)return 0xB1BEC5;
        int Y=read(p[0],x,y),U=read(p[1],x/2,y/2),V=read(p[2],x/2,y/2);
        int c=Math.max(0,Y-16),d=U-128,e=V-128;
        int r=clip((298*c+409*e+128)>>8);
        int g=clip((298*c-100*d-208*e+128)>>8);
        int b=clip((298*c+516*d+128)>>8);
        return (r<<16)|(g<<8)|b;
    }
    private static int read(Image.Plane p,int x,int y){
        ByteBuffer b=p.getBuffer();int i=y*p.getRowStride()+x*p.getPixelStride();
        return i>=0&&i<b.limit()?(b.get(i)&255):128;
    }
    private static int clip(int v){return Math.max(0,Math.min(255,v));}
}
