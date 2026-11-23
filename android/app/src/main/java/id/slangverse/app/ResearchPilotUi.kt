package id.slangverse.app

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ResearchPilotScreen() {
    val context = LocalContext.current
    val store = remember(context) { ResearchPilotStore(context) }
    val bank = remember(context) {
        runCatching { context.assets.open(ResearchPilotBank.ASSET).use { ResearchPilotBank.decode(it.readBytes()) } }.getOrNull()
    }
    val stored = remember(store) { runCatching { store.load() } }
    var session by remember { mutableStateOf(stored.getOrNull()) }
    var savedSessionUnreadable by remember { mutableStateOf(stored.isFailure) }
    var feedbackQuestion by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmationOpen by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { destination ->
        val snapshot = session
        if (destination != null && snapshot != null) {
            exporting = true
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        checkNotNull(context.contentResolver.openOutputStream(destination, "wt"))
                            .bufferedWriter(Charsets.UTF_8).use { it.write(snapshot.toCsv()) }
                    }.isSuccess
                }
                notice = if (saved) "CSV exported." else "Could not export CSV. Choose a destination and retry."
                exporting = false
            }
        }
    }

    val active = session
    val matches = active != null && bank != null && active.matches(bank)
    val latestResponse = active?.responses?.lastOrNull()?.takeIf { it.questionId == feedbackQuestion }
    val feedbackPost = if (matches && latestResponse != null) bank?.posts?.firstOrNull { it.thread.id == latestResponse.questionId } else null
    val question = if (bank != null && feedbackPost == null) active?.nextQuestion(bank) else null

    key(active?.id, question?.thread?.id, feedbackPost?.thread?.id) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Research pilot", fontFamily = WarmEditorial.Display, fontSize = 29.sp)
            Text("30 authored questions · 10 meanings · 3 contexts per meaning", color = WarmEditorial.Muted, fontSize = 13.sp)
            notice?.let { Text(it, color = WarmEditorial.CoralDeep, fontSize = 13.sp) }
            when {
                savedSessionUnreadable -> Text("The saved pilot session cannot be opened.")
                active == null && bank == null -> Text("The bundled pilot bank could not be loaded.")
                active == null && bank != null -> PilotSetup { code, condition ->
                    val newSession = ResearchPilotSession.start(code, condition, bank)
                    if (store.save(newSession)) {
                        session = newSession
                        notice = null
                    } else notice = "Could not save the session. Check available storage and retry."
                }
                active != null -> {
                    Text("${active.participantCode} · ${active.condition.label}", fontWeight = FontWeight.SemiBold)
                    Text("${active.responses.size} / 30 answered · ${active.bankId} · ${active.bankSha256.take(12)}",
                        color = WarmEditorial.Muted, fontSize = 12.sp)
                    when {
                        !matches -> Text("This session uses a different bank revision. Export its saved responses to keep them.")
                        feedbackPost != null && latestResponse != null -> {
                            Text(if (latestResponse.correct) "Meaning matched" else "Review this meaning", fontWeight = FontWeight.Bold)
                            Text(feedbackPost.thread.options[latestResponse.correctIndex])
                            Text(feedbackPost.thread.explanation)
                            Button(onClick = { feedbackQuestion = null }) {
                                Text(if (active.responses.size == 30) "Finish pilot" else "Next question")
                            }
                        }
                        question != null -> PilotQuestion(question) { selected, elapsedMs, hints ->
                            val current = session ?: return@PilotQuestion
                            val updated = current.answer(bank, question.thread.id, selected, elapsedMs, hints)
                            if (updated != current) {
                                if (store.save(updated)) {
                                    feedbackQuestion = question.thread.id
                                    notice = null
                                    session = updated
                                } else notice = "Could not save the answer. Check available storage and retry."
                            }
                        }
                        else -> Text("Session complete. ${active.responses.count { it.correct }} of 30 meanings matched.")
                    }
                    if (active.responses.isNotEmpty()) {
                        OutlinedButton(enabled = !exporting, onClick = {
                            notice = null
                            export.launch("slangverse-pilot-${active.id.take(8)}.csv")
                        }) { Text("Export responses CSV") }
                    }
                }
            }
            if (active != null || savedSessionUnreadable) {
                TextButton(enabled = !exporting, onClick = { confirmationOpen = true }) { Text("Clear pilot session") }
            }
        }
    }

    if (confirmationOpen) {
        AlertDialog(onDismissRequest = { confirmationOpen = false }, title = { Text("Clear pilot session?") },
            text = { Text("This removes the saved pilot responses on this device. Export a CSV first if you need to keep them.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmationOpen = false
                    if (store.clear()) {
                        session = null
                        savedSessionUnreadable = false
                        feedbackQuestion = null
                        notice = "Pilot session cleared."
                    } else notice = "Could not clear the pilot session."
                }) { Text("Clear session") }
            }, dismissButton = { TextButton(onClick = { confirmationOpen = false }) { Text("Cancel") } })
    }
}

