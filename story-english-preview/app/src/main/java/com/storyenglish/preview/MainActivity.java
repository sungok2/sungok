package com.storyenglish.preview;
import android.app.*;import android.os.*;import android.content.*;import android.graphics.*;import android.graphics.drawable.*;import android.speech.tts.*;import android.view.*;import android.widget.*;import java.util.*;

public final class MainActivity extends Activity {
  // Standalone offline UX review, NOT an update of com.storyenglish.app.
  final int NAVY=Color.rgb(11,18,32), SURFACE=Color.rgb(23,37,58), GOLD=Color.rgb(214,168,95), WHITE=Color.WHITE, MUTED=Color.rgb(203,213,225);
  final String[] english={"Thanks for coming out tonight.","I've enjoyed getting to know you.","I feel the same way.","I'd like to take things slowly.","Of course. I respect that.","Thank you for understanding.","We can talk again when we're both ready."};
  final String[] korean={"오늘 저녁에 와 줘서 고마워요.","당신을 알아가는 시간이 즐거웠어요.","저도 같은 마음이에요.","저는 천천히 관계를 발전시키고 싶어요.","물론이죠. 그 마음을 존중해요.","이해해 줘서 고마워요.","서로 준비가 되면 다시 이야기해요."};
  final String[] expressions={"take things slowly","get to know someone","I respect that"};
  final String[] meanings={"서두르지 않고 천천히 진행하다","누군가를 알아 가다","그 뜻을 존중해요"};
  SharedPreferences pref; TextToSpeech tts; boolean voiceReady=false; int page=0,reviewIndex=0; boolean revealed=false;
  LinearLayout root,body;
  int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
  int sp(int n){return pref.getBoolean("big",false)?Math.round(n*1.16f):n;}
  GradientDrawable shape(int c){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(14));return d;}
  TextView text(String s,int size,int color,boolean heavy){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp(size));t.setTextColor(color);t.setLineSpacing(dp(3),1.09f);if(heavy)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
  LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
  void place(LinearLayout l,View v,int gap){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(gap);l.addView(v,p);}
  Button action(String s,Runnable task,boolean strong){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(sp(15));b.setTextColor(strong?NAVY:WHITE);b.setBackground(shape(strong?GOLD:SURFACE));b.setMinHeight(dp(52));b.setContentDescription(s);b.setOnClickListener(v->task.run());return b;}
  void section(String title,String description){place(body,text(title,29,WHITE,true),12);place(body,text(description,15,MUTED,false),8);}
  void box(String label,String detail){LinearLayout c=column();c.setPadding(dp(18),dp(16),dp(18),dp(16));c.setBackground(shape(SURFACE));place(c,text(label,19,WHITE,true),0);place(c,text(detail,15,MUTED,false),8);place(body,c,14);}
  void navigate(int n){if(tts!=null)tts.stop();page=n;draw();}
  boolean done(String key){return pref.getBoolean(key,false);}
  int score(){return (done("listened")?1:0)+(done("quiz")?1:0)+(done("writing")?1:0);}
  @Override public void onCreate(Bundle b){super.onCreate(b);pref=getSharedPreferences("story_preview",MODE_PRIVATE);if(b!=null)page=b.getInt("page",0);
    tts=new TextToSpeech(this,code->{if(code==TextToSpeech.SUCCESS&&tts!=null){int r=tts.setLanguage(Locale.US);voiceReady=(r!=TextToSpeech.LANG_MISSING_DATA&&r!=TextToSpeech.LANG_NOT_SUPPORTED);}});
    tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
      public void onStart(String id){}public void onError(String id){}public void onDone(String id){if("whole".equals(id))runOnUiThread(()->{pref.edit().putBoolean("listened",true).apply();if(page==1)draw();});}});
    draw();
  }
  @Override protected void onSaveInstanceState(Bundle out){out.putInt("page",page);super.onSaveInstanceState(out);}
  @Override protected void onPause(){super.onPause();if(tts!=null)tts.stop();}
  @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();tts=null;}super.onDestroy();}
  void speak(String s,String id){if(!voiceReady||tts==null){new AlertDialog.Builder(this).setMessage("영어 TTS 음성을 사용할 수 없습니다. 기기의 음성 설정을 확인해 주세요.").setPositiveButton("확인",null).show();return;}tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,id);}
  void draw(){root=column();root.setBackgroundColor(NAVY);setContentView(root);
    LinearLayout head=column();head.setPadding(dp(18),dp(12),dp(18),dp(10));place(head,text("STORY ENGLISH",15,GOLD,true),0);place(head,text("3.7.1  ·  OFFLINE UX PREVIEW",12,MUTED,false),4);root.addView(head);
    ScrollView scroll=new ScrollView(this);body=column();body.setPadding(dp(18),0,dp(18),dp(28));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    if(page==0)home();else if(page==1)lesson();else if(page==2)review();else if(page==3)progress();else settings();
    LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(0);tabs.setPadding(dp(5),dp(4),dp(5),dp(4));tabs.setBackgroundColor(SURFACE);
    String[] names={"오늘","학습","복습","기록","설정"};
    for(int i=0;i<5;i++){final int j=i;TextView t=text(names[i],13,i==page?GOLD:MUTED,true);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(52));t.setContentDescription(names[i]+" 탭");t.setOnClickListener(v->navigate(j));tabs.addView(t,new LinearLayout.LayoutParams(0,dp(52),1));}
    root.addView(tabs);
  }
  void home(){section("오늘, 한 장면부터.","실제 학습 흐름을 검토하는 독립 오프라인 시연판입니다.");
    box("THE BOUNDARY  ·  B1 · MATURE","서로의 경계를 존중하는 성인 대화. 비노골적 자체 제작 시연 콘텐츠이며 원본 시리즈의 에피소드는 아닙니다.");
    place(body,action(score()==3?"학습 결과 보기":"이어서 학습하기 →",()->navigate(score()==3?3:1),true),16);
    box("학습 진행",score()+" / 3 활동 완료\n영어 듣기 · 의미 확인 · 문장 작성");
    place(body,text("이 버전은 기존 앱을 대체하지 않습니다. 운영 AI·계정·영상 서비스가 연결되지 않습니다.",14,MUTED,false),16);
  }
  void lesson(){section("The Boundary","듣기 → 표현 이해 → 직접 써보기");
    box("01  듣기","각 문장을 누르면 기기 기본 영어 음성으로 들을 수 있습니다.");
    for(int i=0;i<english.length;i++){final int index=i;LinearLayout line=column();line.setPadding(dp(14),dp(12),dp(14),dp(12));line.setBackground(shape(SURFACE));place(line,text((i+1)+". "+english[i],17,WHITE,true),0);
      if(pref.getBoolean("ko",true))place(line,text(korean[i],14,MUTED,false),6);
      place(line,action("문장 듣기",()->speak(english[index],"line"+index),false),8);place(body,line,10);}
    place(body,action("전체 대화 듣기",()->speak(String.join(" ",english),"whole"),true),16);
    place(body,text(done("listened")?"✓ 전체 듣기 완료":"전체 음성 재생이 끝나면 완료 처리됩니다.",14,MUTED,false),8);
    box("02  이해 확인","I would like to take things slowly. 의 의미는?");
    place(body,action("A. 바로 결정을 내리고 싶어요",()->answer(false),false),8);
    place(body,action("B. 천천히 관계를 발전시키고 싶어요",()->answer(true),false),8);
    place(body,action("C. 대화를 그만두고 싶어요",()->answer(false),false),8);
    if(done("quiz"))place(body,text("✓ 이해 문제 완료",14,GOLD,true),8);
    box("03  직접 영어 문장 작성","서로의 결정을 존중하는 영어 문장을 작성하세요. 입력한 문장 자체는 저장하지 않습니다.");
    EditText input=new EditText(this);input.setSingleLine(false);input.setMinLines(3);input.setTextSize(sp(17));input.setTextColor(WHITE);input.setHintTextColor(MUTED);input.setHint("I understand and respect your decision.");place(body,input,8);
    place(body,action("작성 완료 표시",()->{if(input.getText().toString().trim().length()<10){new AlertDialog.Builder(this).setMessage("영어 문장 10자 이상 작성해 주세요.").setPositiveButton("확인",null).show();}else{pref.edit().putBoolean("writing",true).apply();input.setText("");draw();}},true),10);
    if(done("writing"))place(body,text("✓ 쓰기 단계 완료",14,GOLD,true),8);
    place(body,action("복습으로 이동 →",()->navigate(2),false),18);
  }
  void answer(boolean correct){new AlertDialog.Builder(this).setTitle(correct?"정답입니다":"다시 생각해 보세요").setMessage(correct?"서로의 속도를 존중하는 표현입니다.":"take things slowly는 서두르지 않는다는 뜻입니다.").setPositiveButton("확인",(d,w)->{if(correct){pref.edit().putBoolean("quiz",true).apply();draw();}}).show();}
  void review(){section("표현을 다시 꺼내 보세요.","뜻을 먼저 떠올리고 확인해 보세요.");
    box((reviewIndex+1)+" / 3   "+expressions[reviewIndex],revealed?meanings[reviewIndex]:"뜻을 가린 상태입니다.");
    place(body,action(revealed?"뜻 숨기기":"뜻 확인하기",()->{revealed=!revealed;draw();},true),16);
    if(revealed){place(body,action("기억해요",()->nextReview(true),false),10);place(body,action("다시 볼래요",()->nextReview(false),false),8);}
    place(body,text("복습 기록은 기기에만 보관됩니다.",14,MUTED,false),16);
  }
  void nextReview(boolean remembered){pref.edit().putInt("review",pref.getInt("review",0)+1).putInt("remember",pref.getInt("remember",0)+(remembered?1:0)).apply();reviewIndex=(reviewIndex+1)%3;revealed=false;draw();}
  void progress(){section("학습 기록","기기에 저장된 데모 진행도입니다.");
    box("학습 활동",score()+" / 3 완료"+(score()==3?"\n모든 활동이 완료됐습니다.":"\n남은 단계를 계속 진행해 보세요."));
    box("복습","복습 "+pref.getInt("review",0)+"회 · 기억함 "+pref.getInt("remember",0)+"회");
    place(body,action("학습으로 돌아가기",()->navigate(1),true),16);
    place(body,text("이 기록은 CEFR 공식 평가 결과가 아닙니다.",14,MUTED,false),18);
  }
  void settings(){section("학습 설정","이 기기에만 저장되는 화면 설정입니다.");
    place(body,action("한글 번역 "+(pref.getBoolean("ko",true)?"켜짐":"꺼짐"),()->{pref.edit().putBoolean("ko",!pref.getBoolean("ko",true)).apply();draw();},false),14);
    place(body,action("큰 글자 "+(pref.getBoolean("big",false)?"켜짐":"꺼짐"),()->{pref.edit().putBoolean("big",!pref.getBoolean("big",false)).apply();draw();},false),10);
    place(body,action("학습 기록 초기화",()->new AlertDialog.Builder(this).setTitle("기록을 삭제하시겠습니까?").setMessage("이 기기의 시연 기록과 설정이 모두 삭제됩니다.").setNegativeButton("취소",null).setPositiveButton("삭제",(d,w)->{pref.edit().clear().apply();reviewIndex=0;revealed=false;draw();}).show(),false),20);
    box("개인정보 및 제한사항","인터넷·마이크 권한이 없으며, 로그인과 운영 콘텐츠를 제공하지 않습니다. 영어 문장 입력도 저장되지 않습니다.\n정식 STORY ENGLISH 3.7.1 출시 버전이 아닙니다.");
  }
}
