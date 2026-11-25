package com.azzam.sadeeqi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiGatewaySourcesTest {
    private val gateway = OpenAiGateway(accessTokenProvider = { "test-token" })
    @Test
    fun parsesOnlyUniqueHttpsSourcesAndCapsAtTwelve() {
        val entries = (1..14).joinToString(",") { index ->
            """{"title":"Source $index","url":"https://example.org/$index"}"""
        }
        val body = """{"sources":[$entries,{"title":"Duplicate","url":"https://example.org/1"},{"title":"Unsafe","url":"http://unsafe.example.org"}]}"""

        val sources = gateway.parseSources(body)

        assertEquals(12, sources.size)
        assertEquals("https://example.org/1", sources.first().url)
        assertEquals("Source 1", sources.first().title)
        assertTrue(sources.all { it.url.startsWith("https://") })
    }

    @Test
    fun rejectsMalformedUrlsAndCredentialBearingUrls() {
        val body = """{"sources":[
            {"title":"HTTP","url":"http://example.org"},
            {"title":"Credentials","url":"https://user:pass@example.org/private"},
            {"title":"Malformed","url":"not-a-url"},
            {"title":"Valid","url":"https://example.org/path"}
        ]}"""

        val sources = gateway.parseSources(body)

        assertEquals(listOf("https://example.org/path"), sources.map { it.url })
    }

    @Test
    fun missingSourcesProducesEmptyList() {
        assertTrue(gateway.parseSources("""{"output_text":"hello"}""").isEmpty())
    }
}
