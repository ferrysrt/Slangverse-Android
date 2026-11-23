package id.slangverse.app

import java.text.Normalizer

internal enum class PracticeOrder(val title: String, val description: String) {
    Browse("Browse", "Shuffle the loaded feed when you refresh."),
    Adaptive("Adaptive", "Revisit meanings you are still learning in new contexts when available."),
    Fixed("Fixed order", "Use a stable order for this bank revision with no adaptive suggestions.");

    companion object {
        fun fromStored(value: String?): PracticeOrder = entries.firstOrNull { it.name == value } ?: Browse
    }
}

internal enum class LearningStage(val label: String) {
    New("New"), Practicing("In practice"), Decoded("Decoded"), Mastered("Mastered")
}

internal data class CollectedSense(
    val key: String,
    val term: String,
    val revealedMeaning: String?,
    val stage: LearningStage,
    val matchedContexts: Int,
    val availableContexts: Int,
    val nextPostId: String?
)

internal data class PracticeRecommendation(val postId: String, val reason: String)

private data class PracticeAttempt(val post: FeedPracticePost, val correct: Boolean)

private val extraWhitespace = Regex("\\s+")

private fun studyText(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
    .trim().replace(extraWhitespace, " ").lowercase()

private fun FeedPracticePost.studyKey(): String = learningSenseId?.takeIf(String::isNotBlank)?.let { "sense:$it" }
    ?: "meaning:${studyText(thread.term)}\u0000${studyText(thread.meaning)}"

internal class PracticeLearning(private val progress: FeedPracticeProgress) {
    private val postsById = progress.posts.associateBy { it.thread.id }
    private val activeAttempts = progress.answers.mapNotNull { (id, selected) ->
        val post = postsById[id] ?: return@mapNotNull null
        if (selected !in post.thread.options.indices) return@mapNotNull null
        PracticeAttempt(post, selected == post.thread.correctIndex)
    }
    private val attemptsBySense = activeAttempts.groupBy { it.post.studyKey() }
    private val latestSense = activeAttempts.lastOrNull()?.post?.studyKey()

    private enum class Priority(val reason: String) {
        MissedNewContext("Revisit a meaning you missed in a different context."),
        MatchedNewContext("Try a meaning you matched in another context."),
        Unseen("Try a meaning you haven’t practiced yet."),
        MissedAvailable("Try another available question for a meaning you missed."),
        MorePractice("Continue practicing with an unanswered question.")
    }

    val collection: List<CollectedSense> = progress.posts.groupBy { it.studyKey() }.map { (key, posts) ->
        val attempts = attemptsBySense[key].orEmpty()
        val correct = attempts.filter { it.correct }
        val matchedContexts = correct.mapNotNull { it.post.context }.distinct().size
        val stage = when {
            attempts.isEmpty() -> LearningStage.New
            !attempts.last().correct -> LearningStage.Practicing
            matchedContexts >= 2 -> LearningStage.Mastered
            else -> LearningStage.Decoded
        }
        CollectedSense(key, posts.first().thread.term, attempts.lastOrNull()?.post?.thread?.meaning, stage,
            matchedContexts = matchedContexts, availableContexts = posts.mapNotNull { it.context }.distinct().size,
            nextPostId = posts.firstOrNull { it.thread.id !in progress.answers }?.thread?.id)
    }.sortedWith(compareBy({ studyText(it.term) }, { it.key }))

    val matchedSenses: Int = attemptsBySense.count { (_, attempts) -> attempts.any { it.correct } }
    val masteredSenses: Int = collection.count { it.stage == LearningStage.Mastered }

    private fun priority(post: FeedPracticePost): Priority {
        val attempts = attemptsBySense[post.studyKey()].orEmpty()
        val last = attempts.lastOrNull() ?: return Priority.Unseen
        val seenContexts = attempts.mapNotNull { it.post.context }.toSet()
        val newContext = post.context != null && seenContexts.isNotEmpty() && post.context !in seenContexts
        val mastered = last.correct && attempts.filter { it.correct }.mapNotNull { it.post.context }.distinct().size >= 2
        return when {
            newContext && !last.correct -> Priority.MissedNewContext
            newContext && !mastered -> Priority.MatchedNewContext
            !last.correct -> Priority.MissedAvailable
            else -> Priority.MorePractice
        }
    }

    private val unanswered = progress.posts.filterNot { it.thread.id in progress.answers }
        .map { it to priority(it) }.sortedBy { it.second }
    private val next = unanswered.firstOrNull { it.first.studyKey() != latestSense } ?: unanswered.firstOrNull()

    val recommendation: PracticeRecommendation? = next?.let { (post, priority) ->
        PracticeRecommendation(post.thread.id, priority.reason)
    }

    fun orderedPosts(order: PracticeOrder): List<FeedPracticePost> = when (order) {
        PracticeOrder.Browse -> progress.posts
        PracticeOrder.Fixed -> progress.posts.sortedBy { it.thread.id }
        PracticeOrder.Adaptive -> listOfNotNull(next?.first) + unanswered.filterNot { it == next }.map { it.first } +
            progress.posts.filter { it.thread.id in progress.answers }
    }
}
