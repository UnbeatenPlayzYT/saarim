package com.sarim.app.security

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.sarim.app.data.BrowserRestrictionsManager
import com.sarim.app.data.DeviceAdmin
import com.sarim.app.data.ProtectionDefaults
import com.sarim.app.data.VisualBlockingSettings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The guardrails [MandatoryProtection] re-asserts on every launch: nothing in the app may leave
 * visual blocking off, the built-in words or sites out, or Tor/torrent blocking disabled — and
 * enforcement must stay harmless before the device-owner grant, which is the state every fresh
 * install launches in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MandatoryProtectionTest {

    private lateinit var context: Context

    private val securityPrefs get() =
        context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)

    private val visualPrefs get() =
        context.getSharedPreferences(VisualBlockingSettings.PREFS_NAME, Context.MODE_PRIVATE)

    private val wordsPrefs get() =
        context.getSharedPreferences("blocked_words_prefs", Context.MODE_PRIVATE)

    private val browserPrefs get() =
        context.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        listOf(securityPrefs, visualPrefs, wordsPrefs, browserPrefs).forEach { it.edit { clear() } }
    }

    private fun browserLists(): List<JSONObject> {
        val array = JSONArray(browserPrefs.getString("blocked_lists_json", "[]") ?: "[]")
        return List(array.length()) { array.getJSONObject(it) }
    }

    private fun mandatoryListSites(): Set<String> {
        val list = browserLists()
            .firstOrNull { it.optString("id") == ProtectionDefaults.MANDATORY_BROWSER_LIST_ID }
            ?: return emptySet()
        val sites = list.optJSONArray("sites") ?: return emptySet()
        return buildSet { for (i in 0 until sites.length()) add(sites.optString(i)) }
    }

    // ------------------------------------------------------------------ first launch

    @Test
    fun `enforcement is safe before device owner is granted`() {
        assertFalse(
            "Robolectric starts without an owner, which is the state this test needs",
            DeviceAdmin.policyManager.isDeviceOwnerApp(context.packageName)
        )

        // Both run on the first launch, before ownership; neither may throw a SecurityException.
        MandatoryProtection.enforce(context)
        BrowserRestrictionsManager(context).applyRestrictions()
    }

    // ------------------------------------------------------------------ visual blocking

    @Test
    fun `visual blocking is forced on with every default category and target`() {
        visualPrefs.edit {
            putBoolean(VisualBlockingSettings.KEY_ENABLED, false)
            putBoolean(VisualBlockingSettings.KEY_SUSPEND_ENABLED, false)
            putStringSet(VisualBlockingSettings.KEY_CATEGORIES, emptySet())
            putStringSet(VisualBlockingSettings.KEY_TARGET_PACKAGES, emptySet())
        }

        MandatoryProtection.enforce(context)

        assertTrue(visualPrefs.getBoolean(VisualBlockingSettings.KEY_ENABLED, false))
        assertTrue(visualPrefs.getBoolean(VisualBlockingSettings.KEY_SUSPEND_ENABLED, false))
        assertTrue(
            visualPrefs.getStringSet(VisualBlockingSettings.KEY_CATEGORIES, emptySet())
                .orEmpty()
                .containsAll(VisualBlockingSettings.DEFAULT_CATEGORIES)
        )
        assertTrue(
            visualPrefs.getStringSet(VisualBlockingSettings.KEY_TARGET_PACKAGES, emptySet())
                .orEmpty()
                .containsAll(VisualBlockingSettings.DEFAULT_TARGET_PACKAGES)
        )
    }

    @Test
    fun `user-added categories and monitored apps survive enforcement`() {
        visualPrefs.edit {
            putStringSet(
                VisualBlockingSettings.KEY_CATEGORIES,
                VisualBlockingSettings.DEFAULT_CATEGORIES + "neutral"
            )
            putStringSet(
                VisualBlockingSettings.KEY_TARGET_PACKAGES,
                VisualBlockingSettings.DEFAULT_TARGET_PACKAGES + "com.example.app"
            )
        }

        MandatoryProtection.enforce(context)

        assertTrue(
            visualPrefs.getStringSet(VisualBlockingSettings.KEY_CATEGORIES, emptySet())
                .orEmpty()
                .containsAll(VisualBlockingSettings.DEFAULT_CATEGORIES + "neutral")
        )
        assertTrue(
            visualPrefs.getStringSet(VisualBlockingSettings.KEY_TARGET_PACKAGES, emptySet())
                .orEmpty()
                .contains("com.example.app")
        )
    }

    // ------------------------------------------------------------------ forbidden words

    @Test
    fun `the built-in forbidden words are written back when missing`() {
        wordsPrefs.edit { putStringSet("blocked_words", setOf("custom-word")) }

        MandatoryProtection.enforce(context)

        val words = wordsPrefs.getStringSet("blocked_words", emptySet()).orEmpty()
        assertTrue(words.containsAll(ProtectionDefaults.FORBIDDEN_WORDS))
        assertTrue("user additions are kept", words.contains("custom-word"))
    }

    // ------------------------------------------------------------------ browser lists

    @Test
    fun `the mandatory browser list holds every built-in adult site`() {
        MandatoryProtection.enforce(context)

        val sites = mandatoryListSites()
        assertTrue(
            "mandatory list is missing: ${ProtectionDefaults.ADULT_SITES - sites}",
            sites.containsAll(ProtectionDefaults.ADULT_SITES)
        )
    }

    @Test
    fun `user browser lists are kept next to the mandatory one`() {
        val custom = JSONArray()
            .put(JSONObject().put("id", "custom").put("title", "Mine").put("sites", JSONArray().put("example.org")))
            .toString()
        browserPrefs.edit { putString("blocked_lists_json", custom) }

        MandatoryProtection.enforce(context)

        val ids = browserLists().map { it.optString("id") }
        assertTrue(ids.contains("custom"))
        assertTrue(ids.contains(ProtectionDefaults.MANDATORY_BROWSER_LIST_ID))
        assertEquals("the mandatory list appears once", 1, ids.count { it == ProtectionDefaults.MANDATORY_BROWSER_LIST_ID })
    }

    @Test
    fun `an edited mandatory list is repaired instead of duplicated`() {
        val tampered = JSONArray()
            .put(
                JSONObject()
                    .put("id", ProtectionDefaults.MANDATORY_BROWSER_LIST_ID)
                    .put("title", "Edited")
                    .put("sites", JSONArray().put("pornhub.com"))
            )
            .toString()
        browserPrefs.edit { putString("blocked_lists_json", tampered) }

        MandatoryProtection.enforce(context)

        assertTrue(mandatoryListSites().containsAll(ProtectionDefaults.ADULT_SITES))
        assertEquals(
            1,
            browserLists().count { it.optString("id") == ProtectionDefaults.MANDATORY_BROWSER_LIST_ID }
        )
    }

    // ------------------------------------------------------------------ Tor / torrents

    @Test
    fun `Tor and torrent blocking are switched on and stay on`() {
        securityPrefs.edit {
            putBoolean(MandatoryProtection.KEY_BLOCK_TOR, false)
            putBoolean(MandatoryProtection.KEY_BLOCK_TORRENTS, false)
        }

        MandatoryProtection.enforce(context)

        assertTrue(securityPrefs.getBoolean(MandatoryProtection.KEY_BLOCK_TOR, false))
        assertTrue(securityPrefs.getBoolean(MandatoryProtection.KEY_BLOCK_TORRENTS, false))
        assertTrue(MandatoryProtection.isBlockTorEnabled())
        assertTrue(MandatoryProtection.isBlockTorrentsEnabled())
    }

    // ------------------------------------------------------------------ defaults themselves

    @Test
    fun `adult sites are bare hosts so URLBlocklist matches subdomains`() {
        ProtectionDefaults.ADULT_SITES.forEach { site ->
            assertTrue("$site should not carry a scheme", !site.contains("://"))
            assertFalse("$site should not carry a path", site.contains("/"))
            assertFalse("$site should be a bare host", site.startsWith("www."))
            assertFalse("$site should be lowercase", site != site.lowercase())
        }
        assertEquals("no duplicate entries", ProtectionDefaults.ADULT_SITES.size, ProtectionDefaults.ADULT_SITES.toSet().size)
    }

    @Test
    fun `forbidden words are lowercase because matching is case-insensitive`() {
        ProtectionDefaults.FORBIDDEN_WORDS.forEach { word ->
            assertEquals("$word should be lowercase", word, word.lowercase())
            assertTrue("$word should not be blank", word.isNotBlank())
        }
    }

    @Test
    fun `the enforced resolver is one of the offered filtering hosts`() {
        assertTrue(ProtectionDefaults.FILTERING_DNS_HOST in ProtectionDefaults.FILTERING_DNS_HOSTS)
        assertTrue(ProtectionDefaults.FILTERING_DNS_HOSTS.isNotEmpty())
        ProtectionDefaults.FILTERING_DNS_HOSTS.forEach { host ->
            assertEquals("$host needs a display label", true, ProtectionDefaults.FILTERING_DNS_LABELS.containsKey(host))
        }
    }

    @Test
    fun `enforcing twice leaves the mandatory state intact`() {
        MandatoryProtection.enforce(context)
        val words = wordsPrefs.getStringSet("blocked_words", emptySet()).orEmpty()
        val sites = mandatoryListSites()

        MandatoryProtection.enforce(context)

        assertEquals(words, wordsPrefs.getStringSet("blocked_words", emptySet()).orEmpty())
        assertEquals(sites, mandatoryListSites())
        // Absent means enabled: the service reads this key with a `true` default, and
        // enforcement only rewrites it when it was explicitly turned off.
        assertTrue(visualPrefs.getBoolean(VisualBlockingSettings.KEY_ENABLED, true))
        assertTrue(securityPrefs.getBoolean(MandatoryProtection.KEY_BLOCK_TOR, false))
    }
}
