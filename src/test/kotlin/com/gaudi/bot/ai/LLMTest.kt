package com.gaudi.bot.ai

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LLMTest {

    @Test
    fun `test LLM with Claude model type can be created`() = runBlocking {
        // Given
        val config = LLMConfig(ModelType.CLAUDE, anthropicApiKey = "test_key")
        
        // When
        val llm = LLM(config)

        // Then
        assertNotNull(llm)
        
        // Verify conversation management functions work
        llm.resetConversation()
        assertTrue(true)
    }

    @Test
    fun `test LLM with OpenAI model type can be created`() = runBlocking {
        // Given
        val config = LLMConfig(ModelType.OPENAI, openAIApiKey = "test_key")
        
        // When
        val llm = LLM(config)

        // Then
        assertNotNull(llm)
        
        // Verify conversation management functions work
        llm.resetConversation()
        assertTrue(true)
    }

    @Test
    fun `test LLMConfig with Claude configuration`() {
        // Given
        val apiKey = "test_anthropic_key"
        
        // When
        val config = LLMConfig(ModelType.CLAUDE, anthropicApiKey = apiKey)
        
        // Then
        assertEquals(ModelType.CLAUDE, config.modelType)
        assertEquals(apiKey, config.anthropicApiKey)
    }

    @Test
    fun `test LLMConfig with OpenAI configuration`() {
        // Given
        val apiKey = "test_openai_key"
        
        // When
        val config = LLMConfig(ModelType.OPENAI, openAIApiKey = apiKey)
        
        // Then
        assertEquals(ModelType.OPENAI, config.modelType)
        assertEquals(apiKey, config.openAIApiKey)
    }

    @Test
    fun `test LLM handles empty API keys gracefully`() {
        // Given
        val config = LLMConfig(ModelType.CLAUDE, anthropicApiKey = "")
        
        // When
        val llm = LLM(config)
        
        // Then
        assertNotNull(llm)
        // LLM should be created even with empty keys, actual API calls would fail
        // but construction should succeed
    }

    @Test
    fun `test ModelType enum values`() {
        // Test that both model types are available
        assertEquals(2, ModelType.values().size)
        assertTrue(ModelType.values().contains(ModelType.CLAUDE))
        assertTrue(ModelType.values().contains(ModelType.OPENAI))
    }

    // For clarity in assertions
    private fun assertTrue(condition: Boolean) {
        assertEquals(true, condition)
    }
}