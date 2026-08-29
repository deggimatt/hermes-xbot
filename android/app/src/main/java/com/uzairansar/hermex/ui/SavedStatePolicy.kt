package com.uzairansar.hermex.ui

import androidx.lifecycle.SavedStateHandle

internal object SavedStatePolicy {
    const val MaximumIntentActionCharacters = 256
    const val MaximumNavigationUriCharacters = 8_192
    const val MaximumInputCharacters = 8_192
    const val MaximumSearchCharacters = 4_096
    const val MaximumEncodedStateCharacters = 64 * 1_024

    fun boundedInput(value: String, maximumCharacters: Int = MaximumInputCharacters): String =
        value.take(maximumCharacters)
}

internal fun SavedStateHandle.setBoundedString(
    key: String,
    value: String?,
    maximumCharacters: Int = SavedStatePolicy.MaximumInputCharacters,
) {
    if (value == null) {
        remove<String>(key)
    } else {
        set(key, value.take(maximumCharacters))
    }
}

internal fun SavedStateHandle.setBoundedEncodedState(key: String, encoded: String) {
    if (encoded.length <= SavedStatePolicy.MaximumEncodedStateCharacters) {
        set(key, encoded)
    } else {
        remove<String>(key)
    }
}
