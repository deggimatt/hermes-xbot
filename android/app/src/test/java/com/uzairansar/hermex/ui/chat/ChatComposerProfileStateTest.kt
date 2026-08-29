package com.uzairansar.hermex.ui.chat

import com.uzairansar.hermex.core.model.ProfileSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatComposerProfileStateTest {
    @Test
    fun reportsAttachmentUploadsUntilEveryUploadCompletes() {
        assertFalse(ChatUiState().isUploadingAttachment)
        assertTrue(ChatUiState(attachmentUploadsInFlight = 2).isUploadingAttachment)
    }

    @Test
    fun profileControlIsHiddenForSingleProfileMode() {
        val state = ChatUiState(
            profileOptions = listOf(ProfileSummary(name = "default")),
            isSingleProfileMode = true,
        )

        assertFalse(state.showsProfileControl)
    }

    @Test
    fun profileControlRequiresMoreThanImplicitServerProfile() {
        assertFalse(ChatUiState(profileOptions = emptyList()).showsProfileControl)

        val state = ChatUiState(
            profileOptions = listOf(ProfileSummary(name = "default"), ProfileSummary(name = "work")),
            isSingleProfileMode = false,
        )

        assertTrue(state.showsProfileControl)
    }

    @Test
    fun persistedConversationRequiresNewSessionConfirmationForProfileChange() {
        assertFalse(ChatProfileSwitchPolicy.requiresNewSessionConfirmation(hasPersistedConversation = false))
        assertTrue(ChatProfileSwitchPolicy.requiresNewSessionConfirmation(hasPersistedConversation = true))
    }
}
