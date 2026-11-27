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


    @Test fun savingAndImportingSameLogicalIdDoesNotCreateDuplicates() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val memory = EncryptedMemory(context)
        memory.delete("stable-logical-id")
        memory.save("stable-logical-id", "test", "first")
        memory.save("stable-logical-id", "test", "second")
        assertEquals("second", memory.read("stable-logical-id"))
        assertEquals(1, memory.search("stable-logical-id").count { it.first == "stable-logical-id" })
        assertEquals(listOf("second"), memory.recent(20).filter { it.first == "stable-logical-id" }.map { it.second })

        val backup = memory.exportEncrypted("test-password-123".toCharArray())
        memory.importEncrypted(backup, "test-password-123".toCharArray())
        assertEquals("second", memory.read("stable-logical-id"))
        assertEquals(1, memory.search("stable-logical-id").count { it.first == "stable-logical-id" })
        memory.delete("stable-logical-id")
        assertTrue(memory.recent(20).none { it.first == "stable-logical-id" })
    }

}
