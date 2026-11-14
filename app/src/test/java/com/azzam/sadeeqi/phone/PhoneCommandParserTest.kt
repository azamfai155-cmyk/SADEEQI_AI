package com.azzam.sadeeqi.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneCommandParserTest {
    @Test fun parsesArabicAppLaunch() {
        assertEquals(
            PhoneCommand.OpenApp("com.google.android.youtube", "يوتيوب"),
            PhoneCommandParser.parse("افتح يوتيوب")
        )
    }

    @Test fun parsesWifiSettings() {
        assertEquals(
            PhoneCommand.OpenSettings("android.settings.WIFI_SETTINGS", "إعدادات Wi-Fi"),
            PhoneCommandParser.parse("افتح الواي فاي")
        )
    }

    @Test fun parsesArabicDigitsForAlarm() {
        assertEquals(
            PhoneCommand.SetAlarm(7, 30),
            PhoneCommandParser.parse("اضبط منبه الساعة ٧:٣٠")
        )
    }

    @Test fun parsesDialButDoesNotPlaceCall() {
        assertEquals(PhoneCommand.Dial("777123456"), PhoneCommandParser.parse("اتصل على 777123456"))
    }

    @Test fun parsesSmsComposerRequest() {
        assertEquals(
            PhoneCommand.ComposeSms("777123456", "مرحبا كيف حالك"),
            PhoneCommandParser.parse("اكتب رسالة الى 777123456 نص مرحبا كيف حالك")
        )
    }

    @Test fun ignoresUnrecognizedOrBroadRequests() {
        assertNull(PhoneCommandParser.parse("تحكم بكل الهاتف وافتح أي شيء"))
        assertNull(PhoneCommandParser.parse("منبه بعد 10 دقائق"))
    }
}
