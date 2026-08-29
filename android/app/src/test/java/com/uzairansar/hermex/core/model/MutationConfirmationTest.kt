package com.uzairansar.hermex.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MutationConfirmationTest {
    @Test
    fun emptyResponsesNeverConfirmMutations() {
        assertFalse(SessionMutationResponse().isConfirmedMutation())
        assertFalse(SessionClearResponse().isConfirmedMutation())
        assertFalse(SessionCompressResponse().isConfirmedMutation())
        assertFalse(SessionUndoResponse().isConfirmedMutation())
        assertFalse(SessionRetryResponse().isConfirmedMutation())
        assertFalse(ProjectMutationResponse().isConfirmedMutation())
        assertFalse(CronMutationResponse().isConfirmedMutation())
        assertFalse(ChatCancelResponse().isConfirmedMutation())
        assertFalse(GoalSubmissionResponse().isConfirmedMutation())
        assertFalse(PersonalitySetResponse().isConfirmedPersonalityMutation("focused"))
        assertFalse(ApprovalRespondResponse().isConfirmedMutation())
        assertFalse(SessionYoloResponse().isConfirmedYoloMutation(enabled = true))
        assertFalse(ClarificationRespondResponse().isConfirmedClarification("answer"))
        assertFalse(DefaultModelResponse().isConfirmedDefaultModel("openai-codex"))
        assertFalse(ProfileSwitchResponse().isConfirmedProfileSwitch("review"))
        assertFalse(ProfileCreateResponse().isConfirmedMutation())
        assertFalse(MemoryWriteResponse().isConfirmedMutation())
        assertFalse(SettingsResponse().isConfirmedShowCliSessions(enabled = true))
    }

    @Test
    fun requiredPayloadsConfirmLegacyResponsesWithoutOk() {
        assertTrue(SessionMutationResponse(session = SessionSummary(sessionId = "s1")).isConfirmedMutation())
        assertTrue(SessionClearResponse(session = SessionDetail(sessionId = "s1")).isConfirmedMutation())
        assertTrue(SessionCompressResponse(session = SessionDetail(sessionId = "s1")).isConfirmedMutation())
        assertTrue(ProjectMutationResponse(project = ProjectSummary(projectId = "p1")).isConfirmedMutation())
        assertTrue(CronMutationResponse(jobId = "job-1").isConfirmedMutation())
    }

    @Test
    fun explicitSuccessConfirmsAndErrorsAlwaysReject() {
        assertTrue(SessionMutationResponse(ok = true).isConfirmedMutation())
        assertTrue(SessionUndoResponse(ok = true).isConfirmedMutation())
        assertTrue(SessionRetryResponse(ok = true).isConfirmedMutation())
        assertFalse(SessionMutationResponse(ok = true, error = "failed").isConfirmedMutation())
        assertTrue(ChatCancelResponse(ok = true, cancelled = true).isConfirmedMutation())
        assertTrue(GoalSubmissionResponse(ok = true, action = "set").isConfirmedMutation())
        assertTrue(PersonalitySetResponse(ok = true, personality = "focused").isConfirmedPersonalityMutation("focused"))
        assertTrue(PersonalitySetResponse(ok = true).isConfirmedPersonalityMutation(""))
        assertTrue(ApprovalRespondResponse(ok = true).isConfirmedMutation())
        assertTrue(ApprovalRespondResponse(staleCleared = true).isConfirmedMutation())
        assertTrue(SessionYoloResponse(ok = true, yoloEnabled = true).isConfirmedYoloMutation(enabled = true))
        assertTrue(ClarificationRespondResponse(ok = true, response = "answer").isConfirmedClarification("answer"))
        assertTrue(DefaultModelResponse(ok = true, model = "gpt-5", provider = "openai-codex").isConfirmedDefaultModel("openai-codex"))
        assertTrue(ProfileSwitchResponse(active = "review").isConfirmedProfileSwitch("review"))
        assertTrue(ProfileCreateResponse(ok = true).isConfirmedMutation())
        assertTrue(MemoryWriteResponse(ok = true).isConfirmedMutation())
        assertTrue(SettingsResponse(showCliSessions = true).isConfirmedShowCliSessions(enabled = true))

        assertFalse(PersonalitySetResponse(ok = true, personality = "other").isConfirmedPersonalityMutation("focused"))
        assertFalse(SessionYoloResponse(ok = true, yoloEnabled = false).isConfirmedYoloMutation(enabled = true))
        assertFalse(ClarificationRespondResponse(ok = true, response = "different").isConfirmedClarification("answer"))
        assertFalse(DefaultModelResponse(ok = true, model = "gpt-5", provider = "openai").isConfirmedDefaultModel("openai-codex"))
        assertFalse(ProfileSwitchResponse(active = "default").isConfirmedProfileSwitch("review"))
    }
}
