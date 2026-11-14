package com.azzam.sadeeqi.music

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicCommandParserTest {
    @Test fun parsesArabicPause() {
        assertEquals(MusicCommand.Pause, MusicCommandParser.parse("أوقف الموسيقى"))
    }

    @Test fun parsesNextTrack() {
        assertEquals(MusicCommand.Next, MusicCommandParser.parse("انتقل للأغنية التالية"))
    }

    @Test fun parsesVolumeAdjustment() {
        assertEquals(MusicCommand.VolumeUp, MusicCommandParser.parse("ارفع الصوت"))
    }

    @Test fun preservesSearchQuery() {
        assertEquals(MusicCommand.Search("موسيقى يمنية"), MusicCommandParser.parse("ابحث عن موسيقى يمنية"))
    }

    @Test fun ignoresUnrelatedText() {
        assertEquals(null, MusicCommandParser.parse("ما حالة الطقس اليوم"))
    }
}
