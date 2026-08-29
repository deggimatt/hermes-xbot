package com.uzairansar.hermex.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingMarkdownFadePolicyTest {
    @Test
    fun fadesOnlyTheNewSuffixForAppendOnlyStreamingUpdates() {
        assertEquals(6, streamingSuffixStart("Hello ", "Hello world"))
        assertEquals(11, streamingSuffixStart("Hello world", "Hello world"))
    }

    @Test
    fun reparsedMarkdownFallsBackToTheFirstChangedCharacter() {
        assertEquals(5, streamingSuffixStart("one two", "one too"))
        assertEquals(0, streamingSuffixStart("old", "new"))
        assertEquals(0, streamingSuffixStart("😀", "😁"))
    }
}
