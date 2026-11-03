package com.sarim.app.viewmodels

import android.os.UserManager
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import com.sarim.app.data.DeviceAdmin
import com.sarim.app.data.SystemRestrictions

class RestrictionsViewModel : ViewModel() {
    private val dpm = DeviceAdmin.policyManager
    private val adminName = DeviceAdmin.component

    // Current state in the UI (draft)
    val draftRestrictions = mutableStateMapOf<String, Boolean>()
    
    // Actual state applied in the system
    val appliedRestrictions = mutableStateMapOf<String, Boolean>()

    val recommendedKeys get() = SystemRestrictions.recommendedKeys
    val otherKeys get() = SystemRestrictions.otherKeys
    private val allKeys get() = SystemRestrictions.allKeys

    fun resourceNameFor(key: String): String? = SystemRestrictions.resourceNameFor(key)

    fun isMandatory(key: String): Boolean = key in SystemRestrictions.mandatoryKeys

    init {
        loadRestrictions()
    }

    fun loadRestrictions() {
        if (!dpm.isDeviceOwnerApp(adminName.packageName)) return
        val currentRestrictions = dpm.getUserRestrictions(adminName)
        allKeys.forEach { key ->
            val isEnabled = currentRestrictions.getBoolean(key as String?)
            draftRestrictions[key] = isEnabled
            appliedRestrictions[key] = isEnabled
        }
    }

    fun toggleDraft(key: String, enabled: Boolean) {
        // The mandatory restrictions are the bypass routes around the content filters; they can
        // only ever be turned on.
        if (key in SystemRestrictions.mandatoryKeys) {
            draftRestrictions[key] = true
            return
        }
        draftRestrictions[key] = enabled
    }

    fun toggleAllDrafts(keys: List<String>, enabled: Boolean) {
        keys.forEach { key ->
            draftRestrictions[key] = if (key in SystemRestrictions.mandatoryKeys) true else enabled
        }
    }

    fun applyChanges() {
        allKeys.forEach { key ->
            val shouldEnable = key in SystemRestrictions.mandatoryKeys || draftRestrictions[key] == true
            val currentlyAppliedRestrictions = dpm.getUserRestrictions(adminName)
            if (shouldEnable) {
                dpm.addUserRestriction(adminName, key as String?)
            } else if (currentlyAppliedRestrictions.containsKey(key)) {
                dpm.clearUserRestriction(adminName, key as String?)
            }
        }
        loadRestrictions()
    }

    fun isAllDraftsEnabled(keys: List<String>): Boolean = keys.all { draftRestrictions[it] == true }

    fun hasPendingChanges(): Boolean = allKeys.any { draftRestrictions[it] != appliedRestrictions[it] }
}
