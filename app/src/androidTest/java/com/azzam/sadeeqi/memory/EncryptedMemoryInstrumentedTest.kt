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


    @Test fun savingAndImportingSameLogicalIdDoesNotCreateDuplicates() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val memory = EncryptedMemory(context)
        memory.delete("stable-logical-id")
        memory.save("stable-logical-id", "test", "first")
        memory.save("stable-logical-id", "test", "second")
        assertEquals("second", memory.read("stable-logical-id"))
        assertEquals(1, memory.search("stable-logical-id").count { it.first == "stable-logical-id" })

        val backup = memory.exportEncrypted("test-password-123".toCharArray())
        memory.importEncrypted(backup, "test-password-123".toCharArray())
        assertEquals("second", memory.read("stable-logical-id"))
        assertEquals(1, memory.search("stable-logical-id").count { it.first == "stable-logical-id" })
        memory.delete("stable-logical-id")
    }


    @Test fun wrongBackupPasswordDoesNotModifyExistingMemory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val memory = EncryptedMemory(context)
        memory.delete("backup-atomicity-check")
        memory.save("backup-atomicity-check", "test", "original-value")

        val backup = memory.exportEncrypted("correct-password-123".toCharArray())
        var rejected = false
        try {
            memory.importEncrypted(backup, "incorrect-password-123".toCharArray())
        } catch (_: Exception) {
            rejected = true
        }

        assertTrue("An archive with the wrong password must be rejected", rejected)
        assertEquals("original-value", memory.read("backup-atomicity-check"))
        memory.delete("backup-atomicity-check")
    }

}
