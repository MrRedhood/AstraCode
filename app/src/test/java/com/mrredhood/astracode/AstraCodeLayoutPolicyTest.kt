package com.mrredhood.astracode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraCodeLayoutPolicyTest {
    @Test
    fun selectsBottomNavigationBelowTheRailBreakpoint() {
        assertTrue(AstraCodeLayoutPolicy.usesBottomNavigation(320f))
        assertTrue(AstraCodeLayoutPolicy.usesBottomNavigation(599.9f))
        assertEquals(AstraCodeNavigationMode.BottomBar, AstraCodeLayoutPolicy.navigationMode(599.9f))
    }

    @Test
    fun selectsNavigationRailAtAndAboveTheBreakpoint() {
        assertFalse(AstraCodeLayoutPolicy.usesBottomNavigation(600f))
        assertFalse(AstraCodeLayoutPolicy.usesBottomNavigation(840f))
        assertEquals(AstraCodeNavigationMode.NavigationRail, AstraCodeLayoutPolicy.navigationMode(600f))
    }
}
