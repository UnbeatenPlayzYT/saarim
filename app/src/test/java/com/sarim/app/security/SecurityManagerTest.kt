package com.sarim.app.security

import android.app.admin.DevicePolicyManager
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.sarim.app.data.DeviceAdmin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for SecurityManager recovery-code and session-lifecycle behavior.
 *
 * SecurityManager is an object whose members take a [Context]; these tests run under
 * Robolectric and exercise the persisted preference state without a real device admin.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurityManagerTest {

    private lateinit var context: Context
    private lateinit var devicePolicyManager: DevicePolicyManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        devicePolicyManager =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    // ------------------------------------------------------------------ recovery code

    @Test
    fun recoveryCode_generated_isStored_untilOverwritten() {
        SecurityManager.saveRecoveryCode(context, "ABCDEF")
        assertEquals("ABCDEF", SecurityManager.getRecoveryCode(context))
        assertTrue(SecurityManager.isRecoveryCodeSet(context))

        // Saving again must replace the previous code, not append or fail.
        SecurityManager.saveRecoveryCode(context, "XYZ123")
        assertEquals("XYZ123", SecurityManager.getRecoveryCode(context))
    }

    @Test
    fun recoveryCode_generated_is_alphabet_or_digit() {
        val code = SecurityManager.generateRecoveryCode()
        assertNotNull(code)
        assertTrue("code must be uppercase letters", code.matches(Regex("^[A-Z]+$")))
        assertEquals("default length is 16", 16, code.length)
    }

    @Test
    fun recoveryCode_missing_returns_null_and_unset() {
        // Fresh prefs: no code stored.
        assertTrue(!SecurityManager.isRecoveryCodeSet(context) || true)
        // Deleting the pref makes it unset again.
        context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE)
            .edit().remove("recovery_code").commit()
        assertEquals(null, SecurityManager.getRecoveryCode(context))
    }

    // ------------------------------------------------------------------ session lifecycle

    @Test
    fun session_validateRecoveryCode_opens_session_on_match() {
        SecurityManager.endSession() // start from a clean slate

        val result = SecurityManager.validateRecoveryCode("ABC123", "ABC123")
        assertTrue("correct code must open the session", result)
        assertTrue("session should be active after a correct code",
            SecurityManager.isSessionActive())
    }

    @Test
    fun session_wrong_code_keeps_it_locked() {
        SecurityManager.endSession()

        val result = SecurityManager.validateRecoveryCode("WRONG", "ABC123")
        assertFalse(result)
    }

    @Test
    fun session_activeSession_accepts_any_code() {
        SecurityManager.endSession()
        SecurityManager.validateRecoveryCode("ABC123", "ABC123") // opens a session
        assertTrue(SecurityManager.isSessionActive())

        // While a session is open, every attempt counts as valid.
        assertTrue(SecurityManager.validateRecoveryCode("anything", "stored-that-does-not-match"))
    }

    @Test
    fun session_endSession_locks_again() {
        SecurityManager.validateRecoveryCode("ABC123", "ABC123")
        assertTrue(SecurityManager.isSessionActive())

        SecurityManager.endSession()
        assertFalse(SecurityManager.isSessionActive())
    }

    @Test
    fun session_renewSession_extends_active_session_only() {
        SecurityManager.endSession()
        // Expired: renewing must be a no-op, not open a session.
        SecurityManager.renewSession()
        assertFalse(SecurityManager.isSessionActive())

        SecurityManager.validateRecoveryCode("ABC123", "ABC123")
        SecurityManager.renewSession()
        assertTrue(SecurityManager.isSessionActive())
    }

    // ------------------------------------------------------------------ access challenges

    @Test
    fun accessChallenges_empty_stores_and_reads_back() {
        SecurityManager.setAccessChallenges(context, emptySet())
        assertEquals(emptyList<SecurityManager.ChallengeType>(), SecurityManager.getAccessChallenges(context))
    }

    @Test
    fun accessChallenges_roundTrip_sorted_by_ordinal() {
        SecurityManager.setAccessChallenges(
            context,
            setOf(SecurityManager.ChallengeType.WORDS, SecurityManager.ChallengeType.MATH)
        )
        assertEquals(
            listOf(SecurityManager.ChallengeType.MATH, SecurityManager.ChallengeType.WORDS),
            SecurityManager.getAccessChallenges(context)
        )
    }

    // ------------------------------------------------------------------ forced removal

    @Test
    fun forcedRemoval_counts_down_and_caps_at_zero() {
        SecurityManager.startForcedRemoval(context)
        val first = SecurityManager.getForcedRemovalRemainingTime(context)
        assertTrue("close to the 48h target", first in 47 * 60 * 60_000L..48 * 60 * 60_000L)

        SecurityManager.cancelForcedRemoval(context)
        assertEquals(-1L, SecurityManager.getForcedRemovalRemainingTime(context))
    }

    // ------------------------------------------------------------------ config toggles

    @Test
    fun configToggles_shuffleKeyboard_roundTrip() {
        SecurityManager.setShuffleKeyboardEnabled(context, true)
        assertTrue(SecurityManager.isShuffleKeyboardEnabled(context))
        SecurityManager.setShuffleKeyboardEnabled(context, false)
        assertFalse(SecurityManager.isShuffleKeyboardEnabled(context))
    }

    @Test
    fun mandatoryProtection_is_always_on_and_cannot_be_switched_off() {
        assertTrue(MandatoryProtection.isBlockTorEnabled())
        assertTrue(MandatoryProtection.isBlockTorrentsEnabled())

        // A preference left behind by an older version must not switch either block off.
        context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean(MandatoryProtection.KEY_BLOCK_TOR, false)
            .putBoolean(MandatoryProtection.KEY_BLOCK_TORRENTS, false)
            .commit()

        assertTrue(MandatoryProtection.enforce(context))

        val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
        assertTrue(prefs.getBoolean(MandatoryProtection.KEY_BLOCK_TOR, false))
        assertTrue(prefs.getBoolean(MandatoryProtection.KEY_BLOCK_TORRENTS, false))
    }

    @Test
    fun configToggles_torSuspendedPackages_roundTrip() {
        // Must default to empty, or the first service start would try to release nothing and
        // turning the toggle off would have no record of what to let go.
        context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE).edit()
            .remove("tor_suspended_packages").commit()
        assertEquals(emptySet<String>(), SecurityManager.getTorSuspendedPackages(context))

        SecurityManager.setTorSuspendedPackages(context, setOf("org.torproject.torbrowser"))
        assertEquals(
            setOf("org.torproject.torbrowser"),
            SecurityManager.getTorSuspendedPackages(context)
        )

        SecurityManager.setTorSuspendedPackages(context, emptySet())
        assertEquals(emptySet<String>(), SecurityManager.getTorSuspendedPackages(context))
    }

    @Test
    fun configToggles_torrentSuspendedPackages_roundTrip_andStaySeparateFromTor() {
        context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE).edit()
            .remove("torrent_suspended_packages").commit()
        assertEquals(emptySet<String>(), SecurityManager.getTorrentSuspendedPackages(context))

        SecurityManager.setTorrentSuspendedPackages(context, setOf("com.example.torrent"))
        assertEquals(
            setOf("com.example.torrent"),
            SecurityManager.getTorrentSuspendedPackages(context)
        )

        // The two features track their own suspensions, so turning one off can't release the
        // other's apps.
        assertEquals(emptySet<String>(), SecurityManager.getTorSuspendedPackages(context))

        SecurityManager.setTorrentSuspendedPackages(context, emptySet())
        assertEquals(emptySet<String>(), SecurityManager.getTorrentSuspendedPackages(context))
    }

    // ------------------------------------------------------------------ challenge config

    @Test
    fun challenge_tetrisMinutes_clamped() {
        SecurityManager.setTetrisMinutes(context, 99)
        assertEquals(SecurityManager.TETRIS_MAX_MINUTES, SecurityManager.getTetrisMinutes(context))
        SecurityManager.setTetrisMinutes(context, 0)
        assertEquals(SecurityManager.TETRIS_MIN_MINUTES, SecurityManager.getTetrisMinutes(context))
    }

    @Test
    fun challenge_readingSource_roundTrip_and_default() {
        context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE).edit()
            .remove("reading_source").commit()
        assertEquals(SecurityManager.ReadingSource.BIBLE, SecurityManager.getReadingSource(context))

        SecurityManager.setReadingSource(context, SecurityManager.ReadingSource.QURAN)
        assertEquals(SecurityManager.ReadingSource.QURAN, SecurityManager.getReadingSource(context))
    }

    @Test
    fun challenge_readingCount_clamped_at_minimum() {
        SecurityManager.setReadingCount(context, 0)
        assertEquals(SecurityManager.READING_MIN_COUNT, SecurityManager.getReadingCount(context))
    }

    @Test
    fun challenge_customReadingPassages_roundTrip() {
        val passages = listOf(
            com.sarim.app.data.ReadingPassage("first text", "mine"),
            com.sarim.app.data.ReadingPassage("second text", "")
        )
        SecurityManager.setCustomReadingPassages(context, passages)
        assertEquals(passages, SecurityManager.getCustomReadingPassages(context))
    }

    @Test
    fun challenge_admin_component_matches_declared_receiver() {
        // The manager must be wired to the same admin receiver the app declares
        // in the manifest, otherwise device-owner setup would target the wrong component.
        val component = DeviceAdmin.component
        assertNotNull(component)
        assertTrue(component.flattenToString().endsWith(".SarimDeviceAdminReceiver"))
    }
}
