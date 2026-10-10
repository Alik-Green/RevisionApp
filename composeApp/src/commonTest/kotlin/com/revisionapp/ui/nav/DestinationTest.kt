package com.revisionapp.ui.nav

import com.revisionapp.ui.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DestinationTest {
    @Test
    fun storeIsATopLevelTabAndAchievementsRemainUnderProgress() {
        assertEquals("Store", Destination.of(Route.CourseStore)?.label)
        assertEquals(Destination.Store, Destination.owning(Route.CourseStore))
        assertNull(Destination.of(Route.Achievements))
        assertEquals(Destination.Progression, Destination.owning(Route.Achievements))
    }
}
