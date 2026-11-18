package id.slangverse.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
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

    suspend fun fetchCatalog(requireLlm: Boolean = false): ApiCatalog = withContext(Dispatchers.IO) {
        parseBankCatalog(getText("catalog.json"), requireLlm)
    }

    suspend fun fetchSession(
        senseId: String, requireLlm: Boolean = false, expectedTerm: String? = null, expectedSha256: String? = null
    ): StaticBankSession = withContext(Dispatchers.IO) {
        if (!senseId.matches(Regex("[a-z0-9-]{1,80}"))) throw ApiException("Invalid slang selection")
        parseBankSession(getText("packs/$senseId.json"), senseId, requireLlm, expectedTerm, expectedSha256)
    }

    suspend fun fetchMixedSession(packId: String, expectedSha256: String): StaticBankSession = withContext(Dispatchers.IO) {
        if (!validMixedPackId(packId) || !expectedSha256.matches(bankHashPattern))
            throw ApiException("Invalid mixed pack selection or checksum")
        parseMixedBankSession(getText("packs/$packId.json"), packId, expectedSha256)
    }

    suspend fun fetchPublishedSessions(catalog: ApiCatalog): List<StaticBankSession> =
        loadPublishedSessions(catalog) { request ->
            if (request.legacyTerm == null) fetchMixedSession(request.id, request.sha256)
            else fetchSession(request.id, requireLlm = true,
                expectedTerm = request.legacyTerm, expectedSha256 = request.sha256)
        }

    private fun getText(path: String): String {
        val url = URL("$root/$path?refresh=${System.currentTimeMillis()}")
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
                if (status == 404) "Question bank not found (404). Check its URL and publication status"
                else "GitHub GET failed (HTTP $status)"
            )
            val text = connection.inputStream.use { stream ->
                val maxBytes = 1_000_000
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > maxBytes) throw ApiException("GitHub JSON exceeds 1 MB")
                    output.write(buffer, 0, count)
                }
                String(output.toByteArray(), StandardCharsets.UTF_8)
            }
            return text
        } finally {
            connection.disconnect()
        }
    }
}


private val mixedPackIdPattern = Regex("mixed-[a-z0-9]+(?:-[a-z0-9]+)*")
private fun validMixedPackId(id: String): Boolean = id.length <= 80 && id.matches(mixedPackIdPattern)
private val bankHashPattern = Regex("[a-fA-F0-9]{64}")

private fun requireBankSchema(json: JSONObject, vararg supported: Int) {
    if (json.optInt("schemaVersion", -1) !in supported) throw ApiException("Unsupported GitHub bank schema")
}

private inline fun <T> bankJson(parse: () -> T): T = try {
    parse()
} catch (error: JSONException) {
    throw ApiException("Invalid GitHub bank JSON: ${error.message}")
}

