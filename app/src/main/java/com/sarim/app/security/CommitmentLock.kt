package com.sarim.app.security

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit

/**
 * The commitment: the period the user picks during setup, before anything else is configured,
 * during which adult-content protection cannot be turned off, weakened or removed.
 *
 * The floor is [MIN_DURATION_DAYS] days — a commitment that can be shortened to a weekend is not a
 * commitment — and the ceiling is "permanent", for the user who wants the app to be a one-way
 * decision. [start] is the only way in, and there is no `stop`: not the recovery code, not the
 * settings screens, not a preference write. The two ways out are the period running out, or the
 * device-owner 48-hour forced removal, which takes the app off the device entirely and is
 * deliberately unavailable while a permanent commitment is running.
 *
 * Time is measured the way the rest of the app measures a deadline, because this is exactly the
 * kind of thing someone will try to shorten by changing the device date:
 *  - `elapsedRealtime` never moves when the wall clock does, so it is authoritative while the
 *    device stays up;
 *  - `System.currentTimeMillis` is the only thing that survives a reboot, so it takes over then;
 *  - a persisted watermark records the furthest wall-clock instant ever observed, so moving the
 *    date *backwards* cannot inflate the remaining time either.
 *
 * Not a security boundary against a rooted device or a factory reset — no on-device app is — but
 * [enforce] re-asserts the whole protection stack on every launch, on every service start and on
 * every settings write, and [MandatoryProtection] additionally locks the system doors that lead
 * around it.
 */
object CommitmentLock {

    const val MIN_DURATION_DAYS = 90
    const val MAX_DURATION_DAYS = 3650

    /** Offered in the duration picker; "permanent" is a separate choice, not a number of days. */
    val DURATION_PRESETS_DAYS = listOf(90, 180, 365, 730)

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Stored duration of a commitment that never ends. */
    private const val PERMANENT = -1L

    /**
     * How far the wall clock may run ahead of the stored watermark before it is rewritten. The
     * watermark only matters across a reboot, so coalescing it keeps a per-second UI tick from
     * writing a preference every second, at the cost of at most this much slack if the date is
     * rewound — against a deadline measured in months.
     */
    private const val WATERMARK_INTERVAL_MS = 60L * 60 * 1000

    private const val PREFS_NAME = "security_prefs"
    private const val KEY_START_RTC = "commitment_start_rtc"
    private const val KEY_START_ELAPSED = "commitment_start_elapsed"
    private const val KEY_DURATION_MS = "commitment_duration_ms"
    private const val KEY_LAST_SEEN_RTC = "commitment_last_seen_rtc"

    /** Keys written by the previous, optional strictness lock; read so an upgrade keeps its deadline. */
    private const val LEGACY_KEY_START_RTC = "strictness_lock_start_rtc"
    private const val LEGACY_KEY_START_ELAPSED = "strictness_lock_start_elapsed"
    private const val LEGACY_KEY_DURATION_MS = "strictness_lock_duration_ms"
    private const val LEGACY_KEY_LAST_SEEN_RTC = "strictness_lock_last_seen_rtc"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** [days] limited to [MIN_DURATION_DAYS]..[MAX_DURATION_DAYS]; the floor is what makes it a commitment. */
    fun clampDurationDays(days: Int): Int = days.coerceIn(MIN_DURATION_DAYS, MAX_DURATION_DAYS)

    /**
     * Starts a commitment of [durationDays], or a permanent one when it is null. A running
     * commitment is never restarted or shortened — callers get the existing deadline back
     * instead. Returns the wall-clock instant it runs out, or 0 for a permanent commitment.
     */
    fun start(context: Context, durationDays: Int?): Long {
        if (hasStarted(context)) return endTimeMillis(context)

        val duration = durationDays?.let { clampDurationDays(it) * DAY_MS } ?: PERMANENT
        val nowRtc = System.currentTimeMillis()
        prefs(context).edit {
            putLong(KEY_START_RTC, nowRtc)
            putLong(KEY_START_ELAPSED, SystemClock.elapsedRealtime())
            putLong(KEY_DURATION_MS, duration)
            putLong(KEY_LAST_SEEN_RTC, nowRtc)
            remove(LEGACY_KEY_START_RTC)
            remove(LEGACY_KEY_START_ELAPSED)
            remove(LEGACY_KEY_DURATION_MS)
            remove(LEGACY_KEY_LAST_SEEN_RTC)
        }
        MandatoryProtection.enforce(context)
        return endTimeMillis(context)
    }

