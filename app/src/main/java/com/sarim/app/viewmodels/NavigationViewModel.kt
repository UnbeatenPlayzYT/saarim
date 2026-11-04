package com.sarim.app.viewmodels

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel

/**
 * Screen stack behind [com.sarim.app.activities.MainActivity]'s single-Activity routing.
 *
 * The start screen is a root tab, not a removed route: an unknown screen name has no branch in
 * MainActivity's content `when`, which is exactly how a finished setup ended up on a blank page.
 */
class NavigationViewModel : ViewModel() {
    private val stack = mutableStateListOf(DEFAULT_SCREEN)

    fun selectedTab() = stack.lastOrNull() ?: DEFAULT_SCREEN

    fun navigateTo(screen: String) {
        if (screen in ROOT_SCREENS) {
            stack.clear()
        }
        stack.add(screen)
    }

    fun goBack(): Boolean {
        if (stack.size > 1) {
            stack.removeAt(stack.size - 1)
            return true
        }
        return false
    }

    companion object {
        const val SCREEN_PROTECTION = "protection"
        const val SCREEN_APPS = "apps"
        const val SCREEN_CONFIG = "config"

        const val SCREEN_SETUP_DEVICE_OWNER = "setup_device_owner"
        const val SCREEN_SYSTEM_RESTRICTIONS = "system_restrictions"
        const val SCREEN_BROWSER_RESTRICTIONS = "browser_restrictions"
        const val SCREEN_BLOCKED_WORDS = "blocked_words"
        const val SCREEN_VISUAL_BLOCKING = "visual_blocking"

        /** Screens the bottom bar switches between; navigating to one clears the back stack. */
        val ROOT_SCREENS = listOf(SCREEN_PROTECTION, SCREEN_APPS, SCREEN_CONFIG)

        val DEFAULT_SCREEN = SCREEN_PROTECTION
    }
}
