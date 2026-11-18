package id.slangverse.app

/** Proposal §8.2.2. News is fictional event reaction, not an additional research category. */
internal enum class FeedContext(val label: String, val hashtag: String) {
    Personal("Personal", "#EverydayLife"), Education("Education", "#CampusLife"),
    Work("Work", "#WorkLife"), Prediction("Predictions", "#WhatIf"),
    Sports("Sports", "#Matchday"), Entertainment("Entertainment", "#OpeningNight"),
    Opinion("Opinions", "#MyTake"), Friends("Friends", "#FriendChat"), EventReaction("News", "#TownUpdates")
}

internal enum class FeedDifficulty { Easy, Medium, Hard }

internal data class FeedPracticePost(
    val thread: DetectiveCase,
    val context: FeedContext?,
    val difficulty: FeedDifficulty,
    val avatarId: String = "",
    val replies: List<StoryReply>? = null,
    val published: Boolean = false,
    val sourceLabel: String = "Hand-authored practice",
    val termTargetHandle: String? = null
)

/** Hand-authored UI practice pack. Not generated, expert-validated, or participant research data. */
internal object SocialFeedGame {
    private data class Scene(
        val id: String, val context: FeedContext, val author: String, val handle: String,
        val post: String, val replies: List<String>, val question: String, val explanation: String
    )

    private val scenes = listOf(
        Scene("personal", FeedContext.Personal, "Mira", "@miramoments",
            "Left my keys inside, my phone is at 1%, and the last bus just left. I'm cooked 😭",
            listOf("Can your flatmate let you in?", "Stay by the cafe; I'll come get you."),
            "What does cooked mean with all of that happening?",
            "Being locked out, nearly losing phone power and missing the bus leave Mira in a difficult situation."),
        Scene("education", FeedContext.Education, "Campus Confessions", "@campusfeed",
            "The presentation is tomorrow, my laptop crashed, and I didn't back up anything. I'm cooked 😭",
            listOf("Check your cloud backup before panicking.", "That deadline is going to be brutal."),
            "Why cooked? What happened to the presentation?",
            "The lost presentation and approaching deadline put the author in serious trouble."),
        Scene("work", FeedContext.Work, "Ari", "@ariatwork",
            "Sent the client the wrong file and the meeting starts in two minutes. I'm cooked.",
            listOf("Send the corrected version now.", "I'll ask them to wait before opening it."),
            "What are they saying about their situation with cooked?",
            "The wrong file reaching a client just before a meeting creates an urgent problem."),
        Scene("prediction", FeedContext.Prediction, "Matchday Chat", "@matchdaychat",
            "If our keeper can't play tomorrow, we're cooked. The other team hasn't missed a shot all week.",
            listOf("The backup hasn't trained with the squad yet.", "We need a plan before kickoff."),
            "Does cooked describe what might happen tomorrow?",
            "The author predicts serious difficulty against strong opponents if the keeper cannot play."),
        Scene("sports", FeedContext.Sports, "Ben", "@benchside",
            "Down three goals, five minutes left, and another red card. We're cooked.",
            listOf("There is barely any time to turn this around.", "And now we have one fewer player."),
            "What does cooked say about the team's chances?",
            "The score, remaining time and lost player make recovery very unlikely."),
        Scene("entertainment", FeedContext.Entertainment, "Lila", "@lilalistens",
            "The lead actor just missed the only train to our opening night. We're cooked.",
            listOf("Is the understudy already at the theatre?", "Someone call the stage manager."),
            "Is cooked about the show or something else here?",
            "The missing lead actor threatens the performance, placing the production in trouble."),
        Scene("opinion", FeedContext.Opinion, "Dea", "@deasays",
            "Unpopular opinion: if our project plan depends on nobody ever getting sick, we're cooked.",
            listOf("There isn't a backup for any task.", "Maybe we need some room in that schedule."),
            "What does cooked express about that project plan?",
            "The opinion criticizes a fragile plan: a single absence could cause serious problems."),
        Scene("friends", FeedContext.Friends, "Jo", "@justjo",
            "Me: you brought the tickets, right? Sam: I thought you had them. We're cooked.",
            listOf("The gate closes in ten minutes.", "Check if the confirmation email has a QR code."),
            "Why are these friends calling themselves cooked?",
            "Neither friend has the tickets and entry closes soon; they risk missing the event."),
        Scene("news", FeedContext.EventReaction, "Town Loop", "@townloop",
            "Update from our fictional town fair: the venue moved across town. Our team is still setting up at the old one. We're cooked.",
            listOf("The delivery van has already left.", "Doors open before it can get back."),
            "What is the team reacting to when they say cooked?",
            "The late venue change leaves the team and its equipment in the wrong place with little time to recover."),
        Scene("education-followup", FeedContext.Education, "Mira", "@miramoments",
            "The submission portal says closed. My finished assignment is still sitting in drafts. Cooked.",
            listOf("The course page says late uploads are disabled.", "Email the lecturer and explain what happened."),
            "The work is finished, so what does cooked refer to?",
            "Finishing the assignment did not resolve the missed submission. The closed portal creates the problem.")
    )

