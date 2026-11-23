package id.slangverse.app

import java.io.File
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ResearchPilotSessionTest {
    private val bytes = File(System.getProperty("slangverse.repositoryRoot"),
        "android/app/src/main/assets/${ResearchPilotBank.ASSET}").readBytes()
    private val bank = ResearchPilotBank.decode(bytes)
    private val started = Instant.parse("2026-11-24T01:00:00Z")
    private val answered = Instant.parse("2026-11-24T01:00:09Z")

    @Test fun bundledBankHasMeaningfulCoverageAndBalancedAnswerPositions() {
        assertEquals("slangverse-context-pilot-v1", bank.id)
        assertEquals(30, bank.posts.size)
        assertEquals(mapOf(0 to 8, 1 to 8, 2 to 7, 3 to 7), bank.posts.groupingBy { it.thread.correctIndex }.eachCount())
        val senses = bank.posts.groupBy { it.learningSenseId }
        assertEquals(10, senses.size)
        senses.forEach { (_, posts) ->
            assertEquals(3, posts.map { it.context }.distinct().size)
            assertEquals(3, posts.map { it.thread.options[it.thread.correctIndex].lowercase() }.distinct().size)
            posts.forEach { post ->
                assertTrue(post.thread.post.contains(post.thread.term, ignoreCase = true))
                assertFalse(post.replies!!.single().text.contains(post.thread.options[post.thread.correctIndex], ignoreCase = true))
            }
        }
    }

    @Test fun bothConditionsUseTheSameBankAndAdaptiveReordersAfterAMiss() {
        val fixed = ResearchPilotSession.start("P001", PilotCondition.Fixed, bank, started)
            .answer(bank, "pilot-01", 0, 900, 1, answered).answer(bank, "pilot-02", 3, 800, 0, answered)
        val adaptive = ResearchPilotSession.start("P002", PilotCondition.Adaptive, bank, started)
            .answer(bank, "pilot-01", 0, 900, 1, answered).answer(bank, "pilot-02", 3, 800, 0, answered)
        assertEquals(fixed.bankSha256, adaptive.bankSha256)
        assertEquals("pilot-03", fixed.nextQuestion(bank)?.thread?.id)
        assertEquals("pilot-11", adaptive.nextQuestion(bank)?.thread?.id)
        assertEquals(PilotCondition.Adaptive, ResearchPilotCodec.decode(ResearchPilotCodec.encode(adaptive)).condition)

        for (condition in PilotCondition.entries) {
            var session = ResearchPilotSession.start("P003", condition, bank, started)
            repeat(30) {
                val post = checkNotNull(session.nextQuestion(bank))
                session = session.answer(bank, post.thread.id, post.thread.correctIndex, 700, 0, answered)
            }
            assertNull(session.nextQuestion(bank))
            assertEquals(bank.posts.map { it.thread.id }.toSet(), session.responses.map { it.questionId }.toSet())
            if (condition == PilotCondition.Fixed) {
                assertEquals(bank.posts.map { it.thread.id }, session.responses.map { it.questionId })
            }
        }
    }

    @Test fun recordedAnswersCannotBeOverwrittenAndAnAlternateBankCannotResume() {
        val initial = ResearchPilotSession.start("PI-04", PilotCondition.Fixed, bank, started)
        assertEquals(initial, initial.answer(bank, "pilot-02", 0, 900, 0, answered))
        assertEquals(initial, initial.answer(bank, "pilot-01", 4, 900, 0, answered))
        val session = initial.answer(bank, "pilot-01", 1, 900, 1, answered)
        assertEquals(session, session.answer(bank, "pilot-01", 0, 2000, 0, answered))
        val changedBytes = ResearchPilotBank.decode(bytes + ' '.code.toByte())
        assertFalse(session.matches(changedBytes))
        assertNull(session.nextQuestion(changedBytes))
        assertEquals(session, session.answer(changedBytes, "pilot-02", 3, 2000, 0, answered))
        assertEquals(session, ResearchPilotCodec.decode(ResearchPilotCodec.encode(session)))
    }

    @Test fun csvCarriesTheLockedRevisionOrderAndResponseTiming() {
        val session = ResearchPilotSession.start("P-005", PilotCondition.Fixed, bank, started)
            .answer(bank, "pilot-01", 1, 3250, 1, answered)
        val csv = session.toCsv().split("\r\n")
        assertTrue(csv[0].contains("bank_sha256,started_at_utc,ordinal"))
        assertTrue(csv[1].contains("\"P-005\",\"Fixed\",\"${bank.id}\",\"${bank.sha256}\""))
        assertTrue(csv[1].endsWith("\"1\",\"1\",\"true\",\"3250\",\"1\",\"$answered\""))
        assertEquals(3, csv.size)
        assertFalse(ResearchPilotSession.isValidCode("=2+2"))
        assertFalse(ResearchPilotSession.isValidCode(""))
    }
}
