package id.slangverse.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reset is the one destructive action in the app, so every derived figure has to fall with it. */
class PracticeResetTest {

    @Test
    fun resettingDropsEveryDerivedFigureWithoutTouchingTheFeed() {
        val posts = SocialFeedGame.posts
        val answered = FeedPracticeProgress(posts = posts)
            .answer(posts[0].thread.id, posts[0].thread.correctIndex)
            .answer(posts[1].thread.id, posts[1].thread.correctIndex)
        assertEquals(2, answered.completedCount)
        assertEquals(2, answered.correct)
        assertEquals(2, answered.contexts)
        assertTrue(answered.xp > 0)
        assertTrue(answered.combo > 0)

        val reset = answered.reset()
        assertEquals(emptyMap<String, Int>(), reset.answers)
        assertEquals(0, reset.completedCount)
        assertEquals(0, reset.xp)
        assertEquals(0, reset.combo)
        assertEquals(0, reset.correct)
        assertEquals(0, reset.contexts)
        assertTrue(reset.decisions.isEmpty())
        // The questions themselves are not practice data: they stay in the feed.
        assertEquals(answered.posts, reset.posts)
    }

    @Test
    fun resettingStaysEmptyAndDoesNotAccumulate() {
        val fresh = FeedPracticeProgress()
        assertEquals(fresh, fresh.reset())
        assertEquals(fresh.reset(), fresh.reset().reset())
        // A reset snapshot still has to survive the round trip the store performs.
        val stored = LocalPracticeCodec.decode(LocalPracticeCodec.encode(LocalPracticeSnapshot()))
        assertEquals(emptyList<Pair<String, Int>>(), stored.answers)
        // ... and answering again after a reset still earns a reward.
        val afterReset = fresh.reset().answer(fresh.posts[0].thread.id, fresh.posts[0].thread.correctIndex)
        assertEquals(1, afterReset.completedCount)
        assertTrue(afterReset.xp > 0)
    }
}