    /** True once a commitment has ever been started, whether or not it is still running. */
    fun hasStarted(context: Context): Boolean {
        val prefs = prefs(context)
        if (prefs.contains(KEY_DURATION_MS)) return prefs.getLong(KEY_DURATION_MS, 0L) != 0L
        // Upgrading from the old optional lock: adopt its deadline rather than starting a new one.
        val legacyDuration = prefs.getLong(LEGACY_KEY_DURATION_MS, 0L)
        if (legacyDuration <= 0L) return false
        prefs.edit {
            putLong(KEY_DURATION_MS, legacyDuration)
            putLong(KEY_START_RTC, prefs.getLong(LEGACY_KEY_START_RTC, 0L))
            putLong(KEY_START_ELAPSED, prefs.getLong(LEGACY_KEY_START_ELAPSED, 0L))
            putLong(KEY_LAST_SEEN_RTC, prefs.getLong(LEGACY_KEY_LAST_SEEN_RTC, 0L))
        }
        return true
    }

    /** True when the commitment never ends. */
    fun isPermanent(context: Context): Boolean =
        hasStarted(context) && prefs(context).getLong(KEY_DURATION_MS, 0L) == PERMANENT

    fun isLocked(context: Context): Boolean = hasStarted(context) && remainingMillis(context) > 0L

    /** The configured period in days, or 0 when there is no commitment or it is permanent. */
    fun durationDays(context: Context): Int {
        val duration = prefs(context).getLong(KEY_DURATION_MS, 0L)
        return if (duration <= 0L) 0 else (duration / DAY_MS).toInt()
    }

    /** Wall-clock instant the commitment runs out, or 0 when it is permanent or not started. */
    fun endTimeMillis(context: Context): Long {
        val prefs = prefs(context)
        val duration = prefs.getLong(KEY_DURATION_MS, 0L)
        if (duration <= 0L) return 0L
        return prefs.getLong(KEY_START_RTC, 0L) + duration
    }

    /** Milliseconds left, [Long.MAX_VALUE] for a permanent commitment, or 0 when none is running. */
    fun remainingMillis(context: Context): Long {
        val prefs = prefs(context)
        val duration = prefs.getLong(KEY_DURATION_MS, 0L)
        if (duration == PERMANENT) return Long.MAX_VALUE
        if (duration <= 0L) return 0L

        val startRtc = prefs.getLong(KEY_START_RTC, 0L)
        val startElapsed = prefs.getLong(KEY_START_ELAPSED, 0L)
        val endRtc = startRtc + duration

        val nowRtc = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()

        // The furthest wall-clock instant ever observed, capped at the deadline: a clock pushed
        // wildly into the future must not be able to make the wall-clock reading negative, because
        // that would end the commitment early the next time the device rebooted onto the right time.
        val lastSeenRtc = prefs.getLong(KEY_LAST_SEEN_RTC, 0L)
        if (nowRtc - lastSeenRtc > WATERMARK_INTERVAL_MS) {
            prefs.edit { putLong(KEY_LAST_SEEN_RTC, nowRtc) }
        }
        val trustedRtc = maxOf(nowRtc, lastSeenRtc).coerceAtMost(endRtc)

        val remaining = if (nowElapsed < startElapsed) {
            // elapsedRealtime restarted under us: the device rebooted, so the wall clock is all
            // there is to go on.
            endRtc - trustedRtc
        } else {
            // Never let a wall-clock jump shorten the commitment below what the monotonic clock says.
            maxOf(startElapsed + duration - nowElapsed, endRtc - trustedRtc)
        }

        return remaining.coerceAtLeast(0L)
    }

    /**
     * Re-asserts everything the commitment pins. [MandatoryProtection] is the whole stack now
     * (visual blocking, forbidden words, browser lists, filtering DNS, Tor/torrents and the
     * system restrictions), and it is idempotent, so this is safe to call on every launch, every
     * service start and from a preference listener.
     */
    fun enforce(context: Context): Boolean = MandatoryProtection.enforce(context)
}
