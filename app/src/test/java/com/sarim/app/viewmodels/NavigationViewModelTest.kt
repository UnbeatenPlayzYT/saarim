package com.sarim.app.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app renders nothing for a route MainActivity doesn't know, so the start screen and every
 * path into the content `when` has to stay on a real route.
 */
class NavigationViewModelTest {

    @Test
    fun `starts on the protection tab`() {
        val navigation = NavigationViewModel()

        assertEquals(NavigationViewModel.SCREEN_PROTECTION, navigation.selectedTab())
    }

    @Test
    fun `default screen is one of the root tabs`() {
        assertTrue(NavigationViewModel.DEFAULT_SCREEN in NavigationViewModel.ROOT_SCREENS)
    }

    @Test
    fun `root screens match the three bottom bar tabs`() {
        assertEquals(
            listOf(
                NavigationViewModel.SCREEN_PROTECTION,
                NavigationViewModel.SCREEN_APPS,
                NavigationViewModel.SCREEN_CONFIG
            ),
            NavigationViewModel.ROOT_SCREENS
        )
    }

    @Test
    fun `sub screen keeps the root underneath so back returns to it`() {
        val navigation = NavigationViewModel()

        navigation.navigateTo(NavigationViewModel.SCREEN_BLOCKED_WORDS)
        assertEquals(NavigationViewModel.SCREEN_BLOCKED_WORDS, navigation.selectedTab())

        assertTrue(navigation.goBack())
        assertEquals(NavigationViewModel.SCREEN_PROTECTION, navigation.selectedTab())
    }

    @Test
    fun `switching root tab resets the back stack`() {
        val navigation = NavigationViewModel()

        navigation.navigateTo(NavigationViewModel.SCREEN_VISUAL_BLOCKING)
        navigation.navigateTo(NavigationViewModel.SCREEN_CONFIG)

        assertEquals(NavigationViewModel.SCREEN_CONFIG, navigation.selectedTab())
        assertFalse(navigation.goBack())
    }

    @Test
    fun `back on the start screen does nothing`() {
        val navigation = NavigationViewModel()

        assertFalse(navigation.goBack())
        assertEquals(NavigationViewModel.SCREEN_PROTECTION, navigation.selectedTab())
    }
}
