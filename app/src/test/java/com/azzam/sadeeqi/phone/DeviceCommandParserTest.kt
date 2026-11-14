package com.azzam.sadeeqi.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceCommandParserTest {
    @Test fun parsesCameraAndSettingsCommands() {
        assertEquals(DeviceCommand.OpenCamera, DeviceCommandParser.parse("افتح الكاميرا"))
        assertEquals(DeviceCommand.OpenSettings, DeviceCommandParser.parse("افتح الإعدادات"))
    }

    @Test fun parsesVolumeCommands() {
        assertEquals(DeviceCommand.VolumeUp, DeviceCommandParser.parse("ارفع مستوى الصوت"))
        assertEquals(DeviceCommand.VolumeDown, DeviceCommandParser.parse("اخفض الصوت"))
    }

    @Test fun parsesSupportedApps() {
        assertEquals(
            DeviceCommand.LaunchApp("واتساب", "com.whatsapp"),
            DeviceCommandParser.parse("افتح واتساب")
        )
        assertEquals(
            DeviceCommand.LaunchApp("تيليجرام", "org.telegram.messenger"),
            DeviceCommandParser.parse("شغل تيليجرام")
        )
    }

    @Test fun parsesAlarmTimeWithArabicAndLatinDigits() {
        assertEquals(DeviceCommand.SetAlarm(7, 5), DeviceCommandParser.parse("اضبط منبه الساعة ٧:٠٥"))
        assertEquals(DeviceCommand.SetAlarm(19, 30), DeviceCommandParser.parse("اعمل لي منبه 19:30"))
        assertNull(DeviceCommandParser.parse("اضبط منبه الساعة 25:99"))
    }

    @Test fun doesNotInterpretOrdinaryConversationAsDeviceCommand() {
        assertNull(DeviceCommandParser.parse("كيف أرفع مستوى الصوت في هاتفي؟"))
        assertNull(DeviceCommandParser.parse("أريد أن أفتح واتساب لاحقاً"))
    }
}
