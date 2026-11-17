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

        // Check the live SQLite database and WAL sidecar before replacing the marker.
        // Room commonly keeps recent writes in WAL rather than the main database file.
        val db = context.getDatabasePath("sadeeqi_secure.db")
        val sqliteFiles = listOf(
            db,
            File(db.absolutePath + "-wal"),
            File(db.absolutePath + "-shm")
        )
        val rawSqlite = sqliteFiles
            .filter { it.exists() }
            .joinToString("") { it.readBytes().toString(Charsets.ISO_8859_1) }
        assertFalse("Plaintext marker found in SQLite files", rawSqlite.contains(marker))

        // Re-saving a logical ID must replace its value rather than create duplicates.
        memory.save("acceptance-key", "private-note", "replacement-secret")
        assertEquals("replacement-secret", memory.read("acceptance-key"))
        assertEquals(1, memory.search("acceptance-key").count { it.first == "acceptance-key" })

        memory.update("acceptance-key", "private-note", "updated-secret")
        assertEquals("updated-secret", memory.read("acceptance-key"))

        val password = "test-password-123".toCharArray()
        val backup = memory.exportEncrypted(password)
        assertFalse(backup.contains("updated-secret"))
        memory.delete("acceptance-key")
        assertNull(memory.read("acceptance-key"))
        memory.importEncrypted(backup, "test-password-123".toCharArray())
        memory.importEncrypted(backup, "test-password-123".toCharArray())
        assertEquals("updated-secret", memory.read("acceptance-key"))
        assertEquals(1, memory.search("acceptance-key").count { it.first == "acceptance-key" })
        memory.delete("acceptance-key")
    }
}
