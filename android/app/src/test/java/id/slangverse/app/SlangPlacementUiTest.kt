package id.slangverse.app

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SlangPlacementUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun badgeIsOnTheOriginalPostWhenTheTermIsInThePost() {
        val post = SocialFeedGame.posts.first().copy(termInPost = true)
        rule.setContent {
            FeedSocialPost(post, answeredIndex = null, liked = false, saved = false,
                onLike = {}, onSave = {}, onAnswer = {}, onProfile = {}, onContext = {})
        }
        rule.onNodeWithText("THE SLANG IS IN THIS POST").assertIsDisplayed()
        rule.onNodeWithText("THE SLANG IS IN THIS REPLY").assertDoesNotExist()
    }

    @Test fun highlightingTargetsOnlyTheReplyThatUsesTheWholeTerm() {
        val copy = "Midnight was fun. The snack menu was MID."
        val emphasized = emphasizedSlang(copy, "mid")
        assertEquals(listOf("MID"), emphasized.spanStyles.map { copy.substring(it.start, it.end) })
        val replies = listOf(StoryReply("first", "@naya", "The midnight show was great."),
            StoryReply("second", "@naya", "The other film was mid."))
        rule.setContent { Column { StoryReplies(replies, {}, "@naya", "mid") } }
        rule.onAllNodesWithText("THE SLANG IS IN THIS REPLY").assertCountEquals(1)
    }
}
