package id.slangverse.app

/**
 * The reply that actually gets posted. Packs may carry a natural sentence per option; older packs
 * keep posting the chosen option so the thread never goes blank.
 */
internal fun postedReply(thread: DetectiveCase, index: Int): String =
    thread.replyTexts.getOrNull(index)?.takeIf { it.isNotBlank() }
        ?: displayAnswerOptions(thread.options)?.getOrNull(index)
        ?: thread.options.getOrNull(index).orEmpty()

/** Presentation only; null means the normalized choices cannot be answered unambiguously. */
internal fun displayAnswerOptions(options: List<String>): List<String>? {
    val labels = options.map { option ->
        val value = option.trim()
        val suffixStart = value.indexOfLast { it !in "\"'”’»)]}" } + 1
        val body = value.substring(0, suffixStart)
        val dottedInitialism = Regex("(?:^|\\s)(?:\\p{L}\\.){2,}$").containsMatchIn(body)
        val normalized = if (body.endsWith('.') && !body.endsWith("..") && !dottedInitialism)
            body.dropLast(1).trimEnd() else body
        normalized + value.substring(suffixStart)
    }
    return labels.takeIf { values ->
        values.none { it.isBlank() } &&
            values.map { it.lowercase().replace(Regex("\\s+"), " ") }.distinct().size == values.size
    }
}
