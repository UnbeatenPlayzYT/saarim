package com.sarim.app

import android.app.admin.DevicePolicyIdentifiers
import android.app.admin.PolicyUpdateReceiver
import android.app.admin.PolicyUpdateResult
import android.app.admin.TargetUser
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.sarim.app.data.AppRepository
import com.sarim.app.data.DeviceAdmin
import timber.log.Timber

// Delivered on API 34+ (UPSIDE_DOWN_CAKE) with the result of a device-policy change. It is the
// authoritative confirmation for the device-owner / managed-admin setup, so the setup UI waits on
// this state instead of guessing from the Shizuku process exit code.
//
// PolicyUpdateReceiver.onReceive is final — the base class owns the broadcast and only dispatches
// the two callbacks below — so the admin-activation broadcast the device-owner flow also produces is
// handled by SarimDeviceAdminReceiver, which works on every API level.
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class SarimPolicyUpdateReceiver : PolicyUpdateReceiver() {

    override fun onPolicySetResult(
        context: Context,
        policyIdentifier: String,
        additionalPolicyParams: Bundle,
        targetUser: TargetUser,
        policyUpdateResult: PolicyUpdateResult
    ) {
        log(context, "onPolicySetResult", policyIdentifier, additionalPolicyParams, targetUser, policyUpdateResult)
    }

    override fun onPolicyChanged(
        context: Context,
        policyIdentifier: String,
        additionalPolicyParams: Bundle,
        targetUser: TargetUser,
        policyUpdateResult: PolicyUpdateResult
    ) {
        log(context, "onPolicyChanged", policyIdentifier, additionalPolicyParams, targetUser, policyUpdateResult)
    }

    private fun log(
        context: Context,
        callback: String,
        policyIdentifier: String,
        additionalPolicyParams: Bundle,
        targetUser: TargetUser,
        policyUpdateResult: PolicyUpdateResult
    ) {
        val packageName = additionalPolicyParams.getString(EXTRA_PACKAGE_NAME)
        Timber.tag("SarimPolicyUpdate").d(
            
            "$callback: policy=$policyIdentifier package=$packageName " +
                "user=${describeTargetUser(targetUser)} result=${describeResult(policyUpdateResult)}"
        )

        // The final/authoritative result can disagree with what setApplicationHidden()
        // returned synchronously (e.g. another admin overriding it later). Drop the
        // cache so the next read reflects the real PackageManager state instead of
        // whatever AppRepository optimistically assumed.
        if (policyIdentifier == DevicePolicyIdentifiers.APPLICATION_HIDDEN_POLICY) {
            AppRepository.invalidateCache()
        }

        // PolicyUpdateReceiver results are the authoritative confirmation for the
        // device-owner / managed-admin setup, so record them the same way the explicit
        // intent does (the UI waits on this state).
        if (policyUpdateResult.resultCode == PolicyUpdateResult.RESULT_POLICY_SET) {
            DeviceAdmin.persistResult(context, packageName)
        }
    }

    private fun describeResult(result: PolicyUpdateResult): String = when (result.resultCode) {
        PolicyUpdateResult.RESULT_POLICY_SET -> "RESULT_POLICY_SET"
        PolicyUpdateResult.RESULT_POLICY_CLEARED -> "RESULT_POLICY_CLEARED"
        PolicyUpdateResult.RESULT_FAILURE_CONFLICTING_ADMIN_POLICY -> "RESULT_FAILURE_CONFLICTING_ADMIN_POLICY"
        PolicyUpdateResult.RESULT_FAILURE_STORAGE_LIMIT_REACHED -> "RESULT_FAILURE_STORAGE_LIMIT_REACHED"
        PolicyUpdateResult.RESULT_FAILURE_HARDWARE_LIMITATION -> "RESULT_FAILURE_HARDWARE_LIMITATION"
        else -> "RESULT_FAILURE_UNKNOWN(${result.resultCode})"
    }

    private fun describeTargetUser(targetUser: TargetUser): String = when (targetUser) {
        TargetUser.GLOBAL -> "GLOBAL"
        TargetUser.LOCAL_USER -> "LOCAL_USER"
        TargetUser.PARENT_USER -> "PARENT_USER"
        TargetUser.UNKNOWN_USER -> "UNKNOWN_USER"
        else -> targetUser.toString()
    }

    companion object {
        private val EXTRA_PACKAGE_NAME = PolicyUpdateReceiver.EXTRA_PACKAGE_NAME
    }
}