internal fun parseBankCatalog(text: String, requireLlm: Boolean = false): ApiCatalog = bankJson {
    val json = JSONObject(text)
    requireBankSchema(json, 1, 2)
    val mixed = json.getInt("schemaVersion") == 2
    if (mixed && (json.optString("source") != "github-static" ||
            json.optString("generationSource") != "llm" || json.getBoolean("expertValidated")))
        throw ApiException("Invalid mixed GitHub catalog origin or validation status")
    if (mixed) {
        listOf("datasetVersion", "bankVersion", "status").forEach { json.requiredText(it) }
        if (json.getInt("candidateCount") < 0) throw ApiException("Invalid catalog candidate count")
    }
    if (requireLlm && json.optString("generationSource") != "llm")
        throw ApiException("The default bank must contain published LLM questions")
    val items = json.getJSONArray("senses")
    if (items.length() > 1000 || (!mixed && !requireLlm && items.length() < 1)) throw ApiException("Invalid GitHub catalog")
    val senses = (0 until items.length()).map { i ->
        val item = items.getJSONObject(i)
        ApiSense(
            senseId = item.getString("senseId"),
            term = item.getString("term"),
            reviewStatus = item.optString("reviewStatus", "provisional"),
            packSha256 = item.optString("sha256", "")
        )
    }
    if (senses.any { !it.senseId.matches(Regex("[a-z0-9-]{1,80}")) }) {
        throw ApiException("Invalid sense ID in GitHub catalog")
    }
    if (senses.map { it.senseId }.distinct().size != senses.size) throw ApiException("Duplicate bank sense IDs")
    if (senses.any { it.packSha256.isNotEmpty() && !it.packSha256.matches(Regex("[a-f0-9]{64}")) })
        throw ApiException("Invalid bank checksum")
    val packs = if (mixed) {
        val entries = json.getJSONArray("packs")
        if (entries.length() > 1000) throw ApiException("Invalid mixed GitHub catalog size")
        (0 until entries.length()).map { i ->
            val entry = entries.getJSONObject(i)
            ApiPack(entry.getString("packId"), entry.getString("sha256"), entry.getInt("questionCount"))
        }.also { parsed ->
            if (parsed.any { !validMixedPackId(it.packId) ||
                    !it.sha256.matches(bankHashPattern) || it.questionCount != 10 })
                throw ApiException("Invalid mixed pack ID, checksum, or question count")
            if (parsed.map { it.packId }.distinct().size != parsed.size)
                throw ApiException("Duplicate mixed pack IDs")
        }
    } else emptyList()
    ApiCatalog(
        datasetVersion = json.optString("datasetVersion", "unknown"),
        candidateCount = json.optInt("candidateCount", 0),
        senses = senses,
        generationSource = json.optString("generationSource", "unknown"),
        status = json.optString("status", "ready"),
        packs = packs
    )
}

internal fun parseBankSession(
    text: String, senseId: String, requireLlm: Boolean = false,
    expectedTerm: String? = null, expectedSha256: String? = null
): StaticBankSession = parseStaticBankSession(text, senseId, requireLlm, expectedTerm, expectedSha256, mixed = false)

internal fun parseMixedBankSession(
    text: String, packId: String, expectedSha256: String? = null
): StaticBankSession = parseStaticBankSession(text, packId, true, null, expectedSha256, mixed = true)

