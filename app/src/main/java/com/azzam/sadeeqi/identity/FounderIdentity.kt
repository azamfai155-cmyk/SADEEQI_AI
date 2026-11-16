package com.azzam.sadeeqi.identity

import android.content.Context

/**
 * Canonical, application-owned product identity.
 * Conversation content is never accepted as a source for changing these values.
 */
object FounderIdentity {
    const val PRODUCT_NAME = "SADEEQI AI"
    const val ARABIC_NAME = "صديقي"
    const val FOUNDER_NAME = "عزام فايز"
    const val FOUNDER_ROLE = "المؤسس وصاحب فكرة المشروع"

    const val GREETING = "أهلاً عزام، أنا معك. تفضل"

    fun answerFor(question: String): String? {
        val normalized = question.trim().lowercase()
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace(Regex("[؟?!！،,.؛:]+"), " ")
            .replace(Regex("\\s+"), " ")
        val compact = normalized.replace(" ", "")
        return when {
            listOf("من صنعك", "من انشاك", "من طورك", "من صممك", "مين صنعك", "مين عملك")
                .any { normalized.contains(it) || compact.contains(it.replace(" ", "")) } ->
                "أنا صديقي، مساعد ذكي أنشأه وطوّره عزام فايز ضمن مشروع SADEEQI AI."
            listOf("من مؤسسك", "من هو مؤسسك", "من مؤسس المشروع", "من صاحب المشروع")
                .any { normalized.contains(it) || compact.contains(it.replace(" ", "")) } ->
                "مؤسس مشروع SADEEQI AI هو عزام فايز."
            listOf("من صاحبك", "من صاحب فكرة المشروع", "من صاحب الفكره")
                .any { normalized.contains(it) || compact.contains(it.replace(" ", "")) } ->
                "صاحب فكرة مشروع صديقي هو عزام فايز."
            listOf("ما اسمك", "ما هو اسمك", "اسمك ايش", "ايش اسمك", "من انت", "عرف بنفسك")
                .any { normalized.contains(it) || compact.contains(it.replace(" ", "")) } ->
                "اسمي صديقي، مساعدك الذكي ضمن مشروع SADEEQI AI."
            else -> null
        }
    }

    fun context(): String =
        "هوية المنتج الثابتة: $PRODUCT_NAME ($ARABIC_NAME). " +
            "المؤسس وصاحب الفكرة: $FOUNDER_NAME. " +
            "ميّز بين المؤسس والمساهمين في التنفيذ، ولا تدّعِ أن المؤسس كتب كل الشيفرة."

    fun persist(context: Context) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRODUCT, PRODUCT_NAME)
            .putString(KEY_ARABIC, ARABIC_NAME)
            .putString(KEY_FOUNDER, FOUNDER_NAME)
            .putString(KEY_ROLE, FOUNDER_ROLE)
            .apply()
    }

    fun isPersisted(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PRODUCT, null) == PRODUCT_NAME &&
            prefs.getString(KEY_ARABIC, null) == ARABIC_NAME &&
            prefs.getString(KEY_FOUNDER, null) == FOUNDER_NAME &&
            prefs.getString(KEY_ROLE, null) == FOUNDER_ROLE
    }

    private const val PREFERENCES = "sadeeqi_product_identity"
    private const val KEY_PRODUCT = "product_name"
    private const val KEY_ARABIC = "arabic_name"
    private const val KEY_FOUNDER = "founder_name"
    private const val KEY_ROLE = "founder_role"
}
