package id.slangverse.app

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PublishedFeedTest {
    private val bank = File(System.getProperty("slangverse.repositoryRoot"), "question-bank")
    private fun packText(sense: String = "cooked-trouble") = File(bank, "packs/$sense.json").readText()
    private fun pack(sense: String = "cooked-trouble") = parseBankSession(packText(sense), sense, requireLlm = true)

    @Test fun actualPublishedBankParsesIntoSocialConversationsWithSourceAndAvatarMetadata() {
        val catalog = parseBankCatalog(File(bank, "catalog.json").readText(), requireLlm = true)
        assertEquals("llm", catalog.generationSource)
        assertTrue(catalog.senses.isNotEmpty())
        val ids = mutableSetOf<String>()
        catalog.senses.forEach { sense ->
            val loaded = parseBankSession(packText(sense.senseId), sense.senseId, true, sense.term, sense.packSha256)
            val posts = loaded.toFeedPosts()
            assertEquals(10, posts.size)
            posts.forEach { post ->
                assertTrue(post.published)
                assertTrue(post.sourceLabel.contains("LLM"))
                assertEquals(sense.term.lowercase(), post.thread.term.lowercase())
                assertTrue(ids.add(post.thread.id))
                assertTrue(post.avatarId in ProfilePictureCatalog.ids)
                assertEquals(post.thread.comments, post.replies!!.map { it.text })
                assertTrue(SocialNarrative.story(post).followUp.isEmpty())
            }
            assertEquals(mapOf(FeedDifficulty.Easy to 5, FeedDifficulty.Medium to 3, FeedDifficulty.Hard to 2),
                posts.groupingBy { it.difficulty }.eachCount())
            assertTrue(feedProfiles(posts).keys.containsAll(posts.map { it.thread.handle }))
        }
        assertEquals(catalog.senses.size * 10, ids.size)
    }

    @Test fun defaultFeedRejectsMockWhileLegacyTrainingCanStillOpenIt() {
        val text = JSONObject(packText()).put("generationSource", "mock").toString()
        assertThrows(ApiException::class.java) { parseBankSession(text, "cooked-trouble", requireLlm = true) }
        val legacy = parseBankSession(text, "cooked-trouble")
        assertEquals("github-static-mock", legacy.session.source)
        assertThrows(IllegalArgumentException::class.java) { legacy.toFeedPosts() }
        val catalog = JSONObject(File(bank, "catalog.json").readText()).put("generationSource", "mock")
        assertThrows(ApiException::class.java) { parseBankCatalog(catalog.toString(), requireLlm = true) }
    }

    @Test fun staleHashesAndWrongSlangAreRejectedBeforeGrading() {
        assertThrows(ApiException::class.java) {
            parseBankSession(packText(), "cooked-trouble", true, expectedSha256 = "0".repeat(64))
        }
        assertThrows(ApiException::class.java) {
            parseBankSession(packText(), "cooked-trouble", true, expectedTerm = "lowkey")
        }
    }

    @Test fun switchingPacksRetainsAnswersButCountsOnlyTheActivePackAndCannotFarmXp() {
        val first = pack().toFeedPosts()
        val second = pack("no-cap-truth").toFeedPosts()
        val post = first.first().thread
        val answered = FeedPracticeProgress(posts = first).answer(post.id, post.correctIndex)
        assertEquals(80, answered.xp)
        val switched = answered.copy(posts = second)
        assertEquals(0, switched.xp)
        assertEquals(0, switched.completedCount)
        val restored = switched.copy(posts = first)
        assertEquals(80, restored.xp)
        assertEquals(restored, restored.answer(post.id, post.correctIndex))
        val update = SocialNarrative.updates(restored).single()
        assertTrue(update.isFeedback)
        assertEquals(post.id, update.postId)
    }

    @Test fun bankRevisionChangesInvalidateOldAnswersAndEmptyCatalogIsNotADemo() {
        val original = pack().toFeedPosts()
        val first = original.first().thread
        val answered = FeedPracticeProgress(posts = original).answer(first.id, first.correctIndex)
        val changed = JSONObject(packText()).put("bankNote", "new published revision").toString()
        val newer = parseBankSession(changed, "cooked-trouble", true).toFeedPosts()
        assertNotEquals(original.first().thread.id, newer.first().thread.id)
        assertEquals(0, answered.copy(posts = newer).xp)
        val pending = """{"schemaVersion":1,"generationSource":"llm","status":"awaiting-generation","senses":[]}"""
        assertTrue(parseBankCatalog(pending, requireLlm = true).senses.isEmpty())
        assertEquals(0, FeedPracticeProgress(posts = emptyList()).completedCount)
        assertNull(publishedContext("unknown-category", "unknown-scenario"))
    }
}
