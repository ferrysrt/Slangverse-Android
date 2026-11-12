package id.slangverse.app

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real running Compose app on an Android emulator.
 * A separate GitHub Actions step starts the local MOCK Node backend.
 * No OpenRouter credentials, live LLM calls, or real participant data.
 */
@RunWith(AndroidJUnit4::class)
class SlangversePlaythroughTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 30_000L) {
        rule.waitUntil(timeoutMs) {
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun userCanPlayBuiltInSocialFeedDemoAndSeeRewards() {
        rule.onNodeWithText("◎ SLANGVERSE").assertExists()
        rule.onAllNodesWithText("DECODE THIS POST")[0].performScrollTo().performClick()
        rule.onNodeWithText("What does 'cooked' mean here?").assertExists()
        rule.onNodeWithText("B   In serious trouble").performScrollTo().performClick()
        rule.onNodeWithText("SUBMIT ANSWER").performScrollTo().performClick()
        rule.onNodeWithText("VIEW REWARDS →").performScrollTo().performClick()
        rule.onNodeWithText("🏆 QUEST COMPLETE!", substring = true).assertExists()
        rule.onNodeWithText("EXPLORE NEXT QUEST").performScrollTo().performClick()
        rule.onNodeWithText("Trending & Explore").assertExists()
    }

    @Test
    fun sourceSelectorShowsGitHubAndBackendWithoutPublicSecrets() {
        rule.onNodeWithText("PLAY API SESSION →").performScrollTo().performClick()
        rule.onNodeWithText("Choose question source").assertExists()
        rule.onNodeWithText("GitHub GET").assertExists()
        rule.onNodeWithText("LOAD AVAILABLE SLANG").assertIsNotEnabled()
        rule.onNodeWithText("Live Backend").performClick()
        rule.onNodeWithText("Connect to the backend").assertExists()
        rule.onNodeWithText("Backend access token (optional for local)").assertExists()
    }

    @Test
    fun userCanFinishTenBackendQuestionsAndEarnEightHundredXp() {
        rule.onNodeWithText("PLAY API SESSION →").performScrollTo().performClick()
        rule.onNodeWithText("Live Backend").performClick()
        // Emulator reaches the real Node mock server at http://10.0.2.2:8787.
        rule.onNodeWithText("LOAD AVAILABLE SLANG").performScrollTo().performClick()
        waitForText("200 unreviewed candidate terms")
        rule.onNodeWithText("START 10-CONTEXT SESSION").performScrollTo().performClick()
        waitForText("QUESTION SOURCE: MOCK")

        repeat(10) { index ->
            // All cooked-trouble test fixture keys have the literal option below.
            rule.onNodeWithText("In serious trouble", substring = true).performScrollTo().performClick()
            rule.onNodeWithText("SUBMIT ANSWER").performScrollTo().performClick()
            waitForText("Correct! +80 XP")
            if (index == 9) {
                rule.onNodeWithText("SHOW SESSION RESULTS").performScrollTo().performClick()
            } else {
                rule.onNodeWithText("NEXT POST →").performScrollTo().performClick()
            }
        }

        rule.onNodeWithText("SESSION COMPLETE", substring = true).assertExists()
        rule.onNodeWithText("Correct: 10 / 10").assertExists()
        rule.onNodeWithText("Total earned XP: 800").assertExists()
        rule.onNodeWithText("START NEW SESSION").performScrollTo().performClick()
        rule.onNodeWithText("Choose question source").assertExists()
    }
}