private fun parseStaticBankSession(
    text: String, identity: String, requireLlm: Boolean,
    expectedTerm: String?, expectedSha256: String?, mixed: Boolean
): StaticBankSession = bankJson {
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    if (!expectedSha256.isNullOrBlank() && !digest.equals(expectedSha256, ignoreCase = true))
        throw ApiException("The question bank changed during download. Refresh to load its current version.")
    val pack = JSONObject(text)
    requireBankSchema(pack, if (mixed) 2 else 1)
    val promptVersion = if (mixed) pack.requiredText("promptVersion") else ""
    if (mixed) {
        if (!validMixedPackId(identity) || pack.getString("packId") != identity)
            throw ApiException("GitHub package pack ID mismatch")
        listOf("generatorModel", "datasetVersion").forEach { pack.requiredText(it) }
        pack.requireTimestamp("generatedAt")
        if (pack.getBoolean("expertValidated")) throw ApiException("Mixed packs must remain explicitly unreviewed")
        if (pack.optString("referenceStatus") != "normalized-unreviewed")
            throw ApiException("Invalid mixed pack reference status")
    } else if (pack.getString("senseId") != identity) throw ApiException("GitHub package sense ID mismatch")
    val references = if (mixed) parseMixedReferences(pack.getJSONArray("references")) else emptyMap()
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
        if (mixed && (options.any { referenceKey(it).isBlank() } || options.map(::referenceKey).toSet().size != 4))
            throw ApiException("Each mixed question needs four unique answer keys")
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
                    ApiReply(comment.getString("handle"), comment.getString("text"), comment.optString("avatarId", ""))
                },
                avatarId = post.optString("avatarId", "")
            ),
            question = q.getString("question"),
            options = options,
            questionComment = q.optString("questionComment", ""),
            contextCategory = q.optString("contextCategory", ""),
            senseId = q.optString("senseId", ""),
            targetMeaning = q.optString("targetMeaning", ""),
            questionTarget = q.optString("questionTarget", ""),
            targetHandle = q.optString("targetHandle", "")
        )
        if (question.id.isBlank() || question.contextId.isBlank() ||
            question.question.isBlank() || question.post.content.isBlank()
        ) throw ApiException("Missing GitHub question content")
        if (mixed) {
            if (question.contextCategory !in mixedContextCategories)
                throw ApiException("Unsupported mixed question context category")
            val reference = references[question.senseId]
                ?: throw ApiException("Mixed question has no matching sense reference")
            if (question.slang != reference.term || question.targetMeaning != reference.targetMeaning ||
                question.options[idx] != reference.targetMeaning)
                throw ApiException("Mixed question term, target meaning, or answer does not match its reference")
            if (promptVersion in repeatTermPromptVersions) validateMixedV7TermTarget(question)
            else validateMixedTermTarget(question)
        }
        if (answers.put(question.id, StaticAnswer(idx, explanation)) != null) {
            throw ApiException("Duplicate GitHub question ID")
        }
        question
    }
    if (mixed) {
        if (questions.map { it.senseId }.toSet() != references.keys)
            throw ApiException("Every mixed reference must be used by a question")
        if (questions.map { mixedTermKey(it.slang) }.toSet().size < 2)
            throw ApiException("A mixed GitHub package must use at least two slang terms")
    } else {
        if (questions.map { it.contextId }.toSet().size != 10) throw ApiException("Duplicate GitHub contexts")
        if (questions.map { it.slang.lowercase().trim() }.toSet().size != 1)
            throw ApiException("A GitHub package must use one slang term")
    }
    val distribution = questions.groupingBy { it.difficulty }.eachCount()
    if (distribution["easy"] != 5 || distribution["medium"] != 3 || distribution["hard"] != 2) {
        throw ApiException("Invalid GitHub difficulty distribution (expected 5:3:2)")
    }
    if (expectedTerm != null && questions.any { !it.slang.trim().equals(expectedTerm.trim(), ignoreCase = true) })
        throw ApiException("Pack slang does not match the catalog")
    val origin = pack.optString("generationSource", "unknown")
    if (origin != "mock" && origin != "llm") throw ApiException("Unknown generation origin in GitHub pack")
    if (requireLlm && origin != "llm") throw ApiException("Default feed requires LLM-generated packs; mock content was not loaded")
    StaticBankSession(
        ApiSession(
            id = UUID.randomUUID().toString(),
            source = "github-static-$origin",
            questions = questions,
            senseId = identity,
            packId = if (mixed) identity else "",
            referenceStatus = pack.optString("referenceStatus", "provisional"),
            catalogVersion = pack.optString("datasetVersion", "unknown"),
            contentVersion = digest,
            editoriallyEdited = pack.optJSONObject("editorialReview")?.optString("status") == "agent-edited-provisional"
        ),
        answers
    )
}

private val mixedContextCategories = setOf(
    "personal", "education", "work", "prediction", "sports",
    "entertainment", "opinion", "friends", "event-reaction"
)

/** Prompt versions whose contract allows the term to repeat and binds it to one named surface. */
private val repeatTermPromptVersions = setOf("mixed-v7", "mixed-v8")

private data class MixedReference(val term: String, val targetMeaning: String)

private fun JSONObject.requiredText(key: String): String = getString(key).also {
    if (it.isBlank()) throw ApiException("Missing GitHub bank field: $key")
}

// Option uniqueness is lexical only; normalized references are not expert semantic validation.
private fun referenceKey(text: String): String = text.lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

