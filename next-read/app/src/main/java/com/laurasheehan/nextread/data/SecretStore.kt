package com.laurasheehan.nextread.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The books Worker's address and key. The key is encrypted with a key held in this phone's
 * keystore, which can't be exported, in a file left out of Android's backup and device
 * transfer. On a new phone she connects again. Never built into the app: the repo is public.
 */
class SecretStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    var address: String?
        get() = prefs.getString(ADDRESS, null)
        set(value) { prefs.edit().putString(ADDRESS, value).apply() }

    var key: String?
        get() = prefs.getString(KEY, null)?.let { stored -> runCatching { decrypt(stored) }.getOrNull() }
        set(value) {
            if (value == null) prefs.edit().remove(KEY).apply() else prefs.edit().putString(KEY, encrypt(value)).apply()
        }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(sealed)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.getDecoder().decode(stored)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }

    private companion object {
        const val ALIAS = "next-read-books-key"
        const val ADDRESS = "address"
        const val KEY = "key"
    }
}
