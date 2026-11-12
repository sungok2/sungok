package kr.openscan.scan;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;

/** Share only explicit user-selected local files using temporary read grants. */
final class ScanShare {
    private ScanShare() {}
    static void share(Activity activity,File file){
        if(!file.isFile()){
            Toast.makeText(activity,"내보낼 파일을 찾을 수 없습니다",Toast.LENGTH_SHORT).show();return;
        }
        Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".files",file);
        Intent intent=new Intent(Intent.ACTION_SEND);
        String name=file.getName().toLowerCase(java.util.Locale.US);
        String type=name.endsWith(".json")?"application/json":name.endsWith(".zip")?"application/zip":"application/octet-stream";
        intent.setType(type);intent.putExtra(Intent.EXTRA_STREAM,uri);
        intent.setClipData(ClipData.newUri(activity.getContentResolver(),"OpenScan 3D",uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(intent,"스캔 파일 공유"));
    }
}
