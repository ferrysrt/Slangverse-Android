package id.slangverse.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class DetectiveStep { Investigate, Feedback, Report }

/** A three-case, locally scored V3 demonstration. Not the live LLM or research-testing mode. */
@Composable
internal fun ContextDetective(
    startCaseId: String,
    onExit: () -> Unit
) {
    val cases = remember(startCaseId) { ContextDetectiveGame.orderedFrom(startCaseId) }
    var round by remember(startCaseId) { mutableIntStateOf(0) }
    var selected by remember(startCaseId) { mutableIntStateOf(-1) }
    var pinned by remember(startCaseId) { mutableIntStateOf(-1) }
    var expanded by remember(startCaseId) { mutableStateOf(true) }
    var questionOpened by remember(startCaseId) { mutableStateOf(false) }
    var step by remember(startCaseId) { mutableStateOf(DetectiveStep.Investigate) }
    val decisions = remember(startCaseId) { mutableStateListOf<DetectiveDecision>() }
    val item = cases[round]

    fun advance() {
        if (round == cases.lastIndex) {
            step = DetectiveStep.Report
        }
        else {
            round += 1
            selected = -1
            pinned = -1
            expanded = true
            questionOpened = false
            step = DetectiveStep.Investigate
        }
    }
    fun restart() {
        round = 0
        selected = -1
        pinned = -1
        expanded = true
        questionOpened = false
        decisions.clear()
        step = DetectiveStep.Investigate
    }
    fun submit() {
        if (step != DetectiveStep.Investigate || !questionOpened || selected < 0) return
        if (decisions.any { it.caseId == item.id }) return
        decisions.add(ContextDetectiveGame.grade(item, selected, pinned))
        step = DetectiveStep.Feedback
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) {
                Icon(Icons.Outlined.ArrowBack, "Back to Feed", tint = WarmEditorial.CoralDeep)
            }
            Column(Modifier.weight(1f)) {
                CDLabel("CONTEXT DETECTIVE")
                Text(
                    if (step == DetectiveStep.Report) "Case Report"
                    else "Case " + (round + 1) + " of " + cases.size,
                    fontFamily = WarmEditorial.Display,
                    fontSize = 27.sp, color = WarmEditorial.Ink
                )
            }
            CDLabel("DEMO")
        }

        if (step == DetectiveStep.Report) {
            CDCard(background = WarmEditorial.Blush) {
                CDLabel("CASE FILE COMPLETE")
                Text(decisions.count { it.correct }.toString() + " / " + cases.size + " decoded",
                    fontSize = 35.sp, fontFamily = WarmEditorial.Display, color = WarmEditorial.Ink)
                Text(decisions.sumOf { it.xpEarned }.toString() +
                    " sample XP · three different slang terms",
                    color = WarmEditorial.CoralDeep, fontSize = 12.sp)
                Text("No timer, energy limit, or streak punishment.",
                    fontSize = 12.sp, color = WarmEditorial.Muted)
            }
            decisions.forEach { result ->
                val sample = cases.first { it.id == result.caseId }
                CDCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(sample.term, fontFamily = WarmEditorial.Display, fontSize = 24.sp)
                        Text(if (result.correct) "✓ MATCH" else "↺ REVIEW",
                            fontWeight = FontWeight.Bold, fontSize = 10.sp,
                            color = if (result.correct) WarmEditorial.Olive else WarmEditorial.CoralDeep)
                    }
                    Text("Meaning: " + sample.meaning, fontSize = 13.sp)
                    Text("Clue: " + if (result.pinnedClueIndex == -1) "No clue saved" else sample.comments[result.pinnedClueIndex],
                        fontSize = 11.sp, color = WarmEditorial.Muted)
                }
            }
            Text("These are hand-authored provisional examples. This summary is not research data or a measure of language mastery.",
                fontSize = 11.sp, color = WarmEditorial.Muted)
            CDButton("PLAY AGAIN →", onClick = { restart() })
            CDOutline("RETURN TO FEED", onExit)
            return@Column
        }

        CDCard(background = WarmEditorial.Blush) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CDLabel("CASE " + (round + 1) + " · " + item.hashtag)
                Text(decisions.size.toString() + " resolved", color = WarmEditorial.Muted, fontSize = 11.sp)
            }
            LinearProgressIndicator(
                progress = { (round + 1).toFloat() / cases.size },
                modifier = Modifier.fillMaxWidth().height(7.dp),
                color = WarmEditorial.Coral, trackColor = WarmEditorial.Sand
            )
            Text(if (step == DetectiveStep.Investigate)
                "Read the post and replies, then tap the highlighted comment to answer. Saving a clue is optional."
                else "Your interpretation is now visible inside the highlighted reply.",
                fontSize = 12.sp, color = WarmEditorial.Ink, lineHeight = 18.sp)
        }

        CDCard {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                ProfilePicture(item.handle, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(item.author, fontFamily = WarmEditorial.Display,
                        fontSize = 20.sp, color = WarmEditorial.Ink)
                    Text(item.handle + " · Fictional account", fontSize = 11.sp,
                        color = WarmEditorial.Muted)
                }
            }
            val highlighted = buildAnnotatedString {
                val at = item.post.indexOf(item.term, ignoreCase = true)
                if (at < 0) append(item.post) else {
                    append(item.post.take(at))
                    withStyle(SpanStyle(background = WarmEditorial.Peach, fontWeight = FontWeight.Bold)) {
                        append(item.post.substring(at, at + item.term.length))
                    }
                    append(item.post.drop(at + item.term.length))
                }
            }
            Text(highlighted, fontFamily = WarmEditorial.Display, fontSize = 20.sp,
                lineHeight = 27.sp, color = WarmEditorial.Ink)
            HorizontalDivider(color = WarmEditorial.Sand)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CDLabel("REPLIES · FIND A CLUE")
                Text(item.comments.size.toString() + " replies", fontSize = 11.sp,
                    color = WarmEditorial.Muted)
            }
            item.comments.take(if (expanded) item.comments.size else 1)
                .forEachIndexed { i, comment ->
                    val active = pinned == i
                    val shape = RoundedCornerShape(13.dp)
                    Row(Modifier.fillMaxWidth()
                        .background(if (active) WarmEditorial.Blush else WarmEditorial.Card, shape)
                        .border(if (active) 2.dp else 1.dp,
                            if (active) WarmEditorial.Coral else WarmEditorial.Sand, shape)
                        .clickable(enabled = step == DetectiveStep.Investigate) { pinned = i }
                        .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.Top) {
                        Icon(if (active) Icons.Outlined.BookmarkAdded else Icons.Outlined.Bookmark,
                            contentDescription = if (active) "Pinned clue" else "Pin clue",
                            tint = if (active) WarmEditorial.CoralDeep else WarmEditorial.Muted,
                            modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text("@reply" + (i + 1), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(comment, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                        if (active) Text("PINNED", fontSize = 9.sp,
                            fontWeight = FontWeight.Bold, color = WarmEditorial.CoralDeep)
                    }
                }
            TextButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null, tint = WarmEditorial.CoralDeep)
                Text(if (expanded) "Hide extra replies" else "Read more replies and clues",
                    fontSize = 12.sp, color = WarmEditorial.CoralDeep)
            }
            if (pinned >= 0) Text(
                "Clue saved (optional). This choice is not graded.",
                fontSize = 11.sp, color = WarmEditorial.CoralDeep
            )
            // This is the ONLY location of the question and answer controls.
            // The interaction reads as a reply to another fictional user, not a separate quiz card.
            HorizontalDivider(color = WarmEditorial.Sand)
            val commentShape = RoundedCornerShape(16.dp)
            Column(
                Modifier.fillMaxWidth()
                    .background(WarmEditorial.Blush, commentShape)
                    .border(1.dp, WarmEditorial.Peach, commentShape)
                    .padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ProfilePicture("@confuseduser", size = 32.dp)
                    Text("@confuseduser", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = WarmEditorial.Ink, modifier = Modifier.weight(1f))
                    Surface(color = WarmEditorial.Card, shape = RoundedCornerShape(15.dp),
                        border = BorderStroke(1.dp, WarmEditorial.Coral)) {
                        Text("✳ DECODE", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WarmEditorial.CoralDeep)
                    }
                }
                Text("“" + item.questionComment + "”", fontFamily = WarmEditorial.Display,
                    fontSize = 19.sp, lineHeight = 26.sp, color = WarmEditorial.Ink)

                if (!questionOpened && step == DetectiveStep.Investigate) {
                    CDButton("REPLY WITH THE MEANING ↓", onClick = { questionOpened = true })
                } else {
                    CDLabel("YOUR REPLY · MEANING OF “" + item.term + "”")
                    item.options.forEachIndexed { i, option ->
                        CDOption(
                            index = i,
                            text = option,
                            selected = selected == i,
                            correct = step == DetectiveStep.Feedback && i == item.correctIndex,
                            revealed = step == DetectiveStep.Feedback,
                            onClick = { selected = i }
                        )
                    }
                    if (step == DetectiveStep.Investigate) {
                        Text("Save a clue if helpful; it does not affect scoring.",
                            fontSize = 11.sp, color = WarmEditorial.Muted)
                        CDButton("POST YOUR INTERPRETATION →", enabled = selected >= 0) {
                            submit()
                        }
                    } else {
                        val decision = decisions.last()
                        Column(
                            Modifier.fillMaxWidth().background(WarmEditorial.Card, RoundedCornerShape(13.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            LearningFeedbackPanel(
                                correct = decision.correct, xp = decision.xpEarned,
                                meaning = item.meaning, explanation = item.explanation,
                                sourceLabel = "Hand-authored demo · saved clues are not graded",
                                savedClue = item.comments.getOrNull(pinned))
                            CDButton(if (round == cases.lastIndex) "OPEN CASE REPORT →"
                                else "NEXT CONVERSATION →", onClick = { advance() })
                        }
                    }
                }
            }
        }

        Text("Interactive Comment Quiz V3.1 · Local demo · No live LLM or participant scoring.",
            fontSize = 10.sp, color = WarmEditorial.Muted)
    }
}

