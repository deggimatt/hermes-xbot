package com.uzairansar.hermex.ui

import com.uzairansar.hermex.core.model.ProfileSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProfileShortcutPolicyTest {
    @Test
    fun publishesStableDistinctNamedProfilesWithinLauncherCapacity() {
        val specs = ProfileShortcutPolicy.specs(
            profiles = listOf(
                ProfileSummary(name = "default", displayName = "Default"),
                ProfileSummary(name = "review", displayName = "Review"),
                ProfileSummary(name = "REVIEW", displayName = "Duplicate"),
                ProfileSummary(displayName = "Missing identifier"),
            ),
            maximumCount = 2,
        )

        assertEquals(listOf("default", "review"), specs.map { it.profileName })
        assertEquals(listOf("Default", "Review"), specs.map { it.title })
        assertEquals(specs.map { it.id }, ProfileShortcutPolicy.specs(listOf(
            ProfileSummary(name = "default"),
            ProfileSummary(name = "review"),
        ), 2).map { it.id })
    }

    @Test
    fun javaHashCollisionsStillProduceDistinctShortcutIds() {
        val specs = ProfileShortcutPolicy.specs(
            listOf(ProfileSummary(name = "Aa"), ProfileSummary(name = "BB")),
            maximumCount = 2,
        )

        assertNotEquals(specs[0].id, specs[1].id)
    }
}
