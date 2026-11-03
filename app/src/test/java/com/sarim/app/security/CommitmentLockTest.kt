package com.sarim.app.security

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.sarim.app.data.ProtectionDefaults
import com.sarim.app.data.VisualBlockingSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the commitment: the duration rules (90-day floor, permanent), and the
 * clock-tampering resistance of its deadline.
 *
 * The remaining time is recomputed from preferences rather than stored, so these tests rewind the
 * recorded start the same way a user moving the device date would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommitmentLockTest {

    private val dayMs = 24L * 60 * 60 * 1000

    private lateinit var context: Context

    private val securityPrefs get() =
        context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)

    private val blockingPrefs get() =
        context.getSharedPreferences(VisualBlockingSettings.PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        securityPrefs.edit { clear() }
        blockingPrefs.edit { clear() }
        context.getSharedPreferences("blocked_words_prefs", Context.MODE_PRIVATE).edit { clear() }
        context.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE).edit { clear() }
    }

    // ------------------------------------------------------------------ duration rules

    @Test
    fun duration_is_clamped_to_the_ninety_day_floor() {
        assertEquals(CommitmentLock.MIN_DURATION_DAYS, CommitmentLock.clampDurationDays(1))
        assertEquals(CommitmentLock.MIN_DURATION_DAYS, CommitmentLock.clampDurationDays(0))
        assertEquals(CommitmentLock.MIN_DURATION_DAYS, CommitmentLock.clampDurationDays(-30))
        assertEquals(CommitmentLock.MIN_DURATION_DAYS, CommitmentLock.clampDurationDays(89))
        assertEquals(90, CommitmentLock.clampDurationDays(90))
        assertEquals(180, CommitmentLock.clampDurationDays(180))
    }

    @Test
    fun duration_is_clamped_to_the_maximum() {
        assertEquals(CommitmentLock.MAX_DURATION_DAYS, CommitmentLock.clampDurationDays(999_999))
    }

    @Test
    fun start_below_the_floor_still_lasts_ninety_days() {
        CommitmentLock.start(context, 1)
        assertEquals(90, CommitmentLock.durationDays(context))
        assertTrue(CommitmentLock.isLocked(context))

        val remaining = CommitmentLock.remainingMillis(context)
        assertTrue("remaining should be close to 90 days", remaining in 89 * dayMs..90 * dayMs)
    }

    // ------------------------------------------------------------------ lifecycle

    @Test
    fun no_commitment_until_started() {
        assertFalse(CommitmentLock.isLocked(context))
        assertFalse(CommitmentLock.hasStarted(context))
        assertFalse(CommitmentLock.isPermanent(context))
        assertEquals(0L, CommitmentLock.remainingMillis(context))
        assertEquals(0L, CommitmentLock.endTimeMillis(context))
        assertEquals(0, CommitmentLock.durationDays(context))
    }

    @Test
    fun start_records_the_chosen_duration() {
        val end = CommitmentLock.start(context, 365)

        assertTrue(CommitmentLock.isLocked(context))
        assertTrue(CommitmentLock.hasStarted(context))
        assertFalse(CommitmentLock.isPermanent(context))
        assertEquals(365, CommitmentLock.durationDays(context))
        assertTrue(end > System.currentTimeMillis())
        assertEquals(end, CommitmentLock.endTimeMillis(context))
    }

    @Test
    fun a_running_commitment_is_never_restarted_or_shortened() {
        CommitmentLock.start(context, 90)
        val firstEnd = CommitmentLock.endTimeMillis(context)

        // A second start must not be a way to pick a different (or shorter) period.
        CommitmentLock.start(context, 365)

        assertEquals(90, CommitmentLock.durationDays(context))
        assertEquals(firstEnd, CommitmentLock.endTimeMillis(context))
    }

    @Test
    fun permanent_commitment_never_runs_out() {
        CommitmentLock.start(context, null)

        assertTrue(CommitmentLock.hasStarted(context))
        assertTrue(CommitmentLock.isPermanent(context))
        assertTrue(CommitmentLock.isLocked(context))
        assertEquals(Long.MAX_VALUE, CommitmentLock.remainingMillis(context))
        // There is no deadline to show, and no way to replace it with a timed one.
        assertEquals(0L, CommitmentLock.endTimeMillis(context))
        assertEquals(0, CommitmentLock.durationDays(context))

        CommitmentLock.start(context, 90)
        assertTrue(CommitmentLock.isPermanent(context))
    }

    @Test
    fun forced_removal_is_not_offered_for_a_permanent_commitment() {
        CommitmentLock.start(context, null)
        assertFalse(SecurityManager.canStartForcedRemoval(context))

        securityPrefs.edit { clear() }
        CommitmentLock.start(context, 90)
        assertTrue(SecurityManager.canStartForcedRemoval(context))
    }

    @Test
    fun elapsed_period_releases_the_commitment() {
        CommitmentLock.start(context, 90)

        // Rewind both clocks so the period has just run out.
        val expired = 91 * dayMs
        securityPrefs.edit {
            putLong("commitment_start_rtc", System.currentTimeMillis() - expired)
            putLong("commitment_start_elapsed", System.currentTimeMillis() - expired)
        }

        assertFalse(CommitmentLock.isLocked(context))
        assertEquals(0L, CommitmentLock.remainingMillis(context))
        // Still recorded as a past commitment, so the UI can say "your period ended".
        assertTrue(CommitmentLock.hasStarted(context))
    }

    @Test
    fun a_legacy_strictness_lock_is_adopted_as_the_commitment() {
        val startedAt = System.currentTimeMillis() - 10 * dayMs
        securityPrefs.edit {
            putLong("strictness_lock_start_rtc", startedAt)
            putLong("strictness_lock_start_elapsed", android.os.SystemClock.elapsedRealtime() - 10 * dayMs)
            putLong("strictness_lock_duration_ms", 90 * dayMs)
            putLong("strictness_lock_last_seen_rtc", startedAt)
        }

        assertTrue(CommitmentLock.hasStarted(context))
        assertEquals(90, CommitmentLock.durationDays(context))
        val remaining = CommitmentLock.remainingMillis(context)
        assertTrue("about 80 days should be left, got $remaining", remaining in 79 * dayMs..81 * dayMs)
    }

    // ------------------------------------------------------------------ clock tampering

    @Test
    fun moving_the_date_forward_does_not_shorten_the_commitment() {
        CommitmentLock.start(context, 90)

        // Wall clock claims the period started 91 days ago; elapsedRealtime says otherwise.
        securityPrefs.edit {
            putLong("commitment_start_rtc", System.currentTimeMillis() - 91 * dayMs)
        }

        val remaining = CommitmentLock.remainingMillis(context)
        assertTrue("the monotonic clock must keep the commitment alive", remaining > 89 * dayMs)
    }

    @Test
    fun a_reboot_falls_back_to_the_wall_clock() {
        CommitmentLock.start(context, 90)

        // elapsedRealtime going backwards is what a reboot looks like.
        securityPrefs.edit {
            putLong("commitment_start_elapsed", System.currentTimeMillis() + 60_000L)
        }

        val remaining = CommitmentLock.remainingMillis(context)
        assertTrue("the commitment must survive a reboot", remaining > 89 * dayMs)
    }

    @Test
    fun a_reboot_cannot_be_used_to_reset_the_date_backwards() {
        CommitmentLock.start(context, 90)

        // The device saw 30 days of the commitment pass, then rebooted with the date set back to
        // the start: the watermark is the only record of how much time was really served.
        val startRtc = securityPrefs.getLong("commitment_start_rtc", 0L)
        securityPrefs.edit {
            putLong("commitment_last_seen_rtc", startRtc + 30 * dayMs)
            putLong("commitment_start_elapsed", System.currentTimeMillis() + 60_000L)
        }

        val remaining = CommitmentLock.remainingMillis(context)
        assertTrue(
            "only the ~60 days left should remain, got $remaining",
            remaining in 59 * dayMs..61 * dayMs
        )
    }

    @Test
    fun an_absurd_forward_jump_cannot_make_the_remaining_time_negative() {
        CommitmentLock.start(context, 90)

        securityPrefs.edit {
            putLong("commitment_last_seen_rtc", System.currentTimeMillis() + 3650 * dayMs)
            putLong("commitment_start_elapsed", System.currentTimeMillis() + 60_000L)
        }

        assertEquals(0L, CommitmentLock.remainingMillis(context))
    }

    // ------------------------------------------------------------------ enforcement

    @Test
    fun enforce_re_asserts_protection_even_without_a_running_commitment() {
        // Nothing here is optional: the layers are on from the first launch, not from the moment
        // a commitment starts.
        blockingPrefs.edit {
            putBoolean(VisualBlockingSettings.KEY_ENABLED, false)
            putStringSet(VisualBlockingSettings.KEY_CATEGORIES, emptySet())
            putBoolean(VisualBlockingSettings.KEY_SUSPEND_ENABLED, false)
        }
        securityPrefs.edit { putBoolean("block_tor", false) }

        assertTrue(CommitmentLock.enforce(context))

        assertTrue(blockingPrefs.getBoolean(VisualBlockingSettings.KEY_ENABLED, false))
        assertTrue(blockingPrefs.getBoolean(VisualBlockingSettings.KEY_SUSPEND_ENABLED, false))
        assertTrue(
            blockingPrefs.getStringSet(VisualBlockingSettings.KEY_CATEGORIES, emptySet())!!
                .containsAll(VisualBlockingSettings.DEFAULT_CATEGORIES)
        )
        assertTrue(securityPrefs.getBoolean("block_tor", false))
    }

    @Test
    fun enforce_restores_the_built_in_forbidden_words() {
        val prefs = context.getSharedPreferences("blocked_words_prefs", Context.MODE_PRIVATE)
        prefs.edit { putStringSet("blocked_words", setOf("my own word")) }

        assertTrue(CommitmentLock.enforce(context))

        val words = prefs.getStringSet("blocked_words", emptySet())!!
        assertTrue(ProtectionDefaults.FORBIDDEN_WORDS.all { it in words })
        assertTrue("the user's own additions survive", "my own word" in words)
    }

    @Test
    fun enforce_keeps_categories_the_detector_additionally_supports() {
        blockingPrefs.edit {
            putStringSet(VisualBlockingSettings.KEY_CATEGORIES, setOf("drawings"))
        }

        CommitmentLock.enforce(context)

        val categories = blockingPrefs.getStringSet(VisualBlockingSettings.KEY_CATEGORIES, emptySet())!!
        assertTrue(categories.contains("drawings"))
        assertTrue(categories.containsAll(VisualBlockingSettings.DEFAULT_CATEGORIES))
    }

    @Test
    fun enforce_is_idempotent_so_it_cannot_loop_from_a_prefs_listener() {
        CommitmentLock.start(context, 90)
        // Starting a commitment already enforces; break one setting to give the pass work to do.
        blockingPrefs.edit { putBoolean(VisualBlockingSettings.KEY_ENABLED, false) }

        assertTrue("first pass has fixing to do", CommitmentLock.enforce(context))
        assertFalse("second pass must be a no-op", CommitmentLock.enforce(context))
    }
}
