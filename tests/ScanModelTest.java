import kr.openscan.scan.ScanModel;
import kr.openscan.scan.ScanFileFormats;
import kr.openscan.scan.PlyMeshReader;
import java.io.File;

public final class ScanModelTest {
    public static void main(String[] args) throws Exception {
        ScanModel model=new ScanModel(ScanModel.Mode.OBJECT);
        float[] p={0,0,0, 0.04f,0,0, 0,0.04f,0, 0.04f,0.04f,0};
        int[] c={0xff0000,0x00ff00,0x0000ff,0xffffff};
        boolean[] v={true,true,true,true};
        model.addGrid(2,2,p,c,v);
        require(model.vertices()==4,"4 unique points");
        require(model.triangles()==2,"two triangles");
        model.addGrid(2,2,p,c,v);
        require(model.vertices()==4,"voxel dedup across frames");
        require(model.triangles()==2,"triangle dedup across frames");
        require(model.frames()==2,"frame count");
        ScanModel.Snapshot snap=model.snapshot();
        require(snap.vertices.size()==4,"snapshot vertices");
        model.addPoint(1f,1f,1f,0x112233);
        require(snap.vertices.size()==4 && model.vertices()==5,"immutable point count after snapshot");
        File ply=File.createTempFile("scanmodel_", ".ply");
        File obj=File.createTempFile("scanmodel_", ".obj");
        ScanFileFormats.writePly(ply,snap); ScanFileFormats.writeObj(obj,snap);
        require(ply.length()>150,"PLY output");
        require(obj.length()>80,"OBJ output");
        System.out.println("PASS ScanModel: vertices="+snap.vertices.size()+" faces="+snap.triangles.size());
        System.out.println("PLY="+ply.getAbsolutePath());
        System.out.println("OBJ="+obj.getAbsolutePath());
        ScanModel far=new ScanModel(ScanModel.Mode.OBJECT);
        float[] gap={0,0,0, 8f,0,0, 0,0.04f,0, 8f,0.04f,0};
        far.addGrid(2,2,gap,c,v);
        require(far.triangles()==0,"discontinuity rejection");
        ScanModel invalid=new ScanModel(ScanModel.Mode.OBJECT);
        invalid.addGrid(2,2,p,c,new boolean[]{true,false,true,true});
        require(invalid.vertices()==3 && invalid.triangles()==1,"invalid depth mask");
        ScanModel depthModel = new ScanModel(ScanModel.Mode.OBJECT);
        depthModel.addGrid(2, 2, p, c, v, new float[]{1f, 1f, 3f, 3f});
        require(depthModel.triangles()==0,"occlusion depth gap must reject stretched triangles");
        PlyMeshReader.Result decoded=PlyMeshReader.read(ply,100,100);
        require(decoded.sourceVertexCount==4 && decoded.sourceFaceCount==2 && decoded.pointCount==4 && decoded.meshVertexCount==6,"PLY mesh should be loaded and rendered");
        require(Math.abs(decoded.points[0]+decoded.cx)<0.0001,"viewer points centered without corrupting world coordinates");
        System.out.println("PASS occlusion gap filtering and PLY mesh viewer decoding");
        System.out.println("PASS edge filtering, invalid-mask handling, immutable snapshot");
    }
    static void require(boolean cond,String msg){if(!cond)throw new AssertionError(msg);}
}
