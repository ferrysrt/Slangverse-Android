package id.slangverse.app

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class StagingAccountConfig private constructor(val url: String, val publishableKey: String) {
    companion object {
        fun create(url: String, key: String): StagingAccountConfig? {
            val origin = url.trim().trimEnd('/')
            if (!origin.matches(Regex("https://[a-z0-9]+\\.supabase\\.co")) ||
                !key.matches(Regex("sb_publishable_[A-Za-z0-9_-]+"))
            ) {
                return null
            }
            return StagingAccountConfig(origin, key)
        }
    }
}

internal class ParticipantSession(val userId: String, val accessToken: String, val refreshToken: String)
internal data class StagingResponse(val status: Int, val body: String)
internal class ParticipantSyncError(message: String, val needsRefresh: Boolean = false) : Exception(message)

internal fun interface StagingTransport {
    suspend fun post(path: String, body: String, accessToken: String?): StagingResponse
}

internal class StagingAccountClient(private val transport: StagingTransport) {
    constructor(config: StagingAccountConfig) : this(SupabaseStagingTransport(config))

    suspend fun join(): ParticipantSession = session("/auth/v1/signup", JSONObject())

    suspend fun refresh(current: ParticipantSession): ParticipantSession {
        val renewed = session("/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", current.refreshToken))
        check(renewed.userId == current.userId)
        return renewed
    }

    suspend fun register(username: String, session: ParticipantSession) {
        rpc("register_research_participant", JSONObject().put("p_username", username), session)
    }

    suspend fun record(attempt: ParticipantAttempt, session: ParticipantSession) {
        val data = JSONObject()
            .put("p_event_id", attempt.eventId)
            .put("p_pack_id", attempt.question.packId)
            .put("p_pack_sha256", attempt.question.sha256)
            .put("p_question_id", attempt.question.questionId)
            .put("p_selected_index", attempt.selectedIndex)
            .put("p_mode", attempt.mode)
            .put("p_answered_at", attempt.answeredAt)
        rpc("record_research_answer", data, session)
    }

    private suspend fun session(path: String, data: JSONObject): ParticipantSession {
        val response = transport.post(path, data.toString(), null)
        if (response.status == 429) throw ParticipantSyncError("Sync is busy. Your answers are saved on this phone.")
        if (response.status !in 200..299) {
            val message = if (path.endsWith("/signup") && response.status in 400..499)
                "Participant sign-in is unavailable. Your answers are saved on this phone."
            else "Could not reconnect this participant. Your answers are saved on this phone."
            throw ParticipantSyncError(message)
        }
        val json = JSONObject(response.body)
        val user = json.getJSONObject("user")
        check(user.getBoolean("is_anonymous"))
        val accessToken = json.getString("access_token")
        check(accessToken.matches(Regex("[A-Za-z0-9._-]+")))
        return ParticipantSession(user.getString("id"), accessToken, json.getString("refresh_token"))
    }

    private suspend fun rpc(name: String, data: JSONObject, session: ParticipantSession) {
        val response = transport.post("/rest/v1/rpc/$name", data.toString(), session.accessToken)
        when (response.status) {
            401 -> throw ParticipantSyncError("Reconnecting participant.", needsRefresh = true)
            404 -> throw ParticipantSyncError("Database setup pending. Your answers are saved on this phone.")
        }
        if (response.status !in 200..299) {
            throw ParticipantSyncError("Answers could not sync yet. They are saved on this phone.")
        }
    }
}

private class SupabaseStagingTransport(private val config: StagingAccountConfig) : StagingTransport {
    override suspend fun post(path: String, body: String, accessToken: String?): StagingResponse = withContext(Dispatchers.IO) {
        val connection = URL(config.url + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", config.publishableKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            accessToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val text = if (status in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else ""
            StagingResponse(status, text)
        } finally {
            connection.disconnect()
        }
    }
}
