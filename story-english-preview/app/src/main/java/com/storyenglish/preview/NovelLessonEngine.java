package com.storyenglish.preview;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A conservative, deterministic English fiction study engine.
 * Never rewrites the imported author's text or invents a translation.
 * Glossary matches are string-level hints, not context-verified word-sense analyses.
 */
public final class NovelLessonEngine {
    public static final int MAX_CHARS = 24000;
    private static final Pattern WORD = Pattern.compile("[A-Za-z]+(?:['’][A-Za-z]+)?");
    private NovelLessonEngine() {}

    public static final class GlossaryEntry {
        public final String phrase, meaningKo, register, caution;
        public GlossaryEntry(String phrase, String meaningKo, String register, String caution) {
            this.phrase = phrase;
            this.meaningKo = meaningKo;
            this.register = register;
            this.caution = caution;
        }
    }
    public static final class Match {
        public final GlossaryEntry entry;
        public final int start, end;
        public final String matchedSurface;
        Match(GlossaryEntry entry, int start, int end, String matchedSurface) {
            this.entry = entry;
            this.start = start;
            this.end = end;
            this.matchedSurface = matchedSurface;
        }
    }
    public static final class Exercise {
        public final String originalSentence, maskedSentence, answer;
        public final List<String> options;
        Exercise(String originalSentence, String maskedSentence, String answer, List<String> options) {
            this.originalSentence = originalSentence;
            this.maskedSentence = maskedSentence;
            this.answer = answer;
            this.options = Collections.unmodifiableList(options);
        }
    }
    public static final class Study {
        public final String sourceText;
        public final List<String> sentences;
        public final List<Match> matches;
        public final int wordCount;
        public final Exercise exercise;
        private Study(String sourceText, List<String> sentences, List<Match> matches, int wordCount, Exercise exercise) {
            this.sourceText = sourceText;
            this.sentences = Collections.unmodifiableList(sentences);
            this.matches = Collections.unmodifiableList(matches);
            this.wordCount = wordCount;
            this.exercise = exercise;
        }
    }

    // Hand-curated adult conversational language. No graphic narrative is embedded.
    private static final GlossaryEntry[] GLOSSARY = {
        g("drawn to", "끌리다", "관계·감정", "감정이나 매력의 방향을 나타냄"),
        g("attracted to", "매력을 느끼다", "관계·감정", "맥락에 따라 정서적·성적 매력을 가리킴"),
        g("can't stop thinking about", "계속 생각나다", "구어", "집착과 애정의 차이는 맥락 확인 필요"),
        g("want to be close to", "가까워지고 싶다", "관계·감정", "감정적·신체적 친밀감 모두 가능"),
        g("take it slow", "서두르지 않다", "구어", "상대의 속도를 존중하는 문맥에 자주 쓰임"),
        g("take things slowly", "천천히 진행하다", "구어", "연애나 진행 속도에 사용"),
        g("no pressure", "부담 갖지 마", "구어", "거절 선택권을 실제로 존중하는지 문맥 확인"),
        g("comfortable with", "~에 편안함을 느끼다", "일상", "허용·동의와 완전히 동의어는 아님"),
        g("are you comfortable", "괜찮은가요 / 편한가요", "동의·경계", "확인 질문으로 사용"),
        g("is this okay", "이렇게 해도 괜찮아?", "동의·경계", "상대방에게 확인하는 표현"),
        g("we can stop", "우리 언제든 멈출 수 있어", "동의·경계", "동의 철회를 존중함"),
        g("not ready", "아직 준비되지 않다", "동의·경계", "압박하지 않는 맥락 중요"),
        g("i'd rather not", "그러지 않는 편이 좋겠어요", "거절", "부드럽지만 명확한 거절"),
        g("i would rather not", "그러지 않는 편이 좋겠어요", "거절", "명확한 거절"),
        g("consent", "동의", "성 건강·관계", "동의는 자유롭고 철회 가능해야 함"),
        g("boundaries", "경계·한계", "관계", "대화의 경계 및 개인적 기준"),
        g("make out", "진하게 키스하다", "성인 구어", "지역·세대에 따라 의미 범위가 다름"),
        g("hook up", "만나 관계를 맺다 / 가볍게 만나다", "성인 속어", "성적인 의미의 범위가 모호하므로 문맥 확인 필수"),
        g("one-night stand", "일회성 성적 관계", "성인 속어", "중립적·비난적 어조 차이에 주의"),
        g("friends with benefits", "연애 관계 없이 성적 친밀감을 갖는 사이", "성인 구어", "관계 정의와 양측 기대가 중요"),
        g("no strings attached", "조건·의무 없이", "구어", "연애에서는 관계에 따른 기대가 적다는 의미 가능"),
        g("sleep together", "함께 자다 / 성관계를 갖다", "완곡어법", "문맥에 따라 문자적·성적 의미가 달라짐"),
        g("make love", "사랑을 나누다", "완곡어법", "성관계의 완곡한 표현"),
        g("intimacy", "친밀감", "관계", "정서적·신체적 의미 모두 가능"),
        g("desire", "욕망·바람", "문어·일반", "문맥에 따라 강도와 성적 함의가 다름"),
        g("turn me on", "나를 성적으로 끌리게 하다", "성인 속어", "상대와 장소에 따라 부적절할 수 있음"),
        g("contraception", "피임", "성 건강", "의료·피임 관련 설명과 혼동 금지"),
        g("st i", "성매개감염병", "성 건강", "약어 표기는 STI로 사용"),
        g("sti testing", "성매개감염병 검사", "성 건강", "의학적 검사 안내는 전문가 확인 필요"),
        g("protection", "보호 / 피임 도구", "성 건강", "맥락에 따라 일반적 보호를 뜻함"),
        g("safeword", "중단 신호로 정한 단어", "동의·경계", "안전한 상황에서 사전에 합의된 신호"),
        g("aftercare", "사후 돌봄", "성인 관계", "성적 관계 외의 돌봄 의미도 가능"),
        g("mutual respect", "상호 존중", "관계", "동의와 경계에 중요한 요소")
    };
    private static GlossaryEntry g(String en, String ko, String register, String note) {
        return new GlossaryEntry(en, ko, register, note);
    }
    public static int glossarySize() { return GLOSSARY.length; }

