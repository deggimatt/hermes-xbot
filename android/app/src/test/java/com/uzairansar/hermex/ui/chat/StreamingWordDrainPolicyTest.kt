package com.uzairansar.hermex.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingWordDrainPolicyTest {
    @Test
    fun splitsOneWordWithItsTrailingWhitespaceWithoutChangingContent() {
        val text = "  Hello  world\nfrom Hermex"
        val (head, tail) = StreamingWordDrainPolicy.splitAtUnitBoundary(text, unitCount = 1)

        assertEquals("  Hello  ", head)
        assertEquals("world\nfrom Hermex", tail)
        assertEquals(text, head + tail)
        assertEquals(4, StreamingWordDrainPolicy.unitCount(text))
    }

    @Test
    fun keepsEmojiSequencesAndUnfinishedWordsWhole() {
        val text = "Family 👨‍👩‍👧‍👦 works"
        val (head, tail) = StreamingWordDrainPolicy.splitAtUnitBoundary(text, unitCount = 2)

        assertEquals("Family 👨‍👩‍👧‍👦 ", head)
        assertEquals("works", tail)
        assertEquals(text, head + tail)
        assertEquals("unfinished" to "", StreamingWordDrainPolicy.splitAtUnitBoundary("unfinished", 1))
    }

    @Test
    fun increasesQuotaWhenAOneWordCadenceWouldExceedMaximumLag() {
        assertEquals(1, StreamingWordDrainPolicy.drainQuota(4, cadenceMillis = 48, maximumLagMillis = 1_000))
        assertEquals(5, StreamingWordDrainPolicy.drainQuota(100, cadenceMillis = 48, maximumLagMillis = 1_000))
    }
}
