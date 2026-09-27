package com.laurasheehan.royalmiles.data

import android.content.SharedPreferences
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretStoreTest {

    /** Reversible and visibly not plain text; the real one is AES-GCM in the Android keystore. */
    private class FakeCipher : SecretCipher {
        var broken = false
        override fun encrypt(plain: String) = "enc:" + plain.reversed()
        override fun decrypt(stored: String): String {
            check(!broken) { "keystore key gone" }
            return stored.removePrefix("enc:").reversed()
        }
    }

    @Test
    fun `keys are stored encrypted and read back`() {
        val prefs = MemoryPrefs()
        val store = SecretStore(prefs, FakeCipher())
        store.put(SecretStore.COACH_CHAT_KEY, "chat-key-123")
        assertEquals("enc:321-yek-tahc", prefs.getString(SecretStore.COACH_CHAT_KEY, null))
        assertEquals("chat-key-123", store.get(SecretStore.COACH_CHAT_KEY))
    }

    @Test
    fun `a key from an older build moves in and the plain copy is deleted`() {
        val old = MemoryPrefs().apply { edit().putString("token", "plain-key").putString("address", "https://x").commit() }
        val store = SecretStore(MemoryPrefs(), FakeCipher())
        store.adopt(old, "token", SecretStore.COACH_CHAT_KEY)
        assertEquals("plain-key", store.get(SecretStore.COACH_CHAT_KEY))
        assertNull(old.getString("token", null))
        assertEquals("https://x", old.getString("address", null), "only the key moves")
    }

    @Test
    fun `an undecryptable key is dropped so she reconnects, rather than crashing`() {
        val prefs = MemoryPrefs()
        val cipher = FakeCipher()
        val store = SecretStore(prefs, cipher)
        store.put(SecretStore.COACH_FEED_KEY, "k")
        cipher.broken = true
        assertNull(store.get(SecretStore.COACH_FEED_KEY))
        assertFalse(prefs.contains(SecretStore.COACH_FEED_KEY))
    }

    @Test
    fun `the key file is left out of cloud backup and device transfer`() {
        val res = File("src/main/res/xml")
        val file = "path=\"${SecretStore.FILE}.xml\""
        val legacy = File(res, "backup_rules.xml").readText()
        assertTrue("<exclude domain=\"sharedpref\" $file" in legacy)
        val modern = File(res, "data_extraction_rules.xml").readText()
        assertEquals(2, Regex("<exclude domain=\"sharedpref\" ${Regex.escape(file)}").findAll(modern).count())
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue("android:fullBackupContent=\"@xml/backup_rules\"" in manifest)
        assertTrue("android:dataExtractionRules=\"@xml/data_extraction_rules\"" in manifest)
    }
}

/** Enough of SharedPreferences for these tests. */
private class MemoryPrefs : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { changes[key] = values }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { removals += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            if (clear) values.clear()
            removals.forEach { values.remove(it) }
            values.putAll(changes)
            return true
        }
        override fun apply() {
            commit()
        }
    }
}
