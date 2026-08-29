package com.uzairansar.hermex.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CoroutineResultTest {
    @Test
    fun `ordinary failures are returned`() = runTest {
        val result = runSuspendCatching<String> { error("boom") }

        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun `cancellation is rethrown`() = runTest {
        try {
            runSuspendCatching { throw CancellationException("cancelled") }
            fail("Expected cancellation to escape the result wrapper.")
        } catch (error: CancellationException) {
            assertEquals("cancelled", error.message)
        }
    }
}
