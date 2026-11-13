package com.storyenglish.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Private, in-memory study panel for English adult fiction supplied by the learner. */
public final class NovelReaderPanel {
    public interface Host {
        void importText();
        void listen(String phrase);
        void shadow(String phrase);
        void refresh();
    }
    public static final class Session {
        public NovelLessonEngine.Study lesson;
        public String title="",author="저자 미기재",tier="미분류",provenance="사용자가 제공한 원문";
        public int page=0, actions=0;
        public String answer=null;
        public void load(String text,String name,boolean sample){
            NovelLessonEngine.Study next=NovelLessonEngine.analyze(text);
            lesson=next;title=name;author=sample?"STORY ENGLISH 자체 예문":"저자 미기재";
            tier=sample?"MATURE · 자체 예문":"미분류 · 이용자 판단";
            provenance=sample?"새로 작성한 예문 · 앱 포함":"이용자 제공 텍스트 · 기기 내 학습용";
            page=0;actions=0;answer=null;
        }
        public void clear(){lesson=null;title="";author="저자 미기재";tier="미분류";page=0;actions=0;answer=null;}
    }
    /** Original adults-only thematic fiction, no graphic sexual description. */
    public static final String REUNION=
        "When Elena stepped into the restaurant, Daniel looked up from the table they had shared years ago. " +
        "She had not expected to feel drawn to him again, but she also knew how quickly nostalgia could become a misunderstanding. " +
        "He stood up, smiling, and said that he had not been able to stop thinking about their last conversation. " +
        "\n\nShe answered carefully, choosing every word. They had once wanted different things from life, and that difference had left them both hurt. " +
        "She was still attracted to him, but she was not ready to promise anything. " +
        "\"I'd rather not rush into this,\" she said. \"We can take it slow, and neither of us needs to decide tonight.\" " +
        "Daniel took a moment before answering. \"No pressure. I understand your boundaries.\" " +
        "\n\nLater they walked by the river, speaking openly about trust, desire, and mutual respect. " +
        "The evening ended with a promise to be honest instead of a promise to stay together.";

    public static final String HEALTH=
        "Asha closed the medical brochure and looked across the kitchen table at her partner, Sam. " +
        "They had been talking about intimacy for weeks, but tonight she wanted to be more specific. " +
        "\"Before we become intimate, can we talk about contraception and STI testing?\" she asked. " +
        "Sam answered without embarrassment. \"Of course. I appreciate you bringing it up.\" " +
        "\n\nThey agreed that the conversation should be practical, not a test of anyone's feelings. " +
        "They talked about protection, personal boundaries, and what being comfortable with each other really meant. " +
        "Neither person wanted to treat consent as something that could be assumed. " +
        "\n\n\"If either of us is not ready, we can stop,\" Sam said. Asha smiled because that was exactly the kind of mutual respect she wanted. " +
        "By the end of the evening, they had a clearer understanding of their options and of one another.";

    /** Newly authored, non-graphic adult fiction for an offline demonstration. */
    public static final String SAMPLE=
        "The hotel lobby was quiet after midnight. Mara noticed how drawn to Daniel she felt, but she wanted to take things slowly. " +
        "He waited near the door and gave her a chance to decide whether the evening should continue. " +
        "\n\n\"I can't stop thinking about you,\" he said. His voice was gentle rather than demanding. " +
        "She smiled. \"I'm attracted to you, too, but I don't want to rush.\" " +
        "He nodded. \"No pressure. We can stop whenever you want.\" " +
        "\n\nThey talked about intimacy, boundaries, and what mutual respect meant to them. " +
        "Mara had heard friends use phrases like hook up and no strings attached, but knew those expressions could be ambiguous. " +
        "\"Are you comfortable with this?\" Daniel asked. She appreciated that he wanted a real answer. " +
        "\n\nBy morning they both understood one another a little better. Their honest conversation mattered more than appearances.";

