package kr.openscan.scan;

import java.util.Locale;

/** Honest, inspectable coverage diagnostics, not a metrology accuracy rating. */
public final class ScanQuality {
    private ScanQuality() {}
    public static final class Report {
        public final int frames,vertices,triangles,connectedVertices;
        public final float spanX,spanY,spanZ,meshCoverage;
        public final String concern;
        public Report(int f,int v,int t,int connected,float sx,float sy,float sz,String concern) {
            frames=f;vertices=v;triangles=t;connectedVertices=connected;
            spanX=sx;spanY=sy;spanZ=sz;
            meshCoverage=v==0?0:connected/(float)v;
            this.concern=concern;
        }
        public String description(){
            return String.format(Locale.KOREAN,"유효 프레임 %d · 포인트 %,d · 삼각면 %,d\n면에 연결된 포인트 %.0f%% · 범위 %.2f × %.2f × %.2fm\n%s",
                frames,vertices,triangles,meshCoverage*100,spanX,spanY,spanZ,concern);
        }
    }
    public static Report from(ScanModel.Snapshot scan) {
        int n=scan.vertices.size();
        if(n==0)return new Report(scan.frameCount,0,0,0,0,0,0,"스캔 데이터 없음");
        float minx=Float.POSITIVE_INFINITY,miny=minx,minz=minx,maxx=Float.NEGATIVE_INFINITY,maxy=maxx,maxz=maxx;
        for(ScanModel.Vertex p:scan.vertices){
            minx=Math.min(minx,p.x);miny=Math.min(miny,p.y);minz=Math.min(minz,p.z);
            maxx=Math.max(maxx,p.x);maxy=Math.max(maxy,p.y);maxz=Math.max(maxz,p.z);
        }
        boolean[] linked=new boolean[n];int count=0;
        for(int[] f:scan.triangles)for(int id:f){
            if(id>=0&&id<n&&!linked[id]){linked[id]=true;count++;}
        }
        float fraction=count/(float)n;
        String message;
        if(scan.frameCount<8)message="여러 각도에서 촬영한 프레임이 부족할 수 있습니다";
        else if(scan.triangles.size()<200)message="연속된 표면이 적습니다. 표면 우선 모드 또는 밝은 환경을 시험하세요";
        else if(fraction<0.15f)message="고립된 점이 많습니다. 배경·반사면을 피하고 가까이 촬영하세요";
        else message="기본 수집량 지표 확인. 실제 형상 정확도는 실물과 비교해야 합니다";
        return new Report(scan.frameCount,n,scan.triangles.size(),count,maxx-minx,maxy-miny,maxz-minz,message);
    }
}
