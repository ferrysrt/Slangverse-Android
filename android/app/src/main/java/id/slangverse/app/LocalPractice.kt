package id.slangverse.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * The practice state that outlives the process. Everything else on the Progress screen — XP, combo,
 * meanings matched — is derived from these answers and the loaded feed, so it is not stored twice.
 */
internal data class LocalPracticeSnapshot(
    val answers: List<Pair<String, Int>> = emptyList(),
    val liked: List<String> = emptyList(),
    val saved: List<String> = emptyList()
)

/**
 * JSON codec for [LocalPracticeSnapshot]. Answer order is kept because the combo counter reads the
 * most recent correct run, and a JSON object would not guarantee that order. Anything unreadable is
 * dropped instead of crashing the feed: a broken file must not cost the reader the whole app.
 */
internal object LocalPracticeCodec {
    const val VERSION = 1
    private const val MAX_ANSWERS = 5_000
    private const val MAX_MARKS = 20_000
    private const val MAX_OPTION_INDEX = 3

    fun encode(snapshot: LocalPracticeSnapshot): String {
        val answers = JSONArray()
        snapshot.answers.take(MAX_ANSWERS).forEach { (post, selected) ->
            answers.put(JSONObject().put("post", post).put("selected", selected))
        }
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("answers", answers)
        root.put("liked", JSONArray(snapshot.liked.take(MAX_MARKS)))
        root.put("saved", JSONArray(snapshot.saved.take(MAX_MARKS)))
        return root.toString()
    }

    fun decode(raw: String?): LocalPracticeSnapshot {
        if (raw.isNullOrBlank()) return LocalPracticeSnapshot()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return LocalPracticeSnapshot()
        if (root.optInt("version", 0) != VERSION) return LocalPracticeSnapshot()
        return LocalPracticeSnapshot(
            answers = decodeAnswers(root.optJSONArray("answers")),
            liked = decodeMarks(root.optJSONArray("liked")),
            saved = decodeMarks(root.optJSONArray("saved"))
        )
    }

    private fun decodeAnswers(array: JSONArray?): List<Pair<String, Int>> {
        if (array == null) return emptyList()
        val seen = LinkedHashSet<String>()
        val answers = mutableListOf<Pair<String, Int>>()
        for (index in 0 until minOf(array.length(), MAX_ANSWERS)) {
            val entry = array.optJSONObject(index) ?: continue
            val post = entry.optString("post").trim()
            val selected = entry.optInt("selected", -1)
            if (post.isEmpty() || selected !in 0..MAX_OPTION_INDEX) continue
            if (seen.add(post)) answers += post to selected
        }
        return answers
    }

    private fun decodeMarks(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val marks = LinkedHashSet<String>()
        for (index in 0 until minOf(array.length(), MAX_MARKS)) {
            val value = array.optString(index, "").trim()
            if (value.isNotEmpty()) marks += value
        }
        return marks.toList()
    }
}
