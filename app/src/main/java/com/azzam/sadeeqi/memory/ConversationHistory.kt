package com.azzam.sadeeqi.memory

/**
 * Groups encrypted chat records into independent conversations.
 *
 * Current record IDs: chat:<conversation-id>:<epoch-ms>:<sequence>
 * Legacy record IDs:  chat:<epoch-ms>:<sequence> (kept as one legacy thread).
 */
object ConversationHistory {
    data class Summary(
        val id: String,
        val title: String,
        val updatedAt: Long,
        val messages: List<Pair<String, String>>
    )

    fun conversationId(recordId: String): String {
        val parts = recordId.split(':')
        if (parts.firstOrNull() != "chat") return ""
        return if (parts.size >= 4) parts[1] else "legacy"
    }

    private fun timestamp(recordId: String): Long {
        val parts = recordId.split(':')
        val index = if (parts.size >= 4) 2 else 1
        return parts.getOrNull(index)?.toLongOrNull() ?: 0L
    }

    private fun sequence(recordId: String): Int =
        recordId.substringAfterLast(':').toIntOrNull() ?: 0

    fun group(records: List<Pair<String, String>>): List<Summary> {
        return records
            .filter { it.first.startsWith("chat:") }
            .groupBy { conversationId(it.first) }
            .mapNotNull { (id, entries) ->
                val ordered = entries.sortedWith(
                    compareBy<Pair<String, String>> { timestamp(it.first) }
                        .thenBy { sequence(it.first) }
                )
                val messages = ordered.mapNotNull { (_, value) ->
                    val parts = value.split('\n', limit = 2)
                    if (parts.size == 2 && (parts[0] == "أنت" || parts[0] == "صديقي")) {
                        parts[0] to parts[1]
                    } else null
                }
                if (messages.isEmpty()) return@mapNotNull null
                val firstUserMessage = messages.firstOrNull { it.first == "أنت" }?.second
                val title = firstUserMessage
                    ?.replace('\n', ' ')
                    ?.trim()
                    ?.take(48)
                    ?.takeIf { it.isNotBlank() }
                    ?: "محادثة محفوظة"
                Summary(
                    id = id,
                    title = title,
                    updatedAt = ordered.maxOfOrNull { timestamp(it.first) } ?: 0L,
                    messages = messages
                )
            }
            .sortedByDescending { it.updatedAt }
    }
}
