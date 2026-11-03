package com.sarim.app.data

import android.content.Context
import android.content.RestrictionEntry
import android.content.RestrictionsManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.edit
import com.sarim.app.SarimApplication
import com.sarim.app.models.AppItem

object AppRepository {
    private const val HIDDEN_APPS_PREFS = "hidden_apps_prefs"
    private const val KEY_HIDDEN_PACKAGES = "hidden_packages"

    private val context = SarimApplication.getInstance()
    private val packageManager = context.packageManager
    private val dpm get() = DeviceAdmin.policyManager
    private val adminName get() = DeviceAdmin.component

    private val hiddenAppsPrefs
        get() = context.getSharedPreferences(HIDDEN_APPS_PREFS, Context.MODE_PRIVATE)

    @Volatile
    private var cachedApps: List<AppItem>? = null
    private val cacheLock = Any()

    fun getApps(forceRefresh: Boolean = false): List<AppItem> {
        if (!forceRefresh) {
            cachedApps?.let { return it }
        }
        synchronized(cacheLock) {
            if (!forceRefresh) {
                cachedApps?.let { return it }
            }
            val fresh = loadAppsFromSystem()
            cachedApps = fresh
            return fresh
        }
    }

    private fun loadAppsFromSystem(): List<AppItem> {
        val ratingsPrefs = context.getSharedPreferences("app_ratings", Context.MODE_PRIVATE)

        val installedApps = packageManager.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)

        return installedApps.asSequence()
            .filter { it.packageName != context.packageName }
            .map { appInfo ->
                val packageName = appInfo.packageName
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                val isHidden = try { dpm.isApplicationHidden(adminName, packageName) } catch (_: Exception) { false }
                val isUninstallBlocked = try { dpm.isUninstallBlocked(adminName, packageName) } catch (_: Exception) { false }
                val isSuspended = try { dpm.isPackageSuspended(adminName, packageName) } catch (_: Exception) { false }

                AppItem(
                    name = appInfo.loadLabel(packageManager).toString(),
                    packageName = packageName,
                    icon = appInfo.loadIcon(packageManager),
                    isSystem = isSystem,
                    isHidden = isHidden,
                    isUninstallBlocked = isUninstallBlocked,
                    isSuspended = isSuspended,
                    hasExplicitContent = ratingsPrefs.getBoolean(packageName, false)
                )
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    fun invalidateCache() {
        synchronized(cacheLock) {
            cachedApps = null
        }
    }

    private fun updateCachedApp(packageName: String, transform: (AppItem) -> AppItem) {
        synchronized(cacheLock) {
            cachedApps = cachedApps?.map { if (it.packageName == packageName) transform(it) else it }
        }
    }

    fun setAppHidden(packageName: String, hidden: Boolean) {
        val success = dpm.setApplicationHidden(adminName, packageName, hidden)
        // `success` reflects the real PackageManager state at the time of the call
        // (DevicePolicyManagerService double-checks isApplicationHidden() before
        // returning). Only persist the new state locally when it's true - otherwise
        // the cache/prefs would drift from reality and invert the next toggle.
        Log.d("SarimPolicyUpdate", "setApplicationHidden request: package=$packageName hidden=$hidden accepted=$success")
        if (!success) {
            invalidateCache()
            return
        }

        updateCachedApp(packageName) { it.copy(isHidden = hidden) }

        hiddenAppsPrefs.edit {
            if (hidden) {
                putStringSet(
                    KEY_HIDDEN_PACKAGES,
                    (hiddenAppsPrefs.getStringSet(KEY_HIDDEN_PACKAGES, emptySet()) ?: emptySet()) + packageName
                )
            } else {
                putStringSet(
                    KEY_HIDDEN_PACKAGES,
                    (hiddenAppsPrefs.getStringSet(KEY_HIDDEN_PACKAGES, emptySet()) ?: emptySet()) - packageName
                )
            }
        }
    }

    fun reapplyHiddenIfNeeded(packageName: String) {
        val wasHidden = hiddenAppsPrefs.getStringSet(KEY_HIDDEN_PACKAGES, emptySet())
            ?.contains(packageName) == true
        Log.d("SarimPolicyUpdate", "reapplyHiddenIfNeeded: package=$packageName wasHidden=$wasHidden")
        if (!wasHidden) return

        val success = dpm.setApplicationHidden(adminName, packageName, true)
        Log.d("SarimPolicyUpdate", "reapplyHiddenIfNeeded request: package=$packageName accepted=$success")
        if (success) {
            updateCachedApp(packageName) { it.copy(isHidden = true) }
        } else {
            invalidateCache()
        }
    }

    fun setAppSuspended(packageName: String, suspended: Boolean) {
        dpm.setPackagesSuspended(adminName, arrayOf(packageName), suspended)
        updateCachedApp(packageName) { it.copy(isSuspended = suspended) }
    }

    fun setUninstallBlocked(packageName: String, block: Boolean) {
        dpm.setUninstallBlocked(adminName, packageName, block)
        updateCachedApp(packageName) { it.copy(isUninstallBlocked = block) }
    }

    fun getApplicationRestrictions(packageName: String): Bundle {
        return dpm.getApplicationRestrictions(adminName, packageName)
    }

    fun setApplicationRestrictions(packageName: String, restrictions: Bundle) {
        val current = dpm.getApplicationRestrictions(adminName, packageName)
        current.putAll(restrictions)
        dpm.setApplicationRestrictions(adminName, packageName, current)
    }

    fun getAvailableRestrictions(packageName: String): List<RestrictionEntry> {
        val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager

        try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            if (appInfo.metaData == null) return emptyList()
        } catch (_: PackageManager.NameNotFoundException) {
            return emptyList()
        }

        val restrictions = rm.getManifestRestrictions(packageName)
        return restrictions?.toList() ?: emptyList()
    }
}
