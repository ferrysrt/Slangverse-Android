package id.slangverse.app

import org.junit.Assert.*
import org.junit.Test

class AnswerOptionDisplayTest {
    private fun display(options: List<String>): List<String>? = displayAnswerOptions(options)

    @Test fun currentAndLegacyPacksKeepIndicesAndScoringAfterDisplayFormatting() {
        val bank = java.io.File(System.getProperty("slangverse.repositoryRoot"), "question-bank")
        val catalog = parseBankCatalog(java.io.File(bank, "catalog.json").readText(), requireLlm = true)
        val sessions = catalog.senses.map { sense ->
            parseBankSession(java.io.File(bank, "packs/${sense.senseId}.json").readText(),
                sense.senseId, true, sense.term, sense.packSha256)
        } + catalog.packs.map { pack ->
            parseMixedBankSession(java.io.File(bank, "packs/${pack.packId}.json").readText(), pack.packId, pack.sha256)
        }
        assertTrue(sessions.isNotEmpty())
        sessions.forEach { loaded ->
            loaded.toFeedPosts().forEach { post ->
                val original = post.thread.options.toList()
                val labels = requireNotNull(display(original)) { "Display collision in ${post.thread.id}" }
                assertEquals(original.size, labels.size)
                assertTrue(labels.none { it.endsWith('.') })
                assertEquals(original, post.thread.options)
                labels.indices.forEach { index ->
                    val result = FeedPracticeProgress(posts = listOf(post)).answer(post.thread.id, index)
                    assertEquals(index == post.thread.correctIndex, result.decisions.single().correct)
                    assertEquals(if (index == post.thread.correctIndex) 80 else 15, result.xp)
                }
            }
        }
    }

    @Test fun publishedMixedQ1RemovesPeriodInsideClosingQuoteWithoutChangingOptions() {
        val bank = java.io.File(System.getProperty("slangverse.repositoryRoot"), "question-bank")
        val catalog = parseBankCatalog(java.io.File(bank, "catalog.json").readText(), requireLlm = true)
        val pack = catalog.packs.single { it.packId == "mixed-20261117t194601433z-8ca3b36c-8d92-4c8f-90d8-e61c571d6597" }
        val session = parseMixedBankSession(java.io.File(bank, "packs/${pack.packId}.json").readText(), pack.packId, pack.sha256)
        val question = session.session.questions.single { it.id == "${pack.packId}-q1" }
        val original = question.options.toList()
        val labels = requireNotNull(display(question.options))
        val quoted = "An inside joke or niche reference, short for “if you know, you know.”"
        assertTrue(original.contains(quoted))
        assertEquals("An inside joke or niche reference, short for “if you know, you know”", labels[original.indexOf(quoted)])
        assertEquals(original, question.options)
    }

    @Test fun otherPunctuationInternalPeriodsAndWhitespaceRemainSafe() {
        assertEquals(listOf("Really?", "Yes!", "U.S. travel", "Value 3.14"),
            display(listOf(" Really? ", "Yes!", "U.S. travel.", "Value 3.14.")))
        assertNull(display(listOf(".", "two", "three", "four")))
    }

    @Test fun quotedTerminalPeriodsStillDetectCollisionsAndKeepMeaningfulPunctuation() {
        assertNull(display(listOf("He said “yes.”", "He said “yes”", "Other", "Third")))
        assertEquals(listOf("Wait...", "Really?!", "U.S. travel", "Value 3.14"),
            display(listOf("Wait...", "Really?!", "U.S. travel.", "Value 3.14.")))
    }

    @Test fun abbreviationsKeepTheirMeaningfulFinalPeriod() {
        assertEquals(listOf("U.S.", "e.g.", "Other", "Third"),
            display(listOf("U.S.", "e.g.", "Other.", "Third")))
    }

    @Test fun blankOptionIsUnavailable() {
        assertNull(display(listOf("Meaning.", "   ", "Another", "Third")))
    }

    @Test fun whitespaceDifferencesCannotHideCollisions() {
        assertNull(display(listOf("Same  words.", "same words", "Other", "Third")))
    }

    @Test fun collidingNormalizedOptionsAreNotAnswerable() {
        assertNull(display(listOf("Same.", "same", "Other", "Third")))
    }

    @Test fun terminalPeriodsAreRemovedFromAllOptionsWithoutChangingTheModel() {
        val options = listOf("Correct meaning.", "Distractor", "Another choice.", "Final option")
        val original = options.toList()
        assertEquals(listOf("Correct meaning", "Distractor", "Another choice", "Final option"), display(options))
        assertEquals(original, options)
    }
}
