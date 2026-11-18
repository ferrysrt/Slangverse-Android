package id.slangverse.app

import org.junit.Assert.*
import org.junit.Test

class TrainingSessionValidationTest {
    private fun question(id: String, options: List<String>) = ApiQuestion(
        id, "slang", "easy", "context", ApiPost("Name", "@name", "#tag", "post", emptyList()),
        "Question?", options
    )

    @Test fun validSessionsRemainUnchangedForBothSources() {
        for (source in listOf("github-static", "backend")) {
            val valid = ApiSession("valid", source, listOf(
                question("one", listOf("One.", "Two", "Three", "Four"))
            ))
            assertSame(valid, validateTrainingSession(valid))
            assertEquals("One.", valid.questions.single().options.first())
        }
    }

    @Test fun laterAmbiguousQuestionMustRejectWholeSession() {
        val candidate = ApiSession("new", "backend", listOf(
            question("first", listOf("One.", "Two", "Three", "Four")),
            question("later", listOf("Same.", "same", "Other", "Last"))
        ))
        try {
            validateTrainingSession(candidate)
            fail("session must be rejected before the later ambiguous question")
        } catch (e: ApiException) {
            assertTrue(e.message.orEmpty().contains("later"))
        }
    }
}
