package com.sarim.app.security

import android.content.Context
import android.os.SystemClock
import android.os.UserManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sarim.app.BuildConfig
import com.sarim.app.data.DeviceAdmin
import com.sarim.app.data.ReadingPassage
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import androidx.core.content.edit

class SecurityManager {

    enum class ChallengeType {
        MATH, WORDS, TETRIS, READING
    }

    /** Where the reading challenge takes its texts from. */
    enum class ReadingSource {
        BIBLE, QURAN, QUOTES, CUSTOM
    }

    companion object {
        private const val CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

        const val TETRIS_MIN_MINUTES = 1
        const val TETRIS_MAX_MINUTES = 15
        const val TETRIS_DEFAULT_MINUTES = 3

        const val READING_MIN_COUNT = 1
        const val READING_DEFAULT_COUNT = 5

        const val DEBUG_AUTO_START_SESSION_KEY = "debug_auto_start_session"

        private const val DEBUG_RESTORE_UNKNOWN_SOURCES_KEY = "debug_restore_unknown_sources_restriction"

        private val isRecoveryKeySet = mutableStateOf(false)
        
        var sessionEndTime by mutableLongStateOf(0L)
            private set

        fun isSessionActive(): Boolean = System.currentTimeMillis() < sessionEndTime

        private fun startSession() {
            sessionEndTime = System.currentTimeMillis() + (10 * 60 * 1000) // 10 minutes
        }

        /** Restarts the 10-minute countdown of a session that is still active; an expired one needs the code again. */
        fun renewSession() {
            if (isSessionActive()) startSession()
        }

        fun endSession() {
            sessionEndTime = 0L
        }

        fun consumeDebugAutoStartSession(context: Context): Boolean {
            if (!BuildConfig.DEBUG) return false

            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean(DEBUG_AUTO_START_SESSION_KEY, false)) return false

            prefs.edit { remove(DEBUG_AUTO_START_SESSION_KEY) }
            startSession()
            return true
        }

        fun suspendUnknownSourcesRestrictionForDebugInstall(context: Context) {
            if (!BuildConfig.DEBUG) return

            val dpm = DeviceAdmin.policyManager
            val admin = DeviceAdmin.component
            if (!dpm.isAdminActive(admin)) return

            val wasActive = dpm.getUserRestrictions(admin)
                .getBoolean(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
            if (wasActive) {
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
            }

            context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE).edit {
                putBoolean(DEBUG_RESTORE_UNKNOWN_SOURCES_KEY, wasActive)
            }
        }

        fun consumeDebugRestoreUnknownSourcesRestriction(context: Context) {
            if (!BuildConfig.DEBUG) return

            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean(DEBUG_RESTORE_UNKNOWN_SOURCES_KEY, false)) return
            prefs.edit { remove(DEBUG_RESTORE_UNKNOWN_SOURCES_KEY) }

