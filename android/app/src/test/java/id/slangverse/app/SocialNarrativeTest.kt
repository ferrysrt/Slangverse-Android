package id.slangverse.app

import org.junit.Assert.*
import org.junit.Test

class SocialNarrativeTest {
    @Test fun allVisibleIdentitiesHaveConsistentProfiles() {
        SocialFeedGame.posts.forEach { post ->
            val story = SocialNarrative.story(post)
            val handles = listOf(post.thread.handle, story.followUpHandle, "@confuseduser") +
                story.replies.map { it.handle } + listOfNotNull(story.quote?.handle)
            handles.forEach { handle ->
                val profile = SocialNarrative.profile(handle)
                assertEquals(handle, profile.handle)
                assertTrue(profile.name.isNotBlank())
                assertTrue(profile.bio.isNotBlank())
            }
            assertEquals(post.thread.author, SocialNarrative.profile(post.thread.handle).name)
        }
        assertEquals(2, SocialFeedGame.posts.count { it.thread.handle == "@miramoments" })
    }

    @Test fun nestedRepliesReferenceAnEarlierReplyInTheSameStory() {
        SocialFeedGame.posts.forEach { post ->
            val replies = SocialNarrative.story(post).replies
            val seen = mutableSetOf<String>()
            replies.forEach { reply ->
                assertTrue(reply.parentId == null || reply.parentId in seen)
                assertTrue(seen.add(reply.id))
            }
            assertTrue(replies.any { it.parentId != null })
            assertEquals(post.thread.comments, replies.map { it.text })
        }
    }

    @Test fun quotedPostsProvideEvidenceWithoutAddingScoredQuestions() {
        val ids = SocialFeedGame.posts.map { it.thread.id }
        val quoted = SocialFeedGame.posts.mapNotNull { SocialNarrative.story(it).quote }
        assertEquals(5, quoted.size)
        assertTrue(quoted.all { it.text.isNotBlank() })
        SocialFeedGame.posts.forEach { post ->
            SocialNarrative.story(post).quote?.let { quote ->
                assertFalse(post.thread.options.any { quote.text.contains(it, ignoreCase = true) })
            }
        }
        assertEquals(10, ids.size)
        assertTrue(SocialNarrative.story(SocialFeedGame.posts.last()).quote!!.text.contains("midnight"))
    }

    @Test fun lateSubmissionPilotKeepsNestedVoicesAndOneNeutralFollowUp() {
        val post = SocialFeedGame.posts.single { it.thread.id == "education-followup" }
        val story = SocialNarrative.story(post)
        val (clue, response, nextStep) = story.replies
        assertEquals(listOf("@naya", "@miramoments", "@naya"), story.replies.map { it.handle })
        assertNull(clue.parentId)
        assertEquals(clue.id, response.parentId)
        assertEquals(response.id, nextStep.parentId)

        val initial = FeedPracticeProgress(posts = listOf(post))
        val correctIndex = post.thread.correctIndex
        val correct = initial.answer(post.thread.id, correctIndex)
        val incorrect = initial.answer(post.thread.id, (correctIndex + 1) % post.thread.options.size)
        assertTrue(SocialNarrative.updates(initial).isEmpty())
        val update = SocialNarrative.updates(correct).single()
        assertEquals(ConversationUpdate(post.thread.id, "@miramoments", story.followUp), update)
        assertEquals(listOf(update), SocialNarrative.updates(incorrect))
        assertEquals(80, correct.xp)
        assertEquals(15, incorrect.xp)
    }

    @Test fun updatesAppearOnlyAfterSubmissionAndNeverDuplicate() {
        assertTrue(SocialNarrative.updates(FeedPracticeProgress()).isEmpty())
        val post = SocialFeedGame.posts.first().thread
        val correct = FeedPracticeProgress().answer(post.id, post.correctIndex)
        val wrong = FeedPracticeProgress().answer(post.id, (post.correctIndex + 1) % 4)
        assertEquals(SocialNarrative.updates(correct), SocialNarrative.updates(wrong))
        val update = SocialNarrative.updates(correct).single()
        assertEquals(post.id, update.postId)
        assertEquals(post.handle, update.handle)
        assertTrue(update.text.isNotBlank())
        val reopened = correct.answer(update.postId, post.correctIndex)
        assertEquals(80, reopened.xp)
        assertEquals(listOf(update), SocialNarrative.updates(reopened))
    }

    @Test fun inboxTargetsExactAnsweredConversationsInMostRecentOrder() {
        val answered = listOf(SocialFeedGame.posts[4], SocialFeedGame.posts[0], SocialFeedGame.posts[8])
        val progress = answered.fold(FeedPracticeProgress()) { state, post ->
            state.answer(post.thread.id, post.thread.correctIndex)
        }
        val updates = SocialNarrative.updates(progress)
        assertEquals(answered.reversed().map { it.thread.id }, updates.map { it.postId })
        updates.forEach { update ->
            assertEquals(1, SocialFeedGame.posts.count { it.thread.id == update.postId })
        }
        assertEquals(240, progress.xp)
    }

    @Test fun hashtagsResolveToTheirOriginalPackContext() {
        SocialFeedGame.posts.forEach { post ->
            val context = SocialFeedGame.contextForHashtag(post.thread.hashtag)
            assertEquals(post.context, context)
            assertTrue(SocialFeedGame.filter(context).contains(post))
        }
        assertNull(SocialFeedGame.contextForHashtag("#NotInThisPack"))
        assertEquals(SocialFeedGame.posts.map { it.context }.distinct().size, SocialFeedGame.contextCount)
    }
}
