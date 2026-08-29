package com.uzairansar.hermex.ui.chat

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStreamRecoveryPolicyTest {
    @Test
    fun recoveryPollingBacksOffWhileFailuresRemainRecoverable() {
        assertEquals(2_000L, streamRecoveryRetryDelayMillis(1))
        assertEquals(4_000L, streamRecoveryRetryDelayMillis(2))
        assertEquals(32_000L, streamRecoveryRetryDelayMillis(5))
        assertEquals(60_000L, streamRecoveryRetryDelayMillis(100))
        assertTrue(streamRecoveryShouldRetry(7))
        assertTrue(streamRecoveryShouldRetry(8))
        assertTrue(streamRecoveryShouldRetry(100))
    }

    @Test
    fun foregroundRecoveryRecordsExpireBeforeThePlatformTimeout() {
        val fiveHours = 5L * 60L * 60L * 1_000L
        assertFalse(streamRecoveryRecordExpired(startedAtMillis = 1_000L, nowMillis = 1_000L + fiveHours - 1L))
        assertTrue(streamRecoveryRecordExpired(startedAtMillis = 1_000L, nowMillis = 1_000L + fiveHours))
    }

    @Test
    fun serviceStopsOnlyWhenJobsAndDurableRecordsAreBothEmpty() {
        assertTrue(streamRecoveryShouldStop(activeJobCount = 0, durableRecordCount = 0))
        assertFalse(streamRecoveryShouldStop(activeJobCount = 1, durableRecordCount = 0))
        assertFalse(streamRecoveryShouldStop(activeJobCount = 0, durableRecordCount = 1))
    }

    @Test
    fun recoversWhenActiveStreamFlowClosesWithoutCause() {
        assertTrue(
            ChatStreamRecoveryPolicy.shouldRecoverAfterFlowCompletion(
                cause = null,
                activeStreamId = "stream-1",
                streamId = "stream-1",
            ),
        )
    }

    @Test
    fun ignoresIntentionalCancellationAndInactiveStreams() {
        assertFalse(
            ChatStreamRecoveryPolicy.shouldRecoverAfterFlowCompletion(
                cause = CancellationException("replaced by a replay connection"),
                activeStreamId = "stream-1",
                streamId = "stream-1",
            ),
        )
        assertFalse(
            ChatStreamRecoveryPolicy.shouldRecoverAfterFlowCompletion(
                cause = null,
                activeStreamId = null,
                streamId = "stream-1",
            ),
        )
        assertFalse(
            ChatStreamRecoveryPolicy.shouldRecoverAfterFlowCompletion(
                cause = null,
                activeStreamId = "stream-2",
                streamId = "stream-1",
            ),
        )
    }
}