@Composable
private fun CDCard(
    background: Color = WarmEditorial.Card,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().background(background, shape)
        .border(1.dp, WarmEditorial.Sand, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
}

@Composable
private fun CDLabel(text: String) {
    Text(text, color = WarmEditorial.CoralDeep,
        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
}

@Composable
private fun CDButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 49.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = WarmEditorial.Coral, contentColor = Color.White)) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CDOutline(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, WarmEditorial.CoralDeep)) {
        Text(text, color = WarmEditorial.CoralDeep, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun CDOption(
    index: Int, text: String, selected: Boolean,
    correct: Boolean, revealed: Boolean, onClick: () -> Unit
) {
    val bg = if (revealed && correct) WarmEditorial.Sage
        else if (selected) WarmEditorial.Blush else WarmEditorial.Card
    val line = if (revealed && correct) WarmEditorial.Olive
        else if (selected) WarmEditorial.Coral else WarmEditorial.Sand
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().background(bg, shape)
        .border(if (selected || (revealed && correct)) 2.dp else 1.dp, line, shape)
        .clickable(enabled = !revealed, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(('A' + index).toString() + ".", fontFamily = WarmEditorial.Display,
            fontSize = 20.sp, color = WarmEditorial.Ink)
        Text(text, color = WarmEditorial.Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (revealed && correct) {
            Icon(Icons.Outlined.CheckCircle, "Correct answer",
                tint = WarmEditorial.Olive, modifier = Modifier.size(21.dp))
        } else if (selected && !revealed) {
            Icon(Icons.Outlined.CheckCircle, "Selected",
                tint = WarmEditorial.CoralDeep, modifier = Modifier.size(21.dp))
        } else if (selected && revealed) {
            Text("YOUR CHOICE", fontSize = 9.sp, color = WarmEditorial.CoralDeep)
        }
    }
}