    public static Study analyze(String raw) {
        if (raw == null || raw.trim().length() < 40) throw new IllegalArgumentException("영어 원문을 40자 이상 입력하세요.");
        if (raw.length() > MAX_CHARS) throw new IllegalArgumentException("텍스트가 너무 깁니다. 24,000자 이하의 구간으로 나눠 불러오세요.");
        for (int i=0; i<raw.length(); i++) {
            char ch=raw.charAt(i);
            if (ch == '\u0000' || (ch<0x20 && ch!='\n' && ch!='\r' && ch!='\t')) {
                throw new IllegalArgumentException("제어문자가 있는 텍스트는 불러올 수 없습니다.");
            }
        }
        Matcher wc=WORD.matcher(raw);
        int words=0, englishLetters=0;
        while(wc.find()) { words++; englishLetters+=wc.group().length(); }
        if(words<8 || englishLetters<25) throw new IllegalArgumentException("영어 원문 인식이 어렵습니다. 현재는 영어 원문 학습만 지원합니다.");
        List<String> sentences=new ArrayList<>();
        BreakIterator bi=BreakIterator.getSentenceInstance(Locale.US);
        bi.setText(raw);
        int begin=bi.first(),end=bi.next();
        while(end!=BreakIterator.DONE){
            String sentence=raw.substring(begin,end).trim();
            if(!sentence.isEmpty()) sentences.add(sentence);
            begin=end;end=bi.next();
        }
        if(sentences.isEmpty()) sentences.add(raw.trim());
        List<Match> found=new ArrayList<>();
        for(GlossaryEntry g:GLOSSARY){
            Pattern pat=Pattern.compile("(?iu)(?<![\\p{L}])"+Pattern.quote(g.phrase)+"(?![\\p{L}])");
            Matcher m=pat.matcher(raw);
            while(m.find())found.add(new Match(g,m.start(),m.end(),raw.substring(m.start(),m.end())));
        }
        found.sort((a,b)->Integer.compare(a.start,b.start));
        Exercise exercise=null;
        for(Match match:found){
            String containing=null;
            for(String sent:sentences){
                int at=indexOfIgnoreCase(sent,match.matchedSurface);
                if(at>=0){containing=sent;break;}
            }
            if(containing==null)continue;
            int at=indexOfIgnoreCase(containing,match.matchedSurface);
            if(at<0)continue;
            String hidden=containing.substring(0,at)+"_____"+containing.substring(at+match.matchedSurface.length());
            List<String> options=new ArrayList<>();
            options.add(match.entry.phrase);
            for(GlossaryEntry other:GLOSSARY){
                if(options.size()==4)break;
                if(!other.phrase.equalsIgnoreCase(match.entry.phrase))options.add(other.phrase);
            }
            exercise=new Exercise(containing,hidden,match.entry.phrase,options);
            break;
        }
        return new Study(raw,sentences,found,words,exercise);
    }
    private static int indexOfIgnoreCase(String haystack,String needle){
        return haystack.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
    }
}