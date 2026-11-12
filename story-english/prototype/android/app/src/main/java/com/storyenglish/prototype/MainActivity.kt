package com.storyenglish.prototype

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

private val Night = Color(0xFF0B1220)
private val SurfaceBlue = Color(0xFF111827)
private val Amber = Color(0xFFD6A85F)
private val SoftBlue = Color(0xFF60A5FA)
private val White = Color(0xFFF8FAFC)
private val Secondary = Color(0xFFCBD5E1)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.US
        }
        setContent {
            val palette = darkColorScheme(
                primary = Amber, onPrimary = Night,
                background = Night, onBackground = White,
                surface = SurfaceBlue, onSurface = White,
                secondary = SoftBlue, onSecondary = Night
            )
            MaterialTheme(colorScheme = palette) {
                StoryEnglishPrototype(
                    speak = { text, speed ->
                        if (ttsReady) {
                            tts?.setSpeechRate(speed)
                            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "story-demo")
                        }
                    },
                    stop = { tts?.stop() }
                )
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}

@Composable
private fun StoryEnglishPrototype(
    speak: (String, Float) -> Unit,
    stop: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("prototype_progress", 0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var matureConfirmed by rememberSaveable { mutableStateOf(false) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var showKorean by rememberSaveable { mutableStateOf(true) }
    var speed by rememberSaveable { mutableFloatStateOf(1.0f) }
    var completed by remember { mutableStateOf(preferences.getBoolean("quiz_complete", false)) }
    var reviewCount by remember { mutableIntStateOf(preferences.getInt("review_count", 0)) }

    Column(Modifier.fillMaxSize().background(Night)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text("STORY ENGLISH", color = Amber, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text("3.7.1 · OFFLINE PROTOTYPE", color = Secondary, fontSize = 11.sp)
        }
        HorizontalDivider(color = Color(0xFF334155))
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> WelcomePage(onStart = { tab = 1 })
                1 -> if (!LearningPolicy.canAccess(ContentTier.MATURE, matureConfirmed)) {
                    AccessGate(onConfirm = { matureConfirmed = true }, onBack = { tab = 0 })
                } else {
                    LearningPage(
                        index = index, onIndex = { index = it },
                        showKorean = showKorean, toggleKorean = { showKorean = !showKorean },
                        speed = speed, onSpeed = { speed = it },
                        speak = speak, stop = stop
                    )
                }
                2 -> if (!matureConfirmed) {
                    AccessGate(onConfirm = { matureConfirmed = true }, onBack = { tab = 0 })
                } else {
                    PracticePage(
                        onSpeak = { speak(it, speed) },
                        complete = completed,
                        onComplete = {
                            completed = true
                            preferences.edit().putBoolean("quiz_complete", true).apply()
                        }
                    )
                }
                3 -> ReviewPage(
                    reviewCount = reviewCount,
                    onReviewed = {
                        reviewCount += 1
                        preferences.edit().putInt("review_count", reviewCount).apply()
                    },
                    onSpeak = { speak(it, speed) }
                )
                else -> AboutPage(onErase = {
                    preferences.edit().clear().apply()
                    completed = false
                    reviewCount = 0
                    matureConfirmed = false
                    index = 0
                    stop()
                })
            }
        }
        HorizontalDivider(color = Color(0xFF334155))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("Home", "Story", "Practice", "Review", "Info").forEachIndexed { n, name ->
                TextButton(onClick = { stop(); tab = n }, modifier = Modifier.weight(1f)) {
                    Text(name, color = if (tab == n) Amber else Secondary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun WelcomePage(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle("Learn English. Live the Story.")
        Text("대화의 맥락에서 듣고, 표현을 이해하고, 직접 말해 보세요.", color = Secondary)
        Box(
            Modifier.fillMaxWidth().height(190.dp).background(
                Brush.linearGradient(listOf(Color(0xFF193C5B), Color(0xFF392E3C))),
                RoundedCornerShape(18.dp)
            ).padding(22.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column {
                Text("ORIGINAL LEARNING DEMO", color = Amber, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Text("The Boundary", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("Clara & Daniel · B1–B2", color = Secondary)
            }
        }
        PrimaryButton("학습 시작 / Start lesson", onStart)
        SectionTitle("Season 01 — editorial drafts")
        listOf("THE LAST CONFESSION", "THE KINGDOM OF ASHES", "ECHOES OF TOMORROW").forEach {
            InfoCard {
                Text(it, fontWeight = FontWeight.Bold)
                Text("원본 에피소드 소스 미포함 · 이 프로토타입에서 재생 불가", color = Secondary, fontSize = 12.sp)
            }
        }
        Text("시연용 독립 앱이며 정식 STORY ENGLISH 3.7.1 배포판이 아닙니다.", color = Secondary, fontSize = 12.sp)
    }
}

@Composable
private fun AccessGate(onConfirm: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle("MATURE 교육 콘텐츠")
        Text("연애·관계·동의·경계 설정을 다루는 비노골적 영어 회화입니다.", color = Secondary)
        InfoCard {
            Text("이 데모는 19세 이상 자기확인만 받습니다.", fontWeight = FontWeight.Bold)
            Text("법적 연령 검증을 수행하지 않으며, EXPLICIT 콘텐츠는 제공하지 않습니다.", color = Secondary)
        }
        PrimaryButton("나는 만 19세 이상이며 학습 목적에 동의합니다", onConfirm)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("돌아가기") }
    }
}

@Composable
private fun LearningPage(
    index: Int,
    onIndex: (Int) -> Unit,
    showKorean: Boolean,
    toggleKorean: () -> Unit,
    speed: Float,
    onSpeed: (Float) -> Unit,
    speak: (String, Float) -> Unit,
    stop: () -> Unit
) {
    val dialogue = DemoLesson.dialogues[index]
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("The Boundary")
        Text("7 dialogue segments · B1–B2 · MATURE", color = Secondary, fontSize = 12.sp)
        Box(
            Modifier.fillMaxWidth().height(176.dp).background(
                Brush.verticalGradient(listOf(Color(0xFF1D4053), Color(0xFF121B2A))),
                RoundedCornerShape(16.dp)
            ).padding(16.dp), contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SCENE STORYBOARD", color = Amber, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Text(dialogue.speaker, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Text("정적 화면 · 실제 동영상 아님", color = Secondary, fontSize = 12.sp)
            }
        }
        LinearProgressIndicator(
            progress = { (index + 1).toFloat() / DemoLesson.dialogues.size },
            modifier = Modifier.fillMaxWidth(), color = Amber
        )
        InfoCard {
            Text("${index + 1} / 7 · ${dialogue.speaker}", color = Amber)
            Spacer(Modifier.height(4.dp))
            Text(dialogue.english, fontSize = 20.sp, lineHeight = 28.sp)
            if (showKorean) {
                Spacer(Modifier.height(8.dp))
                Text(dialogue.korean, color = Secondary, lineHeight = 21.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onIndex((index + 6) % 7); stop() }, modifier = Modifier.weight(1f)) { Text("이전") }
            Button(onClick = { speak(dialogue.english, speed) }, modifier = Modifier.weight(1.3f)) { Text("▶ TTS 듣기") }
            OutlinedButton(onClick = { onIndex((index + 1) % 7); stop() }, modifier = Modifier.weight(1f)) { Text("다음") }
        }
        OutlinedButton(onClick = toggleKorean, modifier = Modifier.fillMaxWidth()) {
            Text(if (showKorean) "한국어 자막 숨기기" else "한국어 자막 보이기")
        }
        Text("TTS 속도 · ${String.format(Locale.US, "%.1f", speed)}×", color = Secondary)
        Slider(value = speed, onValueChange = onSpeed, valueRange = 0.6f..1.3f)
        SectionTitle("대화 전체 보기")
        DemoLesson.dialogues.forEachIndexed { n, line ->
            InfoCard(
                modifier = Modifier.clickable { onIndex(n); stop() }
            ) {
                Text("${n + 1}. ${line.speaker}", color = if (index == n) Amber else SoftBlue, fontSize = 12.sp)
                Text(line.english, fontSize = 14.sp)
                if (showKorean) Text(line.korean, color = Secondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PracticePage(
    onSpeak: (String) -> Unit,
    complete: Boolean,
    onComplete: () -> Unit
) {
    var choice by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("Comprehension check")
        InfoCard {
            Text("What does “I won't push you” communicate?", fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            listOf(
                "Demand an immediate decision",
                "Respect the other person's pace",
                "End the conversation forever"
            ).forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clickable { choice = option }.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = choice == option, onClick = { choice = option })
                    Text(option, fontSize = 14.sp)
                }
            }
        }
        Button(
            onClick = { if (DemoLesson.quizCorrect(choice)) onComplete() },
            enabled = choice.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("정답 확인") }
        if (choice.isNotBlank()) {
            Text(
                if (DemoLesson.quizCorrect(choice)) "Correct — respecting boundaries." else "다시 생각해 보세요.",
                color = if (DemoLesson.quizCorrect(choice)) Color(0xFF86EFAC) else Color(0xFFFBBF24)
            )
        }
        Text(if (complete) "이해 문제 완료 ✓" else "이해 문제 미완료", color = Secondary)
        SectionTitle("Write & speak")
        Text("상대방에게 생각할 시간이 필요하다고 영어로 말해 보세요.", color = Secondary)
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it.take(300) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            label = { Text("My reply") }
        )
        Text("예시: “I need some time to think. Thank you for understanding.”", color = Secondary, fontSize = 13.sp)
        OutlinedButton(
            onClick = { onSpeak(answer.trim()) },
            enabled = answer.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("작성한 문장을 TTS로 듣기") }
        Text("AI 채점·음성인식은 이 오프라인 프로토타입에 포함되지 않습니다.", color = Secondary, fontSize = 12.sp)
    }
}

@Composable
private fun ReviewPage(
    reviewCount: Int,
    onReviewed: () -> Unit,
    onSpeak: (String) -> Unit
) {
    var revealed by rememberSaveable { mutableStateOf(setOf<Int>()) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("Expression review")
        Text("복습 완료 누적 횟수: $reviewCount", color = Secondary)
        DemoLesson.expressions.forEachIndexed { n, e ->
            InfoCard {
                Text(e.category, color = Amber, fontSize = 12.sp)
                Text(e.phrase, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                if (n in revealed) {
                    Text(e.korean, color = Secondary)
                    Text(e.explanation, color = Secondary, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { revealed = if (n in revealed) revealed - n else revealed + n }) {
                        Text(if (n in revealed) "뜻 가리기" else "뜻 보기")
                    }
                    TextButton(onClick = { onSpeak(e.phrase) }) { Text("듣기") }
                    TextButton(onClick = onReviewed) { Text("복습 완료") }
                }
            }
        }
    }
}

@Composable
private fun AboutPage(onErase: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle("About this prototype")
        InfoCard {
            Text("Package: com.storyenglish.prototype", fontSize = 14.sp)
            Text("Version: 3.7.1-prototype", fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text("로컬 실행형 시연 앱. 기존 STORY ENGLISH 3.7.0의 원본 소스에 통합하지 않았습니다.", color = Secondary)
            Text("학습 대화는 자체 작성 예시입니다. 시스템 TTS 외 음원/동영상은 포함하지 않습니다.", color = Secondary)
        }
        InfoCard {
            Text("Content policy", color = Amber, fontWeight = FontWeight.Bold)
            Text("GENERAL: 허용 · MATURE: 자기확인 후 데모 제공 · EXPLICIT: 제공하지 않음", color = Secondary)
            Text("19+ 자기확인은 법적 연령 검증이 아닙니다.", color = Secondary)
        }
        InfoCard {
            Text("Privacy", color = Amber)
            Text("인터넷 권한이 없으며 외부 서버 전송이 없습니다. 퀴즈 완료 여부와 복습 횟수만 기기에 저장됩니다.", color = Secondary)
        }
        OutlinedButton(onClick = onErase, modifier = Modifier.fillMaxWidth()) { Text("로컬 학습 진행정보 초기화") }
    }
}

@Composable
private fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceBlue),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = White)
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(text) }
}
