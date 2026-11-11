package com.azzam.sadeeqi.core

/** Offline starter engine. Replace/extend with a remote or on-device model provider. */
class AssistantEngine {
    fun reply(input: String): String {
        val q = input.trim().lowercase()
        return when {
            q.isBlank() -> "اكتب أمرك أو استخدم زر الميكروفون."
            "الساعة" in q || "الوقت" in q -> "الوقت الآن: ${java.text.SimpleDateFormat("HH:mm", java.util.Locale("ar")).format(java.util.Date())}"
            "اسمك" in q -> "أنا صديقي، مساعدك الرقمي."
            "مرحبا" in q || "السلام" in q -> "أهلاً عزام، أنا معك. كيف أساعدك؟"
            else -> "وصلني طلبك: «$input». أنا حالياً في النسخة الأولية دون اتصال بمحرك ذكاء اصطناعي سحابي. أضف مزوداً من الإعدادات في إصدار لاحق لتنفيذ تحليل متقدم."
        }
    }
}
