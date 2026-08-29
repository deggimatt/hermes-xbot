package com.uzairansar.hermex.ui.git

import com.uzairansar.hermex.core.model.GitCheckoutResponse
import com.uzairansar.hermex.core.model.GitCommitMessageResponse
import com.uzairansar.hermex.core.model.GitCommitResponse
import com.uzairansar.hermex.core.model.GitMutationResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitResponsePolicyTest {
    @Test
    fun explicitFalseIsAFailureEvenWithoutAnErrorString() {
        assertEquals("mutation failed", GitMutationResponse(ok = false).failureMessage("mutation failed"))
        assertEquals("commit failed", GitCommitResponse(ok = false).failureMessage("commit failed"))
        assertEquals("message failed", GitCommitMessageResponse(ok = false).failureMessage("message failed"))
        assertEquals("checkout failed", GitCheckoutResponse(ok = false).failureMessage("checkout failed"))
    }

    @Test
    fun serverErrorTakesPriorityAndAffirmativeLegacyResponsesPass() {
        assertEquals("server reason", GitMutationResponse(ok = false, error = " server reason ").failureMessage("fallback"))
        assertNull(GitMutationResponse(ok = true).failureMessage("fallback"))
        assertNull(GitMutationResponse(message = "Fetched").failureMessage("fallback"))
        assertEquals("fallback", GitMutationResponse().failureMessage("fallback"))
        assertEquals("fallback", GitCommitResponse().failureMessage("fallback"))
        assertEquals("fallback", GitCommitMessageResponse().failureMessage("fallback"))
        assertEquals("fallback", GitCheckoutResponse().failureMessage("fallback"))
    }

    @Test
    fun checkoutRestoreFailureCannotReportSuccess() {
        assertEquals(
            "The server could not restore the stashed changes.",
            GitCheckoutResponse(ok = true, restoreFailed = true).failureMessage("checkout failed"),
        )
    }
}
