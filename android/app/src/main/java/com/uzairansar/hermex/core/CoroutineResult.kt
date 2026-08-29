package com.uzairansar.hermex.core

import kotlinx.coroutines.CancellationException

internal suspend inline fun <T> runSuspendCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
