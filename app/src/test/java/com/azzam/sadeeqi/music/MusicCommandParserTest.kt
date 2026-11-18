package com.azzam.sadeeqi.music

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicCommandParserTest {
    @Test fun parsesArabicPause() {
        assertEquals(MusicCommand.Pause, MusicCommandParser.parse("أوقف الموسيقى"))
        assertEquals(MusicCommand.Pause, MusicCommandParser.parse("اطفئ الأغنية"))
    }

    @Test fun parsesNextTrack() {
        assertEquals(MusicCommand.Next, MusicCommandParser.parse("انتقل للأغنية التالية"))
    }

    @Test fun parsesVolumeAdjustment() {
        assertEquals(MusicCommand.VolumeUp, MusicCommandParser.parse("ارفع الصوت"))
    }

    @Test fun preservesSpokenSongAndArtistQuery() {
        assertEquals(MusicCommand.Search("بتمنى أنساك حق شيرين"), MusicCommandParser.parse("شغل لي أغنية بتمنى أنساك حق شيرين"))
    }

    @Test fun parsesQuranSurah() {
        assertEquals(MusicCommand.PlayQuran(2, "البقرة"), MusicCommandParser.parse("شغل سورة البقرة من القرآن"))
    }

    @Test fun parsesDirectSurahPlayCommand() {
        assertEquals(MusicCommand.PlayQuran(36, "يس"), MusicCommandParser.parse("شغل سورة يس"))
    }

    @Test fun opensQuranPickerWithoutSurah() {
        assertEquals(MusicCommand.OpenQuran, MusicCommandParser.parse("شغل لي القرآن الكريم"))
        assertEquals(MusicCommand.OpenQuran, MusicCommandParser.parse("افتح القرآن الكريم"))
        assertEquals(MusicCommand.OpenQuran, MusicCommandParser.parse("افتح المصحف"))
    }

    @Test fun parsesRuqyahRequest() {
        assertEquals(MusicCommand.PlayRuqyah, MusicCommandParser.parse("شغل رقية شرعية"))
    }

    @Test fun parsesMoodBasedMusicRequests() {
        assertEquals(MusicCommand.Search("upbeat energetic music"), MusicCommandParser.parse("شغل شيء حماسي"))
        assertEquals(MusicCommand.Search("calm relaxing instrumental music"), MusicCommandParser.parse("خلينا نروق"))
    }

    @Test fun doesNotTreatEveryMoodMentionAsMusicCommand() {
        assertEquals(null, MusicCommandParser.parse("أنا محتاج روقان اليوم"))
    }

    @Test fun parsesRelaxingMusicRequest() {
        assertEquals(MusicCommand.Search("calm relaxing instrumental music"), MusicCommandParser.parse("شغل لي أغنية هادئة"))
    }

    @Test fun ignoresUnrelatedText() {
        assertEquals(null, MusicCommandParser.parse("ما حالة الطقس اليوم"))
    }
}
