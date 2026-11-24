package id.slangverse.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

internal data class ResearchQuestion(val packId: String, val sha256: String, val questionId: String) {
    val postId get() = "$packId/$sha256/$questionId"
}

internal data class ParticipantAttempt(
    val eventId: String,
    val question: ResearchQuestion,
    val selectedIndex: Int,
    val mode: String,
    val answeredAt: String,
    val synced: Boolean = false
)

internal fun validResearchUsername(username: String) = username.matches(Regex("[A-Za-z0-9_-]{1,24}"))

internal fun guestUsername(random: Random = Random.Default) =
    "guest${random.nextInt(100_000_000, 1_000_000_000)}"

internal fun loadResearchBank(readAsset: (String) -> String): List<StaticBankSession> {
    val catalog = parseBankCatalog(readAsset("catalog.json"), requireLlm = true)
    require(catalog.packs.isNotEmpty())
    return catalog.packs.map { pack ->
        val session = parseMixedBankSession(readAsset("packs/${pack.packId}.json"), pack.packId, pack.sha256)
        check(session.session.questions.size == pack.questionCount)
        session
    }
}

internal object ParticipantAttemptsCodec {
    fun encode(attempts: List<ParticipantAttempt>): String {
        val data = JSONArray()
        for (attempt in attempts) data.put(JSONObject()
            .put("event", attempt.eventId).put("pack", attempt.question.packId)
            .put("sha256", attempt.question.sha256).put("question", attempt.question.questionId)
            .put("selected", attempt.selectedIndex).put("mode", attempt.mode)
            .put("answered_at", attempt.answeredAt).put("synced", attempt.synced))
        return data.toString()
    }

    fun decode(raw: String): List<ParticipantAttempt> {
        val data = JSONArray(raw)
        return List(data.length()) { index ->
            val row = data.getJSONObject(index)
            ParticipantAttempt(row.getString("event"),
                ResearchQuestion(row.getString("pack"), row.getString("sha256"), row.getString("question")),
                row.getInt("selected"), row.getString("mode"), row.getString("answered_at"), row.getBoolean("synced"))
        }
    }
}

internal class ResearchParticipant(context: Context, private val client: StagingAccountClient, project: String) {
    private val prefs = context.getSharedPreferences("research-participant-$project", Context.MODE_PRIVATE)
    private val wakeSync = Channel<Unit>(Channel.CONFLATED)
    private val syncWorker = Mutex()
    private var attempts by mutableStateOf(ParticipantAttemptsCodec.decode(prefs.getString("attempts", "[]") ?: "[]"))
    private var session = prefs.getString("session", null)?.let {
        val data = JSONObject(it)
        ParticipantSession(data.getString("user"), data.getString("access"), data.getString("refresh"))
    }
    private var registered = false
    private var sessionSaved = true
    var username by mutableStateOf(prefs.getString("username", null))
        private set
    var syncNotice by mutableStateOf<String?>(null)
        private set
    val savedAnswers get() = attempts.associate { it.question.postId to it.selectedIndex }
    val pendingAnswers get() = attempts.count { !it.synced }

    fun start(name: String): Boolean {
        if (username != null) return true
        val chosen = name.trim().ifEmpty { guestUsername() }
        if (!validResearchUsername(chosen)) return false
        if (!prefs.edit().putString("username", chosen).commit()) return false
        username = chosen
        return true
    }

    fun queue(question: ResearchQuestion, selected: Int, order: PracticeOrder): Boolean {
        if (username == null || selected !in 0..3) return false
        if (attempts.any { it.question == question }) return true
        val mode = if (order == PracticeOrder.Adaptive) "adaptive" else "browse"
        val attempt = ParticipantAttempt(UUID.randomUUID().toString(), question, selected, mode, Instant.now().toString())
        if (!saveAttempts(attempts + attempt)) {
            syncNotice = "Your answer could not be saved. Try submitting it again."
            return false
        }
        if (syncNotice == "Answers synced") syncNotice = null
        wakeSync.trySend(Unit)
        return true
    }

    private fun saveAttempts(next: List<ParticipantAttempt>): Boolean {
        if (!prefs.edit().putString("attempts", ParticipantAttemptsCodec.encode(next)).commit()) return false
        attempts = next
        return true
    }

    private suspend fun renew(current: ParticipantSession?): ParticipantSession = withContext(NonCancellable) {
        val renewed = if (current == null) client.join() else client.refresh(current)
        session = renewed
        saveSession(renewed)
        renewed
    }

    private fun saveSession(active: ParticipantSession) {
        val data = JSONObject().put("user", active.userId).put("access", active.accessToken)
            .put("refresh", active.refreshToken)
        sessionSaved = prefs.edit().putString("session", data.toString()).commit()
        check(sessionSaved)
    }

    suspend fun sync() = syncWorker.withLock {
        val name = username ?: return@withLock
        while (currentCoroutineContext().isActive) {
            try {
                if (!registered || pendingAnswers != 0) {
                    val active = session ?: renew(null)
                    if (!sessionSaved) saveSession(active)
                    try {
                        upload(name, active)
                    } catch (error: ParticipantSyncError) {
                        if (!error.needsRefresh) throw error
                        upload(name, renew(active))
                    }
                    syncNotice = "Answers synced"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ParticipantSyncError) {
                syncNotice = error.message
            } catch (_: Exception) {
                syncNotice = "Sync unavailable. Your answers are saved on this phone."
            }
            withTimeoutOrNull(15_000) { wakeSync.receive() }
        }
    }

    private suspend fun upload(name: String, active: ParticipantSession) {
        if (!registered) {
            client.register(name, active)
            registered = true
        }
        for (attempt in attempts.filterNot { it.synced }) {
            client.record(attempt, active)
            check(saveAttempts(attempts.map { if (it.eventId == attempt.eventId) it.copy(synced = true) else it }))
        }
    }
}
