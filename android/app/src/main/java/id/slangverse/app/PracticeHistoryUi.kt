package id.slangverse.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PracticeHistoryDialog(progress: FeedPracticeProgress, onDismiss: () -> Unit, onOpenPost: (String) -> Unit) {
    val postsById = progress.posts.associateBy { it.thread.id }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Answer history") }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(progress.decisions.asReversed(), key = { it.caseId }) { result ->
                val post = postsById.getValue(result.caseId)
                OutlinedCard(onClick = { onOpenPost(result.caseId) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(post.thread.term, fontWeight = FontWeight.SemiBold)
                            post.context?.let { Text(it.label, fontSize = 12.sp, color = WarmEditorial.Muted) }
                        }
                        Text(if (result.correct) "Correct" else "Review", fontSize = 12.sp,
                            color = if (result.correct) WarmEditorial.Olive else WarmEditorial.CoralDeep)
                        Text("+${result.xpEarned} XP", fontSize = 12.sp)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
