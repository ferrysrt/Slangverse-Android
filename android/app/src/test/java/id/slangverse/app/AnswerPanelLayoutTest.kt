package id.slangverse.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Layout budget for the feed card on a short viewport, measured on the real composable.
 *
 * Scope is deliberate. The JVM harness here does not apply the scroll container's bring-into-view
 * request, so it cannot assert the scroll itself; that is covered by the device run recorded in
 * TEST_RESULTS.md, where every option and "Send reply" were on screen after one tap. What is
 * asserted here is the budget that makes that scroll possible at all (a panel taller than the
 * screen can never be shown in full) and the mount guard that keeps the feed from moving under the
 * reader's finger.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AnswerPanelLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private val thread = DetectiveCase(
        id = "test-pack/1/q1",
        term = "aura",
        meaning = "A person's current reputation or charisma",
        author = "Daniel Okafor",
        handle = "@danieldesk",
        hashtag = "#OfficeLife",
        post = "Took the team lead role today after a rough month. Everyone in the office says the vibe changed when I walked in.",
        comments = listOf(
            "Your aura in that meeting was unreal, people were still talking about it at lunch.",
            "Congrats on the new role. You'll be great at it."
        ),
        questionComment = "@priyaplans wait, is aura a fitness class they have at the office?",
        options = listOf(
            "A rumor about a coworker spread during a meeting",
            "A plan to skip meetings without anyone being told about it",
            "A kind of ergonomic chair that offices keep in storage",
            "A person's current reputation or charisma"
        ),
        correctIndex = 3,
        explanation = "Priya's reply is about how the room read the author after the promotion, so aura is the standing they have right now."
    )

    private val post = FeedPracticePost(
        thread = thread,
        context = FeedContext.Work,
        difficulty = FeedDifficulty.Easy,
        published = true,
        sourceLabel = "Test fixture"
    )

    private val viewportWidth = 360.dp
    // A deliberately short screen: the whole card is taller than this, which is the whole problem.
    private val viewportHeight = 440.dp

    private fun topOf(label: String): Float = rule.onNodeWithText(label).fetchSemanticsNode().positionInRoot.y

    private fun bottomOf(label: String): Float {
        val node = rule.onNodeWithText(label).fetchSemanticsNode()
        return node.positionInRoot.y + node.size.height
    }

    @Test
    fun theAnswerPanelFitsOnOneShortScreen() {
        rule.setContent {
            Box(Modifier.size(viewportWidth, viewportHeight)) {
                LazyColumn(Modifier.size(viewportWidth, viewportHeight)) {
                    item {
                        FeedSocialPost(
                            post = post, answeredIndex = null, liked = false, saved = false,
                            onLike = {}, onSave = {}, onAnswer = {},
                            onProfile = {}, onContext = {}
                        )
                    }
                }
            }
        }

        rule.onNodeWithText("Answer Kai").performClick()
        rule.waitForIdle()

        val screen = with(rule.density) { viewportHeight.toPx() }
        val panelHeight = bottomOf("Send reply") - topOf("Which meaning fits?")
        assertTrue(
            "the answer panel is ${panelHeight.toInt()}px tall on a ${screen.toInt()}px screen, so no scroll " +
                "could ever show it in full",
            panelHeight <= screen
        )
    }

    @Test
    fun anAlreadyAnsweredCardDoesNotScrollTheFeedWhenItFirstAppears() {
        val listState = LazyListState()
        rule.setContent {
            Box(Modifier.size(viewportWidth, viewportHeight)) {
                LazyColumn(state = listState, modifier = Modifier.size(viewportWidth, viewportHeight)) {
                    item {
                        FeedSocialPost(
                            post = post, answeredIndex = thread.correctIndex, liked = false, saved = false,
                            onLike = {}, onSave = {}, onAnswer = {},
                            onProfile = {}, onContext = {}
                        )
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(
                "the graded panel pulled the feed while the card was still being placed",
                0, listState.firstVisibleItemScrollOffset
            )
        }
    }
}
