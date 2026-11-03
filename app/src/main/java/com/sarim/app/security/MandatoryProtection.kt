package com.sarim.app.security

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.UserManager
import androidx.core.content.edit
import com.sarim.app.data.BrowserRestrictionsManager
import com.sarim.app.data.DeviceAdmin
import com.sarim.app.data.ProtectionDefaults
import com.sarim.app.data.VisualBlockingSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * The guardrails that are on from the first launch and that nothing in the app can switch off.
 *
 * This is the whole protection stack, not a supplement to it: visual blocking with every adult
 * category and the suspension escalation, the built-in forbidden words, the built-in adult-site
 * browser list plus SafeSearch, the filtering private DNS, Tor/Orbot, torrent clients, and the
 * system doors that would otherwise route around all of it (a chosen VPN or resolver, sideloading
 * an app the store would not hand out, uninstalling the blocker, safe boot, USB debugging, changing
 * the date to shorten a commitment).
 *
 * [enforce] is idempotent — it writes only what is out of line — so it is safe to call on every
 * launch, on every service start and from preference listeners that may re-enter it.
 */
object MandatoryProtection {

    private const val PREFS_NAME = "security_prefs"
    private const val BROWSER_PREFS = "browser_prefs"
    private const val KEY_BLOCKED_LISTS = "blocked_lists_json"
    private const val BLOCKED_WORDS_PREFS = "blocked_words_prefs"

    /** Read by the accessibility service; there is no setter on purpose. */
    fun isBlockTorEnabled(): Boolean = true

    /** Read by the accessibility service; there is no setter on purpose. */
    fun isBlockTorrentsEnabled(): Boolean = true

    /**
     * Bypass routes rather than content filters. With these applied the user cannot point the
     * device at a VPN or a resolver of their choosing, cannot install a new app from outside the
     * store, cannot uninstall or force-stop the blocker, cannot boot into safe mode to sidestep
     * it, cannot attach a debugger, and cannot move the clock a commitment is measured against.
     */
    val RESTRICTIONS: List<String> = listOf(
        UserManager.DISALLOW_CONFIG_VPN,
        UserManager.DISALLOW_CONFIG_PRIVATE_DNS,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY,
        UserManager.DISALLOW_UNINSTALL_APPS,
        UserManager.DISALLOW_APPS_CONTROL,
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_DEBUGGING_FEATURES,
        UserManager.DISALLOW_CONFIG_DATE_TIME
    )

    /**
     * Re-asserts the mandatory guardrails. Returns whether anything had to be fixed, so callers
     * that react to preference changes can tell a real write from a no-op.
     */
    fun enforce(context: Context): Boolean {
        var changed = false

        changed = enforceVisualBlocking(context) || changed
        changed = enforceForbiddenWords(context) || changed
        changed = enforceBrowserLists(context) || changed
        changed = enforceSystemRestrictions(context) || changed

        return changed
    }

    /**
     * Visual blocking is always on, every adult category is always classified, and repeated
     * detections always escalate to a suspension. The user can still add apps to monitor — the
     * defaults are simply never removable.
     */
    private fun enforceVisualBlocking(context: Context): Boolean {
        val prefs = context.getSharedPreferences(VisualBlockingSettings.PREFS_NAME, Context.MODE_PRIVATE)
        var changed = false

        if (!prefs.getBoolean(VisualBlockingSettings.KEY_ENABLED, true)) {
            prefs.edit { putBoolean(VisualBlockingSettings.KEY_ENABLED, true) }
            changed = true
        }

        val categories = prefs.getStringSet(
            VisualBlockingSettings.KEY_CATEGORIES, VisualBlockingSettings.DEFAULT_CATEGORIES
        ) ?: VisualBlockingSettings.DEFAULT_CATEGORIES
        if (!categories.containsAll(VisualBlockingSettings.DEFAULT_CATEGORIES)) {
            prefs.edit {
                putStringSet(
                    VisualBlockingSettings.KEY_CATEGORIES,
                    categories + VisualBlockingSettings.DEFAULT_CATEGORIES
                )
            }
            changed = true
        }

        if (!prefs.getBoolean(VisualBlockingSettings.KEY_SUSPEND_ENABLED, true)) {
            prefs.edit { putBoolean(VisualBlockingSettings.KEY_SUSPEND_ENABLED, true) }
            changed = true
        }

        val targets = prefs.getStringSet(
            VisualBlockingSettings.KEY_TARGET_PACKAGES, VisualBlockingSettings.DEFAULT_TARGET_PACKAGES
        ) ?: VisualBlockingSettings.DEFAULT_TARGET_PACKAGES
        if (!targets.containsAll(VisualBlockingSettings.DEFAULT_TARGET_PACKAGES)) {
            prefs.edit {
                putStringSet(
                    VisualBlockingSettings.KEY_TARGET_PACKAGES,
                    targets + VisualBlockingSettings.DEFAULT_TARGET_PACKAGES
                )
            }
            changed = true
        }

        return changed
    }

