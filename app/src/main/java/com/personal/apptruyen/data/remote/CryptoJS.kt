package com.personal.apptruyen.data.remote

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoJS {
    fun encrypt(
        plaintext: String,
        passphrase: String,
    ): String {
        val salt = ByteArray(8)
        SecureRandom().nextBytes(salt)
        val keyAndIv = evpBytesToKey(32, 16, salt, passphrase.toByteArray(Charsets.UTF_8), 1)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyAndIv.key, "AES"), IvParameterSpec(keyAndIv.iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val salted = "Salted__".toByteArray(Charsets.UTF_8)
        val result = ByteArray(salted.size + salt.size + ciphertext.size)
        System.arraycopy(salted, 0, result, 0, salted.size)
        System.arraycopy(salt, 0, result, salted.size, salt.size)
        System.arraycopy(ciphertext, 0, result, salted.size + salt.size, ciphertext.size)

        return Base64.encodeToString(result, Base64.NO_WRAP)
    }

    fun decrypt(
        ciphertextBase64: String,
        passphrase: String,
    ): String {
        try {
            val data = Base64.decode(ciphertextBase64, Base64.DEFAULT)
            if (data.size < 16) return ""
            val salt = data.copyOfRange(8, 16)
            val ciphertext = data.copyOfRange(16, data.size)

            val keyAndIv = evpBytesToKey(32, 16, salt, passphrase.toByteArray(Charsets.UTF_8), 1)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyAndIv.key, "AES"), IvParameterSpec(keyAndIv.iv))
            val plaintext = cipher.doFinal(ciphertext)

            return String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            return ""
        }
    }

    private class KeyAndIv(
        val key: ByteArray,
        val iv: ByteArray,
    )

    private fun evpBytesToKey(
        keySize: Int,
        ivSize: Int,
        salt: ByteArray,
        password: ByteArray,
        iterations: Int,
    ): KeyAndIv {
        val md5 = MessageDigest.getInstance("MD5")
        val targetKeySize = keySize + ivSize
        val derivedBytes = ByteArray(targetKeySize)
        var numberOfDerivedWords = 0
        var block: ByteArray? = null

        while (numberOfDerivedWords < targetKeySize) {
            if (block != null) md5.update(block)
            md5.update(password)
            md5.update(salt)
            block = md5.digest()
            for (i in 1 until iterations) {
                block = md5.digest(block)
            }
            System.arraycopy(block!!, 0, derivedBytes, numberOfDerivedWords, Math.min(block!!.size, targetKeySize - numberOfDerivedWords))
            numberOfDerivedWords += block.size
        }
        return KeyAndIv(derivedBytes.copyOfRange(0, keySize), derivedBytes.copyOfRange(keySize, keySize + ivSize))
    }
}
