package com.gaudi.bot.ai.prompts

import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Paths

/**
 * Loads prompts from external resources
 */
object PromptLoader {
    private val yaml = Yaml()

    // Get the project root directory
    private val projectRootDir: File by lazy {
        // Start with the current working directory
        var currentDir = File(System.getProperty("user.dir"))

        // Navigate up until we find the project root (containing templates dir)
        while (currentDir.parentFile != null && !File(currentDir, "templates").exists()) {
            currentDir = currentDir.parentFile
        }

        currentDir
    }

    /**
     * Get the absolute path to a template file from project's templates directory
     */
    private fun getTemplateFile(templatePath: String): File {
        val templatesDir = File(projectRootDir, "templates")
        return File(templatesDir, templatePath)
    }

    /**
     * Load prompts from a YAML file in templates directory
     */
    fun loadFromYaml(templatePath: String): Map<String, Prompt> {
        val file = getTemplateFile(templatePath)
        if (!file.exists()) throw IllegalArgumentException("Prompt file not found: $file")

        val yamlContent = file.readText()
        val yamlMap = yaml.load<Map<String, Map<String, Any>>>(yamlContent)

        return yamlMap.mapValues { (_, data) ->
            Prompt(
                template = data["template"] as String,
                description = data["description"] as? String ?: ""
            )
        }
    }

    /**
     * Load a prompt from a text file in templates directory
     */
    fun loadFromText(templatePath: String, description: String = ""): Prompt {
        val file = getTemplateFile(templatePath)
        if (!file.exists()) throw IllegalArgumentException("Prompt file not found: $file")

        val template = file.readText()
        return Prompt(template = template, description = description)
    }

    /**
     * Load all prompts from a directory in templates
     */
    fun loadDirectory(directoryPath: String): Map<String, Prompt> {
        val directory = getTemplateFile(directoryPath)
        if (!directory.exists() || !directory.isDirectory) {
            throw IllegalArgumentException("Template directory not found: $directory")
        }

        val prompts = mutableMapOf<String, Prompt>()

        // Process all txt files in the directory
        directory.listFiles { file -> file.extension.lowercase() == "txt" }?.forEach { file ->
            val key = file.nameWithoutExtension.uppercase()
            prompts[key] = Prompt(
                template = file.readText(),
                description = "Loaded from ${file.name}"
            )
        }

        // Try to load any YAML files that might contain multiple prompts
        directory.listFiles { file ->
            file.extension.lowercase() == "yaml" || file.extension.lowercase() == "yml"
        }?.forEach { file ->
            try {
                val yamlPrompts = loadFromYaml(Paths.get(directoryPath, file.name).toString())
                prompts.putAll(yamlPrompts)
            } catch (e: Exception) {
                // Skip invalid YAML files
            }
        }

        return prompts
    }
}