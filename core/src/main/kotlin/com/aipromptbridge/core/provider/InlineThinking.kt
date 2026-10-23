package com.aipromptbridge.core.provider

/** Removes inline "thinking" blocks some OpenAI-compatible models emit (e.g. <think>...</think>). */
object InlineThinking {
    private val options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    private val blocks: List<Regex> =
        listOf("think", "thinking", "thought", "reasoning").map { Regex("<$it>.*?</$it>", options) }

    fun strip(text: String): String {
        var result = text
        for (block in blocks) {
            result = block.replace(result, "")
        }
        return result.trim()
    }
}
