package com.gaudi.bot.ai.prompts

/**
 * Represents a text prompt for an LLM with optional variables
 */
data class Prompt(
    val template: String,
    val description: String = ""
) {
    /**
     * Fill prompt template with provided variables
     */
    fun format(vararg pairs: Pair<String, String>): String {
        var result = template
        for ((key, value) in pairs) {
            result = result.replace("{{$key}}", value)
        }
        return result
    }

    /**
     * Fill prompt template with a map of variables
     */
    fun format(variables: Map<String, String>): String {
        var result = template
        for ((key, value) in variables) {
            result = result.replace("{{$key}}", value)
        }
        return result
    }
}