            val dpm = DeviceAdmin.policyManager
            val admin = DeviceAdmin.component
            if (dpm.isAdminActive(admin)) {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
            }
        }

        fun isShuffleKeyboardEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("shuffle_keyboard", false)
        }

        fun setShuffleKeyboardEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            prefs.edit { putBoolean("shuffle_keyboard", enabled) }
        }

        /**
         * Tor packages the service has suspended. Kept apart from the apps that merely look like
         * Tor so releasing one block never lets go of what another one is holding.
         */
        fun getTorSuspendedPackages(context: Context): Set<String> {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            return prefs.getStringSet("tor_suspended_packages", emptySet()) ?: emptySet()
        }

        fun setTorSuspendedPackages(context: Context, packages: Set<String>) {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            prefs.edit { putStringSet("tor_suspended_packages", packages) }
        }

        /**
         * Torrent apps the service has suspended, kept apart from the Tor set: a client that
         * handles both magnet links and onion traffic must not be released by the other block.
         */
        fun getTorrentSuspendedPackages(context: Context): Set<String> {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            return prefs.getStringSet("torrent_suspended_packages", emptySet()) ?: emptySet()
        }

        fun setTorrentSuspendedPackages(context: Context, packages: Set<String>) {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            prefs.edit { putStringSet("torrent_suspended_packages", packages) }
        }

        /**
         * What has to be solved after the biometric prompt and before the settings open. Defaults
         * to the 32-word challenge: entry has to cost something, or the lock screen is decoration.
         */
        fun getAccessChallenges(context: Context): List<ChallengeType> {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            val stored = prefs.getStringSet("access_challenges", null)
                ?: return listOf(ChallengeType.WORDS)
            return stored
                .mapNotNull { runCatching { ChallengeType.valueOf(it) }.getOrNull() }
                .sortedBy { it.ordinal }
        }

        fun setAccessChallenges(context: Context, challenges: Set<ChallengeType>) {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            prefs.edit {
                putStringSet("access_challenges", challenges.map { it.name }.toSet())
                remove("impulse_lock_mode")
            }
        }

        fun getTetrisMinutes(context: Context): Int {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            return prefs.getInt("tetris_minutes", TETRIS_DEFAULT_MINUTES)
                .coerceIn(TETRIS_MIN_MINUTES, TETRIS_MAX_MINUTES)
        }

        fun setTetrisMinutes(context: Context, minutes: Int) {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            prefs.edit { putInt("tetris_minutes", minutes.coerceIn(TETRIS_MIN_MINUTES, TETRIS_MAX_MINUTES)) }
        }

        fun isTetrisSoundEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean("tetris_sound", true)
        }

        fun setTetrisSoundEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            prefs.edit { putBoolean("tetris_sound", enabled) }
        }

        fun getReadingSource(context: Context): ReadingSource {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            val stored = prefs.getString("reading_source", ReadingSource.BIBLE.name)!!
            return runCatching { ReadingSource.valueOf(stored) }.getOrDefault(ReadingSource.BIBLE)
        }

        fun setReadingSource(context: Context, source: ReadingSource) {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            prefs.edit { putString("reading_source", source.name) }
        }

        /**
         * How many texts the reading challenge shows. Stored as chosen; callers cap it to what the
         * selected source actually has, so switching sources doesn't lose the setting.
         */
        fun getReadingCount(context: Context): Int {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            return prefs.getInt("reading_count", READING_DEFAULT_COUNT).coerceAtLeast(READING_MIN_COUNT)
        }

        fun setReadingCount(context: Context, count: Int) {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            prefs.edit { putInt("reading_count", count.coerceAtLeast(READING_MIN_COUNT)) }
        }

        /** Phrases the user wrote for the reading challenge, in the order they were added. */
        fun getCustomReadingPassages(context: Context): List<ReadingPassage> {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("reading_custom_passages", null) ?: return emptyList()
            return runCatching {
                val array = JSONArray(json)
                List(array.length()) { i ->
                    val item = array.getJSONObject(i)
                    ReadingPassage(item.getString("text"), item.optString("source"))
                }
            }.getOrDefault(emptyList())
        }

        fun setCustomReadingPassages(context: Context, passages: List<ReadingPassage>) {
            val array = JSONArray()
            passages.forEach { array.put(JSONObject().put("text", it.text).put("source", it.source)) }
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            prefs.edit { putString("reading_custom_passages", array.toString()) }
        }

    /**
     * A permanent commitment has no way out by design, so the 48-hour forced removal — which
     * exists precisely to let a timed commitment end early — is not offered for one.
     */
    fun canStartForcedRemoval(context: Context): Boolean = !CommitmentLock.isPermanent(context)

    fun generateRecoveryCode(length: Int = 16): String {
            val random = SecureRandom()
            return (1..length)
                .map { CHAR_POOL[random.nextInt(CHAR_POOL.length)] }
                .joinToString("")
        }

        fun getRecoveryCode(context: Context) : String? {
            return context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE).getString("recovery_code", null)
        }

        fun isRecoveryCodeSet(context: Context) : Boolean  {
            isRecoveryKeySet.value = getRecoveryCode(context) != null
            return isRecoveryKeySet.value
        }

        fun saveRecoveryCode(context: Context, code: String) {
            val prefs = context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE)
            prefs.edit { putString("recovery_code", code) }
            isRecoveryKeySet.value = true
        }

        fun validateRecoveryCode(userInput: String, storedKey: String): Boolean {
            if (isSessionActive()) return true

            val success =  userInput == storedKey

            if (success)
                startSession()

            return success
        }

        fun startForcedRemoval(context: Context) {
            val prefs = context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE)
            prefs.edit {
                putBoolean("forced_removal_active", true)
                putLong("forced_removal_accumulated", 0L)
                putLong("forced_removal_last_elapsed", SystemClock.elapsedRealtime())
            }
        }

        fun cancelForcedRemoval(context: Context) {
            val prefs = context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE)
            prefs.edit {
                putBoolean("forced_removal_active", false)
            }
        }

        fun getForcedRemovalRemainingTime(context: Context): Long {
            val prefs = context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("forced_removal_active", false)) return -1L

            var accumulated = prefs.getLong("forced_removal_accumulated", 0L)
            val lastElapsed = prefs.getLong("forced_removal_last_elapsed", 0L)
            val now = SystemClock.elapsedRealtime()

            val diff = if (now >= lastElapsed) now - lastElapsed else now
            accumulated += diff

            prefs.edit {
                putLong("forced_removal_accumulated", accumulated)
                putLong("forced_removal_last_elapsed", now)
            }

            val target = 48L * 60 * 60 * 1000
            return (target - accumulated).coerceAtLeast(0L)
        }

    }
}
