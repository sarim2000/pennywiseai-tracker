package com.pennywiseai.tracker.data.backup

import android.content.Context
import android.content.SharedPreferences
import com.pennywiseai.tracker.core.DeviceSecretBox
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** The folder backup passphrase setting is only "on" once it's stored, and a missing secret fails closed. */
class FolderBackupPassphraseTest {
    private val prefs = FakePrefs()
    private val context = mockk<Context> {
        every { getSharedPreferences("folder_backup", Context.MODE_PRIVATE) } returns prefs
    }

    @Before
    fun setUp() {
        mockkObject(DeviceSecretBox)
        every { DeviceSecretBox.seal(any(), any()) } answers { "sealed:" + firstArg<String>() }
        every { DeviceSecretBox.open(any(), any()) } answers { firstArg<String>().removePrefix("sealed:") }
    }

    @After
    fun tearDown() = unmockkObject(DeviceSecretBox)

    @Test
    fun `a passphrase that couldn't be stored leaves encryption off and says so`() {
        val store = FolderBackupPassphrase(context)
        prefs.commitSucceeds = false

        assertThrows(IOException::class.java) { store.save("synthetic phrase".toCharArray()) }

        assertEquals(FolderBackupPassphrase.Status.OFF, store.status.value)
        assertNull(FolderBackupPassphrase(context).load())
    }

    @Test
    fun `a stored passphrase is on after a restart`() {
        FolderBackupPassphrase(context).save("synthetic phrase".toCharArray())

        val restarted = FolderBackupPassphrase(context)

        assertEquals(FolderBackupPassphrase.Status.ON, restarted.status.value)
        assertArrayEquals("synthetic phrase".toCharArray(), restarted.load())
    }

    @Test
    fun `a missing secret with encryption on stops backups instead of writing them plain`() {
        FolderBackupPassphrase(context).save("synthetic phrase".toCharArray())
        prefs.values.remove(FolderBackupPassphrase.KEY_SEALED)

        val restarted = FolderBackupPassphrase(context)

        assertEquals(FolderBackupPassphrase.Status.LOST, restarted.status.value)
        assertThrows(FolderBackupPassphrase.LostException::class.java) { restarted.load() }
    }

    @Test
    fun `turning encryption off that couldn't be stored keeps it on`() {
        val store = FolderBackupPassphrase(context)
        store.save("synthetic phrase".toCharArray())
        prefs.commitSucceeds = false

        assertThrows(IOException::class.java) { store.clear() }

        assertEquals(FolderBackupPassphrase.Status.ON, store.status.value)
        assertArrayEquals("synthetic phrase".toCharArray(), store.load())
    }

    /** In-memory prefs whose commit can fail, like a full disk. */
    private class FakePrefs : SharedPreferences {
        val values = mutableMapOf<String, Any?>()
        var commitSucceeds = true

        override fun getAll(): Map<String, *> = values.toMap()
        override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?) = defValues
        override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
        override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
        override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
        override fun contains(key: String) = key in values
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val puts = mutableMapOf<String, Any?>()
            private val removes = mutableSetOf<String>()

            override fun putString(key: String, value: String?) = apply { puts[key] = value }
            override fun putStringSet(key: String, values: Set<String>?) = apply { puts[key] = values }
            override fun putInt(key: String, value: Int) = apply { puts[key] = value }
            override fun putLong(key: String, value: Long) = apply { puts[key] = value }
            override fun putFloat(key: String, value: Float) = apply { puts[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value }
            override fun remove(key: String) = apply { removes += key }
            override fun clear() = apply { removes += values.keys }

            override fun commit(): Boolean {
                if (!commitSucceeds) return false
                removes.forEach { values.remove(it) }
                values.putAll(puts)
                return true
            }

            override fun apply() {
                commit()
            }
        }
    }
}
