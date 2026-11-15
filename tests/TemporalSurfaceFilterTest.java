import kr.openscan.scan.ScanModel;
import kr.openscan.scan.TemporalSurfaceFilter;

public final class TemporalSurfaceFilterTest {
    private static void test(boolean v,String message){if(!v)throw new AssertionError(message);}
    public static void main(String[] args) {
        ScanModel m=new ScanModel(ScanModel.Mode.OBJECT);
        float[] xyz={0,0,1, 0.04f,0,1, 0,0.04f,1, 0.04f,0.04f,1};
        int[] rgb={0xffffff,0xffffff,0xffffff,0xffffff};
        boolean[] valid={true,true,true,true};
        m.addGrid(2,2,xyz,rgb,valid);
        ScanModel.Snapshot one=m.snapshot();
        test(TemporalSurfaceFilter.process(one).stablePoints==0,"single frame not stable");
        m.addGrid(2,2,xyz,rgb,valid);
        test(TemporalSurfaceFilter.process(m.snapshot()).stablePoints==4,"repeated points retained");
        test(TemporalSurfaceFilter.process(m.snapshot()).stableFaces==2,"repeated triangles retained");
        // Two identical samples in ONE capture frame should NOT count as two frames.
        ScanModel single=new ScanModel(ScanModel.Mode.OBJECT);
        float[] d={0,0,1,0.001f,0,1,0,0.04f,1,0.04f,0.04f,1};
        single.addGrid(2,2,d,rgb,valid);
        test(single.vertices()==3,"same voxel dedup within frame");
        test(TemporalSurfaceFilter.process(single.snapshot()).stablePoints==0,
            "same-frame voxel merging must not imply independent support");
        m.addPoint(0.50f,0.50f,0.50f,0xFFFFFF); // new noise appears in 3rd frame only
        TemporalSurfaceFilter.Result r=TemporalSurfaceFilter.process(m.snapshot());
        test(r.rawPoints==5 && r.stablePoints==4,"one-frame isolated noise filtered");
        test(r.stableFaces==2,"surface retained");
        test(!r.suitableForPreview(),"tiny mock data must not trigger stable preview");
        // A repeated textured-looking grid should qualify for a separate stable export.
        ScanModel large=new ScanModel(ScanModel.Mode.OBJECT);
        int side=11;float[] grid=new float[side*side*3];int[] colors=new int[side*side];
        boolean[] present=new boolean[side*side];
        for(int y=0;y<side;y++)for(int x=0;x<side;x++) {
            int k=y*side+x;grid[k*3]=x*0.03f;grid[k*3+1]=y*0.03f;grid[k*3+2]=1;
            present[k]=true;colors[k]=0x99CCAA;
        }
        large.addGrid(side,side,grid,colors,present);
        large.addGrid(side,side,grid,colors,present);
        TemporalSurfaceFilter.Result qualifies=TemporalSurfaceFilter.process(large.snapshot());
        test(qualifies.stablePoints==121,"distinct frames must retain 121 points");
        test(qualifies.stableFaces>=190,"repeated mesh faces retained");
        test(qualifies.suitableForPreview(),"repeat-observed mesh can be separately exported");
        System.out.println("PASS multi-frame stability: retain repeat surfaces, reject single-frame noise");
    }
}
