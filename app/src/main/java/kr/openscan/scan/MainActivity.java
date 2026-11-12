package kr.openscan.scan;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Build;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Phone-first UI: offline scan, save, preview and share. */
public final class MainActivity extends Activity {
    private static volatile Activity foreground;
    private static final int CAMERA_REQUEST=200;
    private Session session;
    private GLSurfaceView surface;
    private ArCameraRenderer renderer;
    private TextView status;
    private Spinner modeSpinner;
    private Button start,save;
    private CheckBox includePhotos;
    private PhotoDataset photoSet;
    private ScanModel model;
    private boolean isRecording=false, installRequested=false, sessionResumed=false;
    private final ExecutorService disk=Executors.newSingleThreadExecutor();
    private volatile boolean exporting=false;
    public static int displayRotation(){
        Activity a=foreground;
        if(a==null)return Surface.ROTATION_0;
        return a.getWindowManager().getDefaultDisplay().getRotation();
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);foreground=this;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF111923);
        TextView header=new TextView(this);header.setText("OpenScan 3D");header.setTextSize(23);header.setTextColor(Color.WHITE);
        header.setPadding(18,16,16,12);root.addView(header);
        FrameLayout preview=new FrameLayout(this);
        surface=new GLSurfaceView(this);surface.setEGLContextClientVersion(2);surface.setPreserveEGLContextOnPause(true);
        renderer=new ArCameraRenderer(s->runOnUiThread(()->{
            status.setText(s);
            if(model!=null && model.limitReached() && isRecording){
                isRecording=false;start.setText("용량 제한 · 저장하세요");
            }
        }));
        surface.setRenderer(renderer);surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        preview.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        TextView overlay=new TextView(this);
        overlay.setText("천천히 이동하며 물체의 여러 면을 비추세요\n반짝이는 물체·유리·움직이는 대상은 품질이 낮을 수 있습니다");
        overlay.setTextSize(12);overlay.setTextColor(Color.WHITE);overlay.setPadding(12,12,12,12);
        overlay.setBackgroundColor(0x66000000);
        FrameLayout.LayoutParams overlayP=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        preview.addView(overlay,overlayP);
        root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        status=new TextView(this);status.setText("카메라 준비 중");status.setTextColor(0xFFE0E8EF);
        status.setTextSize(13);status.setPadding(14,10,12,10);root.addView(status);
        modeSpinner=new Spinner(this);modeSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ScanModel.Mode.values()));
        modeSpinner.setSelection(2);root.addView(modeSpinner,new LinearLayout.LayoutParams(-1,-2));
        includePhotos=new CheckBox(this);includePhotos.setText("사진 데이터셋도 수집 (외부 사진 기반 복원용)");
        includePhotos.setTextColor(Color.WHITE);includePhotos.setChecked(true);root.addView(includePhotos);
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);
        start=button("스캔 시작");save=button("저장");Button gallery=button("내 스캔");
        controls.addView(start,new LinearLayout.LayoutParams(0,54,1));
        controls.addView(save,new LinearLayout.LayoutParams(0,54,1));
        controls.addView(gallery,new LinearLayout.LayoutParams(0,54,1));root.addView(controls);
        TextView foot=new TextView(this);foot.setText("기기 내 저장 · PLY/OBJ · GPS는 위치 참고용");
        foot.setTextSize(11);foot.setTextColor(0xFFBFC9D6);foot.setGravity(Gravity.CENTER);
        foot.setPadding(4,10,4,14);root.addView(foot);
        setContentView(root);
        start.setOnClickListener(v->toggleScan());save.setOnClickListener(v->saveCurrent());gallery.setOnClickListener(v->showLibrary());
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_REQUEST);
        else requestOptionalLocation();
    }
    private Button button(String title){Button b=new Button(this);b.setText(title);b.setAllCaps(false);return b;}
    private void requestOptionalLocation(){
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},201);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code==CAMERA_REQUEST){
            if(grants.length==0||grants[0]!=PackageManager.PERMISSION_GRANTED){
                status.setText("카메라 권한이 필요합니다. 앱 설정에서 권한을 허용하세요.");
            }else{requestOptionalLocation();surface.post(this::ensureSessionRunning);}
        }
    }
    @Override protected void onResume(){super.onResume();foreground=this;ensureSessionRunning();}
    private void ensureSessionRunning(){
        if(sessionResumed || checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;
        try{
            if(session==null){
                ArCoreApk.InstallStatus install=ArCoreApk.getInstance().requestInstall(this,!installRequested);
                if(install==ArCoreApk.InstallStatus.INSTALL_REQUESTED){installRequested=true;return;}
                session=new Session(this);
                Config c=session.getConfig();
                boolean depth=session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
                if(depth)c.setDepthMode(Config.DepthMode.AUTOMATIC);
                c.setFocusMode(Config.FocusMode.AUTO);
                session.configure(c);renderer.configure(session,depth);
                status.setText(depth?"ARCore 깊이 센서 준비 완료":"Depth 미지원: 희소 점군 모드");
            }
            session.resume();surface.onResume();sessionResumed=true;
        }catch(Exception e){status.setText("ARCore 실행 실패: "+e.getClass().getSimpleName()+" · Google Play AR 서비스를 확인하세요");}
    }
    @Override protected void onPause(){
        if(sessionResumed){
            if(surface!=null)surface.onPause();
            if(session!=null)session.pause();
            sessionResumed=false;
        }
        super.onPause();
    }
    @Override protected void onDestroy(){
        foreground=null;renderer.stop();
        if(photoSet!=null&&!exporting){photoSet.discard();photoSet=null;}
        if(session!=null)session.close();disk.shutdown();super.onDestroy();
    }
    private void toggleScan(){
        if(session==null){Toast.makeText(this,"ARCore가 준비되지 않았습니다",Toast.LENGTH_SHORT).show();return;}
        if(exporting)return;
        if(model!=null && model.limitReached()){
            Toast.makeText(this,"점군 용량 제한입니다. 지금까지 스캔한 데이터를 저장하세요.",Toast.LENGTH_LONG).show();return;
        }
        if(isRecording){isRecording=false;renderer.stop();start.setText("스캔 재개");modeSpinner.setEnabled(false);return;}
        if(model==null){
            ScanModel.Mode m=(ScanModel.Mode)modeSpinner.getSelectedItem();model=new ScanModel(m);
        }
        modeSpinner.setEnabled(false);includePhotos.setEnabled(false);isRecording=true;start.setText("일시정지");
        if(includePhotos.isChecked() && photoSet==null){
            try{photoSet=new PhotoDataset(getCacheDir());renderer.setPhotos(photoSet);}
            catch(Exception e){Toast.makeText(this,"사진 수집 불가: "+e.getMessage(),Toast.LENGTH_SHORT).show();}
        }
        renderer.scan(model,(ScanModel.Mode)modeSpinner.getSelectedItem(),true);
    }
    private Location getLocation(){
        if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return null;
        try{
            LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
            Location a=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location b=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location latest=(a==null)?b:(b==null?a:(a.getTime()>b.getTime()?a:b));
            if(latest==null || Math.abs(System.currentTimeMillis()-latest.getTime())>10*60*1000L)return null;
            return latest;
        }catch(Exception ignored){return null;}
    }
    private void saveCurrent(){
        if(exporting)return;
        if(model==null || model.vertices()==0){Toast.makeText(this,"저장할 점군이 없습니다",Toast.LENGTH_SHORT).show();return;}
        isRecording=false;renderer.stop();exporting=true;
        start.setEnabled(false);save.setEnabled(false);
        final ScanModel.Snapshot snapshot=model.snapshot();final Location loc=getLocation();
        final PhotoDataset exportPhotos=photoSet;renderer.setPhotos(null);
        status.setText("PLY/OBJ 저장 중: "+snapshot.vertices.size()+"점");
        disk.submit(()->{
            try{
                File folder=ScanRepository.save(getApplicationContext(),snapshot,loc);
                int photoCount=0;String photoError="";
                if(exportPhotos!=null){
                    try{photoCount=exportPhotos.finish(folder);}catch(Exception photoEx){photoError=" · 사진 저장 오류";}
                }
                final int savedPhotoCount=photoCount;final String photoNote=photoError;
                runOnUiThread(()->{
                    exporting=false;model=null;renderer.scan(null,ScanModel.Mode.OUTDOOR,false);
                    photoSet=null;modeSpinner.setEnabled(true);includePhotos.setEnabled(true);start.setText("스캔 시작");start.setEnabled(true);save.setEnabled(true);
                    status.setText("저장 완료: "+folder.getName()+" · 사진 "+savedPhotoCount+"장"+photoNote);showScanActions(folder);
                });
            }catch(Exception ex){runOnUiThread(()->{
                exporting=false;start.setEnabled(true);save.setEnabled(true);
                status.setText("저장 실패: "+ex.getMessage());
            });}
        });
    }
    private void showLibrary(){
        File[] folders=ScanRepository.list(this);
        if(folders.length==0){Toast.makeText(this,"저장한 스캔이 없습니다",Toast.LENGTH_SHORT).show();return;}
        String[] names=new String[folders.length];for(int i=0;i<folders.length;i++)names[i]=folders[i].getName();
        new AlertDialog.Builder(this).setTitle("내 스캔").setItems(names,(d,which)->showScanActions(folders[which])).setNegativeButton("닫기",null).show();
    }
    private void showScanActions(File folder){
        new AlertDialog.Builder(this).setTitle(folder.getName())
            .setItems(new String[]{"3D 점군 보기","PLY 파일 공유","OBJ 파일 공유","전체 프로젝트 ZIP 공유","GPS 메타데이터 공유"},(d,which)->{
                if(which==0){Intent i=new Intent(this,CloudViewerActivity.class);i.putExtra("scan_path",folder.getAbsolutePath());startActivity(i);}
                else if(which==3){
                    status.setText("ZIP 내보내기 준비 중");
                    disk.submit(()->{
                        try{File zip=ScanRepository.archive(folder);runOnUiThread(()->{status.setText("프로젝트 ZIP 준비 완료");shareFile(zip);});}
                        catch(Exception ex){runOnUiThread(()->status.setText("ZIP 내보내기 오류: "+ex.getMessage()));}
                    });
                }
                else shareFile(new File(folder,which==1?"model.ply":which==2?"model.obj":"metadata.json"));
            }).setNegativeButton("닫기",null).show();
    }
    private void shareFile(File file){
        if(!file.exists()){Toast.makeText(this,"파일이 없습니다",Toast.LENGTH_SHORT).show();return;}
        Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",file);
        Intent share=new Intent(Intent.ACTION_SEND);
        share.setType(file.getName().endsWith(".json")?"application/json":file.getName().endsWith(".zip")?"application/zip":"application/octet-stream");
        share.putExtra(Intent.EXTRA_STREAM,uri);
        share.setClipData(ClipData.newUri(getContentResolver(),"OpenScan 3D",uri));
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share,"파일 내보내기"));
    }
}
