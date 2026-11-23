package id.slangverse.app

internal enum class PracticeSample(val sourceLabel: String) {
    Stories("Offline sample · hand-authored"), Contexts("Context practice · hand-authored");

    val posts: List<FeedPracticePost> get() = when (this) {
        Stories -> SocialFeedGame.posts
        Contexts -> ContextPracticeBank.posts
    }
}

internal object ContextPracticeBank {
    private enum class Sense(val term: String, val meaning: String, val distractors: List<String>) {
        Lowkey("lowkey", "Feeling something somewhat or without making a big deal about it",
            listOf("Feeling certain something will fail", "Keeping a promise", "Asking everyone for permission")),
        NoCap("no cap", "Emphasizing that a statement is honest or not exaggerated",
            listOf("Warning someone not to share a secret", "Saying it is time to stop", "Admitting you were joking"))
    }

    private data class Scene(
        val sense: Sense, val context: FeedContext, val handle: String, val body: String,
        val clueHandle: String, val clue: String, val response: String, val question: String,
        val explanation: String, val followUp: String
    )

    private val scenes = listOf(
        Scene(Sense.Lowkey, FeedContext.Education, "@miramoments",
            "I said I'd be fine going first, but I'm lowkey nervous about presenting to a full room.",
            "@naya", "Want to do one run in the hallway with me?", "Just the opening would help. I'll meet you by the stairs.",
            "How strongly is Mira expressing her nerves?", "Mira admits some nerves without making a big announcement about them.",
            "The hallway run helped. I got through the first slide without reading it word for word."),
        Scene(Sense.NoCap, FeedContext.Sports, "@benchside",
            "I was right behind the goal. No cap, the keeper got a hand on both shots before the final whistle.",
            "@matchdaychat", "The replay shows the second touch too.", "Exactly. From the other stand it looked like the second one hit the post untouched.",
            "What is Ben emphasizing about the two saves?", "Ben stresses that both touches really happened, even if the second was easy to miss.",
            "The replay angle from behind the net finally went up. That's the one I saw live."),
        Scene(Sense.Lowkey, FeedContext.Entertainment, "@lilalistens",
            "Everyone came for the main act, but I'm lowkey more excited to hear the opening band's new song.",
            "@sam", "Want to get there before doors? They play early.", "Yes. Just don't tell the group I moved dinner earlier for the opener.",
            "What does Lila's word choice tell you about her excitement?", "Lila admits a quiet preference that she isn't announcing to the whole group.",
            "They played the new one second. I was glad we made it in before the lights went down."),
        Scene(Sense.NoCap, FeedContext.Work, "@ariatwork",
            "No cap, one icon in the old file links to the wrong checkout screen. I ran through it twice.",
            "@deasays", "Send me the exact screen and step. I'll put it in the handoff.", "Third screen, bottom left. The latest file is fine.",
            "Why does Ari start with that phrase?", "Ari wants Dea to take the report literally rather than as an exaggeration.",
            "I added the screen recording to the handoff. Dea has the right file now."),
        Scene(Sense.Lowkey, FeedContext.Friends, "@justjo",
            "We can go out if everyone wants, but I'd lowkey rather play cards at mine tonight.",
            "@sam", "I haven't left home yet. I can grab snacks on the way to yours.", "Perfect. I'll put the cards out and let the group choose.",
            "How is Jo presenting that preference to the group?", "Jo shares a modest preference without insisting that everyone follow it.",
            "We stayed in. Sam brought the loudest bag of crisps I've ever heard during a quiet round."),
        Scene(Sense.NoCap, FeedContext.Personal, "@miramoments",
            "No cap, I found my missing earbuds in the reusable bag I took to the library last week.",
            "@naya", "The canvas one under your desk?", "That one. I checked every jacket pocket first.",
            "What is Mira signaling about where the earbuds were?", "Mira emphasizes that this oddly specific place is where she really found them.",
            "I gave the canvas bag a pocket for the case. Apparently I needed a system."),
    )

    val posts = scenes.mapIndexed { index, scene ->
        val id = "context-practice-v1-$index"
        val correctIndex = (index + index / 2) % 4
        val options = scene.sense.distractors.toMutableList().apply { add(correctIndex, scene.sense.meaning) }
        val replyTexts = listOf(scene.clue, scene.response)
        FeedPracticePost(
            thread = DetectiveCase(id, scene.sense.term, scene.sense.meaning, SocialNarrative.profile(scene.handle).name,
                scene.handle, scene.context.hashtag, scene.body, replyTexts, scene.question, options, correctIndex, scene.explanation),
            context = scene.context, difficulty = FeedDifficulty.Medium, sourceLabel = "Hand-authored context practice",
            learningSenseId = "context-practice-${scene.sense.name}",
            authoredStory = PostStory(time = "", replies = listOf(
                StoryReply("$id-clue", scene.clueHandle, scene.clue),
                StoryReply("$id-response", scene.handle, scene.response, "$id-clue")
            ), followUpHandle = scene.handle, followUp = scene.followUp)
        )
    }
}
