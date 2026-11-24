package id.slangverse.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ParticipantWelcome(onStart: (String) -> Boolean) {
    var username by rememberSaveable { mutableStateOf("") }
    var failed by rememberSaveable { mutableStateOf(false) }
    val trimmed = username.trim()
    val valid = trimmed.isEmpty() || validResearchUsername(trimmed)
    Box(Modifier.fillMaxSize().background(WarmEditorial.Paper).safeDrawingPadding().padding(24.dp),
        contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            EditorialTitle("Welcome to Slangverse", 34)
            Text("Pick a nickname or start as a guest. This research build records the answers you submit.", color = WarmEditorial.Ink)
            OutlinedTextField(username, { username = it; failed = false }, Modifier.fillMaxWidth(),
                label = { Text("Username (optional)") }, singleLine = true, isError = !valid,
                supportingText = { if (!valid) Text("Use up to 24 letters, numbers, underscores or hyphens.") })
            EditorialPrimary("Start playing", enabled = valid) { failed = !onStart(trimmed) }
            TextButton(onClick = { failed = !onStart("") }) { Text("Skip") }
            if (failed) Text("Your participant profile could not be saved. Try again.", color = WarmEditorial.CoralDeep)
        }
    }
}

@Composable
internal fun ParticipantSummary(participant: ResearchParticipant) {
    PaperCard {
        EditorialTitle(participant.username.orEmpty(), 24)
        val pending = participant.pendingAnswers
        if (pending > 0) Text("Waiting to sync · $pending", fontSize = 12.sp)
        participant.syncNotice?.let { Text(it, fontSize = 12.sp, color = WarmEditorial.Muted) }
    }
}
