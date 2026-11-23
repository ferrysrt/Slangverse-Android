package id.slangverse.app

import java.io.File
import org.json.JSONArray
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
        assertTrue(catalog.senses.isNotEmpty() || catalog.packs.isNotEmpty())
        val ids = mutableSetOf<String>()
        val sessions = catalog.senses.map { sense ->
            parseBankSession(packText(sense.senseId), sense.senseId, true, sense.term, sense.packSha256)
        } + catalog.packs.map { pack ->
            parseMixedBankSession(packText(pack.packId), pack.packId, pack.sha256)
        }
        sessions.forEach { loaded ->
            val posts = loaded.toFeedPosts()
            assertEquals(10, posts.size)
            posts.forEach { post ->
                assertTrue(post.published)
                assertTrue(post.sourceLabel.contains("LLM"))
                assertTrue(loaded.session.questions.any { it.slang == post.thread.term })
                assertTrue(ids.add(post.thread.id))
                assertTrue(post.avatarId in ProfilePictureCatalog.ids)
                assertEquals(post.thread.comments, post.replies!!.map { it.text })
                val story = SocialNarrative.story(post)
                assertEquals("@confuseduser", story.followUpHandle)
                assertTrue(story.followUp.isNotBlank())
                assertNull(story.quote)
            }
            assertEquals(mapOf(FeedDifficulty.Easy to 5, FeedDifficulty.Medium to 3, FeedDifficulty.Hard to 2),
                posts.groupingBy { it.difficulty }.eachCount())
            assertTrue(feedProfiles(posts).keys.containsAll(posts.map { it.thread.handle }))
        }
        assertEquals((catalog.senses.size + catalog.packs.size) * 10, ids.size)
        val feed = mergePublishedFeed(sessions.filter { loaded ->
            selectPublishedPacks(catalog).any { it.id == loaded.session.packId.ifBlank { loaded.session.senseId } }
        })
        assertEquals(selectPublishedPacks(catalog).size * 10, feed.size)
        if (catalog.packs.isEmpty() && catalog.senses.size > 1)
            assertTrue(feed.map { it.thread.term.lowercase() }.distinct().size > 1)
    }

    @Test fun legacyPacksWithoutEditorialMetadataKeepTheirOriginalSourceLabel() {
        val json = JSONObject(packText()).put("referenceStatus", "provisional")
        json.remove("editorialReview")
        val loaded = parseBankSession(json.toString(), "cooked-trouble", requireLlm = true)
        assertFalse(ApiSession("legacy", "github-static-llm", emptyList()).editoriallyEdited)
        assertFalse(loaded.session.editoriallyEdited)
        assertEquals("github-static-llm", loaded.session.source)
        assertTrue(loaded.toFeedPosts().all {
            it.sourceLabel == "Published LLM pack · GitHub GET · locally graded · provisional"
        })
    }

    @Test fun onlyTheExactAgentEditedProvisionalStatusMarksPacksAsEdited() {
        for (status in listOf("", "provisional", "agent-edited-provisional-other")) {
            val json = JSONObject(packText())
                .put("editorialReview", JSONObject().put("status", status))
            val loaded = parseBankSession(json.toString(), "cooked-trouble", requireLlm = true)
            assertFalse(loaded.session.editoriallyEdited)
            assertTrue(loaded.toFeedPosts().none { it.sourceLabel.contains("agent-edited") })
        }
    }

    @Test fun agentEditedPacksDiscloseEditingWithoutChangingProvisionalGrading() {
        val json = JSONObject(packText()).put("referenceStatus", "provisional")
        json.remove("editorialReview")
        val original = parseBankSession(json.toString(), "cooked-trouble", requireLlm = true)
        json.put("editorialReview", JSONObject().put("status", "agent-edited-provisional"))
        val edited = parseBankSession(json.toString(), "cooked-trouble", requireLlm = true)
        assertTrue(edited.session.editoriallyEdited)
        assertEquals("github-static-llm", edited.session.source)
        assertEquals("provisional", edited.session.referenceStatus)
        assertEquals(original.session.questions, edited.session.questions)
        assertEquals(original.answers, edited.answers)
        val posts = edited.toFeedPosts()
        assertTrue(posts.all {
            it.sourceLabel == "Published LLM pack · agent-edited · GitHub GET · locally graded · provisional"
        })
        val question = edited.session.questions.first()
        val post = posts.first().thread
        for (selected in question.options.indices) {
            val grade = gradeStaticAnswer(edited.answers, question.id, selected, 0, 0)
            assertEquals(gradeStaticAnswer(original.answers, question.id, selected, 0, 0), grade)
            assertEquals(if (selected == post.correctIndex) 80 else 15, grade.xpEarned)
            assertEquals(grade.xpEarned, FeedPracticeProgress(posts = posts).answer(post.id, selected).xp)
        }
    }

    @Test fun everySupportedCommentCountReachesThePublishedStoryInOrder() {
        for (count in 1..5) {
            val json = JSONObject(packText())
            val comments = (1..count).map { index ->
                JSONObject().put("handle", "@reply$index").put("text", "Context clue $index")
                    .put("avatarId", ProfilePictureCatalog.ids.first())
            }
            json.getJSONArray("questions").getJSONObject(0).getJSONObject("post")
                .put("comments", JSONArray(comments))
            val loaded = parseBankSession(json.toString(), "cooked-trouble", requireLlm = true)
            val post = loaded.toFeedPosts().first()
            val replies = SocialNarrative.story(post).replies
            assertEquals(count, loaded.session.questions.first().post.comments.size)
            assertEquals((1..count).map { "Context clue $it" }, post.thread.comments)
            assertEquals(post.thread.comments, replies.map { it.text })
            assertEquals((1..count).map { "@reply$it" }, replies.map { it.handle })
            assertTrue(replies.all { it.avatarId == ProfilePictureCatalog.ids.first() })
        }
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
        assertEquals("@confuseduser", update.handle)
        assertEquals(SocialNarrative.story(first.first()).followUp, update.text)
        val incorrect = FeedPracticeProgress(posts = first).answer(post.id, (post.correctIndex + 1) % post.options.size)
        assertEquals(listOf(update), SocialNarrative.updates(incorrect))
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
