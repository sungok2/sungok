import kr.openscan.scan.ScanQuality;
import kr.openscan.scan.ScanModel;
public class ScanQualityTest {
    public static void main(String[] args){
        ScanModel model=new ScanModel(ScanModel.Mode.OBJECT);
        float[] xyz={0,0,1, .04f,0,1, 0,.04f,1, .04f,.04f,1};
        model.addGrid(2,2,xyz,new int[]{1,2,3,4},new boolean[]{true,true,true,true});
        ScanQuality.Report report=ScanQuality.from(model.snapshot());
        if(report.vertices!=4 || report.triangles!=2)throw new AssertionError("counts");
        if(Math.abs(report.meshCoverage-1)>0.001)throw new AssertionError("mesh coverage");
        if(!report.description().contains("프레임 1"))throw new AssertionError("description");
        System.out.println("PASS ScanQuality counts, connected vertices, coverage and bounding dimensions");
    }
}
