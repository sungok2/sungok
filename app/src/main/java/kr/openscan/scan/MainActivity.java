package kr.openscan.scan;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Camera-first scanner UI: a single obvious action in each capture state. */
public final class MainActivity extends Activity {
    private static volatile Activity foreground;
    private static final int CAMERA_REQUEST=200, LOCATION_REQUEST=201;
    private final ExecutorService disk=Executors.newSingleThreadExecutor();
    private Session session;
    private GLSurfaceView surface;
    private ArCameraRenderer renderer;
    private ScanModel model;
    private PhotoDataset photoSet;
    private ScanModel.Mode selectedMode=ScanModel.Mode.OBJECT;
    private boolean isRecording=false, installRequested=false, sessionResumed=false, exporting=false;
    private boolean includePhotos=false, includeGps=false;
    private String lastReport="카메라 준비 중";
    private TextView cameraStatus,scanState,pointCount,faceCount,helper,modeButtons[];
    private Button capture,save,reset,settings,library;
    private ProgressBar savingProgress;

    public static int displayRotation(){
        Activity a=foreground;return a==null?Surface.ROTATION_0:a.getWindowManager().getDefaultDisplay().getRotation();
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);foreground=this;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(UiKit.INK);getWindow().setNavigationBarColor(UiKit.INK);
        includePhotos=getPreferences(MODE_PRIVATE).getBoolean("photos",false);
        includeGps=getPreferences(MODE_PRIVATE).getBoolean("gps",false);
        createScreen();
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_REQUEST);
    }
    private void createScreen(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(UiKit.INK);
        surface=new GLSurfaceView(this);surface.setEGLContextClientVersion(2);
        surface.setPreserveEGLContextOnPause(true);
        renderer=new ArCameraRenderer(s->runOnUiThread(()->{
            lastReport=s;
            if(model!=null && isRecording && model.limitReached()){
                isRecording=false;renderer.stop();
                Toast.makeText(this,"스캔 용량에 도달했습니다. 저장해 주세요.",Toast.LENGTH_LONG).show();
            }
            refreshState();
        }));
        surface.setRenderer(renderer);surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        root.addView(new Reticle(this),new FrameLayout.LayoutParams(-1,-1));

        LinearLayout overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(UiKit.dp(this,16),UiKit.dp(this,14),UiKit.dp(this,16),UiKit.dp(this,14));
        FrameLayout.LayoutParams olp=new FrameLayout.LayoutParams(-1,-1);root.addView(overlay,olp);
        overlay.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(UiKit.dp(this,16),UiKit.dp(this,12)+insets.getSystemWindowInsetTop(),
                UiKit.dp(this,16),UiKit.dp(this,12)+insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox=new LinearLayout(this);titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title=UiKit.text(this,"OpenScan 3D",22,UiKit.TEXT,true);
        TextView subtitle=UiKit.text(this,"정밀 깊이로 표면을 기록",11,UiKit.MUTED,false);
        titleBox.addView(title);titleBox.addView(subtitle);
        header.addView(titleBox,new LinearLayout.LayoutParams(0,-2,1));
        settings=UiKit.button(this,"설정",UiKit.PANEL,UiKit.TEXT,14);
        library=UiKit.button(this,"내 스캔",UiKit.PANEL,UiKit.TEXT,14);
        LinearLayout.LayoutParams small=new LinearLayout.LayoutParams(UiKit.dp(this,66),UiKit.dp(this,43));
        small.rightMargin=UiKit.dp(this,8);header.addView(settings,small);
        header.addView(library,new LinearLayout.LayoutParams(UiKit.dp(this,79),UiKit.dp(this,43)));
        overlay.addView(header);

        LinearLayout modeRow=new LinearLayout(this);modeRow.setPadding(UiKit.dp(this,4),UiKit.dp(this,4),UiKit.dp(this,4),UiKit.dp(this,4));
        modeRow.setBackground(UiKit.fill(this,UiKit.PANEL,18));
        modeButtons=new TextView[3];
        for(int i=0;i<3;i++){
            final ScanModel.Mode m=ScanModel.Mode.values()[i];
            TextView tab=UiKit.text(this,m.label,14,UiKit.MUTED,true);
            tab.setGravity(Gravity.CENTER);tab.setClickable(true);tab.setFocusable(true);
            tab.setContentDescription(m.label+" 스캔 모드 선택");
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,UiKit.dp(this,40),1);
            if(i>0)p.leftMargin=UiKit.dp(this,3);
            modeRow.addView(tab,p);modeButtons[i]=tab;
            tab.setOnClickListener(v->{
                if(model!=null)return;
                selectedMode=m;refreshState();
            });
        }
        LinearLayout.LayoutParams modesP=new LinearLayout.LayoutParams(-1,-2);modesP.topMargin=UiKit.dp(this,20);
        overlay.addView(modeRow,modesP);

        cameraStatus=UiKit.text(this,"카메라 준비 중",12,UiKit.TEXT,true);
        cameraStatus.setPadding(UiKit.dp(this,12),0,UiKit.dp(this,12),0);
        cameraStatus.setBackground(UiKit.fill(this,UiKit.PANEL,14));
        LinearLayout.LayoutParams statusP=new LinearLayout.LayoutParams(-2,UiKit.dp(this,34));statusP.topMargin=UiKit.dp(this,12);
        overlay.addView(cameraStatus,statusP);

        View space=new View(this);overlay.addView(space,new LinearLayout.LayoutParams(1,0,1));

        LinearLayout dock=new LinearLayout(this);dock.setOrientation(LinearLayout.VERTICAL);
        dock.setPadding(UiKit.dp(this,17),UiKit.dp(this,15),UiKit.dp(this,17),UiKit.dp(this,16));
        dock.setBackground(UiKit.fill(this,UiKit.PANEL,24));
        scanState=UiKit.text(this,"스캔 준비",18,UiKit.TEXT,true);
        dock.addView(scanState);
        helper=UiKit.text(this,"사물을 화면 중앙에 두고 0.5~2m에서 천천히 이동하세요",12,UiKit.MUTED,false);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=UiKit.dp(this,5);dock.addView(helper,hp);

        LinearLayout counters=new LinearLayout(this);counters.setGravity(Gravity.CENTER_VERTICAL);
        pointCount=UiKit.text(this,"포인트 0",13,UiKit.TEXT,true);
        faceCount=UiKit.text(this,"메시 0",13,UiKit.TEXT,true);
        counters.addView(pointCount,new LinearLayout.LayoutParams(0,UiKit.dp(this,40),1));
        counters.addView(faceCount,new LinearLayout.LayoutParams(0,UiKit.dp(this,40),1));
        dock.addView(counters);
        savingProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        savingProgress.setIndeterminate(true);savingProgress.setVisibility(View.GONE);
        dock.addView(savingProgress,new LinearLayout.LayoutParams(-1,UiKit.dp(this,3)));

        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
        capture=UiKit.button(this,"스캔 시작",UiKit.ACCENT,UiKit.INK,17);
        save=UiKit.button(this,"저장",UiKit.SURFACE,UiKit.TEXT,17);
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,UiKit.dp(this,58),2);
        a.rightMargin=UiKit.dp(this,9);actions.addView(capture,a);
        actions.addView(save,new LinearLayout.LayoutParams(0,UiKit.dp(this,58),1));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=UiKit.dp(this,6);dock.addView(actions,ap);
        reset=UiKit.button(this,"스캔 폐기",UiKit.PANEL,UiKit.MUTED,13);
        LinearLayout.LayoutParams resetP=new LinearLayout.LayoutParams(-2,UiKit.dp(this,39));resetP.gravity=Gravity.END;
        resetP.topMargin=UiKit.dp(this,4);dock.addView(reset,resetP);
        overlay.addView(dock);
        TextView note=UiKit.text(this,"기기에만 저장  ·  PLY / OBJ 내보내기",11,UiKit.MUTED,false);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,UiKit.dp(this,30));overlay.addView(note,np);
        setContentView(root);

        capture.setOnClickListener(v->toggleScan());save.setOnClickListener(v->saveCurrent());
        reset.setOnClickListener(v->confirmDiscard());settings.setOnClickListener(v->showSettings());
        library.setOnClickListener(v->openLibrary());
        refreshState();
    }
    private void refreshState(){
        if(scanState==null)return;
        int n=model==null?0:model.vertices(),faces=model==null?0:model.triangles();
        boolean hasData=n>0;
        pointCount.setText(String.format(Locale.KOREAN,"포인트 %,d",n));
        faceCount.setText(String.format(Locale.KOREAN,"메시 %,d",faces));
        cameraStatus.setText(lastReport.contains("ARCore:")?"카메라 연결 확인 필요":
            lastReport.contains("추적 대기")?"추적 대기 · 주변을 천천히 비춰주세요":
            lastReport.contains("추적 중")?(lastReport.contains("희소")?"추적 중 · 기본 포인트 모드":"추적 중 · 깊이 스캔 가능"):
            sessionResumed?"카메라 연결 중":"카메라 준비 중");
        cameraStatus.setTextColor(lastReport.contains("추적 대기")?UiKit.WARNING:UiKit.TEXT);
        if(exporting){
            scanState.setText("3D 파일 저장 중");helper.setText("저장이 완료될 때까지 이 화면을 유지하세요");
        }else if(isRecording){
            scanState.setText("공간을 스캔하고 있습니다");helper.setText("천천히 움직이며 대상의 여러 면을 촬영하세요");
        }else if(model!=null){
            scanState.setText("일시정지됨");helper.setText("이어 촬영하거나 현재 데이터를 저장하세요");
        }else{
            scanState.setText("스캔 준비");helper.setText("사물을 화면 중앙에 두고 0.5~2m에서 천천히 이동하세요");
        }
        capture.setText(model==null?"스캔 시작":isRecording?"일시정지":"스캔 계속");
        capture.setEnabled(!exporting && (sessionResumed||model!=null));
        capture.setAlpha(capture.isEnabled()?1f:0.55f);
        save.setEnabled(hasData&&!exporting);save.setAlpha(save.isEnabled()?1f:0.45f);
        reset.setVisibility(model!=null&&!exporting?View.VISIBLE:View.GONE);
        settings.setEnabled(model==null&&!exporting);settings.setAlpha(settings.isEnabled()?1f:0.5f);
        library.setEnabled(!exporting);
        savingProgress.setVisibility(exporting?View.VISIBLE:View.GONE);
        for(int i=0;i<modeButtons.length;i++){
            TextView b=modeButtons[i];boolean active=ScanModel.Mode.values()[i]==selectedMode;
            b.setTextColor(active?UiKit.INK:UiKit.TEXT);
            b.setBackground(UiKit.fill(this,active?UiKit.ACCENT:0x00000000,14));
            b.setAlpha(model==null?1f:0.75f);
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
                Config c=session.getConfig();boolean depth=session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
                if(depth)c.setDepthMode(Config.DepthMode.AUTOMATIC);
                c.setFocusMode(Config.FocusMode.AUTO);session.configure(c);renderer.configure(session,depth);
                lastReport=depth?"추적 대기 · Depth 3D":"추적 대기 · 희소 포인트";
            }
            session.resume();surface.onResume();sessionResumed=true;refreshState();
        }catch(Exception e){
            lastReport="ARCore: "+e.getClass().getSimpleName();refreshState();
            new AlertDialog.Builder(this).setTitle("카메라 시작 실패")
                .setMessage("Google Play AR 서비스와 카메라 권한을 확인해 주세요.\n\n"+e.getClass().getSimpleName())
                .setPositiveButton("확인",null).show();
        }
    }
    @Override protected void onPause(){
        if(isRecording){isRecording=false;renderer.stop();}
        if(sessionResumed){surface.onPause();if(session!=null)session.pause();sessionResumed=false;}
        refreshState();super.onPause();
    }
    @Override protected void onDestroy(){
        foreground=null;
        if(renderer!=null)renderer.stop();
        if(photoSet!=null&&!exporting){photoSet.discard();photoSet=null;}
        if(session!=null)session.close();disk.shutdown();super.onDestroy();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code==CAMERA_REQUEST){
            if(grants.length==0||grants[0]!=PackageManager.PERMISSION_GRANTED){
                new AlertDialog.Builder(this).setTitle("카메라 권한이 필요합니다")
                    .setMessage("3D 스캔을 시작하려면 카메라 접근을 허용해 주세요.")
                    .setPositiveButton("앱 설정",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:"+getPackageName()))))
                    .setNegativeButton("닫기",null).show();
            }else{surface.post(this::ensureSessionRunning);}
        }
        if(code==LOCATION_REQUEST && (grants.length==0||grants[0]!=PackageManager.PERMISSION_GRANTED)){
            includeGps=false;getPreferences(MODE_PRIVATE).edit().putBoolean("gps",false).apply();
            Toast.makeText(this,"위치 권한 없이 3D 스캔은 계속 사용할 수 있습니다",Toast.LENGTH_LONG).show();
        }
    }
    private void toggleScan(){
        if(exporting)return;
        if(!sessionResumed){Toast.makeText(this,"카메라 준비 상태를 확인해 주세요",Toast.LENGTH_SHORT).show();return;}
        if(model!=null&&model.limitReached()){
            Toast.makeText(this,"저장 용량에 도달했습니다. 현재 데이터를 저장해 주세요.",Toast.LENGTH_LONG).show();return;
        }
        if(isRecording){isRecording=false;renderer.stop();refreshState();return;}
        if(model==null){
            model=new ScanModel(selectedMode);
            if(includePhotos){
                try{photoSet=new PhotoDataset(getCacheDir());renderer.setPhotos(photoSet);}
                catch(Exception e){Toast.makeText(this,"사진 수집을 시작하지 못했습니다",Toast.LENGTH_SHORT).show();}
            }
        }
        isRecording=true;renderer.scan(model,selectedMode,true);refreshState();
    }
    private Location getLocation(){
        if(!includeGps)return null;
        if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return null;
        try{
            LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
            Location a=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location b=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            Location latest=a==null?b:b==null?a:a.getTime()>b.getTime()?a:b;
            return latest==null||Math.abs(System.currentTimeMillis()-latest.getTime())>600000?null:latest;
        }catch(Exception ignored){return null;}
    }
    private void saveCurrent(){
        if(exporting)return;
        if(model==null||model.vertices()==0){Toast.makeText(this,"저장할 포인트가 없습니다",Toast.LENGTH_SHORT).show();return;}
        if(model.frames()<12 || model.triangles()<300 || model.vertices()<1500){
            new AlertDialog.Builder(this).setTitle("3D 형상 데이터가 부족합니다")
                .setMessage("현재 "+model.frames()+"프레임, "+model.vertices()+"포인트, "+model.triangles()+"면입니다.\n\n이대로 저장하면 3D 형상이 희미하거나 비어 보일 수 있습니다. 여러 방향에서 조금 더 천천히 촬영하는 것이 좋습니다.")
                .setPositiveButton("계속 촬영",(d,w)->{if(!isRecording)toggleScan();})
                .setNegativeButton("그대로 저장",(d,w)->saveCurrentUnchecked())
                .show();
            return;
        }
        saveCurrentUnchecked();
    }
    private void saveCurrentUnchecked(){
        isRecording=false;renderer.stop();exporting=true;refreshState();
        final ScanModel.Snapshot snap=model.snapshot(); // stable capture on UI thread
        final Location location=getLocation();final PhotoDataset photos=photoSet;renderer.setPhotos(null);
        disk.submit(()->{
            try{
                File folder=ScanRepository.save(getApplicationContext(),snap,location);
                int count=0;String photoNote="";
                if(photos!=null){try{count=photos.finish(folder);}catch(Exception ex){photoNote=" (사진 일부 저장 실패)";}}
                final int photoCount=count;final String note=photoNote;
                runOnUiThread(()->{
                    exporting=false;model=null;photoSet=null;renderer.scan(null,ScanModel.Mode.OUTDOOR,false);
                    lastReport="추적 대기";refreshState();
                    new AlertDialog.Builder(this).setTitle("3D 스캔 저장 완료")
                        .setMessage(String.format(Locale.KOREAN,"%,d 포인트 · %,d 메시\n사진 %d장%s\n\n3D 모델을 바로 확인하시겠습니까?",
                            snap.vertices.size(),snap.triangles.size(),photoCount,note))
                        .setPositiveButton("3D 보기",(d,w)->openViewer(folder))
                        .setNeutralButton("보관함",(d,w)->openLibrary())
                        .setNegativeButton("닫기",null).show();
                });
            }catch(Exception ex){runOnUiThread(()->{
                exporting=false;renderer.setPhotos(photos);refreshState();
                new AlertDialog.Builder(this).setTitle("저장 실패")
                    .setMessage("기록은 유지되어 있습니다. 저장을 다시 시도해 주세요.\n\n"+ex.getMessage())
                    .setPositiveButton("확인",null).show();
            });}
        });
    }
    private void confirmDiscard(){
        if(exporting||model==null)return;
        new AlertDialog.Builder(this).setTitle("현재 스캔을 폐기할까요?")
            .setMessage("저장하지 않은 포인트와 사진은 복구할 수 없습니다.")
            .setPositiveButton("폐기",(d,w)->discard())
            .setNegativeButton("계속 스캔",null).show();
    }
    private void discard(){
        isRecording=false;renderer.stop();renderer.setPhotos(null);
        if(photoSet!=null){photoSet.discard();photoSet=null;}
        model=null;renderer.scan(null,ScanModel.Mode.OUTDOOR,false);refreshState();
    }
    private void showSettings(){
        if(model!=null||exporting)return;
        LinearLayout options=new LinearLayout(this);options.setOrientation(LinearLayout.VERTICAL);
        options.setPadding(UiKit.dp(this,20),UiKit.dp(this,8),UiKit.dp(this,20),0);
        CheckBox photos=new CheckBox(this);photos.setText("연속 사진도 저장 (외부 포토그래메트리용)");
        photos.setTextSize(14);photos.setChecked(includePhotos);
        CheckBox gps=new CheckBox(this);gps.setText("스캔 장소의 GPS 위치 기록 (선택)");
        gps.setTextSize(14);gps.setChecked(includeGps);
        options.addView(photos);options.addView(gps);
        TextView info=UiKit.text(this,"사진 수집은 저장 공간을 더 사용합니다. 이 앱은 사진만으로 정밀 3D 복원을 수행하지 않습니다. GPS는 3D 좌표의 측량 보정값이 아닙니다.",12,0xFF445465,false);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.topMargin=UiKit.dp(this,10);
        options.addView(info,ip);
        new AlertDialog.Builder(this).setTitle("스캔 옵션").setView(options)
            .setPositiveButton("적용",(d,w)->{
                includePhotos=photos.isChecked();includeGps=gps.isChecked();
                getPreferences(MODE_PRIVATE).edit().putBoolean("photos",includePhotos).putBoolean("gps",includeGps).apply();
                if(includeGps && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_REQUEST);
            }).setNeutralButton("촬영 도움말",(d,w)->showHelp())
            .setNegativeButton("취소",null).show();
    }
    private void showHelp(){
        new AlertDialog.Builder(this).setTitle("잘 스캔하는 방법")
            .setMessage("1. 작은 물체는 반드시 사물 모드를 선택하세요.\n\n2. 처음 5초는 천천히 좌우로 움직이며 추적을 안정화하세요.\n\n3. 대상에서 약 0.5~3m 거리를 유지하고, 주변을 천천히 돌며 겹치게 촬영하세요.\n\n4. 포인트와 메시 숫자가 늘어나는지 확인하세요. 품질이 부족하면 저장 전에 다시 이동하며 촬영하세요.\n\n5. 유리·광택·움직이는 물체·먼 건물은 정확도가 낮아질 수 있습니다.\n\n사진 저장 옵션은 외부 복원용 사진을 보관할 뿐, 자동 포토그래메트리를 수행하지 않습니다.")
            .setPositiveButton("확인",null).show();
    }
    private void openLibrary(){
        if(isRecording){isRecording=false;renderer.stop();refreshState();}
        startActivity(new Intent(this,ScanLibraryActivity.class));
    }
    private void openViewer(File folder){
        Intent i=new Intent(this,CloudViewerActivity.class);i.putExtra("scan_path",folder.getAbsolutePath());startActivity(i);
    }
    @Override public void onBackPressed(){
        if(model!=null&&!exporting){
            new AlertDialog.Builder(this).setTitle("스캔을 종료할까요?")
                .setMessage("현재 스캔을 저장하지 않았습니다. 앱을 종료하면 기록이 사라집니다.")
                .setPositiveButton("종료",(d,w)->{discard();finish();})
                .setNegativeButton("계속 촬영",null).show();return;
        }
        if(exporting){Toast.makeText(this,"저장 중에는 종료할 수 없습니다",Toast.LENGTH_SHORT).show();return;}
        super.onBackPressed();
    }
    private static final class Reticle extends View {
        private final Paint paint=new Paint(3);
        Reticle(Activity a){super(a);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setClickable(false);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float w=getWidth(),h=getHeight();
            float size=Math.min(w*0.61f,h*0.35f),x=w/2f,y=h*0.43f;
            float length=Math.min(25f,size*0.12f),half=size/2f;
            paint.setColor(0xA8FFFFFF);paint.setStrokeWidth(UiKit.dp(getContext(),2.1f));
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);
            for(int sx:new int[]{-1,1})for(int sy:new int[]{-1,1}){
                float xx=x+sx*half,yy=y+sy*half;
                c.drawLine(xx,yy,xx-sx*length,yy,paint);
                c.drawLine(xx,yy,xx,yy-sy*length,paint);
            }
            paint.setColor(0xD7FFFFFF);paint.setStyle(Paint.Style.FILL);c.drawCircle(x,y,UiKit.dp(getContext(),2.2f),paint);
        }
    }
}
