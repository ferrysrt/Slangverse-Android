package id.slangverse.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PracticeOrderPicker(order: PracticeOrder, onOrder: (PracticeOrder) -> Unit) {
    Text("Practice order", fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PracticeOrder.entries.forEach { option ->
            FilterChip(selected = order == option, onClick = { onOrder(option) },
                label = { Text(option.title, fontSize = 11.sp) }, colors = learningChipColors())
        }
    }
    Text(order.description, fontSize = 12.sp, color = WarmEditorial.Muted)
}

@Composable
internal fun NextPracticeCard(recommendation: PracticeRecommendation?, onOpenPost: (String) -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = WarmEditorial.Sage)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Next practice", fontWeight = FontWeight.SemiBold)
            Text(recommendation?.reason ?: "You’ve answered every question in this feed.",
                fontSize = 12.sp, color = WarmEditorial.Ink)
            recommendation?.let {
                TextButton(onClick = { onOpenPost(it.postId) }) { Text("Open next question") }
            }
        }
    }
}

@Composable
internal fun SlangCollection(learning: PracticeLearning, onOpenPost: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var startedOnly by rememberSaveable { mutableStateOf(false) }
    Text("Slang collection", fontFamily = WarmEditorial.Display, fontSize = 29.sp)
    if (learning.collection.isEmpty()) {
        Text("Load a question bank before opening the collection.", color = WarmEditorial.Muted)
        return
    }
    Text("${learning.matchedSenses} of ${learning.collection.size} meanings matched · ${learning.masteredSenses} mastered",
        fontSize = 13.sp, color = WarmEditorial.CoralDeep, fontWeight = FontWeight.SemiBold)
    Text("Mastered means your latest answer was correct and you’ve matched that meaning in two contexts.",
        fontSize = 12.sp, color = WarmEditorial.Muted)
    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
        label = { Text("Find a slang term") }, singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !startedOnly, onClick = { startedOnly = false }, label = { Text("All") }, colors = learningChipColors())
        FilterChip(selected = startedOnly, onClick = { startedOnly = true }, label = { Text("Started") }, colors = learningChipColors())
    }
    val filtered = learning.collection.filter { sense ->
        sense.term.contains(query.trim(), ignoreCase = true) && (!startedOnly || sense.stage != LearningStage.New)
    }
    if (filtered.isEmpty()) Text("No matching entries.", color = WarmEditorial.Muted)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        items(filtered, key = { it.key }) { sense ->
            OutlinedCard(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(containerColor = WarmEditorial.Card)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(sense.term, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(sense.stage.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = if (sense.stage == LearningStage.Mastered) WarmEditorial.Olive else WarmEditorial.CoralDeep)
                    Text(sense.revealedMeaning ?: "Answer once to reveal this meaning.", fontSize = 13.sp)
                    if (sense.availableContexts > 0) {
                        Text("${sense.matchedContexts} of ${sense.availableContexts} contexts matched",
                            fontSize = 12.sp, color = WarmEditorial.Muted)
                    }
                    sense.nextPostId?.let { postId ->
                        TextButton(onClick = { onOpenPost(postId) }) { Text("Practice this meaning") }
                    }
                }
            }
        }
    }
}

@Composable
private fun learningChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = WarmEditorial.Peach, selectedLabelColor = WarmEditorial.CoralDeep,
    labelColor = WarmEditorial.Ink
)
