package id.slangverse.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

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

internal data class PublishedPackRequest(val id: String, val sha256: String, val legacyTerm: String? = null)

/**
 * What one bank load produced. [problems] keeps the packs that did not arrive instead of throwing
 * them away: on a phone network a single timed-out pack used to blank the whole feed, so the packs
 * that did arrive are still worth showing. Every session here belongs to the same catalog snapshot,
 * and each question ID carries its pack and content version, so a partial set cannot mix revisions.
 */
internal data class PublishedFeedLoad(
    val sessions: List<StaticBankSession>,
    val problems: List<String> = emptyList()
) {
    val requestedCount: Int get() = sessions.size + problems.size
    val isPartial: Boolean get() = sessions.isNotEmpty() && problems.isNotEmpty()
}

internal fun selectPublishedPacks(catalog: ApiCatalog): List<PublishedPackRequest> =
    if (catalog.packs.isNotEmpty()) catalog.packs.take(12).map { PublishedPackRequest(it.packId, it.sha256) }
    else catalog.senses.take(12).map { PublishedPackRequest(it.senseId, it.packSha256, it.term) }

/**
 * Loads the selected packs concurrently, because up to twelve sequential HTTPS GETs on a slow mobile
 * connection kept the feed empty for the better part of a minute. A pack that fails is recorded and
 * the rest still load; only a load where nothing at all arrived is reported as a failure.
 */
internal suspend fun loadPublishedSessions(
    catalog: ApiCatalog,
    onProgress: (loaded: Int, total: Int) -> Unit = { _, _ -> },
    fetch: suspend (PublishedPackRequest) -> StaticBankSession
): PublishedFeedLoad {
    if (catalog.generationSource != "llm") throw ApiException("Default feed requires a published LLM catalog")
    val requests = selectPublishedPacks(catalog)
    if (requests.isEmpty()) throw ApiException("No generated packs have been published yet (${catalog.status})")
    val sessions = arrayOfNulls<StaticBankSession>(requests.size)
    val problems = arrayOfNulls<String>(requests.size)
    val cancellations = arrayOfNulls<CancellationException>(requests.size)
    val completed = AtomicInteger()
    onProgress(0, requests.size)
    coroutineScope {
        requests.forEachIndexed { index, request ->
            launch {
                try {
                    sessions[index] = fetch(request)
                    onProgress(completed.incrementAndGet(), requests.size)
                } catch (cancellation: CancellationException) {
                    // Kept, not swallowed: a cancelled fetch must not look like a pack that simply
                    // did not arrive, so it is rethrown after the scope has settled.
                    cancellations[index] = cancellation
                } catch (error: Exception) {
                    problems[index] = "Pack ${request.id}: ${error.message ?: "download failed"}"
                    onProgress(completed.incrementAndGet(), requests.size)
                }
            }
        }
    }
    cancellations.filterNotNull().firstOrNull()?.let { throw it }
    val arrived = sessions.filterNotNull()
    val failed = problems.filterNotNull()
    if (arrived.isEmpty() && failed.isNotEmpty()) throw ApiException(failed.joinToString("; "))
    return PublishedFeedLoad(arrived, failed)
}

internal fun mergePublishedFeed(
    sessions: List<StaticBankSession>, random: Random = Random.Default, savedOrder: List<String> = emptyList()
): List<FeedPracticePost> {
    val posts = sessions.flatMap { it.toFeedPosts() }
    val byId = posts.associateBy { it.thread.id }
    require(byId.size == posts.size) { "Duplicate published pack/question identity" }
    val ambiguous = posts.filter { displayAnswerOptions(it.thread.options) == null }
    if (ambiguous.isNotEmpty()) {
        throw ApiException("${ambiguous.size} question(s) have ambiguous answer choices: " +
            ambiguous.joinToString { it.thread.id })
    }
    return if (savedOrder.size == posts.size && savedOrder.toSet() == byId.keys)
        savedOrder.map { byId.getValue(it) }
    else posts.shuffled(random)
}

internal fun StaticBankSession.toFeedPosts(): List<FeedPracticePost> {
    require(session.source == "github-static-llm") { "Default feed accepts published LLM packs only" }
    return session.questions.map { q ->
        val answer = answers.getValue(q.id)
        val id = "${session.packId.ifBlank { session.senseId }}/${session.contentVersion}/${q.id}"
        val context = publishedContext(q.contextCategory, q.contextId)
        FeedPracticePost(
            thread = DetectiveCase(id, q.slang, q.options[answer.correctIndex], q.post.displayName,
                q.post.handle, q.post.hashtag, q.post.content, q.post.comments.map { it.text },
                q.questionComment.ifBlank { q.question }, q.options, answer.correctIndex, answer.explanation,
                replyTexts = q.replyTexts),
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
            sourceLabel = (if (session.editoriallyEdited) "Published LLM pack · agent-edited" else "Published LLM pack") +
                " · GitHub GET · locally graded · ${session.referenceStatus}",
            termTargetHandle = if (q.questionTarget == "reply") q.targetHandle else null,
            learningSenseId = q.senseId.ifBlank { session.senseId }.ifBlank { null }
        )
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
    root: String, loading: Boolean, loadedPacks: Int, totalPacks: Int, error: String?, notice: String?,
    sample: PracticeSample?, order: PracticeOrder, onOrder: (PracticeOrder) -> Unit,
    onRoot: (String) -> Unit, onRefresh: () -> Unit, onMode: (PracticeSample?) -> Unit
) {
    var settings by rememberSaveable { mutableStateOf(false) }
    var draftRoot by rememberSaveable(root) { mutableStateOf(root) }
    // Source plumbing stays collapsed: the feed opens on conversations, not on fetch diagnostics.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(sample?.sourceLabel ?: "Published questions · fetched over HTTPS",
                fontSize = 11.sp, color = WarmEditorial.CoralDeep, modifier = Modifier.weight(1f))
            if (sample == null) TextButton(onClick = onRefresh, enabled = !loading,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("Refresh", fontSize = 11.sp)
            }
            TextButton(onClick = { settings = !settings },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text(if (settings) "Close" else "Source", fontSize = 11.sp)
            }
        }
        if (settings) {
            OutlinedTextField(value = draftRoot, onValueChange = { draftRoot = it }, singleLine = true,
                label = { Text("Public GitHub bank folder URL") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onRoot(draftRoot.trim()) }, enabled = draftRoot.isNotBlank() && !loading) {
                    Text("Use this bank")
                }
            }
                if (sample == null) {
                    TextButton(onClick = { onMode(PracticeSample.Stories) }) { Text("Open offline sample") }
                    TextButton(onClick = { onMode(PracticeSample.Contexts) }) { Text("Open context practice") }
                } else TextButton(onClick = { onMode(null) }) { Text("Return to published questions") }
            Text("GitHub serves pre-generated packs. No API key and no live LLM call happen on your phone; grading is local practice.",
                fontSize = 12.sp, lineHeight = 17.sp, color = WarmEditorial.Muted)
            PracticeOrderPicker(order, onOrder)
        }
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
            Text(if (totalPacks > 0) "Loading published questions… $loadedPacks / $totalPacks packs"
                else "Loading published questions…", fontSize = 12.sp)
        }
        if (notice != null) Text(notice, fontSize = 12.sp, lineHeight = 17.sp, color = WarmEditorial.Muted)
        if (error != null) Text(error, color = WarmEditorial.CoralDeep, fontSize = 12.sp, lineHeight = 17.sp)
    }
}
