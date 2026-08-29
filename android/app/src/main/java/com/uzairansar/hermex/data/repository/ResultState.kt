package com.uzairansar.hermex.data.repository

sealed interface ResultState<out T> {
    data object Loading : ResultState<Nothing>
    data class Data<T>(val value: T, val fromCache: Boolean = false) : ResultState<T>
    data class Error(val message: String, val throwable: Throwable? = null) : ResultState<Nothing>
}

fun Throwable.userMessage(): String =
    message
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(MAXIMUM_USER_MESSAGE_CHARACTERS)
        ?.takeIf { it.isNotEmpty() }
        ?: "Something went wrong."

private const val MAXIMUM_USER_MESSAGE_CHARACTERS = 1_000
