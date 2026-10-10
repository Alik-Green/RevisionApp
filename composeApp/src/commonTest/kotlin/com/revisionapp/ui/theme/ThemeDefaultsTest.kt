package com.revisionapp.ui.theme

import com.revisionapp.ui.SettingsUi
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeDefaultsTest {
    @Test
    fun newAndUnconfiguredProfilesStartInLightMode() {
        assertEquals(ThemeMode.LIGHT, SettingsUi.initial().themeMode)
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStored(null))
    }

    @Test
    fun anExplicitlyStoredAppearanceChoiceIsPreserved() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("SYSTEM"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStored("DARK"))
    }
}
