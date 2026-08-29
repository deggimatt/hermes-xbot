package com.uzairansar.hermex.ui

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedStatePolicyTest {
    @Test
    fun oversizedEncodedStateIsDroppedBeforeItCanEnterTheActivityBundle() {
        val handle = SavedStateHandle(mapOf("state" to "previous"))

        handle.setBoundedEncodedState(
            "state",
            "x".repeat(SavedStatePolicy.MaximumEncodedStateCharacters + 1),
        )

        assertNull(handle.get<String>("state"))
    }

    @Test
    fun inputStateIsCappedAtTheRequestedLimit() {
        val handle = SavedStateHandle()

        handle.setBoundedString("query", "123456", maximumCharacters = 4)

        assertEquals("1234", handle.get<String>("query"))
    }
}
