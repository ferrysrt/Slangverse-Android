package id.slangverse.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextDetectiveGameTest {
    @Test fun threeDistinctSlangTermsEachSession() {
        assertEquals(3, ContextDetectiveGame.cases.size)
        for (start in ContextDetectiveGame.cases) {
            val rotated = ContextDetectiveGame.orderedFrom(start.id)
            assertEquals(start.id, rotated.first().id)
            assertEquals(3, rotated.map { it.id }.distinct().size)
            assertEquals(3, rotated.map { it.term }.distinct().size)
        }
    }

    @Test fun correctAndIncorrectAnswersUseStableXpRules() {
        val item = ContextDetectiveGame.cases.first()
        val correct = ContextDetectiveGame.grade(item, item.correctIndex, 0)
        assertTrue(correct.correct)
        assertEquals(80, correct.xpEarned)
        assertEquals(item.correctIndex, correct.correctIndex)
        assertEquals(0, correct.pinnedClueIndex)

        val wrong = ContextDetectiveGame.grade(item, (item.correctIndex + 1) % 4, 2)
        assertFalse(wrong.correct)
        assertEquals(15, wrong.xpEarned)
        assertEquals(2, wrong.pinnedClueIndex)
    }

    @Test fun pinningAnyValidClueDoesNotChangeAnswerGrade() {
        val item = ContextDetectiveGame.cases.first()
        for (clue in item.comments.indices) {
            val d = ContextDetectiveGame.grade(item, item.correctIndex, clue)
            assertTrue(d.correct)
            assertEquals(ContextDetectiveGame.CORRECT_XP, d.xpEarned)
        }
    }

    @Test fun questionCommentIsPresentAndVariesBySlang() {
        val comments = ContextDetectiveGame.cases.map { it.questionComment }
        assertTrue(comments.all { it.isNotBlank() && it.contains("?") })
        assertEquals(comments.size, comments.distinct().size)
        ContextDetectiveGame.cases.forEach { item ->
            assertTrue(item.questionComment.contains(item.term, ignoreCase = true))
        }
    }

    @Test fun pinningEvidenceIsOptional() {
        val item = ContextDetectiveGame.cases.first()
        val result = ContextDetectiveGame.grade(item, item.correctIndex, -1)
        assertTrue(result.correct)
        assertEquals(-1, result.pinnedClueIndex)
        assertEquals(80, result.xpEarned)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidChoiceRejected() {
        ContextDetectiveGame.grade(ContextDetectiveGame.cases.first(), 4, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidClueRejected() {
        ContextDetectiveGame.grade(ContextDetectiveGame.cases.first(), 1, 99)
    }
}
