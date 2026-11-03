package com.sarim.app.data

import android.os.UserManager
import com.sarim.app.security.MandatoryProtection

object SystemRestrictions {
    /**
     * Applied by [MandatoryProtection] and shown as always on: each one either routes around the
     * content filters (VPN, private DNS), installs around them, removes the blocker, or lets the
     * user boot/debug/rewind their way past it.
     */
    val mandatoryKeys: List<String> = MandatoryProtection.RESTRICTIONS

    val recommendedKeys = mandatoryKeys + UserManager.DISALLOW_FACTORY_RESET

    val restrictionFieldNames: Map<String, String> by lazy {
        UserManager::class.java.fields
            .filter { field ->
                java.lang.reflect.Modifier.isStatic(field.modifiers) &&
                        java.lang.reflect.Modifier.isFinal(field.modifiers) &&
                        (field.name.startsWith("DISALLOW_") || field.name.startsWith("ALLOW_"))
            }
            .associate { (it.get(null) as String) to it.name.lowercase() }
    }

    val otherKeys: List<String> by lazy {
        restrictionFieldNames.keys
            .filter { it !in recommendedKeys }
            .sorted()
    }

    val allKeys: List<String> by lazy {
        (recommendedKeys + otherKeys).distinct()
    }

    fun resourceNameFor(key: String): String? = restrictionFieldNames[key]
}
