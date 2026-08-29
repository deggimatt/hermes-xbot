package com.uzairansar.hermex.ui.chat

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceInputPolicyTest {
    @Test
    fun dictationPreservesTheExistingDraftAndReplacesPartialSpeech() {
        assertEquals("Existing draft spoken words", voiceDictationDraft("Existing draft", "spoken words"))
        assertEquals("Existing draft next result", voiceDictationDraft("Existing draft ", " next result "))
        assertEquals("spoken words", voiceDictationDraft("", " spoken words "))
    }

    @Test
    fun dictationKeepsTheDraftWhenRecognitionReturnsNothing() {
        assertEquals("Existing draft", voiceDictationDraft("Existing draft", "   "))
    }

    @Test
    fun speechErrorsHaveActionableMessages() {
        assertEquals(
            "Microphone permission is required for voice dictation.",
            voiceRecognitionErrorMessage(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS),
        )
        assertEquals(
            "Voice dictation could not reach the speech service.",
            voiceRecognitionErrorMessage(SpeechRecognizer.ERROR_NETWORK),
        )
    }
}