    /**
     * The built-in adult vocabulary is unioned back in on every launch, so it can't be deleted,
     * and every installed browser is always watched for it: the address bar is the shortest route
     * to the content the DNS and URL lists are there to stop.
     */
    private fun enforceForbiddenWords(context: Context): Boolean {
        val prefs = context.getSharedPreferences(BLOCKED_WORDS_PREFS, Context.MODE_PRIVATE)
        var changed = false

        val current = prefs.getStringSet("blocked_words", emptySet()) ?: emptySet()
        if (!current.containsAll(ProtectionDefaults.FORBIDDEN_WORDS)) {
            prefs.edit { putStringSet("blocked_words", current + ProtectionDefaults.FORBIDDEN_WORDS) }
            changed = true
        }

        val browsers = BrowserRestrictionsManager(context).getPossibleBrowsers()
            .map { it.activityInfo.packageName }
            .toSet()
        if (browsers.isNotEmpty()) {
            val targets = prefs.getStringSet("target_packages", emptySet()) ?: emptySet()
            if (!targets.containsAll(browsers)) {
                prefs.edit { putStringSet("target_packages", targets + browsers) }
                changed = true
            }
        }

        return changed
    }

    /**
     * Writes the built-in adult-site list back into the browser restrictions and re-applies them
     * to every installed browser, which is also what forces SafeSearch on.
     */
    private fun enforceBrowserLists(context: Context): Boolean {
        val prefs = context.getSharedPreferences(BROWSER_PREFS, Context.MODE_PRIVATE)
        val lists = runCatching {
            JSONArray(prefs.getString(KEY_BLOCKED_LISTS, null) ?: "[]")
        }.getOrDefault(JSONArray())

        var mandatoryFound = false
        var mandatoryComplete = false
        for (i in 0 until lists.length()) {
            val list = lists.optJSONObject(i) ?: continue
            if (list.optString("id") != ProtectionDefaults.MANDATORY_BROWSER_LIST_ID) continue
            mandatoryFound = true
            val sites = list.optJSONArray("sites")
            val existing = buildSet {
                if (sites != null) for (j in 0 until sites.length()) add(sites.optString(j))
            }
            mandatoryComplete = existing.containsAll(ProtectionDefaults.ADULT_SITES)
            break
        }

        if (!mandatoryFound || !mandatoryComplete) {
            val rewritten = JSONArray()
            var writtenMandatory = false
            for (i in 0 until lists.length()) {
                val list = lists.optJSONObject(i) ?: continue
                if (list.optString("id") == ProtectionDefaults.MANDATORY_BROWSER_LIST_ID) {
                    if (!writtenMandatory) {
                        rewritten.put(mandatoryListObject())
                        writtenMandatory = true
                    }
                } else {
                    rewritten.put(list)
                }
            }
            if (!writtenMandatory) rewritten.put(mandatoryListObject())

            prefs.edit { putString(KEY_BLOCKED_LISTS, rewritten.toString()) }
        }

        BrowserRestrictionsManager(context).applyRestrictions()
        return !mandatoryFound || !mandatoryComplete
    }

    private fun mandatoryListObject(): JSONObject = JSONObject().apply {
        put("id", ProtectionDefaults.MANDATORY_BROWSER_LIST_ID)
        put("title", ProtectionDefaults.mandatoryBrowserListTitle())
        put("sites", JSONArray(ProtectionDefaults.ADULT_SITES))
    }

    /**
     * Applies the device-owner restrictions and pins a filtering private DNS. Without device
     * owner (or the admin active) none of it can be applied; the UI shows the requirement instead
     * of silently doing nothing.
     */
    private fun enforceSystemRestrictions(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var changed = false

        // The preferences stay, so the service keeps reading a truthful value.
        if (!prefs.getBoolean(KEY_BLOCK_TOR, false)) {
            prefs.edit { putBoolean(KEY_BLOCK_TOR, true) }
            changed = true
        }
        if (!prefs.getBoolean(KEY_BLOCK_TORRENTS, false)) {
            prefs.edit { putBoolean(KEY_BLOCK_TORRENTS, true) }
            changed = true
        }

        val dpm = DeviceAdmin.policyManager
        val admin = DeviceAdmin.component
        if (!dpm.isAdminActive(admin)) return changed

        val applied = dpm.getUserRestrictions(admin)
        RESTRICTIONS.forEach { restriction ->
            if (!applied.getBoolean(restriction)) {
                dpm.addUserRestriction(admin, restriction)
                changed = true
            }
        }

        if (ensureFilteringDns(dpm, admin)) changed = true

        return changed
    }

    /**
     * Keeps a family/adult-filtering resolver in place. A resolver the user chose themselves is
     * the first thing anyone tries when the block is unwelcome, so only the filtering hosts are
     * accepted; anything else is replaced with [ProtectionDefaults.FILTERING_DNS_HOST].
     */
    private fun ensureFilteringDns(dpm: DevicePolicyManager, admin: android.content.ComponentName): Boolean {
        val mode = runCatching { dpm.getGlobalPrivateDnsMode(admin) }.getOrDefault(-1)
        val host = runCatching { dpm.getGlobalPrivateDnsHost(admin) }.getOrNull()

        if (mode == DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME &&
            host != null && host in ProtectionDefaults.FILTERING_DNS_HOSTS
        ) {
            return false
        }

        val result = runCatching {
            dpm.setGlobalPrivateDnsModeSpecifiedHost(admin, ProtectionDefaults.FILTERING_DNS_HOST)
        }.getOrDefault(-1)

        return result == DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR
    }

    const val KEY_BLOCK_TOR = "block_tor"
    const val KEY_BLOCK_TORRENTS = "block_torrents"
}