private fun surfaceKey(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

private fun termCount(text: String, term: String): Int {
    val key = surfaceKey(term)
    if (key.isBlank()) return 0
    val words = key.split(' ').filter { it.isNotBlank() }
    val hay = surfaceKey(text).split(' ').filter { it.isNotBlank() }
    var total = 0
    var i = 0
    while (i + words.size <= hay.size) {
        if (hay.subList(i, i + words.size).joinToString(" ") == key) total++
        i++
    }
    return total
}

/** The term belongs to exactly one social surface, exactly once, and the target must name it. */
private fun validateMixedTermTarget(question: ApiQuestion) {
    if (question.questionTarget != "post" && question.questionTarget != "reply")
        throw ApiException("Mixed question has no valid question target")
    val postCount = termCount(question.post.content, question.slang)
    val replyCounts = question.post.comments.map { termCount(it.text, question.slang) }
    if (postCount + replyCounts.sum() != 1)
        throw ApiException("Mixed question slang must appear exactly once in one post or reply")
    val inPost = postCount == 1
    val questionText = question.question.lowercase(Locale.ROOT)
    if (question.questionTarget == "post") {
        if (!inPost) throw ApiException("Mixed question target does not match the slang surface")
        if (!questionText.contains("post")) throw ApiException("Mixed question must name the post it asks about")
        if (question.targetHandle.isNotBlank() && question.targetHandle != question.post.handle)
            throw ApiException("Mixed question target handle does not match the post")
    } else {
        val index = replyCounts.indexOfFirst { it == 1 }
        val target = question.post.comments.getOrNull(index)
            ?: throw ApiException("Mixed question target reply does not contain the slang")
        if (target.handle != question.targetHandle)
            throw ApiException("Mixed question target handle does not match the slang reply")
        if (!questionText.contains(target.handle.lowercase(Locale.ROOT)))
            throw ApiException("Mixed question must name the reply it asks about")
    }
}

/** V7 targets one named surface; other surfaces may repeat the same term. */
private fun validateMixedV7TermTarget(question: ApiQuestion) {
    if (question.targetHandle.isBlank()) throw ApiException("Mixed question requires a target handle")
    val questionText = question.question.lowercase(Locale.ROOT)
    when (question.questionTarget) {
        "post" -> {
            if (question.targetHandle != question.post.handle || termCount(question.post.content, question.slang) == 0)
                throw ApiException("Mixed question target does not match the slang post")
            if (!Regex("\\bpost\\b").containsMatchIn(questionText)) throw ApiException("Mixed question must name the post it asks about")
        }
        "reply" -> {
            val targets = question.post.comments.filter { it.handle.equals(question.targetHandle, ignoreCase = true) }
            if (targets.size != 1 || targets.single().handle != question.targetHandle) throw ApiException("Mixed question target reply handle is missing or ambiguous")
            if (termCount(targets.single().text, question.slang) == 0)
                throw ApiException("Mixed question target reply does not contain the slang")
            val binding = Regex(Regex.escape(question.targetHandle) + "(?![a-z0-9_])")
            if (!binding.containsMatchIn(question.question))
                throw ApiException("Mixed question must name the reply it asks about")
        }
        else -> throw ApiException("Mixed question has no valid question target")
    }
}

// Match ECMAScript whitespace, including BOM/NBSP, for cross-runtime candidate IDs.
private val mixedWhitespace = Regex("[\\s\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]+")

private fun mixedTermKey(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
    .replace(mixedWhitespace, " ").trim().lowercase(Locale.ROOT)

internal fun mixedCandidateIdFor(term: String, definition: String): String {
    val json = JSONArray().put(mixedTermKey(term)).put(mixedTermKey(definition)).toString()
    // Android escapes every solidus; JVM org.json also escapes some Unicode. JSON.stringify does neither.
    val canonical = buildString {
        var i = 0
        while (i < json.length) {
            val c = json[i++]
            if (c == '\\') {
                when (val escaped = json[i++]) {
                    '/' -> append('/')
                    'u' -> {
                        val code = json.substring(i, i + 4).toInt(16)
                        if (code >= 0x20 && code !in 0xd800..0xdfff) append(code.toChar())
                        else append("\\u").append(json.substring(i, i + 4).lowercase(Locale.ROOT))
                        i += 4
                    }
                    else -> append('\\').append(escaped)
                }
            } else if (c.isHighSurrogate() && i < json.length && json[i].isLowSurrogate()) {
                append(c).append(json[i++])
            } else if (c.isSurrogate()) {
                append("\\u").append(c.code.toString(16).padStart(4, '0'))
            } else append(c)
        }
    }
    return java.security.MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }.take(24)
}

private fun JSONObject.requireTimestamp(key: String) {
    try {
        Instant.parse(requiredText(key))
    } catch (_: DateTimeParseException) {
        throw ApiException("Invalid GitHub bank timestamp: $key")
    }
}

private fun literalTermPattern(term: String): Regex {
    val literal = term.replace(mixedWhitespace, " ").trim().split(" ")
        .joinToString(mixedWhitespace.pattern) { Regex.escape(it) }
    // Underscores may be Markdown emphasis; letters/numbers/marks must not extend the term.
    return Regex("(?<![\\p{L}\\p{N}\\p{M}])$literal(?![\\p{L}\\p{N}\\p{M}])", RegexOption.IGNORE_CASE)
}

private fun parseMixedReferences(entries: JSONArray): Map<String, MixedReference> {
    if (entries.length() !in 2..10) throw ApiException("Mixed pack needs 2–10 sense references")
    val references = mutableMapOf<String, MixedReference>()
    for (i in 0 until entries.length()) {
        val entry = entries.getJSONObject(i)
        val senseId = entry.requiredText("senseId")
        if (!senseId.matches(Regex("normalized-[a-f0-9]{24}"))) throw ApiException("Invalid mixed reference sense ID")
        val reference = MixedReference(entry.requiredText("term"), entry.requiredText("targetMeaning"))
        if (referenceKey(reference.targetMeaning).isBlank()) throw ApiException("Empty mixed reference meaning")
        val definition = entry.requiredText("normalizedDefinition")
        val candidateId = entry.requiredText("sourceCandidateId")
        if (senseId != "normalized-$candidateId" || mixedCandidateIdFor(reference.term, definition) != candidateId)
            throw ApiException("Mixed reference identity does not match its term and normalized definition")
        if (reference.targetMeaning != definition) throw ApiException("Mixed target meaning must equal its normalized definition")
        val termPattern = literalTermPattern(reference.term)
        val sources = entry.getJSONArray("referenceSources")
        if (sources.length() == 0) throw ApiException("Missing mixed reference sources")
        for (j in 0 until sources.length()) {
            val source = sources.getJSONObject(j)
            val url = try { URL(source.requiredText("url")) } catch (_: java.net.MalformedURLException) {
                throw ApiException("Invalid mixed reference source URL")
            }
            if (url.protocol !in listOf("https", "http") || url.host.isBlank() || url.userInfo != null)
                throw ApiException("Invalid mixed reference source URL (credentials are not allowed)")
            source.requiredText("title")
            if (!termPattern.containsMatchIn(source.requiredText("quote")))
                throw ApiException("Mixed source quote must contain the literal slang term")
            source.requireTimestamp("collectedAt")
            if (source.requiredText("collectionMethod") != "firecrawl")
                throw ApiException("Mixed source collection method must be firecrawl")
            if (!source.requiredText("contentSha256").matches(bankHashPattern))
                throw ApiException("Invalid mixed reference source checksum")
        }
        if (references.put(senseId, reference) != null) throw ApiException("Duplicate mixed reference sense IDs")
    }
    return references
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