@Composable
private fun PilotSetup(onStart: (String, PilotCondition) -> Unit) {
    var participantCode by rememberSaveable { mutableStateOf("") }
    var condition by rememberSaveable { mutableStateOf(PilotCondition.Fixed) }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(value = participantCode, onValueChange = { participantCode = it.take(24) },
        modifier = Modifier.fillMaxWidth(), label = { Text("Participant code") }, singleLine = true,
        supportingText = { Text("Use an assigned code such as P001.") })
    Text("Session order", fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PilotCondition.entries.forEach { option ->
            FilterChip(selected = condition == option, onClick = { condition = option }, label = { Text(option.label) })
        }
    }
    Text("The question bank and order setting are locked when the session starts.", fontSize = 13.sp)
    Row(Modifier.fillMaxWidth().toggleable(acknowledged, role = Role.Checkbox) { acknowledged = it }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = acknowledged, onCheckedChange = null)
        Text("I understand that this session records answers, hints viewed, and response times on this device.", fontSize = 13.sp)
    }
    Button(enabled = acknowledged && ResearchPilotSession.isValidCode(participantCode),
        onClick = { onStart(participantCode, condition) }) { Text("Start pilot") }
}

@Composable
private fun PilotQuestion(post: FeedPracticePost, onAnswer: (Int, Long, Int) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    var hintShown by rememberSaveable { mutableStateOf(false) }
    var hintViewed by rememberSaveable { mutableStateOf(false) }
    var activeMillis by rememberSaveable { mutableLongStateOf(0) }
    var resumedAt by remember { mutableStateOf<Long?>(null) }
    DisposableEffect(lifecycle) {
        fun pause() {
            resumedAt?.let { activeMillis += (SystemClock.elapsedRealtime() - it).coerceAtLeast(0) }
            resumedAt = null
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumedAt = SystemClock.elapsedRealtime()
                Lifecycle.Event.ON_PAUSE -> pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            pause()
        }
    }
    val thread = post.thread
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${thread.author} · ${checkNotNull(post.context).label}", fontWeight = FontWeight.SemiBold)
            Text(thread.post)
            TextButton(onClick = {
                hintShown = !hintShown
                if (hintShown) hintViewed = true
            }) {
                Text(if (hintShown) "Hide reply" else "Show reply")
            }
            if (hintShown) post.replies.orEmpty().forEach {
                Text("${SocialNarrative.profile(it.handle).name}: ${it.text}", fontSize = 13.sp, color = WarmEditorial.Muted)
            }
            Text(thread.questionComment, fontWeight = FontWeight.Bold)
            Column(Modifier.selectableGroup()) {
                thread.options.forEachIndexed { index, option ->
                    Row(Modifier.fillMaxWidth().selectable(index == selected, role = Role.RadioButton) { selected = index }
                        .heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RadioButton(selected = index == selected, onClick = null)
                        Text(option, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    }
                }
            }
            Button(enabled = selected in thread.options.indices, onClick = {
                val currentInterval = resumedAt?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0) } ?: 0
                onAnswer(selected, activeMillis + currentInterval, if (hintViewed) 1 else 0)
            }) { Text("Submit answer") }
        }
    }
}
