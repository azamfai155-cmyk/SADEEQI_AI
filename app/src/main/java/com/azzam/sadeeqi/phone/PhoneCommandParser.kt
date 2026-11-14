package com.azzam.sadeeqi.phone

/** Parses only explicit, allow-listed phone actions. Unknown requests stay with the assistant. */
sealed class PhoneCommand {
    data class OpenApp(val packageName: String, val appName: String) : PhoneCommand()
    data class OpenSettings(val action: String, val label: String) : PhoneCommand()
    data object Camera : PhoneCommand()
    data object Files : PhoneCommand()
    data object VolumeUp : PhoneCommand()
    data object VolumeDown : PhoneCommand()
    data class SetAlarm(val hour: Int, val minute: Int) : PhoneCommand()
    data class Dial(val number: String) : PhoneCommand()
    data class ComposeSms(val number: String, val body: String) : PhoneCommand()
}

object PhoneCommandParser {
    private val digitMap = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9'
    )

    private fun normalize(value: String): String = value
        .map { digitMap[it] ?: it }
        .joinToString("")
        .lowercase()
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace(Regex("[!！؟?،,.؛;]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val appAliases = listOf(
        listOf("واتساب", "واتس اب", "whatsapp") to ("com.whatsapp" to "واتساب"),
        listOf("يوتيوب", "youtube") to ("com.google.android.youtube" to "يوتيوب"),
        listOf("فيسبوك", "فيس بوك", "facebook") to ("com.facebook.katana" to "فيسبوك"),
        listOf("انستغرام", "انستجرام", "انستا", "instagram") to ("com.instagram.android" to "إنستغرام"),
        listOf("تيليجرام", "تلغرام", "telegram") to ("org.telegram.messenger" to "تيليجرام"),
        listOf("كروم", "chrome") to ("com.android.chrome" to "كروم"),
        listOf("خرائط", "خرائط جوجل", "google maps") to ("com.google.android.apps.maps" to "خرائط جوجل")
    )

    fun parse(raw: String): PhoneCommand? {
        val text = normalize(raw).removePrefix("يا صديقي ").removePrefix("صديقي ").trim()
        if (text.isBlank()) return null

        if (listOf("ارفع الصوت", "علي الصوت", "زود الصوت", "ارفع صوت الهاتف", "ارفع صوت الجوال").any(text::contains)) {
            return PhoneCommand.VolumeUp
        }
        if (listOf("خفض الصوت", "وطي الصوت", "قلل الصوت", "اخفض صوت الهاتف", "خفض صوت الجوال").any(text::contains)) {
            return PhoneCommand.VolumeDown
        }
        if (listOf("افتح الكاميرا", "شغل الكاميرا", "الكاميرا").any { text == it || text.startsWith("$it ") }) {
            return PhoneCommand.Camera
        }
        if (listOf("افتح ملفاتي", "افتح الملفات", "اختيار ملف").any(text::contains)) {
            return PhoneCommand.Files
        }
        if (listOf("افتح الاعدادات", "افتح إعدادات الهاتف", "إعدادات الهاتف").any(text::contains)) {
            return PhoneCommand.OpenSettings("android.settings.SETTINGS", "الإعدادات")
        }
        if (listOf("افتح الواي فاي", "اعدادات الواي فاي", "إعدادات الواي فاي").any(text::contains)) {
            return PhoneCommand.OpenSettings("android.settings.WIFI_SETTINGS", "إعدادات Wi-Fi")
        }
        if (listOf("افتح البلوتوث", "اعدادات البلوتوث", "إعدادات البلوتوث").any(text::contains)) {
            return PhoneCommand.OpenSettings("android.settings.BLUETOOTH_SETTINGS", "إعدادات Bluetooth")
        }

        val sms = Regex("^(?:اكتب|جهز|افتح) رسالة (?:الى|ل|إلى) ([+0-9]{5,16}) (?:نص|محتوى) (.+)$").find(text)
        if (sms != null) return PhoneCommand.ComposeSms(sms.groupValues[1], sms.groupValues[2].trim())

        val dial = Regex("^(?:اتصل على|اتصل بـ|اتصل|اطلب) ([+0-9]{5,16})$").find(text)
        if (dial != null) return PhoneCommand.Dial(dial.groupValues[1])

        if (text.contains("منبه")) {
            val time = Regex("(?:الساعة\\s*|على\\s*الساعة\\s*|منبه\\s+)([0-9]{1,2})(?::([0-9]{2}))?").find(text)
            if (time != null) {
                val hour = time.groupValues[1].toIntOrNull() ?: return null
                val minute = time.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
                if (hour in 0..23 && minute in 0..59) return PhoneCommand.SetAlarm(hour, minute)
            }
        }

        if (text.startsWith("افتح ") || text.startsWith("شغل ")) {
            appAliases.firstOrNull { (aliases, _) -> aliases.any { alias -> text.removePrefix("افتح ").removePrefix("شغل ").removePrefix("تطبيق ").trim() == alias } }
                ?.let { (_, app) -> return PhoneCommand.OpenApp(app.first, app.second) }
        }
        return null
    }
}
