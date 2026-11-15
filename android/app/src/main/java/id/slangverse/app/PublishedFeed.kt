package id.slangverse.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object PublishedFeedDefaults {
    const val BANK_ROOT = "https://raw.githubusercontent.com/ferrysrt/Slangverse-Android/main/question-bank"
}

internal fun publishedContext(category: String, contextId: String): FeedContext? {
    if (category.isNotBlank()) return when (category) {
        "personal", "personal-experience" -> FeedContext.Personal
        "education" -> FeedContext.Education
        "work" -> FeedContext.Work
        "prediction" -> FeedContext.Prediction
        "sports" -> FeedContext.Sports
        "entertainment" -> FeedContext.Entertainment
        "opinion" -> FeedContext.Opinion
        "friends" -> FeedContext.Friends
        "event-reaction" -> FeedContext.EventReaction
        else -> null
    }
    // Navigation tags for legacy exported packs; not a semantic classification claim.
    return when (contextId) {
        "campus", "exam", "application" -> FeedContext.Education
        "gaming" -> FeedContext.Entertainment
        "office", "technology", "finance" -> FeedContext.Work
        "travel" -> FeedContext.Personal
        "sports" -> FeedContext.Sports
        "event" -> FeedContext.EventReaction
        else -> null
    }
}

internal fun StaticBankSession.toFeedPosts(): List<FeedPracticePost> {
    require(session.source == "github-static-llm") { "Default feed accepts published LLM packs only" }
    return session.questions.map { q ->
        val answer = answers.getValue(q.id)
        val id = "${session.senseId}/${session.contentVersion}/${q.id}"
        val context = publishedContext(q.contextCategory, q.contextId)
        FeedPracticePost(
            thread = DetectiveCase(id, q.slang, q.options[answer.correctIndex], q.post.displayName,
                q.post.handle, q.post.hashtag, q.post.content, q.post.comments.map { it.text },
                q.questionComment.ifBlank { q.question }, q.options, answer.correctIndex, answer.explanation),
            context = context,
            difficulty = when (q.difficulty) {
                "easy" -> FeedDifficulty.Easy
                "medium" -> FeedDifficulty.Medium
                else -> FeedDifficulty.Hard
            },
            avatarId = q.post.avatarId,
            replies = q.post.comments.mapIndexed { index, reply ->
                StoryReply("$id-reply-$index", reply.handle, reply.text, avatarId = reply.avatarId)
            },
            published = true,
            sourceLabel = "Published LLM pack · GitHub GET · locally graded · ${session.referenceStatus}")
    }
}

internal fun feedProfiles(posts: List<FeedPracticePost>): Map<String, FictionalProfile> {
    val profiles = SocialNarrative.profiles.toMutableMap()
    posts.forEach { post ->
        profiles.putIfAbsent(post.thread.handle, FictionalProfile(post.thread.handle, post.thread.author,
            "A fictional account in this published question pack.", resolveProfilePictureId(post.avatarId, post.thread.handle)))
        post.replies.orEmpty().forEach { reply ->
            profiles.putIfAbsent(reply.handle, FictionalProfile(reply.handle, reply.handle.trimStart('@'),
                "A fictional participant in this conversation.", resolveProfilePictureId(reply.avatarId, reply.handle)))
        }
    }
    return profiles
}

@Composable
internal fun PublishedBankControls(
    root: String, catalog: ApiCatalog?, selectedSense: String, loading: Boolean, error: String?, demo: Boolean,
    onRoot: (String) -> Unit, onSelect: (String) -> Unit, onRefresh: () -> Unit, onMode: (Boolean) -> Unit
) {
    var settings by rememberSaveable { mutableStateOf(false) }
    var draftRoot by rememberSaveable(root) { mutableStateOf(root) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (demo) "OFFLINE SAMPLE · HAND-AUTHORED" else "PUBLISHED LLM QUESTIONS · GITHUB GET",
            fontSize = 11.sp, color = WarmEditorial.CoralDeep)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRefresh, enabled = !loading && !demo) { Text("Refresh bank") }
            TextButton(onClick = { settings = !settings }) { Text("Question source") }
        }
        if (settings) {
            OutlinedTextField(value = draftRoot, onValueChange = { draftRoot = it }, singleLine = true,
                label = { Text("Public GitHub bank folder URL") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onRoot(draftRoot.trim()) }, enabled = draftRoot.isNotBlank() && !loading) { Text("Use this bank") }
            TextButton(onClick = { onMode(!demo) }) {
                Text(if (demo) "Return to published LLM questions" else "Open hand-authored offline sample")
            }
            Text("GitHub distributes pre-generated packs. No API key or live LLM call is used on your phone. Static grading is local practice.",
                fontSize = 12.sp, color = WarmEditorial.Muted)
        }
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Loading published questions…", fontSize = 13.sp)
        }
        if (error != null) Text(error, color = WarmEditorial.CoralDeep, fontSize = 13.sp)
        if (!demo && !loading && catalog?.senses?.isEmpty() == true)
            Text("No generated packs have been published yet. Refresh after the bank publisher finishes.", fontSize = 13.sp)
        if (!demo && catalog != null && catalog.senses.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                catalog.senses.forEach { sense ->
                    FilterChip(selected = sense.senseId == selectedSense, enabled = !loading,
                        onClick = { onSelect(sense.senseId) }, label = { Text(sense.term) })
                }
            }
        }
    }
}
