package com.uzairansar.hermex.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownSafetyPolicyTest {
    @Test
    fun longMarkdownIsChunkedWithoutDataLossOrSplitSurrogatePairs() {
        val markdown = "abc\uD83D\uDE00tail"

        val chunks = markdownPlainTextChunks(markdown, maximumCharacters = 4)

        assertEquals(markdown, chunks.joinToString(""))
        assertEquals(listOf("abc", "\uD83D\uDE00ta", "il"), chunks)
    }

    @Test
    fun shortMarkdownRemainsOneChunk() {
        val markdown = "small response"
        assertEquals(listOf(markdown), markdownPlainTextChunks(markdown, 100))
    }
}
