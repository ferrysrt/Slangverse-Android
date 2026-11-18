package id.slangverse.app

import org.junit.Assert.*
import org.junit.Test

class RemoteTrainingAnswerDisplayTest {
    @Test fun displayedChoicesKeepOriginalIndicesForStaticGrading() {
        val options = listOf("Wrong.", "Right.", "Another", "Last.")
        val labels = requireNotNull(displayAnswerOptions(options))
        assertEquals(listOf("Wrong", "Right", "Another", "Last"), labels)
        val answers = mapOf("q" to StaticAnswer(1, "Explanation"))
        labels.indices.forEach { selectedIndex ->
            val grade = gradeStaticAnswer(answers, "q", selectedIndex, 0, 0)
            assertEquals(selectedIndex == 1, grade.correct)
            assertEquals(if (selectedIndex == 1) 80 else 15, grade.xpEarned)
            assertEquals("Right", labels[grade.correctIndex])
        }
        assertEquals(listOf("Wrong.", "Right.", "Another", "Last."), options)
    }

    @Test fun unsafeChoicesAreUnavailable() {
        assertNull(displayAnswerOptions(listOf("Same.", "same", "Other", "Last")))
        assertNull(displayAnswerOptions(listOf(".", "Different", "Other", "Last")))
    }
}
