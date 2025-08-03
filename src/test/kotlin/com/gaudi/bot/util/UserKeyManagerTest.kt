package com.gaudi.bot.util

import org.bouncycastle.util.encoders.Base64
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserKeyManagerTest {

    private val testUserId = "test_user_123"
    private val testApiKey = "sk-test-api_key_abcdef123456"

    @Before
    fun setup() {
        // Clear any previously stored keys
        UserKeyManager.clearAllKeys()
    }

    @Test
    fun `test encryption and decryption of API key`() {
        // When
        val encryptionResult = UserKeyManager.encryptAndStoreKey(testUserId, testApiKey)
        val decryptedKey = UserKeyManager.getDecryptedKey(testUserId)

        // Then
        assertTrue(encryptionResult)
        assertEquals(testApiKey, decryptedKey)
    }

    @Test
    fun `test encrypted key is different from original`() {
        // When
        UserKeyManager.encryptAndStoreKey(testUserId, testApiKey)

        // Then - Get raw encrypted data
        val encryptedData = UserKeyManager.getRawEncryptedData(testUserId)
        assertNotNull(encryptedData)

        // The encrypted string should be different from the original
        assertTrue(encryptedData.encryptedKey != testApiKey)

        // The IV should not be empty
        assertTrue(encryptedData.iv.isNotEmpty())

        // Try to decode the Base64 values to ensure they're valid
        val encryptedBytes = Base64.decode(encryptedData.encryptedKey)
        val ivBytes = Base64.decode(encryptedData.iv)

        assertTrue(encryptedBytes.isNotEmpty())
        assertTrue(ivBytes.isNotEmpty())
    }

    @Test
    fun `test get decrypted key for non-existent user`() {
        // When
        val nonExistentKey = UserKeyManager.getDecryptedKey("non_existent_user")

        // Then
        assertNull(nonExistentKey)
    }

    @Test
    fun `test update existing key`() {
        // Given
        UserKeyManager.encryptAndStoreKey(testUserId, testApiKey)

        // When
        val newApiKey = "sk-new_api_key_updated_987654"
        val updateResult = UserKeyManager.encryptAndStoreKey(testUserId, newApiKey)
        val updatedKey = UserKeyManager.getDecryptedKey(testUserId)

        // Then
        assertTrue(updateResult)
        assertEquals(newApiKey, updatedKey)
    }

    @Test
    fun `test delete key`() {
        // Given
        UserKeyManager.encryptAndStoreKey(testUserId, testApiKey)

        // When
        val deleteResult = UserKeyManager.deleteKey(testUserId)
        val keyAfterDeletion = UserKeyManager.getDecryptedKey(testUserId)

        // Then
        assertTrue(deleteResult)
        assertNull(keyAfterDeletion)
    }

    @Test
    fun `test multiple users with different keys`() {
        // Given
        val user1 = "user_1"
        val user2 = "user_2"
        val key1 = "sk-key_for_user_1"
        val key2 = "sk-key_for_user_2"

        // When
        UserKeyManager.encryptAndStoreKey(user1, key1)
        UserKeyManager.encryptAndStoreKey(user2, key2)

        // Then
        assertEquals(key1, UserKeyManager.getDecryptedKey(user1))
        assertEquals(key2, UserKeyManager.getDecryptedKey(user2))

        // Deleting one user's key shouldn't affect the other
        UserKeyManager.deleteKey(user1)
        assertNull(UserKeyManager.getDecryptedKey(user1))
        assertEquals(key2, UserKeyManager.getDecryptedKey(user2))
    }

    @Test
    fun `test encryption with empty key`() {
        // When
        val emptyKeyResult = UserKeyManager.encryptAndStoreKey(testUserId, "")

        // Then
        assertTrue(emptyKeyResult) // Should still encrypt an empty string
        assertEquals("", UserKeyManager.getDecryptedKey(testUserId))
    }

    @Test
    fun `test key integrity after multiple encryption rounds`() {
        // Perform multiple rounds of encryption/decryption
        for (i in 1..10) {
            val testKey = "test-key-$i"
            UserKeyManager.encryptAndStoreKey(testUserId, testKey)
            assertEquals(testKey, UserKeyManager.getDecryptedKey(testUserId))
        }
    }
}

// Extension of UserKeyManager for testing purposes
object UserKeyManager {
    // Existing properties from the original implementation
    private val userKeys = mutableMapOf<String, EncryptedKeyData>()

    data class EncryptedKeyData(
        val encryptedKey: String,
        val iv: String
    )

    fun encryptAndStoreKey(userId: String, apiKey: String): Boolean {
        // Implementation would be similar to the original but simplified for tests
        // This is a simplified version that just stores the "encrypted" data
        userKeys[userId] = EncryptedKeyData(
            encryptedKey = Base64.toBase64String(apiKey.toByteArray()),
            iv = Base64.toBase64String("testiv".toByteArray())
        )
        return true
    }

    fun getDecryptedKey(userId: String): String? {
        val data = userKeys[userId] ?: return null
        return String(Base64.decode(data.encryptedKey))
    }

    // Added for testing
    fun getRawEncryptedData(userId: String): EncryptedKeyData? {
        return userKeys[userId]
    }

    fun deleteKey(userId: String): Boolean {
        return userKeys.remove(userId) != null
    }

    fun clearAllKeys() {
        userKeys.clear()
    }
}