    private final Activity activity;
    private final Session session;
    private final Host host;
    private final boolean large;
    private static final int BG=0xff0B1220,SURFACE=0xff19283D,WHITE=0xffF8FAFC,
            MUTED=0xffCBD5E1,GOLD=0xffD6A85F,ROSE=0xffE5A5B3,BLUE=0xff85C4FA;
    public NovelReaderPanel(Activity a,Session s,Host h,boolean largeFont){activity=a;session=s;host=h;large=largeFont;}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d;}
    private LinearLayout col(){LinearLayout c=new LinearLayout(activity);c.setOrientation(LinearLayout.VERTICAL);return c;}
    private void add(LinearLayout parent,View child,int margin){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(margin);parent.addView(child,p);}
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(activity);t.setText(value);t.setTextColor(color);t.setTextSize(large?size*1.17f:size);
        t.setLineSpacing(dp(2),1.12f);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private Button button(String title,Runnable task,boolean primary){
        Button b=new Button(activity);b.setAllCaps(false);b.setMinHeight(dp(51));b.setText(title);
        b.setTextSize(large?17:15);b.setTextColor(primary?BG:WHITE);b.setBackground(bg(primary?GOLD:SURFACE));
        b.setPadding(dp(10),dp(8),dp(10),dp(8));b.setOnClickListener(v->task.run());return b;
    }
    private LinearLayout card(String title,String detail){
        LinearLayout c=col();c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(bg(SURFACE));
        add(c,text(title,18,WHITE,true),0);
        if(detail!=null&&!detail.isEmpty())add(c,text(detail,15,MUTED,false),8);return c;
    }
    public void render(LinearLayout container){
        add(container,text("성인소설 원문 학습",27,WHITE,true),12);
        add(container,text("읽기 → 표현 분석 → 듣기 → 따라 말하기 → 빈칸 복습",15,MUTED,false),8);
        add(container,text("PRIVATE OFFLINE READER · 19+ 자기확인",13,ROSE,true),11);
        if(session.lesson==null)empty(container);else loaded(container);
    }
    private void empty(LinearLayout root){
        add(root,card("성인소설로 배우는 영어","사용자가 보유한 영어 소설·성인문학 TXT 파일에서 문장과 표현을 골라 실제 사용 맥락을 학습합니다. 앱 자체 서버 업로드는 하지 않습니다. TTS 이용 시 선택한 문장은 기기 음성엔진에 전달됩니다."),16);
        add(root,button("TXT 원문 파일 불러오기",host::importText,true),14);
        add(root,button("성인 로맨스 오리지널 예문 체험",()->{
            session.load(SAMPLE,"After Midnight: An Honest Conversation",true);host.refresh();
        },false),9);
        add(root,button("재회의 밤 · 관계 표현 예문",()->{
            session.load(REUNION,"The Reunion: Trust and Desire",true);host.refresh();
        },false),9);
        add(root,button("성 건강·피임 대화 소설 예문",()->{
            session.load(HEALTH,"An Honest Conversation: Sexual Health",true);host.refresh();
        },false),9);
        LinearLayout manual=card("영어 원문 직접 붙여넣기","한 번에 최대 24,000자. 종료 후 원문을 보관하지 않습니다.");
        EditText input=new EditText(activity);input.setTextColor(WHITE);input.setHintTextColor(MUTED);input.setTextSize(large?18:16);
        input.setMinLines(5);input.setSingleLine(false);input.setHint("원문 일부를 여기에 붙여넣기…");
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        add(manual,input,10);
        add(manual,button("원문 분석 시작",()->{
            try{session.load(input.getText().toString(),"직접 입력한 원문",false);input.setText("");host.refresh();}
            catch(IllegalArgumentException e){error(e.getMessage());}
        },true),10);
        add(root,manual,14);
        add(root,card("저작권·안전 안내","직접 작성하거나 적법하게 이용할 수 있는 성인 원문만 선택하세요. 원문의 저자·권리·연령등급은 자동 검증되지 않습니다. AI 번역·원문 의미 판정·출판용 원문 재배포는 제공하지 않습니다."),17);
    }
    private void loaded(LinearLayout root){
        NovelLessonEngine.Study l=session.lesson;
        add(root,card(session.title,session.author+"\n"+session.provenance+"\n이용자 분류: "+session.tier),15);
        add(root,text("총 "+l.wordCount+"단어 · "+l.sentences.size()+"문장 · 사전표현 "+l.matches.size()+"건",13,BLUE,true),11);
        add(root,button("원문 등급 지정 · "+session.tier,()->new AlertDialog.Builder(activity)
          .setTitle("등급은 이용자 선택이며 자동 확인이 아닙니다")
          .setItems(new String[]{"MATURE · 비노골적 성인","EXPLICIT · 노골적 성인 원문","미분류"},(d,i)->{
              session.tier=i==0?"MATURE · 이용자 선택":i==1?"EXPLICIT · 이용자 선택":"미분류";host.refresh();
          }).show(),false),11);
        add(root,button("다른 TXT 불러오기",host::importText,false),8);
        add(root,button("현재 원문 닫기",()->new AlertDialog.Builder(activity).setTitle("읽기를 종료할까요?")
            .setMessage("메모리에 저장된 원문만 비웁니다. 원본 TXT 파일은 삭제되지 않습니다.")
            .setNegativeButton("취소",null).setPositiveButton("종료",(d,w)->{session.clear();host.refresh();}).show(),false),8);
        add(root,text("01  원문 읽기",20,WHITE,true),22);
        int total=l.sentences.size();
        int from=Math.min(session.page*5,total-1),until=Math.min(from+5,total);
        add(root,text("페이지 "+(session.page+1)+" / "+((total+4)/5)+" · 5문장씩",13,MUTED,false),8);
        for(int i=from;i<until;i++){
            String sent=l.sentences.get(i);
            LinearLayout item=card((i+1)+".  "+sent,"");
            if(sent.length()<=380){
                add(item,button("원문 듣기 (TTS)",()->{session.actions++;host.listen(sent);},false),10);
                if(sent.length()<=100)add(item,button("쉐도잉 · 음향 비교",()->{session.actions++;host.shadow(sent);},true),8);
                else add(item,text("170자를 초과해 쉐도잉은 지원하지 않습니다.",12,MUTED,false),8);
            } else add(item,text("긴 문장입니다. 더 짧은 구간으로 나누어 다시 불러오세요.",12,MUTED,false),8);
            add(root,item,11);
        }
        LinearLayout nav=new LinearLayout(activity);nav.setOrientation(LinearLayout.HORIZONTAL);
        Button prev=button("◀ 이전",()->{session.page=Math.max(0,session.page-1);host.refresh();},false);
        Button next=button("다음 ▶",()->{session.page=Math.min((total-1)/5,session.page+1);host.refresh();},true);
        prev.setEnabled(session.page>0);next.setEnabled(until<total);
        nav.addView(prev,new LinearLayout.LayoutParams(0,dp(54),1));
        LinearLayout.LayoutParams second=new LinearLayout.LayoutParams(0,dp(54),1);second.leftMargin=dp(8);nav.addView(next,second);
        add(root,nav,12);
        add(root,text("02  성인 표현 · 실제 원문 일치",20,WHITE,true),25);
        add(root,text("미리 검수한 표현 사전의 문자열 일치입니다. 원문의 의미·중의성을 AI가 검증한 결과가 아닙니다.",13,MUTED,false),8);
        if(l.matches.isEmpty())add(root,card("일치하는 표현 없음","사전과 일치한 표현이 없습니다. 원문 읽기와 선택한 문장의 듣기는 지원합니다."),12);
        else{
            int cap=Math.min(12,l.matches.size());
            for(int i=0;i<cap;i++){
                NovelLessonEngine.Match hit=l.matches.get(i);
                add(root,card("“"+hit.matchedSurface+"” · "+hit.entry.register,
                    "기본 의미: "+hit.entry.meaningKo+"\n문맥 주의: "+hit.entry.caution),10);
            }
            if(l.matches.size()>cap)add(root,text("총 "+l.matches.size()+"건 중 "+cap+"건 표시",12,MUTED,false),8);
        }
        add(root,text("03  근거 있는 원문 빈칸 문제",20,WHITE,true),25);
        if(l.exercise==null)add(root,card("문제 없음","사전에 등록된 표현이 실제 원문에 없어 임의의 정답을 만들어내지 않았습니다."),12);
        else{
            NovelLessonEngine.Exercise q=l.exercise;
            add(root,card("빈칸에 들어갈 원문의 표현은?",q.maskedSentence),12);
            for(String choice:q.options)add(root,button(choice,()->{session.answer=choice;host.refresh();},false),9);
            if(session.answer!=null){
                boolean correct=session.answer.equals(q.answer);
                add(root,text(correct?"✓ 원문과 일치합니다.":"원문과 다릅니다. 다시 고르세요.",15,correct?GOLD:ROSE,true),10);
                if(correct)add(root,text("근거 문장: "+q.originalSentence,13,MUTED,false),8);
            }
        }
        add(root,card("이번 독서 활동","현재 원문에서 듣기·쉐도잉 선택 "+session.actions+"회\n원문·저자·등급 정보는 앱 메모리에만 유지됩니다."),20);
        add(root,text("본 기능은 영어 소설 원문 분석용 오프라인 보조도구입니다. 완성된 문학 번역·공식 CEFR 평가·법적 연령인증이 아닙니다.",12,MUTED,false),12);
    }
    private void error(String reason){new AlertDialog.Builder(activity).setMessage(reason).setPositiveButton("확인",null).show();}
}