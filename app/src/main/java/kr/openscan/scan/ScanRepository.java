package kr.openscan.scan;

import android.content.Context;
import android.location.Location;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.FileInputStream;
import org.json.JSONObject;

/** Local-only storage; files never leave the device unless explicitly shared. */
public final class ScanRepository {
    private ScanRepository() {}
    public static File root(Context ctx) {
        File external=ctx.getExternalFilesDir(null);
        File path=new File(external!=null?external:ctx.getFilesDir(), "scans");
        if(!path.isDirectory() && !path.mkdirs()) throw new IllegalStateException("Cannot create scan directory");
        return path;
    }
    public static File[] list(Context context) {
        File[] folders=root(context).listFiles(File::isDirectory);
        if(folders==null)return new File[0];
        java.util.Arrays.sort(folders,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        return folders;
    }
    public static File save(Context ctx,ScanModel.Snapshot data,Location location) throws Exception {
        if(data.vertices.isEmpty())throw new IllegalArgumentException("스캔 데이터가 없습니다");
        String name="Scan_"+new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        File folder=new File(root(ctx),name);
        if(!folder.mkdirs())throw new IOException("Cannot create folder");
        try {
            ScanFileFormats.writePly(new File(folder,"model.ply"),data);
            ScanFileFormats.writeObj(new File(folder,"model.obj"),data);
            JSONObject meta=new JSONObject();
            meta.put("name",name); meta.put("format_version",1);
            meta.put("created_at_epoch_ms",System.currentTimeMillis());
            meta.put("mode",data.mode.name()); meta.put("frames",data.frameCount);
            meta.put("points",data.vertices.size());meta.put("triangles",data.triangles.size());
            meta.put("coordinates","local_ARCore_world_meters");
            meta.put("gps_role","approximate_scan_site_only_not_georeferenced");
            if(location!=null) {
                meta.put("gps_lat",location.getLatitude());meta.put("gps_lon",location.getLongitude());
                if(location.hasAccuracy())meta.put("gps_accuracy_m",location.getAccuracy());
                meta.put("gps_timestamp_ms",location.getTime());
            }
            try(FileOutputStream out=new FileOutputStream(new File(folder,"metadata.json"))){
                out.write(meta.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            return folder;
        } catch(Exception e) { // Avoid exposing partially written scan as completed.
            File[] files=folder.listFiles();if(files!=null)for(File f:files)f.delete();folder.delete();throw e;
        }
    }
    /** Creates a self-contained portable archive including photos and camera poses. */
    public static File archive(File folder) throws IOException {
        File outFile=new File(folder,"project_export.zip");
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)))){
            File[] children=folder.listFiles();
            if(children!=null)for(File child:children){
                if(!child.equals(outFile))appendZip(zip,child,child.getName());
            }
        } catch(IOException e){outFile.delete();throw e;}
        return outFile;
    }
    private static void appendZip(ZipOutputStream zip,File item,String relative) throws IOException {
        if(item.isDirectory()){
            File[] children=item.listFiles();
            if(children!=null)for(File child:children)appendZip(zip,child,relative+"/"+child.getName());
            return;
        }
        zip.putNextEntry(new ZipEntry(relative));
        try(FileInputStream in=new FileInputStream(item)){
            byte[] buffer=new byte[32768];int n;
            while((n=in.read(buffer))!=-1)zip.write(buffer,0,n);
        }
        zip.closeEntry();
    }

}
