package id.slangverse.app

import java.security.MessageDigest
import org.json.JSONObject

internal data class ResearchPilotBank(val id: String, val sha256: String, val posts: List<FeedPracticePost>) {
    private data class Sense(val id: String, val term: String, val meaning: String)

    companion object {
        const val ASSET = "pilot_bank_v1.json"

        fun decode(bytes: ByteArray): ResearchPilotBank {
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            val id = json.getString("id").also { require(it.matches(Regex("[a-z0-9-]+"))) }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val senses = json.getJSONArray("senses").objects().map {
                Sense(it.getString("id"), it.getString("term"), it.getString("meaning"))
            }
            val sensesById = senses.associateBy(Sense::id)
            require(senses.size == 10 && sensesById.size == senses.size)
            require(senses.all { it.id.isNotBlank() && it.term.isNotBlank() && it.meaning.isNotBlank() })

            val posts = json.getJSONArray("questions").objects().map { question ->
                val sense = sensesById.getValue(question.getString("sense"))
                val optionsJson = question.getJSONArray("options")
                val options = (0 until optionsJson.length()).map(optionsJson::getString)
                val answerIndex = question.getInt("answerIndex")
                require(options.size == 4 && displayAnswerOptions(options) != null && answerIndex in options.indices)
                val context = FeedContext.valueOf(question.getString("context"))
                val author = SocialNarrative.profile(question.getString("author"))
                val replyAuthor = SocialNarrative.profile(question.getString("replyAuthor"))
                val questionId = question.getString("id")
                val body = question.getString("body")
                val reply = question.getString("reply")
                val prompt = question.getString("question")
                val explanation = question.getString("explanation")
                require(listOf(questionId, body, reply, prompt, explanation).all(String::isNotBlank))
                FeedPracticePost(
                    thread = DetectiveCase(questionId, sense.term, sense.meaning, author.name, author.handle,
                        context.hashtag, body, listOf(reply), prompt, options, answerIndex, explanation),
                    context = context, difficulty = FeedDifficulty.Medium,
                    replies = listOf(StoryReply("$questionId-reply", replyAuthor.handle, reply)),
                    sourceLabel = "Authored research pilot", learningSenseId = sense.id)
            }
            require(posts.size == 30 && posts.map { it.thread.id }.toSet().size == posts.size)
            require(posts.groupBy { it.learningSenseId }.let { groups ->
                groups.keys == sensesById.keys && groups.values.all { items ->
                    items.size == 3 && items.map { it.context }.distinct().size == 3
                }
            })
            return ResearchPilotBank(id, digest, posts)
        }
    }
}
