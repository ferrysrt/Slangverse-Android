package id.slangverse.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * GET-only question-bank client for PUBLIC GitHub raw files or GitHub Pages.
 * No GitHub PAT, OpenRouter key, backend API, or POST requests are involved.
 * Public static packs necessarily expose answer keys: local grading is DEMO ONLY.
 */
data class StaticAnswer(val correctIndex: Int, val explanation: String)
data class StaticBankSession(val session: ApiSession, val answers: Map<String, StaticAnswer>)

class GithubStaticBank(rawRootUrl: String) {
    private val root = rawRootUrl.trim().trimEnd('/')
    init {
        val url = URL(root)
        val host = url.host.lowercase()
        require(url.protocol == "https" && url.userInfo == null && (url.port == -1 || url.port == 443)) {
            "GitHub bank needs an HTTPS URL without credentials."
        }
        require(host == "raw.githubusercontent.com" || host.endsWith(".github.io")) {
            "Use a public raw.githubusercontent.com or GitHub Pages URL. Private repositories require authentication."
        }
        require(url.query == null && url.ref == null) { "Use a GitHub question-bank folder URL without query or fragment." }
    }

    suspend fun fetchCatalog(): ApiCatalog = withContext(Dispatchers.IO) {
        val json = getJson("catalog.json")
        requireSchema(json)
        val items = json.getJSONArray("senses")
        if (items.length() < 1 || items.length() > 100) throw ApiException("Invalid GitHub catalog")
        val senses = (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            ApiSense(
                senseId = item.getString("senseId"),
                term = item.getString("term"),
                reviewStatus = item.optString("reviewStatus", "provisional")
            )
        }
        if (senses.any { !it.senseId.matches(Regex("[a-z0-9-]{1,80}")) }) {
            throw ApiException("Invalid sense ID in GitHub catalog")
        }
        ApiCatalog(
            datasetVersion = json.optString("datasetVersion", "unknown"),
            candidateCount = json.optInt("candidateCount", 0),
            senses = senses
        )
    }

    suspend fun fetchSession(senseId: String): StaticBankSession = withContext(Dispatchers.IO) {
        if (!senseId.matches(Regex("[a-z0-9-]{1,80}"))) throw ApiException("Invalid slang selection")
        val pack = getJson("packs/$senseId.json")
        requireSchema(pack)
        if (pack.getString("senseId") != senseId) throw ApiException("GitHub package sense ID mismatch")
        val entries = pack.getJSONArray("questions")
        if (entries.length() != 10) throw ApiException("GitHub package must contain exactly 10 questions")
        val answers = mutableMapOf<String, StaticAnswer>()
        val questions = (0 until entries.length()).map { i ->
            val q = entries.getJSONObject(i)
            val post = q.getJSONObject("post")
            val comments = post.getJSONArray("comments")
            if (comments.length() !in 1..5) throw ApiException("Question comments must contain 1–5 hints")
            val options = q.getJSONArray("options").asStrings()
            if (options.size != 4 || options.any { it.isBlank() } ||
                options.map { it.lowercase().trim() }.toSet().size != 4
            ) throw ApiException("Each question needs four unique options")
            val idx = q.getInt("correctIndex")
            if (idx !in 0..3) throw ApiException("Invalid GitHub answer index")
            val explanation = q.getString("explanation")
            if (explanation.isBlank()) throw ApiException("Missing question explanation")
            val question = ApiQuestion(
                id = q.getString("id"),
                slang = q.getString("slang"),
                difficulty = q.getString("difficulty"),
                contextId = q.getString("contextId"),
                post = ApiPost(
                    displayName = post.getString("displayName"),
                    handle = post.getString("handle"),
                    hashtag = post.getString("hashtag"),
                    content = post.getString("content"),
                    comments = (0 until comments.length()).map { j ->
                        val comment = comments.getJSONObject(j)
                        ApiReply(comment.getString("handle"), comment.getString("text"))
                    }
                ),
                question = q.getString("question"),
                options = options
            )
            if (question.id.isBlank() || question.contextId.isBlank() ||
                question.question.isBlank() || question.post.content.isBlank()
            ) throw ApiException("Missing GitHub question content")
            if (answers.put(question.id, StaticAnswer(idx, explanation)) != null) {
                throw ApiException("Duplicate GitHub question ID")
            }
            question
        }
        if (questions.map { it.contextId }.toSet().size != 10) throw ApiException("Duplicate GitHub contexts")
        if (questions.map { it.slang.lowercase().trim() }.toSet().size != 1) {
            throw ApiException("A GitHub package must use one slang term")
        }
        val distribution = questions.groupingBy { it.difficulty }.eachCount()
        if (distribution["easy"] != 5 || distribution["medium"] != 3 || distribution["hard"] != 2) {
            throw ApiException("Invalid GitHub difficulty distribution (expected 5:3:2)")
        }
        val origin = pack.optString("generationSource", "unknown")
        if (origin != "mock" && origin != "llm") throw ApiException("Unknown generation origin in GitHub pack")
        StaticBankSession(
            ApiSession(
                id = UUID.randomUUID().toString(),
                source = "github-static-$origin",
                questions = questions,
                senseId = senseId,
                referenceStatus = pack.optString("referenceStatus", "provisional"),
                catalogVersion = pack.optString("datasetVersion", "unknown")
            ),
            answers
        )
    }

    private fun requireSchema(json: JSONObject) {
        if (json.optInt("schemaVersion", -1) != 1) throw ApiException("Unsupported GitHub bank schema")
    }

    private fun getJson(path: String): JSONObject {
        val url = URL("$root/$path")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-cache")
            val status = connection.responseCode
            if (status != 200) throw ApiException(
                if (status == 404) "GitHub JSON not found or repo is private (404)"
                else "GitHub GET failed (HTTP $status)"
            )
            val text = connection.inputStream.use { stream ->
                val maxBytes = 1_000_000
                val bytes = stream.readNBytes(maxBytes + 1)
                if (bytes.size > maxBytes) throw ApiException("GitHub JSON exceeds 1 MB")
                String(bytes, StandardCharsets.UTF_8)
            }
            return try { JSONObject(text) } catch (_: Exception) {
                throw ApiException("GitHub returned an invalid JSON object")
            }
        } finally {
            connection.disconnect()
        }
    }
}

private fun JSONArray.asStrings(): List<String> = (0 until length()).map { getString(it) }

/** Research-unsafe local demonstration grading: the answer key is public in the static pack. */
fun gradeStaticAnswer(
    answers: Map<String, StaticAnswer>,
    questionId: String,
    selectedIndex: Int,
    answeredCount: Int,
    previousXp: Int
): ApiGrade {
    val answer = answers[questionId] ?: throw ApiException("Question answer not found in GitHub pack")
    if (selectedIndex !in 0..3) throw ApiException("Select an option first")
    val correct = selectedIndex == answer.correctIndex
    val earned = if (correct) 80 else 15
    return ApiGrade(
        correct = correct,
        correctIndex = answer.correctIndex,
        explanation = answer.explanation,
        xpEarned = earned,
        answeredCount = answeredCount + 1,
        totalCount = answers.size,
        totalXp = previousXp + earned
    )
}
