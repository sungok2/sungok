package kr.openscan.scan;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

/** Reader for OpenScan's binary PLY files. Limits memory and avoids an outlier-driven camera fit. */
public final class PlyMeshReader {
    private PlyMeshReader() {}
    public static final class Result {
        public final float[] points, pointColors, mesh, meshColors;
        public final int pointCount, meshVertexCount, sourceVertexCount, sourceFaceCount;
        public final float cx, cy, cz, radius;
        Result(float[] p,float[] c,float[] m,float[] mc,int pn,int mn,int totalVertices,int totalFaces,float x,float y,float z,float r){
            points=p;pointColors=c;mesh=m;meshColors=mc;pointCount=pn;meshVertexCount=mn;
            sourceVertexCount=totalVertices;sourceFaceCount=totalFaces;
            cx=x;cy=y;cz=z;radius=r;
        }
    }
    private static String readLine(DataInputStream in) throws IOException {
        StringBuilder s=new StringBuilder();int ch;
        while((ch=in.read())>=0){
            if(ch=='\n')return s.toString();
            if(ch!='\r')s.append((char)ch);
            if(s.length()>512)throw new IOException("PLY header line too long");
        }
        return null;
    }
    private static float readFloat(DataInputStream in) throws IOException {
        return Float.intBitsToFloat(Integer.reverseBytes(in.readInt()));
    }
    private static int readInt(DataInputStream in) throws IOException {return Integer.reverseBytes(in.readInt());}
    private static float center(float[] original,int axis,int count){
        float[] sorted=new float[count];
        for(int i=0;i<count;i++)sorted[i]=original[i*3+axis];
        Arrays.sort(sorted);
        return (sorted[(int)((count-1)*0.02)]+sorted[(int)((count-1)*0.98)])*0.5f;
    }
    private static float robustRange(float[] original,int axis,int count){
        float[] sorted=new float[count];
        for(int i=0;i<count;i++)sorted[i]=original[i*3+axis];
        Arrays.sort(sorted);
        return sorted[(int)((count-1)*0.98)]-sorted[(int)((count-1)*0.02)];
    }
    public static Result read(File file,int maxPoints,int maxFaces) throws IOException {
        if(maxPoints<1 || maxFaces<0)throw new IllegalArgumentException("limits");
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file),131072))){
            if(!"ply".equals(readLine(in)) || !"format binary_little_endian 1.0".equals(readLine(in)))
                throw new IOException("Unsupported PLY format");
            int vertexCount=-1,faceCount=0;String line;boolean ended=false;
            while((line=readLine(in))!=null){
                if(line.startsWith("element vertex "))vertexCount=Integer.parseInt(line.substring(15).trim());
                else if(line.startsWith("element face "))faceCount=Integer.parseInt(line.substring(13).trim());
                if("end_header".equals(line)){ended=true;break;}
            }
            if(!ended||vertexCount<1||vertexCount>ScanModel.MAX_VERTICES || faceCount<0 ||faceCount>ScanModel.MAX_FACES)
                throw new IOException("Invalid PLY header counts");
            float[] vertices=new float[vertexCount*3];int[] rgb=new int[vertexCount];
            for(int i=0;i<vertexCount;i++){
                float x=readFloat(in),y=readFloat(in),z=readFloat(in);
                if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IOException("Non-finite PLY vertex");
                vertices[3*i]=x;vertices[3*i+1]=y;vertices[3*i+2]=z;
                rgb[i]=(in.readUnsignedByte()<<16)|(in.readUnsignedByte()<<8)|in.readUnsignedByte();
            }
            int faceStride=Math.max(1,(faceCount+Math.max(1,maxFaces)-1)/Math.max(1,maxFaces));
            int[] faceIndices=new int[Math.min(maxFaces,faceCount)*3];int kept=0;
            for(int i=0;i<faceCount;i++){
                int len=in.readUnsignedByte();
                if(len>20)throw new IOException("Invalid face polygon size");
                int a=-1,b=-1,c=-1;
                for(int j=0;j<len;j++){
                    int idx=readInt(in);
                    if(j==0)a=idx;else if(j==1)b=idx;else if(j==2)c=idx;
                }
                if(len!=3 || i%faceStride!=0 || kept>=maxFaces)continue;
                if(a<0||b<0||c<0||a>=vertexCount||b>=vertexCount||c>=vertexCount)continue;
                faceIndices[kept*3]=a;faceIndices[kept*3+1]=b;faceIndices[kept*3+2]=c;kept++;
            }
            float cx=center(vertices,0,vertexCount),cy=center(vertices,1,vertexCount),cz=center(vertices,2,vertexCount);
            float radius=Math.max(0.20f,Math.max(robustRange(vertices,0,vertexCount),
                Math.max(robustRange(vertices,1,vertexCount),robustRange(vertices,2,vertexCount)))*0.5f);
            int pointStride=Math.max(1,(vertexCount+maxPoints-1)/maxPoints);
            int pn=(vertexCount+pointStride-1)/pointStride;
            float[] points=new float[pn*3],colors=new float[pn*3];
            for(int i=0,at=0;i<vertexCount;i+=pointStride,at++){
                points[at*3]=vertices[i*3]-cx;points[at*3+1]=vertices[i*3+1]-cy;points[at*3+2]=vertices[i*3+2]-cz;
                floatColor(rgb[i],colors,at*3);
            }
            float[] mesh=new float[kept*9],meshColors=new float[kept*9];
            for(int i=0;i<kept*3;i++){
                int index=faceIndices[i];int dest=i*3;
                mesh[dest]=vertices[index*3]-cx;mesh[dest+1]=vertices[index*3+1]-cy;mesh[dest+2]=vertices[index*3+2]-cz;
                floatColor(rgb[index],meshColors,dest);
            }
            return new Result(points,colors,mesh,meshColors,pn,kept*3,vertexCount,faceCount,cx,cy,cz,radius);
        }
    }
    private static void floatColor(int rgb,float[] into,int idx){
        into[idx]=((rgb>>>16)&255)/255f;into[idx+1]=((rgb>>>8)&255)/255f;into[idx+2]=(rgb&255)/255f;
    }
}
