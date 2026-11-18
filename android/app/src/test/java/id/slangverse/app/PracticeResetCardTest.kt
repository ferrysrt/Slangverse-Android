package id.slangverse.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Reset is destructive and cannot be undone, so the second tap is the contract this locks: nothing
 * is cleared when the card is opened, nothing is cleared on cancel, and one confirmation clears
 * exactly once.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PracticeResetCardTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun nothingIsClearedUntilTheConfirmationIsPressed() {
        var resets = 0
        rule.setContent { PracticeResetCard(answered = 2, likes = 1, saves = 3) { resets++ } }

        rule.onNodeWithText("YES, RESET").assertDoesNotExist()
        rule.onNodeWithText("RESET LOCAL PRACTICE").performClick()
        rule.waitForIdle()
        assertEquals("opening the confirmation must not clear anything", 0, resets)
        rule.onNodeWithText("YES, RESET").assertIsDisplayed()

        rule.onNodeWithText("CANCEL").performClick()
        rule.waitForIdle()
        assertEquals("cancelling must not clear anything", 0, resets)
        rule.onNodeWithText("YES, RESET").assertDoesNotExist()

        rule.onNodeWithText("RESET LOCAL PRACTICE").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("YES, RESET").performClick()
        rule.waitForIdle()
        assertEquals(1, resets)
        // The control returns to its first step instead of staying armed.
        rule.onNodeWithText("YES, RESET").assertDoesNotExist()
        rule.onNodeWithText("RESET LOCAL PRACTICE").assertIsDisplayed()
    }

    @Test
    fun anEmptySnapshotOffersNothingToClear() {
        var resets = 0
        rule.setContent { PracticeResetCard(answered = 0, likes = 0, saves = 0) { resets++ } }

        rule.onNodeWithText("Nothing has been saved on this phone yet.").assertIsDisplayed()
        rule.onNodeWithText("RESET LOCAL PRACTICE").assertDoesNotExist()
        assertEquals(0, resets)
    }
}