    val posts = scenes.mapIndexed { index, scene ->
        val difficulty = when { index < 5 -> FeedDifficulty.Easy; index < 8 -> FeedDifficulty.Medium; else -> FeedDifficulty.Hard }
        val distractors = when (difficulty) {
            FeedDifficulty.Easy -> listOf("Preparing food", "In serious trouble", "Feeling confident", "Planning a meal")
            FeedDifficulty.Medium -> listOf("Simply feeling tired", "In serious trouble", "Having finished successfully", "Feeling embarrassed but facing no problem")
            FeedDifficulty.Hard -> listOf("Being certain the problem is already solved", "In serious trouble", "Being frustrated by a minor inconvenience only", "Giving up interest without facing an obstacle")
        }
        val options = distractors.drop(index % 4) + distractors.take(index % 4)
        FeedPracticePost(
            DetectiveCase(scene.id, "cooked", "In serious trouble", scene.author, scene.handle,
                scene.context.hashtag, scene.post, scene.replies, scene.question,
                options, options.indexOf("In serious trouble"), scene.explanation), scene.context, difficulty)
    }

    val contextCount: Int get() = posts.map { it.context }.distinct().size

    fun contextForHashtag(hashtag: String): FeedContext? = FeedContext.entries.firstOrNull { it.hashtag == hashtag }

    fun filter(context: FeedContext?): List<FeedPracticePost> = posts.filter { context == null || it.context == context }
}

/** Results are keyed by post, so filtering, navigation and repeated taps cannot farm XP. */
internal data class FeedPracticeProgress(
    val answers: Map<String, Int> = emptyMap(),
    val posts: List<FeedPracticePost> = SocialFeedGame.posts
) {
    val decisions: List<DetectiveDecision> get() = answers.mapNotNull { (id, selection) ->
        val post = posts.firstOrNull { it.thread.id == id } ?: return@mapNotNull null
        ContextDetectiveGame.grade(post.thread, selection, -1)
    }
    val completedCount: Int get() = decisions.size
    val contextCount: Int get() = posts.mapNotNull { it.context }.distinct().size
    val xp: Int get() = decisions.sumOf { it.xpEarned }
    val correct: Int get() = decisions.count { it.correct }
    val contexts: Int get() = posts.filter { it.thread.id in answers }.mapNotNull { it.context }.distinct().size
    val combo: Int get() = decisions.takeLastWhile { it.correct }.size

    fun answer(postId: String, selectedIndex: Int): FeedPracticeProgress {
        val post = posts.first { it.thread.id == postId }
        require(selectedIndex in post.thread.options.indices)
        return if (postId in answers) this else copy(answers = answers + (postId to selectedIndex))
    }

    /** Drops every reply so XP, combo, matches and the history rows all fall back to zero. */
    fun reset(): FeedPracticeProgress = copy(answers = emptyMap())
}
