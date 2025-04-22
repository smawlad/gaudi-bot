package com.gaudi.bot.ai.prompts

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Manages prompt templates from the project's templates directory
 */
object PromptManager {
    private val promptCache = mutableMapOf<String, Prompt>()

    init {
        try {
            val roles = PromptLoader.loadDirectory("roles")
            promptCache.putAll(roles)

            val prefilled = PromptLoader.loadDirectory("assistant-prefilled")
            promptCache.putAll(prefilled)

            val taskPrompts = PromptLoader.loadDirectory("tasks")
            promptCache.putAll(taskPrompts)

            logger.info { "Loaded ${promptCache.size} prompts from templates directory" }
        } catch (e: Exception) {
            logger.warn { "Warning: Failed to load some prompts: ${e.message}" }
        }
    }

    /**
     * Get a prompt by its key
     */
    fun getPrompt(key: String): Prompt? = promptCache[key]

    /**
     * Load a specific prompt file on demand
     */
    fun loadSpecificPrompt(path: String): Prompt {
        return PromptLoader.loadFromText(path).also { prompt ->
            // Extract filename without extension as the key
            val key = File(path).nameWithoutExtension.uppercase()
            promptCache[key] = prompt
        }
    }

    /**
     * Load prompts from a YAML file
     */
    fun loadFromYamlFile(path: String) {
        val prompts = PromptLoader.loadFromYaml(path)
        promptCache.putAll(prompts)
        logger.info { "Loaded ${prompts.size} prompts from YAML file: $path" }
    }

    /**
     * Get all available prompt keys
     */
    fun getAvailablePrompts(): List<String> = promptCache.keys.toList()
}