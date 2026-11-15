package id.slangverse.app

import org.junit.Assert.*
import org.junit.Test

class SocialFeedGameTest {
    @Test fun practicePackKeepsOneSenseTenItemsAndProposalDifficultyRatio() {
        val posts = SocialFeedGame.posts
        assertEquals(10, posts.size)
        assertEquals(10, posts.map { it.thread.id }.distinct().size)
        assertEquals(setOf("cooked"), posts.map { it.thread.term }.toSet())
        assertEquals(setOf("In serious trouble"), posts.map { it.thread.meaning }.toSet())
        assertEquals(mapOf(FeedDifficulty.Easy to 5, FeedDifficulty.Medium to 3, FeedDifficulty.Hard to 2),
            posts.groupingBy { it.difficulty }.eachCount())
        posts.forEach {
            assertEquals(4, it.thread.options.distinct().size)
            assertEquals(it.thread.meaning, it.thread.options[it.thread.correctIndex])
        }
        assertTrue(posts.map { it.thread.correctIndex }.distinct().size > 1)
    }

    @Test fun everyProposalContextHasAWorkingFilter() {
        assertEquals(9, FeedContext.entries.size)
        assertEquals(SocialFeedGame.posts, SocialFeedGame.filter(null))
        FeedContext.entries.forEach { context ->
            val filtered = SocialFeedGame.filter(context)
            assertFalse(filtered.isEmpty())
            assertTrue(filtered.all { it.context == context })
        }
        assertEquals(listOf("news"), SocialFeedGame.filter(FeedContext.EventReaction).map { it.thread.id })
    }

    @Test fun repliesAwardXpOnceAcrossContextChangesAndReview() {
        val post = SocialFeedGame.posts.first().thread
        val progress = FeedPracticeProgress().answer(post.id, post.correctIndex)
        assertEquals(80, progress.xp)
        assertEquals(1, progress.correct)
        assertEquals(1, progress.contexts)
        SocialFeedGame.filter(FeedContext.Work)
        assertEquals(progress, progress.answer(post.id, (post.correctIndex + 1) % 4))
        val second = SocialFeedGame.posts[1].thread
        val mixed = progress.answer(second.id, (second.correctIndex + 1) % 4)
        assertEquals(95, mixed.xp)
        assertEquals(0, mixed.combo)
        assertEquals(1, mixed.correct)
        assertEquals(2, mixed.contexts)
        assertEquals(mixed, FeedPracticeProgress(mixed.answers.toMap()))
    }

    @Test fun completedFeedHasBoundedProgress() {
        val complete = SocialFeedGame.posts.fold(FeedPracticeProgress()) { state, post ->
            state.answer(post.thread.id, post.thread.correctIndex)
        }
        assertEquals(10, complete.answers.size)
        assertEquals(9, complete.contexts)
        assertEquals(800, complete.xp)
        assertEquals(10, complete.combo)
    }

    @Test(expected = IllegalArgumentException::class) fun invalidReplyCannotEarnXp() {
        FeedPracticeProgress().answer(SocialFeedGame.posts.first().thread.id, 4)
    }
}
