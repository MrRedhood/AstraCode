package com.mrredhood.astracode

import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationTest {
    @Test
    fun proposedPrimaryNavigationHasFiveDestinations() {
        val destinations = listOf("Chat", "Code", "Git", "Build", "More")
        assertEquals(5, destinations.size)
        assertEquals("Chat", destinations.first())
        assertEquals("More", destinations.last())
    }
}
