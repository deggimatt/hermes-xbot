package com.uzairansar.hermex.ui.chat

import com.uzairansar.hermex.core.model.ModelSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelCatalogSelectionTest {
    @Test
    fun uniqueModelMatchSurvivesProviderAliasDifference() {
        val model = ModelSummary(
            id = "cx/gpt-5.6-sol",
            label = "GPT-5.6 Sol",
            provider = "custom:local-localhost-20128",
        )

        assertEquals(
            model,
            listOf(model).firstMatchingCatalogModel("cx/gpt-5.6-sol", "custom"),
        )
    }

    @Test
    fun exactProviderWinsWhenModelExistsAcrossProviders() {
        val openAi = ModelSummary(id = "shared-model", provider = "openai")
        val custom = ModelSummary(id = "shared-model", provider = "custom")

        assertEquals(
            custom,
            listOf(openAi, custom).firstMatchingCatalogModel("shared-model", "custom"),
        )
    }

    @Test
    fun unmatchedProviderDoesNotGuessBetweenAmbiguousModels() {
        val models = listOf(
            ModelSummary(id = "shared-model", provider = "openai"),
            ModelSummary(id = "shared-model", provider = "custom"),
        )

        assertNull(models.firstMatchingCatalogModel("shared-model", "unknown-provider"))
    }
}
