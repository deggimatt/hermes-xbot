package com.uzairansar.hermex.ui.chat

import org.junit.Assert.assertNotEquals
import org.junit.Test

class StreamStatusNotifierIdTest {
    @Test
    fun identifiersDoNotPreserveKnownJavaHashCollisions() {
        val server = "https://example.com/"

        assertNotEquals(
            notificationIdentifier("stream", server, "Aa"),
            notificationIdentifier("stream", server, "BB"),
        )
    }

    @Test
    fun namespacesProduceDifferentIdentifiers() {
        assertNotEquals(
            notificationIdentifier("stream", "server", "session"),
            notificationIdentifier("complete", "server", "session"),
        )
    }
}
