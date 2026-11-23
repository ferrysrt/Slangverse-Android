package id.slangverse.app

import org.junit.Assert.*
import org.junit.Test

class PracticeLearningTest {
    private val campus = SocialFeedGame.posts.single { it.thread.id == "education" }
    private val office = SocialFeedGame.posts.single { it.thread.id == "work" }
    private val home = SocialFeedGame.posts.single { it.thread.id == "personal" }
    private val sameContext = SocialFeedGame.posts.single { it.thread.id == "education-followup" }
    private val otherSense = home.copy(thread = home.thread.copy(id = "other", term = "no cap", meaning = "Honestly"),
        learningSenseId = "no-cap-honest")

    private fun FeedPracticeProgress.correct(post: FeedPracticePost) = answer(post.thread.id, post.thread.correctIndex)
    private fun FeedPracticeProgress.incorrect(post: FeedPracticePost) =
        answer(post.thread.id, (post.thread.correctIndex + 1) % post.thread.options.size)

    @Test fun missedMeaningReturnsInADifferentContextAfterAnInterveningMeaning() {
        val missed = FeedPracticeProgress(posts = listOf(campus, sameContext, otherSense, office)).incorrect(campus)
        val beforeSpacing = PracticeLearning(missed)
        assertEquals(otherSense.thread.id, beforeSpacing.recommendation?.postId)
        assertEquals(otherSense, beforeSpacing.orderedPosts(PracticeOrder.Adaptive).first())

        val spaced = PracticeLearning(missed.correct(otherSense))
        assertEquals(office.thread.id, spaced.recommendation?.postId)
        assertTrue(spaced.recommendation!!.reason.contains("different context"))
        assertEquals(listOf(office, sameContext, campus, otherSense), spaced.orderedPosts(PracticeOrder.Adaptive))
    }

    @Test fun collectionUnlocksOnlyAttemptedMeaningsAndRequiresTwoKnownContexts() {
        val initial = FeedPracticeProgress(posts = listOf(campus, sameContext, office, home, otherSense))
        assertTrue(PracticeLearning(initial).collection.all { it.revealedMeaning == null && it.stage == LearningStage.New })
        val twiceInSchool = initial.correct(campus).correct(sameContext)
        val started = PracticeLearning(twiceInSchool)
        val decoded = started.collection.first { it.term == "cooked" }
        assertEquals(LearningStage.Decoded, decoded.stage)
        assertEquals("In serious trouble", decoded.revealedMeaning)
        assertEquals(1, decoded.matchedContexts)
        assertEquals(3, decoded.availableContexts)
        assertEquals(1, started.matchedSenses)
        assertEquals(0, started.masteredSenses)

        val mastered = twiceInSchool.correct(office)
        assertEquals(1, PracticeLearning(mastered).masteredSenses)
        val latestMiss = PracticeLearning(mastered.incorrect(home))
        assertEquals(0, latestMiss.masteredSenses)
        assertEquals(LearningStage.Practicing, latestMiss.collection.first { it.term == "cooked" }.stage)
        assertEquals(1, latestMiss.matchedSenses)
    }

    @Test fun equalSurfaceFormsWithDifferentSenseIdsStaySeparate() {
        val foodSense = office.copy(learningSenseId = "cooked-food", thread = office.thread.copy(meaning = "Prepared by heating"))
        val troubleSense = campus.copy(learningSenseId = "cooked-trouble")
        val progress = FeedPracticeProgress(posts = listOf(troubleSense, foodSense)).correct(troubleSense).correct(foodSense)
        val learned = PracticeLearning(progress)
        assertEquals(2, learned.collection.size)
        assertEquals(2, learned.matchedSenses)
        assertEquals(0, learned.masteredSenses)
        assertNull(learned.recommendation)
    }

    @Test fun fallbackAvoidsAnsweredPostsAndKeepsUnloadedHistoryOutOfRecommendations() {
        val oldAnswer = FeedPracticeProgress(posts = listOf(campus)).incorrect(campus)
        val oneOff = PracticeLearning(oldAnswer.copy(posts = listOf(otherSense)))
        assertEquals(otherSense.thread.id, oneOff.recommendation?.postId)
        assertEquals("Try a meaning you haven’t practiced yet.", oneOff.recommendation?.reason)
        assertEquals(LearningStage.New, oneOff.collection.single().stage)
        val noAlternateContext = PracticeLearning(oldAnswer.copy(posts = listOf(campus, sameContext, otherSense)))
        assertEquals(otherSense.thread.id, noAlternateContext.recommendation?.postId)
        assertEquals(sameContext.thread.id, PracticeLearning(oldAnswer.copy(posts = listOf(campus, sameContext))).recommendation?.postId)
    }

    @Test fun fixedOrderIgnoresHistoryAndBrowseKeepsTheLoadedOrder() {
        val posts = listOf(office, home, campus)
        val initial = FeedPracticeProgress(posts = posts)
        val changed = initial.incorrect(home).copy(posts = posts.reversed())
        assertEquals(posts, PracticeLearning(initial).orderedPosts(PracticeOrder.Browse))
        assertEquals(listOf(campus, home, office), PracticeLearning(initial).orderedPosts(PracticeOrder.Fixed))
        assertEquals(PracticeLearning(initial).orderedPosts(PracticeOrder.Fixed),
            PracticeLearning(changed).orderedPosts(PracticeOrder.Fixed))
        assertEquals(PracticeOrder.Browse, PracticeOrder.fromStored("unrecognized"))
        assertEquals(PracticeOrder.Fixed, PracticeOrder.fromStored("Fixed"))
    }

    @Test fun bundledContextPracticeHasTwoStableSensesAndThreeDistinctContextsEach() {
        val posts = PracticeSample.Contexts.posts
        assertEquals(6, posts.size)
        assertEquals(6, posts.map { it.thread.id }.distinct().size)
        assertEquals(setOf("lowkey", "no cap"), posts.map { it.thread.term }.toSet())
        assertTrue(PracticeLearning(FeedPracticeProgress(posts = posts)).collection.all { it.availableContexts == 3 })
        posts.forEach { post ->
            assertEquals(post.thread.meaning, post.thread.options[post.thread.correctIndex])
            assertNotNull(displayAnswerOptions(post.thread.options))
            val story = SocialNarrative.story(post)
            assertEquals(story.replies[0].id, story.replies[1].parentId)
            assertEquals(post.thread.handle, story.followUpHandle)
            assertTrue(story.followUp.isNotBlank())
        }
        val first = FeedPracticeProgress(posts = posts).incorrect(posts[0])
        assertEquals(posts[1].thread.id, PracticeLearning(first).recommendation?.postId)
        assertEquals(posts[2].thread.id, PracticeLearning(first.correct(posts[1])).recommendation?.postId)
        assertEquals(10, PracticeSample.Stories.posts.size)
    }
}
