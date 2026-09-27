package com.laurasheehan.royalmiles.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The coach feed key and the coach chat key. Encrypted with a key that lives in this phone's
 * keystore and can't be exported, in a file left out of Android's cloud backup and device
 * transfer (res/xml/backup_rules.xml, res/xml/data_extraction_rules.xml). A key restored onto
 * another phone could never be decrypted there anyway; on a new phone she connects again.
 */
class SecretStore(
    private val prefs: SharedPreferences,
    private val cipher: SecretCipher = KeystoreCipher(),
) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    /** Null when absent, or when it can't be decrypted (then it is dropped, and she reconnects). */
    fun get(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        return runCatching { cipher.decrypt(stored) }.getOrElse {
            prefs.edit().remove(name).apply()
            null
        }
    }

    fun put(name: String, value: String) {
        prefs.edit().putString(name, cipher.encrypt(value)).commit()
    }

    fun remove(vararg names: String) {
        val edit = prefs.edit()
        names.forEach { edit.remove(it) }
        edit.apply()
    }

    /**
     * Moves a key an older build saved in plain text into this store, then deletes the plain copy.
     * Deleted only after the encrypted copy is written, so a failure can't lose it.
     */
    fun adopt(from: SharedPreferences, oldKey: String, name: String) {
        val plain = from.getString(oldKey, null) ?: return
        if (get(name) == null) put(name, plain)
        if (get(name) != null) from.edit().remove(oldKey).commit()
    }

    companion object {
        /** Must match the exclusions in res/xml/backup_rules.xml and data_extraction_rules.xml. */
        const val FILE = "secrets"
        const val COACH_FEED_KEY = "coach_feed_key"
        const val COACH_CHAT_KEY = "coach_chat_key"
    }
}

interface SecretCipher {
    fun encrypt(plain: String): String
    fun decrypt(stored: String): String
}

/** AES-GCM with a non-exportable key in the Android keystore; the IV travels with each value. */
class KeystoreCipher : SecretCipher {
    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(sealed)
    }

    override fun decrypt(stored: String): String {
        val sealed = Base64.getDecoder().decode(stored)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed, 0, IV_BYTES))
        return String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "royal_miles_secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
