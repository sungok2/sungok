import kr.openscan.scan.DepthGeometry;
public final class DepthGeometryTest {
    public static void main(String[] args) {
        float[] k=DepthGeometry.scaleIntrinsics(960,480,960,540,1920,1080,160,120);
        checkClose(k[0],80);checkClose(k[1],53.333332f);
        checkClose(k[2],80);checkClose(k[3],60);
        float[] v=new float[3];
        DepthGeometry.unproject(80,60,2,k,v);
        checkClose(v[0],0);checkClose(v[1],0);checkClose(v[2],-2);
        DepthGeometry.unproject(160,60,2,k,v);
        checkClose(v[0],2);
        DepthGeometry.unproject(80,60+k[1],2,k,v);
        checkClose(v[1],-2);
        System.out.println("PASS raw depth texture intrinsics and signed camera axes");
    }
    private static void checkClose(float actual,float wanted){
        if(Math.abs(actual-wanted)>0.0002f)throw new AssertionError(actual+" != "+wanted);
    }
}
