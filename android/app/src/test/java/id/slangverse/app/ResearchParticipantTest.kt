package id.slangverse.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.file.Paths
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ResearchParticipantTest {
    @Test
    fun bundledBankUsesTheSamePackRevisionsAsTheServerSeed() {
        val bank = Paths.get(System.getProperty("slangverse.repositoryRoot"), "question-bank").toFile()
        val sessions = loadResearchBank { bank.resolve(it).readText() }
        val posts = mergePublishedFeed(sessions)
        assertEquals(90, posts.size)
        assertTrue(posts.all { it.thread.id == it.researchQuestion?.postId })
    }

    @Test
    fun skippedUsernameAndPendingAnswerSurviveReopenAndRetryUsingOneEvent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val calls = mutableListOf<Pair<String, JSONObject>>()
        val client = StagingAccountClient { path, body, token ->
            calls += path to JSONObject(body)
            when {
                path == "/auth/v1/signup" -> {
                    assertEquals(null, token)
                    assertEquals(0, JSONObject(body).length())
                    session("first.token", "first-refresh")
                }
                path.endsWith("register_research_participant") -> StagingResponse(204, "")
                path.endsWith("record_research_answer") && token == "first.token" -> StagingResponse(401, "")
                path.endsWith("grant_type=refresh_token") -> {
                    assertEquals("first-refresh", JSONObject(body).getString("refresh_token"))
                    session("second.token", "second-refresh")
                }
                path.endsWith("record_research_answer") && token == "second.token" -> StagingResponse(204, "")
                else -> error("Unexpected request")
            }
        }
        val first = ResearchParticipant(context, client, "participant-test")
        assertFalse(first.start("user@example.test"))
        assertTrue(first.start(""))
        assertTrue(first.username!!.matches(Regex("guest[0-9]{9}")))
        val question = ResearchQuestion("pack", "a".repeat(64), "question")
        assertTrue(first.queue(question, 1, PracticeOrder.Adaptive))
        assertTrue(first.queue(question, 2, PracticeOrder.Adaptive))
        assertEquals(1, first.pendingAnswers)

        val restored = ResearchParticipant(context, client, "participant-test")
        assertEquals(first.username, restored.username)
        assertEquals(mapOf(question.postId to 1), restored.savedAnswers)
        val sync = launch { restored.sync() }
        try {
            withTimeout(5_000) { while (restored.pendingAnswers != 0) yield() }
        } finally {
            sync.cancelAndJoin()
        }
        val records = calls.filter { it.first.endsWith("record_research_answer") }.map { it.second }
        assertEquals(2, records.size)
        assertEquals(records.first().toString(), records.last().toString())
        assertEquals("adaptive", records.first().getString("p_mode"))
        assertEquals(restored.username, calls.first { it.first.endsWith("register_research_participant") }
            .second.getString("p_username"))
        val synced = ResearchParticipant(context, client, "participant-test")
        assertEquals(0, synced.pendingAnswers)
        assertEquals(mapOf(question.postId to 1), synced.savedAnswers)
    }

    private fun session(token: String, refresh: String) = StagingResponse(200, """
        {"access_token":"$token","refresh_token":"$refresh","user":{"id":"participant-id","is_anonymous":true}}
    """.trimIndent())
}
