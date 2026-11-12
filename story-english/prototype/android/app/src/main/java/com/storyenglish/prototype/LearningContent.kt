package com.storyenglish.prototype

enum class ContentTier { GENERAL, MATURE, EXPLICIT }

object LearningPolicy {
    fun canAccess(tier: ContentTier, matureConfirmed: Boolean): Boolean =
        when (tier) {
            ContentTier.GENERAL -> true
            ContentTier.MATURE -> matureConfirmed
            ContentTier.EXPLICIT -> false
        }
}

data class Dialogue(
    val speaker: String,
    val english: String,
    val korean: String,
    val startMs: Int,
    val endMs: Int
)

data class Expression(
    val phrase: String,
    val korean: String,
    val category: String,
    val explanation: String
)

object DemoLesson {
    const val title = "The Boundary"
    const val description = "An honest conversation · B1–B2 · Original authored demo"
    val dialogues = listOf(
        Dialogue("Clara", "Can we talk about what happened last night?", "어젯밤 있었던 일에 대해 이야기할 수 있을까?", 500, 3698),
        Dialogue("Daniel", "Of course. I felt I crossed a line.", "물론이지. 내가 선을 넘었다고 느꼈어.", 4078, 7292),
        Dialogue("Clara", "I appreciate you saying that. I need time to think.", "그렇게 말해줘서 고마워. 생각할 시간이 필요해.", 7672, 11917),
        Dialogue("Daniel", "I understand. I won't push you.", "이해해. 재촉하지 않을게.", 12297, 15178),
        Dialogue("Clara", "We can take things slowly and be honest with each other.", "천천히 가면서 서로 솔직하게 이야기할 수 있어.", 15558, 19510),
        Dialogue("Daniel", "That sounds fair. Please tell me if anything feels uncomfortable.", "그게 좋겠어. 불편한 게 있으면 말해줘.", 19890, 25195),
        Dialogue("Clara", "Thank you. Boundaries make trust possible.", "고마워. 경계가 있어야 신뢰가 가능해.", 25575, 29231)
    )
    val expressions = listOf(
        Expression("cross a line", "선을 넘다", "Everyday expression", "To behave in a way that exceeds an acceptable boundary."),
        Expression("take things slowly", "천천히 진행하다", "Collocation", "To proceed without pressure or haste."),
        Expression("I won't push you.", "재촉하지 않을게요.", "Sentence pattern", "A respectful way to communicate that you will not pressure someone.")
    )
    fun quizCorrect(choice: String): Boolean = choice == "Respect the other person's pace"
}
