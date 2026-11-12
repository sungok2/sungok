package kr.openscan.scan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Independent of Android: deterministic 3D point/triangle accumulation. */
public final class ScanModel {
    public enum Mode {
        OBJECT("사물", 0.015f, 6.0f, 4, 0.18f),
        ROOM("실내", 0.035f, 12.0f, 6, 0.35f),
        OUTDOOR("야외", 0.075f, 28.0f, 8, 0.75f);
        public final String label;
        public final float voxelSize, maxDepth, maxEdge;
        public final int pixelStep;
        Mode(String label, float voxelSize, float maxDepth, int pixelStep, float maxEdge) {
            this.label = label; this.voxelSize = voxelSize; this.maxDepth = maxDepth;
            this.pixelStep = pixelStep; this.maxEdge = maxEdge;
        }
        @Override public String toString() { return label; }
    }
    private static final int BIAS = 1 << 20;
    private static final long MASK = (1L << 21) - 1;
    public static final int MAX_VERTICES = 350_000;
    public static final int MAX_FACES = 600_000;

    public static final class Vertex {
        public float x, y, z;
        public int r, g, b, observations;
        Vertex(float x, float y, float z, int r, int g, int b) {
            this.x=x; this.y=y; this.z=z; this.r=r; this.g=g; this.b=b; this.observations=1;
        }
        Vertex(Vertex other) {
            this.x=other.x; this.y=other.y; this.z=other.z;
            this.r=other.r; this.g=other.g; this.b=other.b; this.observations=other.observations;
        }
        void merge(float x,float y,float z,int r,int g,int b) {
            // Weighted averaging dampens noise but caps old-observation influence.
            int n = Math.min(12, observations);
            this.x=(this.x*n+x)/(n+1); this.y=(this.y*n+y)/(n+1); this.z=(this.z*n+z)/(n+1);
            this.r=(this.r*n+r)/(n+1); this.g=(this.g*n+g)/(n+1); this.b=(this.b*n+b)/(n+1);
            observations++;
        }
    }
    public static final class Snapshot {
        public final List<Vertex> vertices;
        public final List<int[]> triangles;
        public final int frameCount;
        public final Mode mode;
        Snapshot(List<Vertex> vertices, List<int[]> triangles, int frameCount, Mode mode) {
            this.vertices=vertices; this.triangles=triangles; this.frameCount=frameCount; this.mode=mode;
        }
    }
    private final Map<Long,Integer> voxelIndex = new HashMap<>();
    private final List<Vertex> vertices = new ArrayList<>();
    private final List<int[]> faces = new ArrayList<>();
    private final Set<Long> faceKeys = new HashSet<>();
    private Mode mode;
    private int frames;
    public ScanModel(Mode mode) { this.mode = mode; }

    /** Integer voxel address, collision-free in the 3D +/-1,048,576 cell working range. */
    private long key(float x,float y,float z) {
        long ix = Math.round(Math.floor(x/mode.voxelSize)) + BIAS;
        long iy = Math.round(Math.floor(y/mode.voxelSize)) + BIAS;
        long iz = Math.round(Math.floor(z/mode.voxelSize)) + BIAS;
        if (ix<0 || iy<0 || iz<0 || ix>MASK || iy>MASK || iz>MASK) return -1;
        return (ix<<42)|(iy<<21)|iz;
    }
    private int put(float x,float y,float z,int rgb) {
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))return -1;
        long k = key(x,y,z);
        if(k<0) return -1;
        Integer prior=voxelIndex.get(k);
        int r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;
        if(prior!=null) { vertices.get(prior).merge(x,y,z,r,g,b); return prior; }
        if(vertices.size()>=MAX_VERTICES) return -1;
        int i=vertices.size();
        voxelIndex.put(k,i); vertices.add(new Vertex(x,y,z,r,g,b)); return i;
    }
    /** Inputs are row-major, invalid samples use valid=false; 3D in world meters. */
    public synchronized void addGrid(int width,int height,float[] xyz,int[] colors,boolean[] valid) {
        if(width<2 || height<2 || xyz.length!=width*height*3 || colors.length!=width*height || valid.length!=width*height)
            throw new IllegalArgumentException("Invalid depth grid dimensions");
        int[] ids=new int[width*height];
        for(int k=0;k<ids.length;k++) {
            ids[k]=valid[k]?put(xyz[k*3],xyz[k*3+1],xyz[k*3+2],colors[k]):-1;
        }
        for(int y=0;y<height-1 && faces.size()<MAX_FACES;y++) for(int x=0;x<width-1 && faces.size()<MAX_FACES;x++) {
            int a=ids[y*width+x],b=ids[y*width+x+1];
            int c=ids[(y+1)*width+x],d=ids[(y+1)*width+x+1];
            if(a>=0 && b>=0 && c>=0 && d>=0) {
                addTriangle(a,b,c);addTriangle(b,d,c);
            }else if(a<0){addTriangle(b,d,c);
            }else if(b<0){addTriangle(a,d,c);
            }else if(c<0){addTriangle(a,b,d);
            }else {addTriangle(a,b,c);}
        }
        frames++;
    }
    /** Fallback for devices with ARCore but without Depth API: sparse points, no triangles. */
    public synchronized void addPoint(float x,float y,float z,int rgb) { put(x,y,z,rgb); }
    public synchronized void markFrame() { frames++; }
    private void addTriangle(int a,int b,int c) {
        if(a<0||b<0||c<0||a==b||b==c||a==c||faces.size()>=MAX_FACES) return;
        Vertex p=vertices.get(a),q=vertices.get(b),r=vertices.get(c);
        if(dist2(p,q)>mode.maxEdge*mode.maxEdge || dist2(q,r)>mode.maxEdge*mode.maxEdge || dist2(r,p)>mode.maxEdge*mode.maxEdge) return;
        float ux=q.x-p.x,uy=q.y-p.y,uz=q.z-p.z;
        float vx=r.x-p.x,vy=r.y-p.y,vz=r.z-p.z;
        float cx=uy*vz-uz*vy,cy=uz*vx-ux*vz,cz=ux*vy-uy*vx;
        if(cx*cx+cy*cy+cz*cz < 0.00000001f) return;
        int lo=Math.min(a,Math.min(b,c)), hi=Math.max(a,Math.max(b,c)), mid=a+b+c-lo-hi;
        long key=((long)lo<<38)|((long)mid<<19)|hi;
        if(faceKeys.add(key))faces.add(new int[]{a,b,c});
    }
    private float dist2(Vertex a,Vertex b) {
        float x=a.x-b.x,y=a.y-b.y,z=a.z-b.z;
        return x*x+y*y+z*z;
    }
    public synchronized int vertices() { return vertices.size(); }
    public synchronized int triangles() { return faces.size(); }
    public synchronized int frames() { return frames; }
    public synchronized boolean limitReached() { return vertices.size()>=MAX_VERTICES || faces.size()>=MAX_FACES; }
    public synchronized Snapshot snapshot() {
        List<Vertex> vv=new ArrayList<>(vertices.size());
        for(Vertex v:vertices) vv.add(new Vertex(v));
        List<int[]> ff=new ArrayList<>(faces.size());
        for(int[] f:faces) ff.add(f.clone());
        return new Snapshot(vv,ff,frames,mode);
    }
}
