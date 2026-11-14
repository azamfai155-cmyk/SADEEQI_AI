package com.azzam.sadeeqi.phone

sealed interface DeviceCommand {
    data class LaunchApp(val appName: String, val packageName: String) : DeviceCommand
    data object OpenCamera : DeviceCommand
    data object OpenSettings : DeviceCommand
    data object VolumeUp : DeviceCommand
    data object VolumeDown : DeviceCommand
    data class SetAlarm(val hour: Int, val minute: Int) : DeviceCommand
}

object DeviceCommandParser {
    private val appAliases = mapOf(
        "واتساب" to ("واتساب" to "com.whatsapp"),
        "whatsapp" to ("واتساب" to "com.whatsapp"),
        "تيليجرام" to ("تيليجرام" to "org.telegram.messenger"),
        "telegram" to ("تيليجرام" to "org.telegram.messenger"),
        "يوتيوب" to ("يوتيوب" to "com.google.android.youtube"),
        "youtube" to ("يوتيوب" to "com.google.android.youtube")
    )

    fun parse(raw: String): DeviceCommand? {
        val text = normalize(raw)
        when (text) {
            "افتح الكاميرا", "شغل الكاميرا", "فتح الكاميرا" -> return DeviceCommand.OpenCamera
            "افتح الاعدادات", "افتح الضبط", "اذهب للاعدادات" -> return DeviceCommand.OpenSettings
            "ارفع مستوى الصوت", "ارفع الصوت", "علي الصوت" -> return DeviceCommand.VolumeUp
            "اخفض مستوى الصوت", "اخفض الصوت", "وطي الصوت" -> return DeviceCommand.VolumeDown
        }

        val launch = Regex("^(?:افتح|شغل|قم بفتح)\\s+(.+)$").matchEntire(text)
        if (launch != null) {
            val alias = appAliases[launch.groupValues[1].trim()]
            if (alias != null) return DeviceCommand.LaunchApp(alias.first, alias.second)
        }

        val alarm = Regex("^(?:اضبط|انشئ|اعمل)\\s+(?:لي\\s+)?منبه(?:\\s+الساعه)?\\s+(\\d{1,2})(?::(\\d{1,2}))?$")
            .matchEntire(text)
        if (alarm != null) {
            val hour = alarm.groupValues[1].toIntOrNull() ?: return null
            val minute = alarm.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
            if (hour in 0..23 && minute in 0..59) return DeviceCommand.SetAlarm(hour, minute)
        }
        return null
    }

    fun confirmationText(command: DeviceCommand): String = when (command) {
        is DeviceCommand.LaunchApp -> "هل تسمح لصديقي بفتح تطبيق ${command.appName}؟"
        DeviceCommand.OpenCamera -> "هل تريد فتح كاميرا الهاتف؟"
        DeviceCommand.OpenSettings -> "هل تريد فتح إعدادات الهاتف؟"
        DeviceCommand.VolumeUp -> "هل تريد رفع مستوى صوت الوسائط؟"
        DeviceCommand.VolumeDown -> "هل تريد خفض مستوى صوت الوسائط؟"
        is DeviceCommand.SetAlarm -> "هل تريد فتح تطبيق الساعة لإعداد منبه عند %02d:%02d؟".format(command.hour, command.minute)
    }

    private fun normalize(value: String): String = value.trim().lowercase()
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .replace(Regex("[٠-٩]")) { match -> (match.value[0].code - '٠'.code).toString() }
        .replace(Regex("[!！؟?،,.]+"), " ")
        .replace(Regex("\\s+"), " ")
}
