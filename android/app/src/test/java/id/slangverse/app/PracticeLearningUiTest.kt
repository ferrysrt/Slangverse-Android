package id.slangverse.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PracticeLearningUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun switchingToFixedShowsTheNonAdaptiveSettingAndPersistsIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PracticeProgressStore(context)
        rule.setContent {
            var order by remember { mutableStateOf(store.loadOrder()) }
            Column {
                PracticeOrderPicker(order) { selected ->
                    order = selected
                    store.saveOrder(selected)
                }
            }
        }
        rule.onNodeWithText("Browse").assertIsSelected()
        rule.onNodeWithText("Adaptive").performClick()
        rule.onNodeWithText("Adaptive").assertIsSelected()
        rule.onNodeWithText("Fixed order").performClick()
        rule.onNodeWithText("Fixed order").assertIsSelected()
        rule.onNodeWithText(PracticeOrder.Fixed.description).assertIsDisplayed()
        assertEquals(PracticeOrder.Fixed, PracticeProgressStore(context).loadOrder())
    }

    @Test fun collectionKeepsUnseenMeaningsHiddenAndOpensTheExactQuestion() {
        val post = SocialFeedGame.posts.first()
        var opened: String? = null
        rule.setContent {
            Column(Modifier.fillMaxSize()) {
                SlangCollection(PracticeLearning(FeedPracticeProgress(posts = listOf(post)))) { opened = it }
            }
        }
        rule.onNodeWithText(post.thread.meaning).assertDoesNotExist()
        rule.onNodeWithText("Answer once to reveal this meaning.").assertIsDisplayed()
        rule.onNodeWithText("Practice this meaning").performScrollTo().performClick()
        assertEquals(post.thread.id, opened)
    }
}
