package com.gaudi.bot.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.bouncycastle.util.encoders.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val logger = KotlinLogging.logger {}

object UserKeyManager {

    // In a real app, this would be stored in a secure database
    private val userKeys = mutableMapOf<String, EncryptedKeyData>()

    // In a real app, this would be stored securely, possibly in a vault service
    private val masterKey: SecretKey by lazy { generateMasterKey() }

    data class EncryptedKeyData(
        val encryptedKey: String,
        val iv: String
    )

    private fun generateMasterKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        return keyGen.generateKey()
    }

    fun encryptAndStoreKey(userId: String, apiKey: String): Boolean {
        return try {
            // Generate a random IV
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)

            // Encrypt the API key
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val paramSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, paramSpec)
            val encryptedKey = cipher.doFinal(apiKey.toByteArray())

            // Store the encrypted key with its IV
            userKeys[userId] = EncryptedKeyData(
                encryptedKey = Base64.toBase64String(encryptedKey),
                iv = Base64.toBase64String(iv)
            )

            true
        } catch (e: Exception) {
            logger.error(e) { "Error encrypting API key for user $userId" }
            false
        }
    }

    fun getDecryptedKey(userId: String): String? {
        val keyData = userKeys[userId] ?: return null

        return try {
            val encryptedBytes = Base64.decode(keyData.encryptedKey)
            val iv = Base64.decode(keyData.iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val paramSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, paramSpec)

            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes)
        } catch (e: Exception) {
            logger.error(e) { "Error decrypting API key for user $userId" }
            null
        }
    }
}