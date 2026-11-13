package kr.openscan.scan;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.OutputStream;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Human-readable scan projects rather than raw file-name selection dialogs. */
public final class ScanLibraryActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private LinearLayout cards;
    private TextView subtitle;
    private File pendingExport;
    private static final int EXPORT_DOCUMENT_REQUEST=403;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(UiKit.INK);getWindow().setNavigationBarColor(UiKit.INK);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiKit.INK);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(UiKit.dp(this,20),UiKit.dp(this,20),UiKit.dp(this,20),UiKit.dp(this,8));
        Button back=UiKit.button(this,"‹",UiKit.SURFACE,UiKit.TEXT,12);
        back.setTextSize(26);UiKit.accessibility(back,"스캔 화면으로 돌아가기");
        header.addView(back,new LinearLayout.LayoutParams(UiKit.dp(this,48),UiKit.dp(this,48)));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        TextView title=UiKit.text(this,"내 스캔",23,UiKit.TEXT,true);
        subtitle=UiKit.text(this,"저장된 프로젝트",12,UiKit.MUTED,false);
        labels.addView(title);labels.addView(subtitle);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.leftMargin=UiKit.dp(this,14);
        header.addView(labels,tp);
        Button newScan=UiKit.button(this,"+ 스캔",UiKit.ACCENT,UiKit.INK,14);
        header.addView(newScan,new LinearLayout.LayoutParams(UiKit.dp(this,83),UiKit.dp(this,44)));
        root.addView(header);
        Button importPly=UiKit.button(this,"기존 PLY 파일 가져오기",UiKit.SURFACE,UiKit.TEXT,14);
        LinearLayout.LayoutParams importLayout=new LinearLayout.LayoutParams(-1,UiKit.dp(this,46));
        importLayout.leftMargin=UiKit.dp(this,16);importLayout.rightMargin=UiKit.dp(this,16);
        importLayout.bottomMargin=UiKit.dp(this,8);
        root.addView(importPly,importLayout);
        importPly.setOnClickListener(v->{
            Intent open=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            open.setType("*/*");open.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(open,402);
        });
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        cards=new LinearLayout(this);cards.setOrientation(LinearLayout.VERTICAL);
        cards.setPadding(UiKit.dp(this,16),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,30));
        scroll.addView(cards,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        back.setOnClickListener(v->finish());newScan.setOnClickListener(v->finish());
    }
    @Override protected void onResume(){
        super.onResume();refresh();
        String path=getIntent().getStringExtra("export_zip_path");
        getIntent().removeExtra("export_zip_path");
        if(path!=null){
            File folder=new File(path);
            if(allowedFolder(folder)){exportArchive(folder);}
        }
    }
    private boolean allowedFolder(File folder){
        try{
            String root=ScanRepository.root(this).getCanonicalPath()+File.separator;
            return folder.getCanonicalPath().startsWith(root) && folder.isDirectory();
        }catch(Exception ignored){return false;}
    }
    private void startDocumentExport(File file,String mime){
        if(!file.isFile()){Toast.makeText(this,"저장할 파일이 없습니다",Toast.LENGTH_SHORT).show();return;}
        pendingExport=file;
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType(mime);
        save.putExtra(Intent.EXTRA_TITLE,file.getName());
        try{startActivityForResult(save,EXPORT_DOCUMENT_REQUEST);}
        catch(Exception e){pendingExport=null;new AlertDialog.Builder(this).setTitle("저장 위치 선택 불가")
            .setMessage(e.getMessage()).setPositiveButton("확인",null).show();}
    }
    private void exportArchive(File folder){
        Toast.makeText(this,"프로젝트 ZIP 준비 중",Toast.LENGTH_SHORT).show();
        io.execute(()->{
            try{
                File zip=ScanRepository.archive(folder);
                runOnUiThread(()->startDocumentExport(zip,"application/zip"));
            }catch(Exception e){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("ZIP 생성 실패")
                .setMessage(e.getMessage()).setPositiveButton("확인",null).show());}
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==EXPORT_DOCUMENT_REQUEST){
            File source=pendingExport;pendingExport=null;
            if(result!=RESULT_OK||data==null||data.getData()==null||source==null)return;
            Uri output=data.getData();
            io.execute(()->{
                try(OutputStream out=getContentResolver().openOutputStream(output,"w");
                    FileInputStream in=new FileInputStream(source)){
                    if(out==null)throw new java.io.IOException("선택한 저장 위치를 열 수 없습니다");
                    byte[] buffer=new byte[64*1024];int n;
                    while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                    out.flush();
                    runOnUiThread(()->new AlertDialog.Builder(this).setTitle("파일 내보내기 완료")
                        .setMessage(source.getName()+" 파일을 선택한 폴더에 저장했습니다.\n\n파일 앱의 해당 폴더에서 확인할 수 있습니다.")
                        .setPositiveButton("확인",null).show());
                }catch(Exception e){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("파일 내보내기 실패")
                    .setMessage("앱 내부 프로젝트는 유지됩니다.\n\n"+e.getMessage())
                    .setPositiveButton("확인",null).show());}
            });
            return;
        }
        if(request!=402 || result!=RESULT_OK || data==null || data.getData()==null)return;
        Uri uri=data.getData();
        Toast.makeText(this,"PLY 검사 및 가져오기 중…",Toast.LENGTH_SHORT).show();
        io.execute(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new java.io.IOException("선택한 파일을 열 수 없습니다");
                File folder=ScanRepository.importPly(getApplicationContext(),in);
                runOnUiThread(()->{
                    refresh();Toast.makeText(this,"PLY 파일을 가져왔습니다",Toast.LENGTH_SHORT).show();show3D(folder);
                });
            }catch(Exception ex){
                runOnUiThread(()->new AlertDialog.Builder(this).setTitle("PLY 가져오기 실패")
                    .setMessage("이 앱이 만든 binary little endian PLY 파일을 선택하세요.\n\n"+ex.getMessage())
                    .setPositiveButton("확인",null).show());
            }
        });
    }
    private void refresh(){
        cards.removeAllViews();File[] scans=ScanRepository.list(this);
        subtitle.setText(String.format(Locale.KOREAN,"저장된 스캔 %d개 · 기기 내 보관",scans.length));
        if(scans.length==0){
            LinearLayout empty=new LinearLayout(this);empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);empty.setPadding(UiKit.dp(this,20),UiKit.dp(this,88),UiKit.dp(this,20),0);
            TextView icon=UiKit.text(this,"▧",56,UiKit.ACCENT,true);icon.setGravity(Gravity.CENTER);
            TextView title=UiKit.text(this,"아직 저장한 스캔이 없습니다",19,UiKit.TEXT,true);title.setGravity(Gravity.CENTER);
            TextView desc=UiKit.text(this,"사물이나 공간을 천천히 촬영한 뒤\n저장을 누르면 이곳에 모입니다.",14,UiKit.MUTED,false);
            desc.setGravity(Gravity.CENTER);desc.setPadding(0,UiKit.dp(this,14),0,0);
            empty.addView(icon);empty.addView(title);empty.addView(desc);
            cards.addView(empty,new LinearLayout.LayoutParams(-1,-2));return;
        }
        for(File folder:scans)addCard(folder);
    }
    private void addCard(File folder){
        JSONObject meta=readMetadata(folder);
        String date=new SimpleDateFormat("yyyy.MM.dd   HH:mm",Locale.KOREA).format(new Date(folder.lastModified()));
        String label=meta.optString("mode","OUTDOOR");
        String mode="OBJECT".equals(label)?"사물":"ROOM".equals(label)?"실내":"야외";
        long points=meta.optLong("points",0),faces=meta.optLong("triangles",0);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(UiKit.dp(this,17),UiKit.dp(this,16),UiKit.dp(this,17),UiKit.dp(this,13));
        card.setBackground(UiKit.line(this,UiKit.SURFACE,20,0xFF2B4251));
        TextView title=UiKit.text(this,"3D 스캔  ·  "+mode,18,UiKit.TEXT,true);
        card.addView(title);
        TextView info=UiKit.text(this,date,12,UiKit.MUTED,false);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.topMargin=UiKit.dp(this,5);
        card.addView(info,ip);
        TextView stats=UiKit.text(this,String.format(Locale.KOREAN,"포인트 %,d   |   메시 %,d",points,faces),13,UiKit.ACCENT,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=UiKit.dp(this,10);
        card.addView(stats,sp);
        LinearLayout actions=new LinearLayout(this);
        Button view=UiKit.button(this,"3D 보기",UiKit.ACCENT,UiKit.INK,13);
        Button share=UiKit.button(this,"내보내기 / 관리",0xFF233C4C,UiKit.TEXT,13);
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,UiKit.dp(this,47),1);a.rightMargin=UiKit.dp(this,8);
        actions.addView(view,a);actions.addView(share,new LinearLayout.LayoutParams(0,UiKit.dp(this,47),1));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=UiKit.dp(this,15);card.addView(actions,bp);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=UiKit.dp(this,12);
        cards.addView(card,cp);
        view.setOnClickListener(v->show3D(folder));share.setOnClickListener(v->showActions(folder));
    }
    private JSONObject readMetadata(File folder){
        try{
            File f=new File(folder,"metadata.json");
            if(!f.isFile()||f.length()>65536)return new JSONObject();
            return new JSONObject(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));
        }catch(Exception ignored){return new JSONObject();}
    }
    private void show3D(File folder){
        Intent i=new Intent(this,CloudViewerActivity.class);
        i.putExtra("scan_path",folder.getAbsolutePath());startActivity(i);
    }
    private void showActions(File folder){
        new AlertDialog.Builder(this).setTitle("스캔 파일 관리")
            .setItems(new String[]{"다운로드 폴더 등에 ZIP 저장","PLY 파일로 저장","OBJ 파일로 저장", "PLY 점군 공유","OBJ 메시 공유","전체 ZIP 프로젝트 공유","GPS 메타데이터 공유","스캔 삭제"},(d,which)->{
                if(which==0)exportArchive(folder);
                if(which==1)startDocumentExport(new File(folder,"model.ply"),"application/octet-stream");
                if(which==2)startDocumentExport(new File(folder,"model.obj"),"text/plain");
                if(which==3)ScanShare.share(this,new File(folder,"model.ply"));
                if(which==4)ScanShare.share(this,new File(folder,"model.obj"));
                if(which==6)ScanShare.share(this,new File(folder,"metadata.json"));
                if(which==5){
                    Toast.makeText(this,"ZIP 파일 준비 중…",Toast.LENGTH_SHORT).show();
                    io.execute(()->{
                        try{File zip=ScanRepository.archive(folder);runOnUiThread(()->ScanShare.share(this,zip));}
                        catch(Exception e){runOnUiThread(()->Toast.makeText(this,"ZIP 생성 실패: "+e.getMessage(),Toast.LENGTH_LONG).show());}
                    });
                }
                if(which==7)confirmDelete(folder);
            }).setNegativeButton("닫기",null).show();
    }
    private void confirmDelete(File folder){
        new AlertDialog.Builder(this).setTitle("저장된 스캔을 삭제할까요?")
            .setMessage("PLY, OBJ, 사진, GPS 정보가 모두 삭제되며 복구할 수 없습니다.")
            .setPositiveButton("삭제",(d,w)->{
                io.execute(()->{
                    boolean ok=deleteRecursively(folder);
                    runOnUiThread(()->{
                        Toast.makeText(this,ok?"스캔을 삭제했습니다":"일부 파일을 삭제하지 못했습니다",Toast.LENGTH_SHORT).show();
                        refresh();
                    });
                });
            }).setNegativeButton("취소",null).show();
    }
    private boolean deleteRecursively(File path){
        if(path.isDirectory()){
            File[] kids=path.listFiles();if(kids!=null)for(File f:kids)if(!deleteRecursively(f))return false;
        }
        return path.delete();
    }
    @Override protected void onDestroy(){io.shutdown();super.onDestroy();}
}
