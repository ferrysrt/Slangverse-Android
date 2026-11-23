package id.slangverse.app

internal data class FictionalProfile(
    val handle: String, val name: String, val bio: String,
    val avatarId: String = resolveProfilePictureId(handle = handle)
)
internal data class StoryReply(val id: String, val handle: String, val text: String, val parentId: String? = null, val avatarId: String = "")
internal data class StoryQuote(val handle: String, val text: String)
internal data class PostStory(
    val time: String,
    val replies: List<StoryReply>,
    val quote: StoryQuote? = null,
    val followUpHandle: String,
    val followUp: String
)
internal data class ConversationUpdate(val postId: String, val handle: String, val text: String, val isFeedback: Boolean = false)

/** Scripted identities and story continuity. No real accounts, incoming messages, or generated items. */
internal object SocialNarrative {
    val profiles = listOf(
        FictionalProfile("@miramoments", "Mira", "Student, bus commuter, and collector of unfinished playlists. Friends with Naya and Jo."),
        FictionalProfile("@campusfeed", "Campus Confessions", "A fictional campus noticeboard for deadlines, group projects, and student stories."),
        FictionalProfile("@ariatwork", "Ari", "Junior designer learning to survive client meetings. Works on projects with Dea."),
        FictionalProfile("@matchdaychat", "Matchday Chat", "Pre-match conversations from our fictional local football club."),
        FictionalProfile("@benchside", "Ben", "Usually watching from the touchline. Follows Matchday Chat and never leaves before the final whistle."),
        FictionalProfile("@lilalistens", "Lila", "Student theatre volunteer. Talks rehearsals, music, and opening nights."),
        FictionalProfile("@deasays", "Dea", "Design student with opinions about teamwork. Collaborates with Ari."),
        FictionalProfile("@justjo", "Jo", "Mira's friend. Plans weekend outings with Sam; sometimes forgets the practical details."),
        FictionalProfile("@townloop", "Town Loop", "Updates from an entirely fictional town: fairs, community venues, and local events."),
        FictionalProfile("@naya", "Naya", "Mira's classmate. Usually the friend asking one more useful question."),
        FictionalProfile("@sam", "Sam", "Jo's friend and frequent concert companion. Always checks the confirmation email eventually."),
        FictionalProfile("@confuseduser", "Kai", "New to these conversations. Asks what people mean so everyone can follow along.")
    ).associateBy { it.handle }

    fun profile(handle: String): FictionalProfile = profiles.getValue(handle)

    private val quotes = mapOf(
        "education" to StoryQuote("@campusfeed", "Reminder: project presentations start tomorrow morning. Bring your slides."),
        "sports" to StoryQuote("@matchdaychat", "Match update: our keeper is unavailable. The backup starts today."),
        "entertainment" to StoryQuote("@lilalistens", "Opening night tonight! Cast call is one hour before the audience arrives."),
        "news" to StoryQuote("@townloop", "Fictional fair notice: today's event has moved to the community hall across town."),
        "education-followup" to StoryQuote("@campusfeed", "Assignment reminder: uploads close at midnight. Late uploads are disabled.")
    )

    private val followUps = mapOf(
        "personal" to "I reached my flatmate from the cafe. Waiting here until they arrive.",
        "education" to "I'm checking the shared drive with my group now. Thanks for joining the conversation.",
        "work" to "I sent a correction and asked the client to use the new file.",
        "prediction" to "The coach is working on the lineup. We'll know more at kickoff.",
        "sports" to "Final whistle. We stayed to support the team; tomorrow's training is going to be busy.",
        "entertainment" to "The stage manager has called the understudy. We're waiting for an update.",
        "opinion" to "Ari and I are adding backup owners to the project tasks.",
        "friends" to "Sam found the confirmation email. We're asking the gate staff if they can scan it.",
        "news" to "The team has called the new venue to explain the delivery problem.",
        "education-followup" to "Screenshot sent. It's still marked missing, but at least there's a paper trail."
    )

    fun story(post: FeedPracticePost): PostStory {
        val id = post.thread.id
        if (post.published) return PostStory(
            // Published packs carry no real timestamp; an empty time hides the meta line entirely.
            time = "", replies = post.replies.orEmpty(),
            followUpHandle = post.thread.handle, followUp = "")
        val firstHandle = when (post.context) {
            FeedContext.Sports -> "@matchdaychat"
            FeedContext.Prediction -> "@benchside"
            FeedContext.Work -> "@deasays"
            FeedContext.Opinion -> "@ariatwork"
            FeedContext.Friends -> "@sam"
            else -> "@naya"
        }
        val secondHandle = when (id) {
            "personal", "friends" -> "@justjo"
            else -> post.thread.handle
        }
        return PostStory(
            time = if (id == "education-followup") "Later that night" else "Earlier today",
            replies = buildList {
                add(StoryReply("$id-clue", firstHandle, post.thread.comments[0]))
                add(StoryReply("$id-response", secondHandle, post.thread.comments[1], "$id-clue"))
                if (id == "education-followup") {
                    add(StoryReply("$id-next-step", firstHandle, post.thread.comments[2], "$id-response"))
                }
            },
            quote = quotes[id], followUpHandle = post.thread.handle,
            followUp = followUps.getValue(id)
        )
    }

    /** Exactly one scripted update per submitted post, regardless of correctness or social actions. */
    fun updates(progress: FeedPracticeProgress): List<ConversationUpdate> = progress.decisions
        .asReversed().map { decision ->
            val post = progress.posts.first { it.thread.id == decision.caseId }
            val story = story(post)
            if (post.published) ConversationUpdate(post.thread.id, post.thread.handle,
                "Review your interpretation of “${post.thread.term}” and its learning feedback.", isFeedback = true)
            else ConversationUpdate(post.thread.id, story.followUpHandle, story.followUp)
        }
}
