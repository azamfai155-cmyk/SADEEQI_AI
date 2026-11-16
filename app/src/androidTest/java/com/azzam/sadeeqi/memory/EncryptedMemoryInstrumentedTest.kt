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
        val databaseFiles = listOf(db, File(db.absolutePath + "-wal"), File(db.absolutePath + "-shm"))
        val rawDatabaseBytes = databaseFiles.filter { it.exists() }
            .joinToString(separator = "") { it.readBytes().toString(Charsets.ISO_8859_1) }
        assertFalse("Plaintext marker found in SQLite database files", rawDatabaseBytes.contains(marker))

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
        val recentMatches = memory.recent(10).filter { it.first == id }
        assertEquals(1, recentMatches.size)
        assertEquals("newer-value", recentMatches.single().second)
        memory.delete(id)
        assertNull(memory.read(id))
        assertTrue(memory.recent(10).none { it.first == id })
    }
}
