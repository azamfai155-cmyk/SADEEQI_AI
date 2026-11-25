package com.azzam.sadeeqi.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class SadeeqiReminder(
    val id: String,
    val title: String,
    val dueAtMillis: Long
)

object ReminderValidator {
    fun normalizeTitle(value: String): String? =
        value.trim().takeIf { it.isNotEmpty() && it.length <= 120 }

    fun isFuture(dueAtMillis: Long, nowMillis: Long): Boolean =
        dueAtMillis > nowMillis
}

object ReminderScheduler {
    private const val PREFS = "sadeeqi_local_reminders"
    private const val KEY_ITEMS = "items"
    private const val TAG = "sadeeqi-reminder"

    fun schedule(
        context: Context,
        title: String,
        dueAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): SadeeqiReminder {
        val safeTitle = ReminderValidator.normalizeTitle(title)
            ?: throw IllegalArgumentException("اكتب عنواناً للتذكير لا يتجاوز 120 حرفاً.")
        require(ReminderValidator.isFuture(dueAtMillis, nowMillis)) {
            "اختر وقتاً في المستقبل."
        }

        val reminder = SadeeqiReminder(UUID.randomUUID().toString(), safeTitle, dueAtMillis)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(dueAtMillis - nowMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_ID to reminder.id,
                    ReminderWorker.KEY_TITLE to reminder.title
                )
            )
            .addTag(TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName(reminder.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
        val items = pending(context).toMutableList().apply { add(reminder) }
        save(context, items)
        return reminder
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(id))
        save(context, pending(context).filterNot { it.id == id })
    }

    fun pending(context: Context): List<SadeeqiReminder> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val title = item.optString("title")
                val due = item.optLong("dueAtMillis", 0L)
                if (id.isNotBlank() && title.isNotBlank() && due > 0L) {
                    add(SadeeqiReminder(id, title, due))
                }
            }
        }.sortedBy { it.dueAtMillis }
    }

    internal fun markDelivered(context: Context, id: String) {
        save(context, pending(context).filterNot { it.id == id })
    }

    private fun save(context: Context, reminders: List<SadeeqiReminder>) {
        val array = JSONArray()
        reminders.forEach { reminder ->
            array.put(
                JSONObject()
                    .put("id", reminder.id)
                    .put("title", reminder.title)
                    .put("dueAtMillis", reminder.dueAtMillis)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }

    private fun uniqueWorkName(id: String) = "sadeeqi-reminder-$id"
}
