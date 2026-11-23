package id.slangverse.app

import android.content.Context
import java.time.Instant
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal enum class PilotCondition(val label: String) { Fixed("Fixed order"), Adaptive("Adaptive") }

internal data class PilotResponse(
    val questionId: String,
    val senseId: String,
    val term: String,
    val context: String,
    val selected: Int,
    val correctIndex: Int,
    val elapsedMs: Long,
    val hintsViewed: Int,
    val answeredAt: Instant
) {
    init {
        require(selected in 0..3 && correctIndex in 0..3 && elapsedMs >= 0 && hintsViewed in 0..1)
    }

    val correct: Boolean get() = selected == correctIndex
}

internal data class ResearchPilotSession(
    val id: String,
    val participantCode: String,
    val condition: PilotCondition,
    val bankId: String,
    val bankSha256: String,
    val startedAt: Instant,
    val responses: List<PilotResponse> = emptyList()
) {
    init {
        require(isValidCode(participantCode) && bankSha256.matches(Regex("[0-9a-f]{64}")))
        require(responses.size <= 30 && responses.map(PilotResponse::questionId).distinct().size == responses.size)
    }

    fun matches(bank: ResearchPilotBank): Boolean = bank.id == bankId && bank.sha256 == bankSha256

    fun nextQuestion(bank: ResearchPilotBank): FeedPracticePost? {
        if (!matches(bank)) return null
        val answered = responses.associate { it.questionId to it.selected }
        if (condition == PilotCondition.Fixed) return bank.posts.firstOrNull { it.thread.id !in answered }
        val next = PracticeLearning(FeedPracticeProgress(answers = answered, posts = bank.posts)).recommendation
        return bank.posts.firstOrNull { it.thread.id == next?.postId }
    }

    fun answer(bank: ResearchPilotBank, questionId: String, selected: Int, elapsedMs: Long,
               hintsViewed: Int, now: Instant = Instant.now()): ResearchPilotSession {
        val post = nextQuestion(bank) ?: return this
        if (post.thread.id != questionId || selected !in post.thread.options.indices) return this
        val response = PilotResponse(questionId, checkNotNull(post.learningSenseId), post.thread.term,
            checkNotNull(post.context).name, selected, post.thread.correctIndex, elapsedMs, hintsViewed, now)
        return copy(responses = responses + response)
    }

    fun toCsv(): String = buildString {
        append("session_id,participant_code,condition,bank_id,bank_sha256,started_at_utc,ordinal,question_id,sense_id,term,context,selected_index,correct_index,is_correct,response_time_ms,hints_viewed,answered_at_utc\r\n")
        responses.forEachIndexed { index, response ->
            append(listOf(id, participantCode, condition.name, bankId, bankSha256, startedAt, index + 1,
                response.questionId, response.senseId, response.term, response.context, response.selected,
                response.correctIndex, response.correct, response.elapsedMs, response.hintsViewed, response.answeredAt)
                .joinToString(",") { value -> "\"${value.toString().replace("\"", "\"\"")}\"" })
            append("\r\n")
        }
    }

    companion object {
        private val codePattern = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,23}")

        fun isValidCode(code: String): Boolean = codePattern.matches(code)

        fun start(code: String, condition: PilotCondition, bank: ResearchPilotBank,
                  now: Instant = Instant.now()): ResearchPilotSession =
            ResearchPilotSession(UUID.randomUUID().toString(), code, condition, bank.id, bank.sha256, now)
    }
}

internal object ResearchPilotCodec {
    fun encode(session: ResearchPilotSession): String = JSONObject()
        .put("schema", 1).put("id", session.id).put("participantCode", session.participantCode)
        .put("condition", session.condition.name).put("bankId", session.bankId)
        .put("bankSha256", session.bankSha256).put("startedAt", session.startedAt.toString())
        .put("responses", JSONArray(session.responses.map { response ->
            JSONObject().put("questionId", response.questionId).put("senseId", response.senseId)
                .put("term", response.term).put("context", response.context).put("selected", response.selected)
                .put("correctIndex", response.correctIndex).put("elapsedMs", response.elapsedMs)
                .put("hintsViewed", response.hintsViewed).put("answeredAt", response.answeredAt.toString())
        })).toString()

    fun decode(text: String): ResearchPilotSession {
        val json = JSONObject(text)
        require(json.getInt("schema") == 1)
        val responses = json.getJSONArray("responses").objects().map { response ->
            PilotResponse(response.getString("questionId"), response.getString("senseId"), response.getString("term"),
                response.getString("context"), response.getInt("selected"), response.getInt("correctIndex"),
                response.getLong("elapsedMs"), response.getInt("hintsViewed"), Instant.parse(response.getString("answeredAt")))
        }
        return ResearchPilotSession(json.getString("id"), json.getString("participantCode"),
            PilotCondition.valueOf(json.getString("condition")), json.getString("bankId"), json.getString("bankSha256"),
            Instant.parse(json.getString("startedAt")), responses)
    }
}

internal class ResearchPilotStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("research-pilot", Context.MODE_PRIVATE)

    fun load(): ResearchPilotSession? = prefs.getString("session-v1", null)?.let(ResearchPilotCodec::decode)
    fun save(session: ResearchPilotSession): Boolean = prefs.edit()
        .putString("session-v1", ResearchPilotCodec.encode(session)).commit()
    fun clear(): Boolean = prefs.edit().remove("session-v1").commit()
}
