package com.revisionapp.ui.screens

import com.revisionapp.domain.progression.AvatarPartCatalog
import com.revisionapp.domain.progression.AvatarPartCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AvatarFeatureGroupingTest {
    @Test
    fun hairAndEyeColoursStayUnderTheirOwnFeatures() {
        assertEquals(AvatarPartCategory.HAIR_COLOR, partCategoryFor(AvatarFeatureArea.HAIR, AvatarFeatureControl.COLOUR))
        assertEquals(AvatarPartCategory.EYE_COLOR, partCategoryFor(AvatarFeatureArea.EYES, AvatarFeatureControl.COLOUR))
        assertEquals(AvatarFeatureArea.HAIR, areaForCategory(AvatarPartCategory.HAIR_COLOR))
        assertEquals(AvatarFeatureArea.EYES, areaForCategory(AvatarPartCategory.EYE_COLOR))

        assertTrue(AvatarPartCatalog.inCategory(AvatarPartCategory.HAIR_COLOR).all { it.id.startsWith("hair-") })
        assertTrue(AvatarPartCatalog.inCategory(AvatarPartCategory.EYE_COLOR).all { it.id.startsWith("eyes-") })
    }
}
