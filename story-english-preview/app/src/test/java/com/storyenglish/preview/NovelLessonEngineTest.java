package com.storyenglish.preview;

import org.junit.Test;
import static org.junit.Assert.*;

public class NovelLessonEngineTest {
    private static final String SAMPLE="She felt drawn to him in the quiet light. He asked, \"Is this okay?\" She nodded and told him that they could take it slow. They knew there was no pressure and they could stop whenever they wanted.";
    @Test public void preservesExactUserSource() { assertEquals(SAMPLE, NovelLessonEngine.analyze(SAMPLE).sourceText); }
    @Test public void countsEnglishWordsAndSentences() { NovelLessonEngine.Study s=NovelLessonEngine.analyze(SAMPLE);assertTrue(s.wordCount>20);assertTrue(s.sentences.size()>=3); }
    @Test public void matchesCuratedAdultMeaningWithOriginalOffsets() { NovelLessonEngine.Study s=NovelLessonEngine.analyze(SAMPLE);assertFalse(s.matches.isEmpty());for(NovelLessonEngine.Match m:s.matches)assertEquals(m.matchedSurface,SAMPLE.substring(m.start,m.end)); }
    @Test public void providesGroundedClozeOnlyWhenMatched() { NovelLessonEngine.Study s=NovelLessonEngine.analyze(SAMPLE);assertNotNull(s.exercise);assertTrue(s.exercise.originalSentence.contains(s.exercise.answer)||s.exercise.originalSentence.toLowerCase().contains(s.exercise.answer));assertEquals(4,s.exercise.options.size());assertTrue(s.exercise.options.contains(s.exercise.answer)); }
    @Test public void noFalseGlossaryWhenNotPresent() { NovelLessonEngine.Study s=NovelLessonEngine.analyze("The weather changed gradually above the village. The bookkeeper went to the railway station in the early afternoon. The train arrived safely.");assertEquals(0,s.matches.size());assertNull(s.exercise); }
    @Test(expected=IllegalArgumentException.class) public void refusesEmptyInput() {NovelLessonEngine.analyze("  ");}
    @Test(expected=IllegalArgumentException.class) public void refusesBinaryInput() {NovelLessonEngine.analyze(SAMPLE+"\u0000");}
    @Test(expected=IllegalArgumentException.class) public void refusesTooLargeInput() {NovelLessonEngine.analyze(SAMPLE.repeat(200));}
    @Test(expected=IllegalArgumentException.class) public void refusesNonEnglishInput() {NovelLessonEngine.analyze("그녀는 그를 바라보았다. 그리고 다음 날 아침 모든 것이 달라졌다. 새로운 이야기가 계속되었다.");}
    @Test public void distinguishesAmbiguousPhrasesWithoutDisambiguationClaim() { NovelLessonEngine.Study s=NovelLessonEngine.analyze("They planned to hook up after the concert, and she wondered what he really meant by the phrase. They did not agree on its meaning yet.");assertFalse(s.matches.isEmpty());assertTrue(s.matches.get(0).entry.caution.contains("모호")); }
}