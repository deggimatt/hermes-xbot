package com.uzairansar.hermex.ui.chat

import com.uzairansar.hermex.core.model.ModelSummary

internal fun List<ModelSummary>.firstMatchingCatalogModel(
    model: String?,
    provider: String?,
): ModelSummary? {
    val targetModel = model.normalizedCatalogValue() ?: return null
    val candidates = filter { option ->
        listOfNotNull(option.id, option.name)
            .any { value -> value.equals(targetModel, ignoreCase = true) }
    }
    if (candidates.isEmpty()) return null

    val targetProvider = provider.normalizedCatalogValue() ?: return candidates.first()
    return candidates.firstOrNull { option ->
        option.provider.normalizedCatalogValue()?.equals(targetProvider, ignoreCase = true) == true
    } ?: candidates.singleOrNull()
}

private fun String?.normalizedCatalogValue(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }
