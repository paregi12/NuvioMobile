package com.nuvio.app.features.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerSubtitleUtilsTest {

    @Test
    fun testParseFromTextSrtWithEndTimes() {
        val srtText = """
            1
            00:00:01,000 --> 00:00:04,500
            Hello world!
            Second line

            2
            00:00:05,000 --> 00:00:08,000
            Testing sub
        """.trimIndent()

        val cues = PlayerSubtitleCueParser.parseFromText(srtText, "sub.srt")
        assertEquals(2, cues.size)

        assertEquals(1000L, cues[0].startTimeMs)
        assertEquals(4500L, cues[0].endTimeMs)
        assertEquals("Hello world!\nSecond line", cues[0].text)

        assertEquals(5000L, cues[1].startTimeMs)
        assertEquals(8000L, cues[1].endTimeMs)
        assertEquals("Testing sub", cues[1].text)
    }

    @Test
    fun testParseFromTextWebVttMetadataSkipping() {
        val vttText = """
            WEBVTT

            STYLE
            ::cue {
              color: yellow;
            }

            NOTE This is a comment

            00:01.000 --> 00:04.000
            VTT Cue Text
        """.trimIndent()

        val cues = PlayerSubtitleCueParser.parseFromText(vttText, "sub.vtt")
        assertEquals(1, cues.size)
        assertEquals(1000L, cues[0].startTimeMs)
        assertEquals(4000L, cues[0].endTimeMs)
        assertEquals("VTT Cue Text", cues[0].text)
    }

    @Test
    fun testZeroDurationCueFiltering() {
        val srtText = """
            1
            00:00:01,000 --> 00:00:01,000
            Zero duration cue

            2
            00:00:02,000 --> 00:00:05,000
            Valid cue
        """.trimIndent()

        val cues = PlayerSubtitleCueParser.parseFromText(srtText, "test.srt")
        assertEquals(1, cues.size)
        assertEquals("Valid cue", cues[0].text)
    }

    @Test
    fun testParseMultilineTtmlCue() {
        val ttmlText = """
            <tt>
              <body>
                <div>
                  <p begin="00:00:01.000" end="00:00:04.500">
                    Hello<br/>world
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val cues = PlayerSubtitleCueParser.parse(ttmlText, "sub.ttml")

        assertEquals(1, cues.size)
        assertEquals(1000L, cues.single().startTimeMs)
        assertEquals(4500L, cues.single().endTimeMs)
        assertEquals("Hello\nworld", cues.single().text)
    }

    @Test
    fun testParseFromTextAssWithStylesAndTags() {
        val assText = """
            [Script Info]
            Title: Test ASS
            ScriptType: v4.00+

            [V4+ Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
            Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.50,0:00:04.25,Default,,0,0,0,,{\b1}Hello{\b0} world!\NSecond line
            Dialogue: 0,0:00:05.10,0:00:08.90,Default,,0,0,0,,{\pos(100,200)}Special cue, with comma!
        """.trimIndent()

        val cues = PlayerSubtitleCueParser.parseFromText(assText, "https://example.com/subs/test.ass")
        assertEquals(2, cues.size)

        assertEquals(1500L, cues[0].startTimeMs)
        assertEquals(4250L, cues[0].endTimeMs)
        assertEquals("Hello world!\nSecond line", cues[0].text)

        assertEquals(5100L, cues[1].startTimeMs)
        assertEquals(8900L, cues[1].endTimeMs)
        assertEquals("Special cue, with comma!", cues[1].text)
    }

    @Test
    fun testParseFromTextAssWithoutEventsHeader() {
        val assText = """
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:01:10.00,0:01:15.50,Default,,0,0,0,,{\an8}Top subtitle
        """.trimIndent()

        val cues = PlayerSubtitleCueParser.parseFromText(assText, "sub.ssa")
        assertEquals(1, cues.size)
        assertEquals(70000L, cues[0].startTimeMs)
        assertEquals(75500L, cues[0].endTimeMs)
        assertEquals("Top subtitle", cues[0].text)
    }
}
