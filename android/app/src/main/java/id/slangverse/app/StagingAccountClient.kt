package id.slangverse.app

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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

internal class StagingSession(val email: String, val accessToken: String)
internal data class StagingProgress(val answered: Long, val correct: Long, val xp: Long)
internal data class StagingResponse(val status: Int, val body: String)
internal class StagingAccountError(message: String, val sessionExpired: Boolean = false) : Exception(message)

internal fun interface StagingTransport {
    suspend fun post(path: String, body: String, accessToken: String?): StagingResponse
}

internal class StagingAccountClient(private val transport: StagingTransport) {
    constructor(config: StagingAccountConfig) : this(SupabaseStagingTransport(config))

    suspend fun signIn(email: String, password: String): StagingSession {
        val request = JSONObject().put("email", email.trim()).put("password", password)
        val response = transport.post("/auth/v1/token?grant_type=password", request.toString(), null)
        if (response.status == 429) throw StagingAccountError("Too many sign-in attempts. Try again shortly.")
        if (response.status in 400..499) throw StagingAccountError("Sign-in failed. Check the test account and password.")
        if (response.status !in 200..299) throw StagingAccountError("Sign-in is temporarily unavailable.")
        val json = JSONObject(response.body)
        val token = json.getString("access_token")
        if (!token.matches(Regex("[A-Za-z0-9._-]+"))) throw StagingAccountError("Invalid sign-in response.")
        val verifiedEmail = json.getJSONObject("user").optString("email").ifBlank { email.trim() }
        return StagingSession(verifiedEmail, token)
    }

    suspend fun progress(session: StagingSession): StagingProgress {
        val response = transport.post("/rest/v1/rpc/my_practice_progress", "{}", session.accessToken)
        when (response.status) {
            401 -> throw StagingAccountError("Session expired. Sign in again.", sessionExpired = true)
            404 -> throw StagingAccountError("The staging database setup has not been applied yet.")
            403 -> throw StagingAccountError("This account cannot read staging progress.")
        }
        if (response.status !in 200..299) throw StagingAccountError("Account progress is temporarily unavailable.")
        val rows = JSONArray(response.body)
        check(rows.length() == 1)
        val row = rows.getJSONObject(0)
        return StagingProgress(row.getLong("answered"), row.getLong("correct"), row.getLong("xp"))
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
