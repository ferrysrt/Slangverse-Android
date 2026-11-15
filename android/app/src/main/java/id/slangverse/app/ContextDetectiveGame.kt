package id.slangverse.app

/**
 * V3 demo-only cases. These examples are hand-authored, provisional and not expert-validated.
 * They do not replace the research generator's 10-context packages or the connected API session.
 */
internal data class DetectiveCase(
    val id: String,
    val term: String,
    val meaning: String,
    val author: String,
    val handle: String,
    val hashtag: String,
    val post: String,
    val comments: List<String>,
    val questionComment: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

internal data class DetectiveDecision(
    val caseId: String,
    val term: String,
    val selectedIndex: Int,
    val pinnedClueIndex: Int,
    val correct: Boolean,
    val xpEarned: Int,
    val correctIndex: Int
)

internal object ContextDetectiveGame {
    const val CORRECT_XP = 80
    const val PRACTICE_XP = 15

    val cases: List<DetectiveCase> = listOf(
        DetectiveCase(
            id = "campus", term = "cooked", meaning = "In serious trouble",
            author = "Campus Confessions", handle = "@campusfeed", hashtag = "#CampusChaos",
            post = "The presentation is tomorrow, my laptop crashed, and I didn't back up anything. I'm cooked 😭",
            comments = listOf(
                "Please tell me you saved a copy somewhere 💀",
                "That deadline is going to be brutal...",
                "Maybe check your cloud backup before panicking 😭"
            ),
            questionComment = "Wait, what do you mean by cooked? Are you literally making dinner? 😭",
            options = listOf("Feeling hungry", "In serious trouble", "Feeling confident", "Planning a meal"),
            correctIndex = 1,
            explanation = "The crashed laptop and looming presentation create an urgent problem. " +
                "The replies recognize how difficult the situation has become."
        ),
        DetectiveCase(
            id = "exam", term = "no cap", meaning = "Speaking honestly",
            author = "studybro", handle = "@studybro", hashtag = "#ExamWeek",
            post = "Just aced the midterm with five minutes to spare — no cap, I was stressing the whole time 😤",
            comments = listOf(
                "Wait, really? You actually finished already?",
                "You earned that break after all that studying.",
                "I still haven't checked my grade."
            ),
            questionComment = "No cap? Did you lose your hat or something? I'm confused 😅",
            options = listOf("Making a joke", "Feeling uncertain", "Speaking honestly", "Changing the subject"),
            correctIndex = 2,
            explanation = "The speaker emphasizes that the exam result really happened. " +
                "Here, 'no cap' indicates sincerity rather than exaggeration."
        ),
        DetectiveCase(
            id = "travel", term = "lowkey", meaning = "Quietly or somewhat",
            author = "nightowl", handle = "@nightowl", hashtag = "#WeekendVibes",
            post = "I lowkey want to drop everything and take a spontaneous weekend trip somewhere new... 🏔️",
            comments = listOf(
                "You have been talking about needing a break all week.",
                "That sounds pretty tempting, honestly.",
                "Maybe a quiet weekend is exactly what you need."
            ),
            questionComment = "Lowkey? As in an actual key? Or am I missing the point?",
            options = listOf("Loudly announcing", "Quietly or somewhat", "Completely forgetting", "Never wanting"),
            correctIndex = 1,
            explanation = "The desire is expressed casually and with understatement. " +
                "The post is not a forceful announcement of a definite plan."
        )
    )

    init {
        require(cases.map { it.id }.distinct().size == cases.size)
        require(cases.map { it.term }.distinct().size == cases.size)
        require(cases.all { it.options.size == 4 && it.correctIndex in 0..3 && it.comments.size >= 2 && it.questionComment.isNotBlank() })
    }

    /** Rotates three distinct senses; unlike a 10-item generator pack, no repeated sense is shown. */
    fun orderedFrom(startCaseId: String): List<DetectiveCase> {
        val offset = cases.indexOfFirst { it.id == startCaseId }.coerceAtLeast(0)
        return cases.drop(offset) + cases.take(offset)
    }

    /** Evidence pinning is optional and reflective, never a graded requirement. */
    fun grade(case: DetectiveCase, selectedIndex: Int, pinnedClueIndex: Int): DetectiveDecision {
        require(selectedIndex in case.options.indices) { "invalid-answer" }
        require(pinnedClueIndex == -1 || pinnedClueIndex in case.comments.indices) { "invalid-clue" }
        val correct = selectedIndex == case.correctIndex
        return DetectiveDecision(
            caseId = case.id,
            term = case.term,
            selectedIndex = selectedIndex,
            pinnedClueIndex = pinnedClueIndex,
            correct = correct,
            xpEarned = if (correct) CORRECT_XP else PRACTICE_XP,
            correctIndex = case.correctIndex
        )
    }
}
