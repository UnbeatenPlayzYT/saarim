package com.sarim.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.sarim.app.data.DeviceAdmin
import com.sarim.app.security.MandatoryProtection

class SarimDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        // The broadcast that marks the end of the device-owner flow is delivered here on every API
        // level; on API 34+ SarimPolicyUpdateReceiver also confirms it from the policy result.
        DeviceAdmin.persistResult(context, context.packageName)

        // The restrictions that keep the content filters from being routed around need device
        // owner to exist at all, so provisioning is the first moment they can be applied.
        MandatoryProtection.enforce(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }

}
