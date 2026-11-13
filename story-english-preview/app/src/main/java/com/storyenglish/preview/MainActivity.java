package com.storyenglish.preview;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.ImageView;
import android.widget.VideoView;
import android.widget.MediaController;
import android.net.Uri;
import android.content.Intent;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import android.media.AudioManager;
import android.media.AudioFocusRequest;
import android.media.AudioAttributes;

/** STORY ENGLISH 3.7.2 LAB: stand-alone offline feature prototype, not 3.7.0 source integration. */
public final class MainActivity extends Activity implements SpeechLab.Listener {
    private static final int BG=0xff0B1220,SURFACE=0xff182438,EDGE=0xff334155,
                             GOLD=0xffD6A85F,WHITE=0xffF8FAFC,MUTED=0xffCBD5E1,
                             ROSE=0xffE5A5B3,BLUE=0xff79B9FC;
    private static final int[] REP={1,2,3,5,10}, GAP={0,1,2,3,5,8};
    private static final String[][] EN={
        {"I really enjoy being close to you.","I feel the same way.","May I kiss you?","Yes, I'd like that.","If anything feels uncomfortable, please tell me.","I will. Thank you for asking."},
        {"I want to be honest about intimacy.","I'm listening. What feels comfortable for you?","I'd prefer to take things slowly.","That works for me. There's no pressure.","We can stop or change our minds at any time.","Exactly. Consent matters every time."},
        {"Can we talk about sexual health before we become intimate?","Of course. I think that's important.","I'd like us to discuss contraception and STI testing.","I appreciate you bringing it up.","We should make decisions we both feel good about.","Agreed. Let's talk openly and respectfully."},
        {"I want to be closer to you.","I feel the same. Is this okay?","Yes, but let us take our time.","Of course. We can stop whenever you want."}
    };
    private static final String[][] KO={
        {"당신과 가까이 있는 시간이 정말 좋아요.","저도 같은 마음이에요.","키스해도 될까요?","네, 저도 좋아요.","불편한 점이 생기면 꼭 말해 주세요.","그럴게요. 물어봐 줘서 고마워요."},
        {"친밀한 관계에 대해 솔직하게 이야기하고 싶어요.","듣고 있어요. 어떤 것이 편안한가요?","저는 서두르지 않고 천천히 진행하고 싶어요.","좋아요. 부담 갖지 않아도 돼요.","우리는 언제든 멈추거나 생각을 바꿀 수 있어요.","맞아요. 동의는 매번 중요하죠."},
        {"친밀한 관계를 갖기 전에 성 건강에 대해 이야기할까요?","물론이죠. 중요하다고 생각해요.","피임과 성매개감염병 검사에 대해 이야기하고 싶어요.","이야기를 꺼내 줘서 고마워요.","두 사람 모두 마음 편한 결정을 해야 해요.","동의해요. 솔직하고 서로 존중하며 이야기해요."},
        {"난 너와 좀 더 가까워지고 싶어.","나도 그래. 이렇게 해도 괜찮아?","응. 하지만 서두르지 말자.","물론이지. 원하면 언제든 멈출 수 있어."}
    };
    private static final String[] TITLES={"The First Kiss","An Intimate Conversation","Sexual Health & Boundaries","A Quiet Evening"};
    private static final String[] DESCRIPTIONS={
        "친밀한 감정 표현 · 키스 전 동의 요청 · 상대방 반응 존중",
        "친밀감에 대한 대화 · 속도 조절 · 언제든 동의 철회",
        "성 건강 · 피임 · STI 검사에 대한 자연스러운 영어 표현",
        "서로의 친밀감과 선택을 확인한 뒤 장면이 어두워지는 시네마틱 영상 학습"
    };
    private static final String[] FUNCTIONS={"데이트와 동의 표현","친밀감·경계 표현","성 건강·책임 있는 대화","친밀감과 동의 · 장면 이해"};
    private SharedPreferences prefs;
    private SpeechLab lab;
    private LinearLayout root,body;
    private TextView statusText,countdownText,scoreText;
    private ProgressBar recordLevel;
    private SpectrogramView spectra;
    private float[][] refImage;
    private SpectralAnalyzer.Result lastResult;
    private int scene=3,line=2,tab=0,repeatIndex=2,gapIndex=2;
    private boolean onStage=false;
    private boolean ttsRequested=false;
    private boolean cinemaOpen=false;
    private VideoView cinematicVideo;
    private boolean canShowAdult=false;
    private final NovelReaderPanel.Session novelSession=new NovelReaderPanel.Session();
    private String novelSentence=null;
    private static final int REQUEST_NOVEL_TXT=617;
    private int cinemaStage=0;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean audioFocused=false;
    private final int micRequest=108;
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        prefs=getSharedPreferences("adult_learning_preview",MODE_PRIVATE);
        repeatIndex=Math.max(0,Math.min(REP.length-1,prefs.getInt("repeats",2)));
        gapIndex=Math.max(0,Math.min(GAP.length-1,prefs.getInt("gap",2)));
        canShowAdult=prefs.getBoolean("confirmed19",false);
        cinemaStage=prefs.getInt("cinema_stage",0);
        if(saved!=null){
            scene=Math.max(0,Math.min(TITLES.length-1,saved.getInt("scene",scene)));
            line=Math.max(0,Math.min(EN[scene].length-1,saved.getInt("line",line)));
            tab=Math.max(0,Math.min(5,saved.getInt("tab",tab)));
        }
        audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
        if(audioManager!=null){
            AudioAttributes attributes=new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            focusRequest=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(change->{
                    if(change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                       change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){
                        runOnUiThread(()->{
                            pauseCinema();
                            if(lab!=null)lab.pause();
                            releaseAudioFocus();
                            if(statusText!=null)statusText.setText("다른 오디오로 전환되어 재생을 일시중지했습니다.");
                        });
                    }
                }).build();
        }
        lab=new SpeechLab(this,this);
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("scene",scene);out.putInt("line",line);out.putInt("tab",tab);super.onSaveInstanceState(out);}
    @Override protected void onPause(){super.onPause();if(cinematicVideo!=null)cinematicVideo.pause();if(lab!=null)lab.pause();releaseAudioFocus();ttsRequested=false;}
    @Override protected void onDestroy(){stopCinema();releaseAudioFocus();if(lab!=null)lab.shutdown();super.onDestroy();}
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private float font(int s){return prefs.getBoolean("large",false)?s*1.18f:s;}
    private GradientDrawable surface(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;}
    private TextView label(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(font(size));t.setLineSpacing(dp(3),1.10f);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void add(LinearLayout parent,View view,int margin){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(margin);parent.addView(view,p);}
    private void weighted(LinearLayout parent,View view,int margin){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=dp(margin);parent.addView(view,p);}
    private Button button(String title,Runnable task,boolean primary){Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextColor(primary?BG:WHITE);b.setTextSize(font(15));b.setMinHeight(dp(52));b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setBackground(surface(primary?GOLD:SURFACE));b.setOnClickListener(v->task.run());b.setContentDescription(title);return b;}
    private LinearLayout card(String title,String detail){LinearLayout c=column();c.setPadding(dp(17),dp(16),dp(17),dp(16));c.setBackground(surface(SURFACE));add(c,label(title,19,WHITE,true),0);if(detail!=null&&!detail.isEmpty())add(c,label(detail,14,MUTED,false),8);return c;}
    private void heading(String title,String subtitle){add(body,label(title,27,WHITE,true),12);add(body,label(subtitle,15,MUTED,false),7);}
    private void go(int screen){stopCinema();releaseAudioFocus();cinemaOpen=false;tab=screen;onStage=false;if(lab!=null)lab.stop();ttsRequested=false;refImage=null;lastResult=null;render();}
    private void changeScene(int index){scene=index;line=Math.min(2,EN[scene].length-1);novelSentence=null;go(1);}
    private void selectLine(int index){line=index;novelSentence=null;refImage=null;lastResult=null;tab=2;render();prepareReference();}
    private void prepareReference(){pauseCinema();if(lab!=null){ttsRequested=true;lab.setPhrase(novelSentence==null?EN[scene][line]:novelSentence,line);}}
    private boolean requestAudioFocus(){
        if(audioManager==null||focusRequest==null)return false;
        if(audioFocused)return true;
        audioFocused=audioManager.requestAudioFocus(focusRequest)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if(!audioFocused)new AlertDialog.Builder(this)
            .setMessage("현재 다른 오디오 앱을 사용 중입니다. 재생을 다시 시도해 주세요.")
            .setPositiveButton("확인",null).show();
        return audioFocused;
    }
    private void releaseAudioFocus(){
        if(audioManager!=null&&focusRequest!=null&&audioFocused){
            audioManager.abandonAudioFocusRequest(focusRequest);
            audioFocused=false;
        }
    }
    private void render(){stopCinema();root=column();root.setBackgroundColor(BG);setContentView(root);
        LinearLayout title=column();title.setPadding(dp(18),dp(12),dp(18),dp(10));add(title,label("STORY ENGLISH",15,GOLD,true),0);add(title,label("CINEMATIC AUDIO LAB · 3.7.4 · OFFLINE PREVIEW",11,MUTED,false),5);root.addView(title);
        ScrollView scroll=new ScrollView(this);body=column();body.setPadding(dp(18),0,dp(18),dp(32));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(!canShowAdult){ageGate();return;}
        if(tab==0)home();else if(tab==1)learning();else if(tab==2)shadowing();else if(tab==3)progress();else if(tab==4)settings();else novelStudy();
        LinearLayout tabs=row();tabs.setPadding(dp(5),dp(5),dp(5),dp(5));tabs.setBackgroundColor(SURFACE);
        String[] names={"오늘","장면","소설","쉐도잉","설정"};
        int[] targets={0,1,5,2,4};
        for(int i=0;i<5;i++){final int idx=targets[i];TextView t=label(names[i],13,idx==tab?GOLD:MUTED,true);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(50));t.setContentDescription(names[i]+" 탭");t.setOnClickListener(v->go(idx));tabs.addView(t,new LinearLayout.LayoutParams(0,dp(50),1));}
        root.addView(tabs,new LinearLayout.LayoutParams(-1,-2));
    }
    private void ageGate(){heading("성인 영어 학습","19세 이상 성인 대상 · MATURE 교육용 콘텐츠");
        add(body,card("포함되는 주제","데이트, 키스 전 동의, 친밀감, 경계 설정, 성 건강 및 피임에 관한 실제 대화 표현을 학습합니다. 노골적인 신체 노출이나 성행위 영상은 포함하지 않습니다."),18);
        add(body,card("접근 안내","아래 확인은 본인의 성인 여부를 스스로 선언하는 절차이며 법적인 연령 검증이 아닙니다. 이 검토판은 로그인·서버 연결이 없고 녹음 음성을 외부로 전송하지 않습니다."),14);
        add(body,button("저는 19세 이상이며 성인 학습 콘텐츠를 이용하겠습니다",()->{prefs.edit().putBoolean("confirmed19",true).apply();canShowAdult=true;render();},true),18);
        add(body,label("이 검토판은 법적 연령 확인과 권리 승인된 EXPLICIT 영상 제공 기능을 대체하지 않습니다.",13,MUTED,false),12);
    }

    /** Offline cinematic learning clip: original synthetic dialogue, original ambient pad,
     * animated-still editing, burned-in Korean/English captions; not filmed actors or erotica.
     * Never starts automatically and pauses on Activity backgrounding. */
    private void stopCinema(){
        if(cinematicVideo!=null){
            try{cinematicVideo.stopPlayback();}catch(Exception ignored){}
            cinematicVideo=null;
        }
    }
    private void pauseCinema(){
        if(cinematicVideo!=null){
            try{if(cinematicVideo.isPlaying())cinematicVideo.pause();}catch(Exception ignored){}
        }
    }
    private void cinematicLesson(){
        if(scene!=3)return;
        add(body,label("CINEMATIC LISTENING  ·  A Quiet Evening",15,ROSE,true),14);
        add(body,label("25초 · 영어/한국어 자막 · 합성 영어 대사 · 직접 제작한 배경음",13,MUTED,false),6);
        if(!cinemaOpen){
            add(body,button("▶ 영상 학습 열기",()->{
                if(lab!=null)lab.stop();
                cinemaOpen=true;render();
            },true),10);
            return;
        }
        VideoView video=new VideoView(this);
        cinematicVideo=video;
        video.setBackgroundColor(BG);
        video.setContentDescription("합의된 성인 간 친밀감을 키스 직전 장면과 페이드아웃으로 암시한 시네마틱 학습 영상. 영어 및 한국어 자막 포함.");
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,dp(222));vp.topMargin=dp(12);
        body.addView(video,vp);
        video.setVideoURI(Uri.parse("android.resource://"+getPackageName()+"/"+R.raw.scene_quiet_evening));
        MediaController controller=new MediaController(this);
        controller.setAnchorView(video);
        video.setMediaController(controller);
        video.setOnPreparedListener(mp->{mp.setLooping(false);});
        video.setOnCompletionListener(mp->{releaseAudioFocus();setCinemaStage(2);});
        video.setOnErrorListener((mp,what,extra)->{
            new AlertDialog.Builder(this).setMessage("영상을 재생할 수 없습니다. 저장 공간 또는 미디어 코덱을 확인해 주세요.")
                .setPositiveButton("확인",null).show();
            return true;
        });
        add(body,button("▶ 재생 / 일시정지",()->{
            if(video.isPlaying()){video.pause();releaseAudioFocus();}
            else if(requestAudioFocus()){if(lab!=null)lab.stop();video.start();}
        },true),10);
        add(body,button("↻ 처음부터 다시 보기",()->{
            if(requestAudioFocus()){if(lab!=null)lab.stop();video.seekTo(0);video.start();}
        },false),9);
        add(body,button("영상 닫기",()->{stopCinema();releaseAudioFocus();cinemaOpen=false;render();},false),8);
        add(body,label("학습 표현: I want to be closer to you. / Is this okay? / Let's take our time. / We can stop whenever you want.",14,MUTED,false),12);
        if(cinemaStage==1)add(body,label("영상 종료 후 의미 이해 단계가 활성화됩니다.",13,BLUE,true),9);
        add(body,label("이 영상은 정지 이미지에 화면 이동과 장면 전환을 더한 교육용 편집물입니다. 실제 성우·배우 촬영 영상이 아닙니다.",12,MUTED,false),8);
    }

    /** Read-only selection of licensed or learner-authored English TXT via the Android system picker. */
    private void importNovelTxt(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        startActivityForResult(intent,REQUEST_NOVEL_TXT);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQUEST_NOVEL_TXT || resultCode!=RESULT_OK || data==null || data.getData()==null)return;
        Uri uri=data.getData();
        try(InputStream in=getContentResolver().openInputStream(uri);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            if(in==null)throw new IllegalArgumentException("선택한 파일을 열 수 없습니다.");
            byte[] buf=new byte[4096];int n;
            while((n=in.read(buf))!=-1){
                if(bytes.size()+n>98304)throw new IllegalArgumentException("96KiB를 초과하는 파일입니다. 일부를 나누어 불러오세요.");
                bytes.write(buf,0,n);
            }
            String raw=StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
            if(!raw.isEmpty() && raw.charAt(0)=='\uFEFF')raw=raw.substring(1);
            String title="선택한 영문 원문";
            try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
                if(cursor!=null&&cursor.moveToFirst()){
                    int idx=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if(idx>=0){
                        String name=cursor.getString(idx);
                        if(name!=null&&!name.isEmpty())title=name.length()>90?name.substring(0,90):name;
                    }
                }
            }catch(Exception ignored){}
            novelSession.load(raw,title,false);
            novelSentence=null;go(5);
        }catch(Exception ex){
            new AlertDialog.Builder(this).setTitle("원문 가져오기 실패")
                .setMessage(ex instanceof IllegalArgumentException?ex.getMessage():"UTF-8 영어 TXT 파일을 확인해 주세요.")
                .setPositiveButton("확인",null).show();
        }
    }
    private void novelStudy(){
        NovelReaderPanel.Host host=new NovelReaderPanel.Host(){
            @Override public void importText(){importNovelTxt();}
            @Override public void listen(String phrase){
                if(requestAudioFocus()){
                    if(phrase.length()<=100)lab.playOrPrepare(phrase,0,REP[repeatIndex],GAP[gapIndex]);
                    else lab.playTextRepeats(phrase,REP[repeatIndex],GAP[gapIndex]);
                }
            }
            @Override public void shadow(String phrase){
                novelSentence=phrase;go(2);prepareReference();
            }
            @Override public void refresh(){render();}
        };
        NovelReaderPanel panel=new NovelReaderPanel(this,novelSession,host,prefs.getBoolean("large",false));
        panel.render(body);
        add(body,button("영화 장면으로 돌아가기 →",()->{novelSentence=null;go(1);},false),20);
    }
    private void setCinemaStage(int next){
        if(next<=cinemaStage)return;
        cinemaStage=next;
        prefs.edit().putInt("cinema_stage",cinemaStage).apply();
    }
    private void cinemaAnswer(boolean correct,String explanation,int stage){
        if(correct){
            setCinemaStage(stage);
            if(prefs.getBoolean("sounds",true))SoundFx.play(81);
        }
        new AlertDialog.Builder(this)
            .setTitle(correct?"원문에 맞습니다":"다시 생각해 보세요")
            .setMessage(explanation)
            .setPositiveButton("확인",(d,w)->{if(correct)render();}).show();
    }
    private void immersionCoach(){
        if(scene!=3)return;
        add(body,label("STORY INTERACTION · 이야기를 따라가는 3단계",19,WHITE,true),19);
        add(body,label("완료 "+Math.min(3,cinemaStage)+" / 3 · 대사 예측 → 장면 감상 → 의미 이해",13,BLUE,true),7);
        if(cinemaStage<1){
            add(body,card("01 · 대사 예측","상대방이 더 가까워지고 싶다고 말했을 때, 자연스럽게 동의를 확인하는 대사는?"),11);
            add(body,button("Is this okay?",()->cinemaAnswer(true,"상대방에게 괜찮은지 확인하는 질문입니다.",1),true),9);
            add(body,button("You don't have a choice.",()->cinemaAnswer(false,"상대방의 선택을 무시하므로 동의 표현이 아닙니다.",1),false),8);
            add(body,button("We must hurry.",()->cinemaAnswer(false,"상대방의 의사를 확인하는 표현은 아닙니다.",1),false),8);
        }else if(cinemaStage<2){
            add(body,card("02 · 장면 감상","짧은 영상을 보고 어떤 대사에서 상대방의 속도를 존중하는지 찾아보세요."),11);
            add(body,button("영상 플레이어 열기",()->{cinemaOpen=true;render();},true),9);
            add(body,label("영상이 끝날 때까지 자동 재생되지 않습니다. 언제든 일시정지할 수 있습니다.",13,MUTED,false),7);
        }else if(cinemaStage<3){
            add(body,card("03 · 표현 해석","“Let us take our time.”의 정확한 뉘앙스는?"),11);
            add(body,button("서두르지 말고 천천히 하자는 뜻",()->cinemaAnswer(true,"상대방의 속도를 존중하면서 진행하자는 표현입니다.",3),true),9);
            add(body,button("지금 즉시 결정하자는 뜻",()->cinemaAnswer(false,"take our time은 서두르지 않는다는 뜻입니다.",3),false),8);
        }else{
            add(body,card("✓ 장면 학습 완료","이야기의 의미를 이해했습니다. 직접 발화해 보면 표현을 더 오래 기억하는 데 도움이 됩니다."),12);
            add(body,button("동의 확인 표현 쉐도잉 연습 →",()->{novelSentence=null;line=1;go(2);prepareReference();},true),10);
            add(body,button("같은 주제의 오리지널 소설 읽기 →",()->{
                novelSession.load(NovelReaderPanel.SAMPLE,"After Midnight: An Honest Conversation",true);
                novelSentence=null;
                go(5);
            },false),10);
        }
    }
    private View sceneArtwork(){
        if(scene==0 || scene==3){
            ImageView image=new ImageView(this);
            image.setImageResource(R.drawable.romantic_scene);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setContentDescription("조명이 따뜻한 실내에서 두 성인이 손을 잡고 가까이 마주 보는 친밀한 장면");
            return image;
        }
        AdultSceneView graphic=new AdultSceneView(this);
        graphic.setVariant(scene);
        return graphic;
    }
    private void home(){heading("영어, 더 가까운 대화로.","성인이 실제로 마주하는 친밀한 상황을 듣고 말해 보세요.");
        add(body,sceneArtwork(),18);
        immersionCoach();
        cinematicLesson();
        add(body,label("MATURE · 비노골적 성인 상황 · 교육용",13,ROSE,true),12);
        add(body,card("오늘의 장면 · "+TITLES[scene],DESCRIPTIONS[scene]),10);
        add(body,button("장면 듣고 학습하기  →",()->go(1),true),14);
        add(body,card("성인소설로 이어지는 영어 학습","이야기를 읽고 실제 원문 표현을 분석한 뒤 듣기·쉐도잉으로 연결합니다."),18);
        add(body,button("📖 소설 원문으로 학습하기 →",()->go(5),true),12);
        add(body,label("성인 영어 주제 "+TITLES.length+"개",20,WHITE,true),22);
        for(int i=0;i<TITLES.length;i++){final int idx=i;
            add(body,button((i+1)+"  "+TITLES[i]+"  ·  "+FUNCTIONS[i],()->changeScene(idx),false),10);
        }
        add(body,label("A Quiet Evening에는 합성음성 기반 25초 시네마틱 장면이 포함됩니다. 전문 성우·배우 영상 및 기존 앱 전체 콘텐츠는 제공되지 않습니다.",13,MUTED,false),18);
    }
    private void learning(){heading(TITLES[scene],"상황을 보고, 실제 대화를 듣고, 필요한 문장을 선택하세요.");
        add(body,sceneArtwork(),12);
        immersionCoach();
        cinematicLesson();
        add(body,label("MATURE  ·  "+FUNCTIONS[scene],13,ROSE,true),10);
        add(body,button("대화 전체 듣기  ▶",()->{if(requestAudioFocus()){pauseCinema();lab.playDialogue(EN[scene]);}},true),12);
        add(body,label("표현을 눌러 쉐도잉·원음 비교로 이동합니다.",14,MUTED,false),16);
        for(int i=0;i<EN[scene].length;i++){
            final int n=i;LinearLayout c=card((i%2==0?"A:  ":"B:  ")+EN[scene][i],prefs.getBoolean("translation",true)?KO[scene][i]:"");
            add(c,button("이 문장 집중 듣기 · 쉐도잉 →",()->selectLine(n),false),10);
            add(body,c,10);
        }
    }
    private void adjust(boolean isRepeat,int direction){if(isRepeat){repeatIndex=(repeatIndex+direction+REP.length)%REP.length;prefs.edit().putInt("repeats",repeatIndex).apply();}
        else{gapIndex=(gapIndex+direction+GAP.length)%GAP.length;prefs.edit().putInt("gap",gapIndex).apply();}render();}
    private void controlRow(String title,String value,boolean isRepeat){LinearLayout r=row();r.setPadding(dp(12),dp(8),dp(12),dp(8));r.setBackground(surface(SURFACE));
        TextView name=label(title+"  "+value,16,WHITE,true);r.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        Button minus=button("−",()->adjust(isRepeat,-1),false);minus.setMinWidth(dp(52));r.addView(minus,new LinearLayout.LayoutParams(dp(52),dp(52)));
        Button plus=button("+",()->adjust(isRepeat,1),false);plus.setMinWidth(dp(52));r.addView(plus,new LinearLayout.LayoutParams(dp(52),dp(52)));
        add(body,r,10);
    }
    private void shadowing(){heading("쉐도잉 스튜디오","반복 듣기 · 자동 녹음 · 실제 스펙트로그램 비교");
        add(body,label("THE SCENE  ·  "+TITLES[scene],13,ROSE,true),8);
        add(body,card(novelSentence==null?"따라 말할 문장":"성인소설 원문 따라 말하기",(novelSentence==null?EN[scene][line]:novelSentence)+(novelSentence==null && prefs.getBoolean("translation",true)?"\n"+KO[scene][line]:"")),10);
        add(body,button(novelSentence==null?"다른 장면 문장 선택":"소설 읽기로 돌아가기",()->go(novelSentence==null?1:5),false),10);
        controlRow("반복 횟수",REP[repeatIndex]+"회",true);
        controlRow("반복 간격",GAP[gapIndex]+"초",false);
        add(body,button("기준 음성 반복 듣기  ▶",()->{if(requestAudioFocus()){pauseCinema();lab.playOrPrepare(novelSentence==null?EN[scene][line]:novelSentence,line,REP[repeatIndex],GAP[gapIndex]);}},true),14);
        add(body,button("재생 중지",()->{lab.stop();releaseAudioFocus();ttsRequested=false;refImage=null;lastResult=null;render();},false),9);
        add(body,label("기준 음성은 현재 기기의 TTS 합성음입니다. 사람이 연기한 원음이 아닙니다.",13,MUTED,false),8);
        add(body,label("내 목소리로 따라 말하기",20,WHITE,true),20);
        add(body,label("마이크 허용 → 3 · 2 · 1 → 자동 녹음. 말을 마친 뒤 약 1.3초 정적이면 자동 종료됩니다.",14,MUTED,false),8);
        countdownText=label("●  READY",37,ROSE,true);countdownText.setGravity(Gravity.CENTER);countdownText.setMinHeight(dp(76));add(body,countdownText,10);
        recordLevel=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);recordLevel.setMax(100);recordLevel.setProgress(0);add(body,recordLevel,8);
        add(body,button("3·2·1 카운트다운 후 자동 녹음",this::requestShadowing,true),12);
        add(body,button("녹음 취소",()->{lab.stop();releaseAudioFocus();ttsRequested=false;refImage=null;lastResult=null;render();},false),9);
        scoreText=label("",19,GOLD,true);add(body,scoreText,14);
        spectra=new SpectrogramView(this);spectra.setSpectra(refImage,lastResult==null?null:lastResult.learnerImage);add(body,spectra,12);
        add(body,label("그래프: 가로축 시간, 세로축 주파수 110Hz~6kHz. 음향 유사도는 발음·단어 인식 정확도가 아닙니다.",13,MUTED,false),9);
        if(lastResult!=null)showComparison(lastResult);
        statusText=label(lab.hasReference()?"기준 음성 준비됨":ttsRequested?"기준 음성 합성 중…":"기준 음성 준비 버튼을 누르세요.",14,BLUE,false);add(body,statusText,14);
        if(!lab.hasReference()) add(body,button("기준 음성 생성 / 다시 시도",this::prepareReference,false),10);
    }
    private void showComparison(SpectralAnalyzer.Result result){if(scoreText!=null){
        if(result.similarity<0)scoreText.setText(result.note);
        else scoreText.setText("음향 유사도 "+result.similarity+" / 100  ·  "+(result.similarity>=78?"높음":result.similarity>=55?"중간":"다시 비교"));}}
    private void requestShadowing(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},micRequest);return;
        }startShadowing();
    }
    private void startShadowing(){pauseCinema();releaseAudioFocus();if(!lab.hasReference()){if(!ttsRequested)prepareReference();status("기준 음성 준비 후 녹음할 수 있습니다.");return;}
        onStage=true;lab.beginCountdown();}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==micRequest){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED&&tab==2)startShadowing();
            else new AlertDialog.Builder(this).setMessage("마이크 권한이 허용되지 않아 녹음을 시작할 수 없습니다. 권한 없이도 듣기 기능은 이용할 수 있습니다.").setPositiveButton("확인",null).show();}
    }
    @Override public void status(String text){if(statusText!=null&&tab==2)statusText.setText(text);}
    @Override public void playbackFinished(){releaseAudioFocus();}
    @Override public void countdown(int second){if(tab!=2||countdownText==null)return;
        if(second>0){countdownText.setText(String.valueOf(second));countdownText.setTextColor(GOLD);
            countdownText.announceForAccessibility("녹음 시작까지 "+second+"초");
            if(!prefs.getBoolean("reduce_motion",false)){countdownText.setScaleX(1.35f);countdownText.setScaleY(1.35f);countdownText.animate().scaleX(1).scaleY(1).setDuration(380).start();}
        }else if(second==0){countdownText.setText("●  RECORDING");countdownText.setTextColor(ROSE);countdownText.announceForAccessibility("녹음 시작");}
        else {countdownText.setText("✓  FINISHED");countdownText.setTextColor(BLUE);}
    }
    @Override public void meter(float value){if(tab==2&&recordLevel!=null)recordLevel.setProgress((int)(100*value));}
    @Override public void reference(float[][] image){refImage=image;if(tab==2&&spectra!=null)spectra.setSpectra(refImage,lastResult==null?null:lastResult.learnerImage);}
    @Override public void comparison(SpectralAnalyzer.Result result){lastResult=result;
        if(tab==2){if(spectra!=null)spectra.setSpectra(result.referenceImage,result.learnerImage);showComparison(result);}
        if(result.similarity>=0){int best=prefs.getInt("best",-1);prefs.edit().putInt("attempts",prefs.getInt("attempts",0)+1).putInt("best",Math.max(best,result.similarity)).apply();
            if(prefs.getBoolean("sounds",true))SoundFx.play(result.similarity);
        }
    }
    private void progress(){heading("나의 연습 기록","기기 안에 저장되는 시도 횟수와 음향 유사도입니다.");
        int attempts=prefs.getInt("attempts",0),best=prefs.getInt("best",-1);
        add(body,card("쉐도잉 횟수",attempts+"회"),14);
        add(body,card("최고 음향 유사도",best<0?"아직 기록 없음":best+" / 100"),12);
        add(body,label("다른 목소리·속도·장치·배경 소음에 따라 비교값이 변합니다. 학습 숙련도나 CEFR 등급을 판정하지 않습니다.",13,MUTED,false),14);
        add(body,button("쉐도잉 시작하기 →",()->{novelSentence=null;go(2);prepareReference();},true),18);
    }
    private void setting(String key,String labelText,boolean defaultValue){boolean value=prefs.getBoolean(key,defaultValue);
        add(body,button(labelText+"  ·  "+(value?"켜짐":"꺼짐"),()->{prefs.edit().putBoolean(key,!value).apply();render();},false),12);
    }
    private void settings(){heading("학습 환경","원하는 속도와 화면·효과음을 직접 조절하세요.");
        setting("translation","한국어 번역",true);
        setting("sounds","유사도별 효과음",true);
        setting("large","글자 확대",false);
        setting("reduce_motion","시각적 움직임 최소화",false);
        add(body,button("학습 기록 보기",()->go(3),false),12);
        add(body,button("성인소설 원문 읽기 →",()->go(5),false),10);
        add(body,card("개인정보","마이크로 녹음한 실제 PCM은 메모리에서 분석한 뒤 폐기하며 파일로 저장하거나 업로드하지 않습니다. 기기에는 반복 설정, 완료 횟수, 최고 음향 유사도만 보관합니다."),18);
        add(body,button("모든 학습 기록 삭제",()->new AlertDialog.Builder(this).setTitle("기록을 삭제할까요?").setMessage("앱의 학습 기록, 성인 자기확인, 설정이 지워집니다.")
            .setNegativeButton("취소",null).setPositiveButton("삭제",(d,w)->{lab.stop();prefs.edit().clear().apply();canShowAdult=false;refImage=null;lastResult=null;render();}).show(),false),18);
        add(body,label("이 APK는 독립 오프라인 기능 검토판입니다. EXPLICIT 영상/음향은 법적 권리 확인·성인 접근 제어·사람 검토 후 별도로 제공해야 합니다.",13,MUTED,false),20);
    }
}