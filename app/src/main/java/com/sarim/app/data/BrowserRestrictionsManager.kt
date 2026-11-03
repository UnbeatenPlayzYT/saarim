package com.sarim.app.data

import android.content.RestrictionsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import org.json.JSONArray
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri

class BrowserRestrictionsManager(private val context: Context) {

    fun getPossibleBrowsers(): List<ResolveInfo> {
        val pm = context.packageManager
        val resolvedPackages = mutableSetOf<String>()
        val results = mutableListOf<ResolveInfo>()

        val browserCategoryIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_BROWSER)
        }

        val httpIntent = Intent(Intent.ACTION_VIEW, "http://www.example.com".toUri())
        val httpsIntent = Intent(Intent.ACTION_VIEW, "https://www.example.com".toUri())

        val flags =
            PackageManager.MATCH_ALL

        listOf(browserCategoryIntent, httpIntent, httpsIntent).forEach { intent ->
            pm.queryIntentActivities(intent, flags).forEach { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName

                if (packageName != context.packageName && resolvedPackages.add(packageName)) {
                    results.add(resolveInfo)
                }
            }
        }

        return results
    }

    fun isBrowser(packageName: String): Boolean {
        return getPossibleBrowsers().any { it.activityInfo.packageName == packageName }
    }

    /**
     * Every installed app that handles a magnet link or a `.torrent` file.
     *
     * Cached for the same reason as [getPossibleTorApps]: the accessibility service asks for this
     * per package-added broadcast and whenever the "Block Torrent apps" toggle changes. Call
     * [invalidateTorrentCache] once the installed-app list may have changed.
     */
    fun getPossibleTorrentApps(): Set<String> {
        cachedTorrentApps?.let { return it }

        val pm = context.packageManager

        // Clients declare `.torrent` handling in different shapes: some only the mime type, some a
        // scheme plus the mime type (`file`, `content`), some neither and just listen for magnet
        // links. One probe doesn't reach all of them, so ask for each shape and union the results.
        val probes = listOf(
            Intent(Intent.ACTION_VIEW, "magnet:?xt=urn:btih:1234567890ABCDEF".toUri()),
            Intent(Intent.ACTION_VIEW).apply { type = TORRENT_MIME_TYPE },
            Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.EMPTY, TORRENT_MIME_TYPE) },
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType("content://com.example/document/1".toUri(), TORRENT_MIME_TYPE)
            },
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType("file:///sdcard/Download/example.torrent".toUri(), TORRENT_MIME_TYPE)
            }
        )

        val suspiciousPackages = probes
            .flatMap { pm.queryIntentActivities(it, PackageManager.MATCH_DEFAULT_ONLY) }
            .map { it.activityInfo.packageName }
            .toSet()

        cachedTorrentApps = suspiciousPackages
        return suspiciousPackages
    }

    fun invalidateTorrentCache() {
        cachedTorrentApps = null
    }

    fun isTorrentApp(packageName: String): Boolean {
        return packageName in getPossibleTorrentApps()
    }

    /**
     * Tor apps are recognized the same way the torrent list is: by looking at what's installed.
     * Tor Browser deliberately doesn't register as [Intent.CATEGORY_APP_BROWSER], so [isBrowser]
     * alone misses it and a different signal is needed.
     *
     * Three signals, in order of reliability:
     *  1. [TOR_PACKAGE_NAMES]: the official and legacy package ids, checked by name so a package
     *     is caught the moment it appears — including in the middle of an ACTION_PACKAGE_ADDED
     *     broadcast, before this class has (re)scanned the installed apps.
     *  2. [TOR_IDENTITY_TOKENS] over the package name and label, which is what catches a rebuilt
     *     or rebranded Tor client whose package id differs from the official build.
     *  3. [getPossibleTorApps], the cached installed-app sweep, for apps whose package name hides
     *     their purpose but whose user-visible label doesn't.
     */
    fun isTorApp(packageName: String): Boolean {
        if (packageName in TOR_PACKAGE_NAMES) return true
        if (matchesTorIdentity(packageName)) return true
        return packageName in getPossibleTorApps()
    }

    /**
     * Every installed app that looks like a Tor component, i.e. matches [TOR_IDENTITY_TOKENS] on
     * its package name or its label.
     *
     * Cached because the sweep enumerates and labels every installed app, and the accessibility
     * service asks for this set both per package-added broadcast and whenever the "Block Tor"
     * toggle changes. Call [invalidateTorCache] once the installed-app list may have changed.
     */
    fun getPossibleTorApps(): Set<String> {
        cachedTorApps?.let { return it }

        val pm = context.packageManager
        val result = mutableSetOf<String>()

        runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA).forEach { app ->
                val label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault("")
                if (matchesTorIdentity(app.packageName, label)) result.add(app.packageName)
            }
        }

        cachedTorApps = result
        return result
    }

    fun invalidateTorCache() {
        cachedTorApps = null
    }

    private fun matchesTorIdentity(packageName: String, label: String = ""): Boolean {
        val identity = normalizeIdentity("$packageName $label")
        return TOR_IDENTITY_TOKENS.any { identity.contains(it) }
    }

    /** Lowercased and stripped of separators, so `tor_browser`, `tor.browser` and `torbrowser` all collide. */
    private fun normalizeIdentity(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

    /** Process-wide: the installed-app list doesn't depend on which instance asks for it. */
    companion object {
        /**
         * Official and legacy Tor / Orbot package ids. Kept explicit (rather than relying on the
         * sweep alone) so a freshly installed package is recognized before the next sweep.
         */
        private val TOR_PACKAGE_NAMES = setOf(
            "org.torproject.torbrowser",       // Tor Browser (Google Play / official builds)
            "org.torproject.torbrowser_alpha", // Tor Browser alpha
            "org.torproject.android",          // Orbot: Proxy with Tor
            "org.torproject.torservices",      // TorServices, the Tor provider other apps bind to
            "info.guardianproject.orfox",      // Orfox, the legacy Guardian Project browser
            "info.guardianproject.orbot",      // legacy Orbot package id
            "org.torproject.orbot",            // legacy Orbot package id
            "net.freehaven.tor"                // ancient Tor control package id
        )

        /**
         * Substrings of a normalized package name or label that identify a Tor component.
         * `torproject` alone covers every app published under the org.torproject.* id, so a new
         * official Tor app is blocked without a code change.
         */
        private val TOR_IDENTITY_TOKENS = listOf(
            "torbrowser", "torproject", "torservices", "orbot", "orfox", "onionbrowser"
        )

        private const val TORRENT_MIME_TYPE = "application/x-bittorrent"

        @Volatile
        private var cachedTorApps: Set<String>? = null

        @Volatile
        private var cachedTorrentApps: Set<String>? = null
    }

    fun supportsRestrictions(packageName: String): Boolean {
        return try {
            val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
            rm.getManifestRestrictions(packageName).any { it.key == "URLBlocklist" }
                    && rm.getManifestRestrictions(packageName).any { it.key == "ForceGoogleSafeSearch" }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Pushes the blocked hosts and forced SafeSearch into every installed browser that supports
     * managed restrictions.
     *
     * The built-in adult sites are unioned in on every call, so a missing or edited preference
     * can never shrink the list — that is what the `installed` parameter used to gate, and there
     * is no longer a path that applies one without the other.
     */
    fun applyRestrictions() {
        val prefs = context.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("blocked_lists_json", null)

        val allSites = mutableSetOf<String>().apply { addAll(ProtectionDefaults.ADULT_SITES) }
        try {
            if (json != null) {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val sitesArray = array.getJSONObject(i).getJSONArray("sites")
                    for (j in 0 until sitesArray.length()) {
                        val site = sitesArray.getString(j).trim()
                        if (site.isNotEmpty()) allSites.add(site)
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        val urlRestrictions = Bundle().apply {
            putStringArray("URLBlocklist", allSites.toTypedArray())
            putBoolean("ForceGoogleSafeSearch", true)
        }

        getPossibleBrowsers().forEach { info ->
            AppRepository.setApplicationRestrictions(info.activityInfo.packageName, urlRestrictions)
        }
    }
}
