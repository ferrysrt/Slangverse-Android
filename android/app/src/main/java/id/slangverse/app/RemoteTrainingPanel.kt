package id.slangverse.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Night = WarmEditorial.Ink
private val PanelColor = WarmEditorial.Card
private val PanelRaised = WarmEditorial.Blush
private val MintGreen = WarmEditorial.Coral
private val CoralPink = WarmEditorial.CoralDeep
private val Secondary = WarmEditorial.Muted
private val PrimaryText = WarmEditorial.Ink

/**
 * Session gameplay supports two distinctly labeled sources: public GitHub GET static demo
 * (local scoring) and mock/live-LLM Node backend (server-side scoring).
 * Parent composable provides vertical scrolling.
 */
@Composable
fun RemoteTrainingPanel() {
    val scope = rememberCoroutineScope()
    var questionMode by rememberSaveable { mutableStateOf("github") }
    var githubRoot by rememberSaveable { mutableStateOf("") }
    var staticAnswers by remember { mutableStateOf<Map<String, StaticAnswer>>(emptyMap()) }
    var serverUrl by rememberSaveable { mutableStateOf("http://10.0.2.2:8787") }
    // Deliberately not saved to disk or Android saved-instance state.
    var clientToken by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<ApiSession?>(null) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var pinnedClueIndex by remember { mutableIntStateOf(-1) }
    var questionOpened by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ApiGrade?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var totalXp by remember { mutableIntStateOf(0) }
    var correctCount by remember { mutableIntStateOf(0) }
    var combo by remember { mutableIntStateOf(0) }
    var highestCombo by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var expandedReplies by remember { mutableStateOf(false) }
    var catalog by remember { mutableStateOf<ApiCatalog?>(null) }
    var selectedSenseId by rememberSaveable { mutableStateOf("cooked-trouble") }

    @Composable fun Action(text: String, enabled: Boolean = true, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = Night),
            shape = RoundedCornerShape(17.dp)
        ) { Text(text, fontWeight = FontWeight.Bold) }
    }
    @Composable fun Sheet(content: @Composable ColumnScope.() -> Unit) {
        Column(
            Modifier.fillMaxWidth().background(PanelColor, RoundedCornerShape(18.dp)).padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }

    if (session == null) {
        Sheet {
            Text("Choose question source", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { questionMode = "github"; catalog = null; error = null },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, if (questionMode == "github") MintGreen else Secondary)
                ) { Text("GitHub GET", color = MintGreen, fontSize = 12.sp) }
                OutlinedButton(
                    onClick = { questionMode = "backend"; catalog = null; error = null },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, if (questionMode == "backend") MintGreen else Secondary)
                ) { Text("Live Backend", color = MintGreen, fontSize = 12.sp) }
            }
            if (questionMode == "github") {
                Text("GET-only public JSON question bank. No laptop IP or backend required; demo grading runs locally.", color = MintGreen, fontSize = 12.sp)
                OutlinedTextField(
                    value = githubRoot,
                    onValueChange = { githubRoot = it; catalog = null },
                    label = { Text("Public GitHub JSON folder URL") },
                    placeholder = { Text("https://raw.githubusercontent.com/.../public-question-bank") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("The repository holding JSON must be PUBLIC, or use public GitHub Pages. No GitHub token is used. Static packs expose answer keys; not suitable for tamper-proof player scoring.", color = Secondary, fontSize = 11.sp)
            } else {
            Text("Connect to the backend", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Real HTTP requests · mock or live LLM · server-side grading", color = MintGreen, fontSize = 11.sp)
            Text("Emulator address: http://10.0.2.2:8787", color = Secondary, fontSize = 12.sp)
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Backend base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MintGreen, unfocusedBorderColor = Secondary,
                    focusedTextColor = PrimaryText, unfocusedTextColor = PrimaryText,
                    focusedLabelColor = MintGreen, unfocusedLabelColor = Secondary
                )
            )
            OutlinedTextField(
                value = clientToken,
                onValueChange = { clientToken = it },
                label = { Text("Backend access token (optional for local)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MintGreen, unfocusedBorderColor = Secondary,
                    focusedTextColor = PrimaryText, unfocusedTextColor = PrimaryText,
                    focusedLabelColor = MintGreen, unfocusedLabelColor = Secondary
                )
            )
            Text("For cloud hosting, enter the HTTPS URL and the separate backend access token. NEVER enter your OpenRouter API key here.", color = MintGreen, fontSize = 11.sp)
            Text("For local Wi-Fi testing, use http://LAPTOP-IP:8787 on a trusted network with HOST=0.0.0.0. Do not use public HTTP.", color = Secondary, fontSize = 12.sp)
            }
            Action("LOAD AVAILABLE SLANG", enabled = !loading && (questionMode != "github" || githubRoot.isNotBlank())) {
                loading = true; error = null
                scope.launch {
                    try {
                        catalog = if (questionMode == "github") GithubStaticBank(githubRoot).fetchCatalog()
                            else SlangverseApi(serverUrl, clientToken).fetchCatalog()
                        if (catalog?.senses?.none { it.senseId == selectedSenseId } == true) {
                            selectedSenseId = catalog!!.senses.firstOrNull()?.senseId ?: "cooked-trouble"
                        }
                    } catch (e: Exception) { error = e.message ?: "Catalog unavailable" }
                    finally { loading = false }
                }
            }
            if (catalog != null) {
                Text(if (questionMode == "github") "Pre-generated GitHub question bank · local demo scoring"
                     else "${catalog!!.candidateCount} unreviewed candidate terms. Only provisional selections below are playable.",
                    color = Secondary, fontSize = 11.sp)
                catalog!!.senses.forEach { sense ->
                    OutlinedButton(
                        onClick = { selectedSenseId = sense.senseId },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, if (selectedSenseId == sense.senseId) MintGreen else Secondary)
                    ) {
                        Text((if (selectedSenseId == sense.senseId) "✓ " else "") +
                            sense.term + " · " + sense.reviewStatus, color = MintGreen)
                    }
                }
            }
            Action("START 10-CONTEXT SESSION", enabled = !loading && catalog != null) {
                loading = true; error = null
                scope.launch {
                    try {
                        if (questionMode == "github") {
                            val downloaded = GithubStaticBank(githubRoot).fetchSession(selectedSenseId)
                            staticAnswers = downloaded.answers
                            session = downloaded.session
                        } else {
                            staticAnswers = emptyMap()
                            session = SlangverseApi(serverUrl, clientToken).createSession(selectedSenseId)
                        }
                        currentIndex = 0; selectedIndex = -1; pinnedClueIndex = -1; questionOpened = false; result = null; finished = false
                        correctCount = 0; combo = 0; highestCombo = 0; totalXp = 0
                    } catch (e: Exception) { error = e.message ?: "Connection failed" }
                    finally { loading = false }
                }
            }
        }
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = MintGreen)
            Text(if (questionMode == "github") "Fetching public GitHub JSON with GET..."
                 else "Connecting to backend. Live generation can take longer than mock mode.",
                color = Secondary, fontSize = 12.sp)
        }
        if (error != null) Text(error.orEmpty(), color = CoralPink, fontSize = 12.sp)
        return
    }

    val activeSession = session ?: return
    val current = activeSession.questions.getOrNull(currentIndex)
    if (finished || current == null) {
        Sheet {
            Text("🏆 SESSION COMPLETE", fontSize = 24.sp, color = MintGreen, fontWeight = FontWeight.Bold)
            Text("${activeSession.questions.size} contexts of '${activeSession.questions.first().slang}'", color = Secondary)
            Text("Correct: $correctCount / ${activeSession.questions.size}", fontWeight = FontWeight.Bold)
            Text("Total earned XP: $totalXp", fontSize = 21.sp, color = MintGreen)
            Text("Best combo: $highestCombo", color = Secondary)
            Text("Data source: ${activeSession.source.uppercase()} · Reference: ${activeSession.referenceStatus}. Semantic accuracy is not expert-validated.", color = Secondary, fontSize = 11.sp)
            if (activeSession.source.startsWith("github-static")) {
                Text("Local demo scores only: answer keys are included in the publicly readable JSON.", color = Secondary, fontSize = 11.sp)
            }
            Action("START NEW SESSION") { session = null; questionOpened = false; finished = false; error = null }
        }
        return
    }

    Text("QUESTION SOURCE: ${activeSession.source.uppercase()} · ${activeSession.senseId} · ${activeSession.referenceStatus}", color = Secondary, fontSize = 10.sp)
    if (activeSession.source.startsWith("github-static")) {
        Text("Pre-generated GitHub pack · local demo scoring · NOT a live LLM request", color = Secondary, fontSize = 10.sp)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("CONTEXT DETECTIVE · ${current.difficulty.uppercase()}", color = MintGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("${currentIndex + 1} / ${activeSession.questions.size}", color = Secondary)
    }
    LinearProgressIndicator(
        progress = { currentIndex.toFloat() / activeSession.questions.size },
        color = MintGreen,
        modifier = Modifier.fillMaxWidth()
    )
    Text("Practice combo ×$combo     $totalXp XP", color = CoralPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Text("Live/GitHub question packs contain 10 contexts of one slang; V3 casual cases are a separate mixed-slang demo.", color = Secondary, fontSize = 10.sp)
    Sheet {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfilePicture(current.post.handle, current.post.avatarId, size = 43.dp)
            Column(Modifier.weight(1f)) {
                Text(current.post.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(current.post.handle + " · Fictional account", color = Secondary, fontSize = 11.sp)
            }
            Text("⋯", fontSize = 21.sp, color = Secondary)
        }
        Text(current.post.content, color = PrimaryText, fontSize = 16.sp, lineHeight = 23.sp)
        Text(current.post.hashtag + " · Fictional community", color = MintGreen, fontSize = 11.sp)
        HorizontalDivider(color = WarmEditorial.Sand)
        Text("♡   Reply   ↻   Share   ♧", color = Secondary, fontSize = 12.sp)
        HorizontalDivider(color = WarmEditorial.Sand)
        Text("01 · INVESTIGATE · PIN A REPLY", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        current.post.comments.take(if (expandedReplies) 3 else 1).forEachIndexed { idx, comment ->
            Row(
                Modifier.fillMaxWidth()
                    .background(if (pinnedClueIndex == idx) PanelRaised else PanelColor, RoundedCornerShape(12.dp))
                    .clickable(enabled = result == null && !loading) { pinnedClueIndex = idx }
                    .padding(7.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                ProfilePicture(comment.handle, comment.avatarId, size = 28.dp)
                Column {
                    Text(comment.handle, color = MintGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(comment.text, color = PrimaryText, fontSize = 12.sp, lineHeight = 18.sp)
                    if (pinnedClueIndex == idx) Text("✓ PINNED", color = MintGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        TextButton(onClick = { expandedReplies = !expandedReplies }) {
            Text(if (expandedReplies) "Hide extra replies ↑" else "Need a clue? View more replies ↓", color = MintGreen, fontSize = 12.sp)
        }
        Text("Save any reply as a clue if helpful; evidence is optional and unscored.",
            color = Secondary, fontSize = 10.sp)
        HorizontalDivider(color = WarmEditorial.Sand)
        val specialShape = RoundedCornerShape(16.dp)
        Column(
            Modifier.fillMaxWidth()
                .background(WarmEditorial.Blush, specialShape)
                .border(1.dp, WarmEditorial.Peach, specialShape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfilePicture("@confuseduser", size = 31.dp)
                Text("@confuseduser", fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    color = Night, modifier = Modifier.weight(1f))
                Surface(color = WarmEditorial.Card, shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, WarmEditorial.Coral)) {
                    Text("✳ DECODE", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        color = CoralPink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("“" + current.questionComment.ifBlank { current.question } + "”",
                fontSize = 18.sp, lineHeight = 25.sp, color = Night)
            if (!questionOpened && result == null) {
                Action("REPLY WITH THE MEANING ↓") { questionOpened = true }
            } else {
    Text("YOUR REPLY · CHOOSE THE MEANING", color = CoralPink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    current.options.forEachIndexed { index, option ->
        val isSelected = selectedIndex == index
        val isCorrect = result?.correctIndex == index
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(enabled = result == null && !loading) { selectedIndex = index },
            color = if (isCorrect) WarmEditorial.Sage else if (isSelected) WarmEditorial.Peach else PanelColor,
            border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MintGreen else PanelRaised),
            shape = RoundedCornerShape(17.dp)
        ) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${'A' + index}    $option", color = PrimaryText, fontSize = 13.sp)
            }
        }
    }
    if (result != null) {
        Sheet {
            Text(if (result!!.correct) "✓ Correct! +${result!!.xpEarned} XP" else "Not quite. +${result!!.xpEarned} XP", color = MintGreen, fontWeight = FontWeight.Bold)
            Text(result!!.explanation, color = PrimaryText, fontSize = 12.sp)
            if (pinnedClueIndex in current.post.comments.indices) {
                Text("Your pinned clue: " + current.post.comments[pinnedClueIndex].text,
                    color = Secondary, fontSize = 12.sp)
            }
            Text(if (activeSession.source.startsWith("github-static"))
                 "Locally graded demo; correct answers are public in GitHub JSON."
                 else "Server-graded result; not evidence of semantic accuracy.", color = Secondary, fontSize = 10.sp)
        }
        Action(if (currentIndex == activeSession.questions.lastIndex) "SHOW SESSION RESULTS" else "NEXT CONVERSATION →") {
            if (currentIndex == activeSession.questions.lastIndex) finished = true
            else { currentIndex += 1; selectedIndex = -1; pinnedClueIndex = -1; questionOpened = false; result = null; expandedReplies = false; error = null }
        }
    } else {
        Action(if (loading) "SUBMITTING…" else "POST YOUR INTERPRETATION →", enabled = selectedIndex >= 0 && !loading) {
            loading = true; error = null
            scope.launch {
                try {
                    val response = if (activeSession.source.startsWith("github-static"))
                        gradeStaticAnswer(staticAnswers, current.id, selectedIndex, currentIndex, totalXp)
                    else SlangverseApi(serverUrl, clientToken).submitAnswer(activeSession.id, current.id, selectedIndex)
                    result = response
                    totalXp = response.totalXp
                    if (response.correct) {correctCount += 1; combo += 1; highestCombo = maxOf(highestCombo, combo)} else combo = 0
                } catch (e: Exception) { error = e.message ?: "Submission failed" }
                finally { loading = false }
            }
        }
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MintGreen)
    if (error != null) Text(error.orEmpty(), color = CoralPink, fontSize = 12.sp)
            }
        }
    }
}
