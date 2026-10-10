package com.revisionapp.ui.theme

import com.revisionapp.ui.SettingsUi
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeDefaultsTest {
    @Test
    fun newAndUnconfiguredProfilesFollowTheSystemAppearance() {
        assertEquals(ThemeMode.SYSTEM, SettingsUi.initial().themeMode)
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
    }

    @Test
    fun anExplicitlyStoredAppearanceChoiceIsPreserved() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("SYSTEM"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStored("DARK"))
    }
}
