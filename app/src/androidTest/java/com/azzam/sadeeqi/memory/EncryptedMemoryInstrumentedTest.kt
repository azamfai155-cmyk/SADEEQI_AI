package com.azzam.sadeeqi.memory

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EncryptedMemoryInstrumentedTest {
    @Test fun saveReadSearchUpdateDeleteAndPortableBackup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val memory = EncryptedMemory(context)
        val marker = "PRIVATE_TEST_MARKER_9f4c2"
        memory.save("acceptance-key", "private-note", marker)
        assertEquals(marker, memory.read("acceptance-key"))
        assertTrue(memory.search("PRIVATE_TEST_MARKER").isNotEmpty())
        memory.update("acceptance-key", "private-note", "updated-secret")
        assertEquals("updated-secret", memory.read("acceptance-key"))

        val db = context.getDatabasePath("sadeeqi_secure.db")
        val raw = File(db.absolutePath).takeIf { it.exists() }?.readBytes()?.toString(Charsets.ISO_8859_1).orEmpty()
        assertFalse("Plaintext marker found in SQLite main file", raw.contains(marker))

        val password = "test-password-123".toCharArray()
        val backup = memory.exportEncrypted(password)
        assertFalse(backup.contains("updated-secret"))
        memory.delete("acceptance-key")
        assertNull(memory.read("acceptance-key"))
        memory.importEncrypted(backup, "test-password-123".toCharArray())
        assertEquals("updated-secret", memory.read("acceptance-key"))
        memory.delete("acceptance-key")
    }

    @Test fun repeatedLogicalIdsReturnTheNewestEncryptedValue() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val memory = EncryptedMemory(context)
        val id = "repeat-key-${System.currentTimeMillis()}"
        memory.save(id, "private-note", "older-value")
        memory.save(id, "private-note", "newer-value")

        assertEquals("newer-value", memory.read(id))
        assertEquals("newer-value", memory.recent(10).first { it.first == id }.second)
        memory.delete(id)
    }
}
