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

    @Test fun everyPublishedPackOptionSurvivesDisplayNormalisation() {
        // Any published pack is exercised: the previous version pinned one pack ID and its Q1 text,
        // so publishing a newer pack broke a test that has nothing to do with the published data.
        val bank = java.io.File(System.getProperty("slangverse.repositoryRoot"), "question-bank")
        val catalog = parseBankCatalog(java.io.File(bank, "catalog.json").readText(), requireLlm = true)
        assertTrue("expected at least one published mixed pack", catalog.packs.isNotEmpty())
        catalog.packs.forEach { pack ->
            val session = parseMixedBankSession(java.io.File(bank, "packs/${pack.packId}.json").readText(),
                pack.packId, pack.sha256)
            session.session.questions.forEach { question ->
                val original = question.options.toList()
                val labels = requireNotNull(display(original)) { "Display collision in ${question.id}" }
                assertEquals(original.size, labels.size)
                assertTrue("${question.id}: ${labels}", labels.none { it.endsWith('.') })
                // The graded option stays the verbatim reference meaning; only the label loses the period.
                assertEquals(original, question.options)
                assertEquals(original[session.answers.getValue(question.id).correctIndex],
                    question.options[session.answers.getValue(question.id).correctIndex])
            }
        }
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
