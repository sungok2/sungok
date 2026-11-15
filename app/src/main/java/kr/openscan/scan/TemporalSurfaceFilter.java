package kr.openscan.scan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Conservative export-side denoising. Never modifies the raw capture.
 * Retains vertices recorded in >=2 independent capture frames and only faces
 * fully supported by such vertices. This DOES NOT fix bad camera poses, incorrect
 * calibration, or replace TSDF / bundle adjustment.
 */
public final class TemporalSurfaceFilter {
    private TemporalSurfaceFilter() {}

    public static final class Result {
        public final ScanModel.Snapshot filtered;
        public final int stablePoints, stableFaces, rawPoints, rawFaces;
        Result(ScanModel.Snapshot s,int sp,int sf,int rp,int rf) {
            filtered=s;stablePoints=sp;stableFaces=sf;rawPoints=rp;rawFaces=rf;
        }
        public boolean suitableForPreview() {
            // Avoid presenting a tiny fraction of a model as a complete reconstruction.
            return rawPoints>=100 && stablePoints>=100 && stableFaces>=30
                && stablePoints>=rawPoints/20;
        }
    }

    public static Result process(ScanModel.Snapshot source) {
        int n=source.vertices.size();int[] map=new int[n];Arrays.fill(map,-1);
        List<ScanModel.Vertex> filteredVertices=new ArrayList<>();
        for(int i=0;i<n;i++) {
            ScanModel.Vertex v=source.vertices.get(i);
            if(v.framesSeen<2)continue;
            map[i]=filteredVertices.size();
            filteredVertices.add(new ScanModel.Vertex(v));
        }
        List<int[]> faces=new ArrayList<>();
        for(int[] face:source.triangles) {
            if(face.length!=3)continue;
            int a=face[0],b=face[1],c=face[2];
            if(a<0||b<0||c<0||a>=n||b>=n||c>=n)continue;
            if(map[a]>=0&&map[b]>=0&&map[c]>=0)
                faces.add(new int[]{map[a],map[b],map[c]});
        }
        return new Result(new ScanModel.Snapshot(filteredVertices,faces,source.frameCount,source.mode),
            filteredVertices.size(),faces.size(),n,source.triangles.size());
    }
}
