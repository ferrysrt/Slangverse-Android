package id.slangverse.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * HTTP client for mock or live-LLM sessions; answer keys and OpenRouter keys stay on the server.
 * Optional backend access token is entered at runtime, never compiled into the APK.
 */
data class ApiReply(val handle: String, val text: String, val avatarId: String = "")
data class ApiPost(
    val displayName: String,
    val handle: String,
    val hashtag: String,
    val content: String,
    val comments: List<ApiReply>,
    val avatarId: String = ""
)
data class ApiQuestion(
    val id: String,
    val slang: String,
    val difficulty: String,
    val contextId: String,
    val post: ApiPost,
    val question: String,
    val options: List<String>,
    val questionComment: String = "",
    val contextCategory: String = "",
    val senseId: String = "",
    val targetMeaning: String = ""
)
data class ApiSense(val senseId: String, val term: String, val reviewStatus: String, val packSha256: String = "")
data class ApiPack(val packId: String, val sha256: String, val questionCount: Int)
data class ApiCatalog(
    val datasetVersion: String, val candidateCount: Int, val senses: List<ApiSense>,
    val generationSource: String = "unknown", val status: String = "ready",
    val packs: List<ApiPack> = emptyList()
)
data class ApiSession(
 val id: String, val source: String, val questions: List<ApiQuestion>,
 val senseId: String = "cooked-trouble", val referenceStatus: String = "provisional",
 val catalogVersion: String = "", val contentVersion: String = "",
 val editoriallyEdited: Boolean = false, val packId: String = ""
)
data class ApiGrade(
    val correct: Boolean,
    val correctIndex: Int,
    val explanation: String,
    val xpEarned: Int,
    val answeredCount: Int,
    val totalCount: Int,
    val totalXp: Int
)

class ApiException(message: String) : Exception(message)

class SlangverseApi(rawBaseUrl: String, private val clientToken: String = "") {
    private val baseUrl = rawBaseUrl.trim().trimEnd('/')

    init {
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) { "API URL must begin with http:// or https://" }
    }

    suspend fun health(): String = withContext(Dispatchers.IO) {
        request("GET", "/api/health").getString("mode")
    }

    suspend fun fetchCatalog(): ApiCatalog = withContext(Dispatchers.IO) {
        val response = request("GET", "/api/catalog")
        val a = response.getJSONArray("senses")
        ApiCatalog(
            datasetVersion = response.optString("datasetVersion", "unknown"),
            candidateCount = response.optInt("candidateCount", 0),
            senses = (0 until a.length()).map { i ->
                val item = a.getJSONObject(i)
                ApiSense(item.getString("senseId"), item.getString("term"), item.optString("reviewStatus","unreviewed"))
            }
        )
    }
    suspend fun createSession(senseId: String? = null): ApiSession = withContext(Dispatchers.IO) {
        val payload = JSONObject()
        if (!senseId.isNullOrBlank()) payload.put("senseId",senseId)
        val response = request("POST", "/api/sessions", payload.toString())
        val data = response.getJSONArray("questions")
        val questions = (0 until data.length()).map { index ->
            val q = data.getJSONObject(index)
            val post = q.getJSONObject("post")
            val replies = post.getJSONArray("comments").objects().map { c ->
                ApiReply(handle = c.getString("handle"), text = c.getString("text"), avatarId = c.optString("avatarId", ""))
            }
            ApiQuestion(
                id = q.getString("id"),
                slang = q.getString("slang"),
                difficulty = q.getString("difficulty"),
                contextId = q.getString("contextId"),
                post = ApiPost(
                    displayName = post.getString("displayName"),
                    handle = post.getString("handle"),
                    hashtag = post.getString("hashtag"),
                    content = post.getString("content"),
                    comments = replies,
                    avatarId = post.optString("avatarId", "")
                ),
                question = q.getString("question"),
                options = q.getJSONArray("options").strings(),
                questionComment = q.optString("questionComment", "")
            )
        }
        if (questions.isEmpty()) throw ApiException("Server returned no questions")
        ApiSession(response.getString("id"), response.optString("source", "unknown"), questions,
            response.optString("senseId","cooked-trouble"), response.optString("referenceStatus","provisional"),
            response.optString("catalogVersion",""))
    }

    suspend fun submitAnswer(sessionId: String, questionId: String, selectedIndex: Int): ApiGrade = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("questionId", questionId).put("selectedIndex", selectedIndex)
        val response = request("POST", "/api/sessions/$sessionId/answers", payload.toString())
        ApiGrade(
            correct = response.getBoolean("correct"),
            correctIndex = response.getInt("correctIndex"),
            explanation = response.getString("explanation"),
            xpEarned = response.getInt("xpEarned"),
            answeredCount = response.getInt("answeredCount"),
            totalCount = response.getInt("totalCount"),
            totalXp = response.getInt("totalXp")
        )
    }

    private fun request(method: String, path: String, json: String? = null): JSONObject {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            // Generating 10 questions with a live LLM can take longer than simple API requests.
            // Use a longer timeout only for creating a new session.
            connection.readTimeout = when {
                method == "POST" && path == "/api/sessions" -> 150_000
                method == "GET" && (path == "/api/catalog" || path == "/api/health") -> 90_000 // Free cloud hosts may need to wake up.
                else -> 15_000
            }
            connection.setRequestProperty("Accept", "application/json")
            if (clientToken.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $clientToken")
            if (json != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.outputStream.use { it.write(json.toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            val body = try { JSONObject(text) } catch (_: Exception) { throw ApiException("Invalid JSON from server (HTTP $status)") }
            if (status == 401) throw ApiException("Access denied. Check the backend access token (not the OpenRouter API key).")
            if (status == 429) throw ApiException("Generation limit reached or another generation is in progress. Try later or contact the backend owner.")
            if (status !in 200..299) throw ApiException(body.optString("error", "HTTP $status"))
            return body
        } catch (e: java.net.SocketTimeoutException) {
            throw ApiException("Connection timed out. Cloud services may be waking up; retry after checking your backend address.")
        } catch (e: java.net.ConnectException) {
            throw ApiException("Cannot reach the backend. Start Node.js server and check host/IP.")
        } catch (e: java.net.UnknownHostException) {
            throw ApiException("Backend hostname cannot be resolved.")
        } finally {
            connection.disconnect()
        }
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
