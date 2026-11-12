package com.storyenglish.prototype

import org.junit.Assert.*
import org.junit.Test

class LearningPolicyTest {
    @Test fun generalVisible() {
        assertTrue(LearningPolicy.canAccess(ContentTier.GENERAL, false))
    }
    @Test fun matureRequiresConfirmation() {
        assertFalse(LearningPolicy.canAccess(ContentTier.MATURE, false))
        assertTrue(LearningPolicy.canAccess(ContentTier.MATURE, true))
    }
    @Test fun explicitAlwaysBlockedInPrototype() {
        assertFalse(LearningPolicy.canAccess(ContentTier.EXPLICIT, false))
        assertFalse(LearningPolicy.canAccess(ContentTier.EXPLICIT, true))
    }
    @Test fun originalDialogueAndTimedCaptionsConsistent() {
        assertEquals(7, DemoLesson.dialogues.size)
        assertEquals(3, DemoLesson.expressions.size)
        var previousEnd = 0
        for (line in DemoLesson.dialogues) {
            assertTrue(line.startMs >= previousEnd)
            assertTrue(line.endMs > line.startMs)
            assertTrue(line.english.isNotBlank() && line.korean.isNotBlank())
            previousEnd = line.endMs
        }
    }
    @Test fun comprehensionAnswerIsDeterministic() {
        assertTrue(DemoLesson.quizCorrect("Respect the other person's pace"))
        assertFalse(DemoLesson.quizCorrect("Push for an immediate answer"))
    }
}
