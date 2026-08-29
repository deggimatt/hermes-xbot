package com.uzairansar.hermex.ui.git

import com.uzairansar.hermex.core.model.GitCheckoutResponse
import com.uzairansar.hermex.core.model.GitCommitMessageResponse
import com.uzairansar.hermex.core.model.GitCommitResponse
import com.uzairansar.hermex.core.model.GitMutationResponse

internal fun GitMutationResponse.failureMessage(fallback: String): String? =
    error.nonBlankOrNull() ?: fallback.takeUnless {
        ok != false && (ok == true || status != null || git != null || message.nonBlankOrNull() != null)
    }

internal fun GitCommitResponse.failureMessage(fallback: String): String? =
    error.nonBlankOrNull() ?: fallback.takeUnless {
        ok != false && (ok == true || sha.nonBlankOrNull() != null || shortSha.nonBlankOrNull() != null)
    }

internal fun GitCommitMessageResponse.failureMessage(fallback: String): String? =
    error.nonBlankOrNull() ?: fallback.takeUnless {
        ok != false && (ok == true || message.nonBlankOrNull() != null)
    }

internal fun GitCheckoutResponse.failureMessage(fallback: String): String? =
    error.nonBlankOrNull()
        ?: restoreError.nonBlankOrNull()
        ?: "The server could not restore the stashed changes.".takeIf { restoreFailed == true }
        ?: fallback.takeUnless {
            ok != false && (ok == true || status != null || git != null || branches != null || currentBranch.nonBlankOrNull() != null)
        }

private fun String?.nonBlankOrNull(): String? = this?.trim()?.take(MAXIMUM_GIT_ERROR_CHARACTERS)?.takeIf { it.isNotEmpty() }

private const val MAXIMUM_GIT_ERROR_CHARACTERS = 1_000
