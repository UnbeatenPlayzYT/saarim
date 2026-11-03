package com.sarim.app.screens.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sarim.app.R
import com.sarim.app.security.CommitmentLock
import com.sarim.app.security.SecurityManager
import kotlinx.coroutines.delay

/**
 * Gates a sensitive action behind the recovery-code dialog, unless a recovery session is already
 * active or no recovery code has been set yet (first run, before [SetupRecovery][com.sarim.app.screens.setup.SetupRecovery]).
 * Replaces the `pendingAction`/`showRecoveryDialog` pair every settings screen used to hand-roll.
 *
 * While a commitment is running it refuses every settings change, including one the recovery code
 * would otherwise authorise: that is what the commitment is for. The single exception is an action
 * that only ever makes the app stricter ([run]'s `strengtheningAction`), so protection can be
 * restored without waiting out the period.
 */
class RecoveryGate(private val context: Context) {
    private var pendingAction by mutableStateOf<(() -> Unit)?>(null)
    private var pendingCancel: (() -> Unit)? = null

    /** Set when an action was refused because the commitment period is still running. */
    var commitmentBlocked by mutableStateOf(false)
        internal set

    val isDialogVisible: Boolean get() = pendingAction != null

    /** Whether a recovery session is active — screens use this to enable/disable gated controls. */
    var isSessionActive by mutableStateOf(SecurityManager.isSessionActive())
        internal set

    /** True while the commitment freezes settings, so screens can say so instead of looking broken. */
    val isCommitmentLocked: Boolean get() = CommitmentLock.isLocked(context)

    fun updateSessionActive() {
        val active = SecurityManager.isSessionActive()
        if (isSessionActive != active) {
            isSessionActive = active
        }
    }

    /**
     * [onCancel] runs if the user dismisses the dialog instead of confirming — for rolling back
     * optimistic UI updates. [strengtheningAction] bypasses the commitment freeze for the few
     * actions that only add protection.
     */
    fun run(
        onCancel: (() -> Unit)? = null,
        strengtheningAction: Boolean = false,
        action: () -> Unit
    ) {
        if (!strengtheningAction && isCommitmentLocked) {
            onCancel?.invoke()
            commitmentBlocked = true
            return
        }

        val storedCode = SecurityManager.getRecoveryCode(context)
        if (storedCode == null || SecurityManager.isSessionActive()) {
            action()
        } else {
            pendingAction = action
            pendingCancel = onCancel
        }
    }

    fun confirm(code: String): Boolean {
        val storedCode = SecurityManager.getRecoveryCode(context) ?: return true
        if (!SecurityManager.validateRecoveryCode(code, storedCode)) return false
        updateSessionActive()
        pendingAction?.invoke()
        clear()
        return true
    }

    fun dismiss() {
        pendingCancel?.invoke()
        clear()
    }

    fun acknowledgeCommitmentBlock() {
        commitmentBlocked = false
    }

    private fun clear() {
        pendingAction = null
        pendingCancel = null
    }
}

@Composable
fun rememberRecoveryGate(): RecoveryGate {
    val context = LocalContext.current
    val gate = remember { RecoveryGate(context) }

    LaunchedEffect(gate, SecurityManager.sessionEndTime) {
        gate.updateSessionActive()
        while (gate.isSessionActive) {
            val remaining = SecurityManager.sessionEndTime - System.currentTimeMillis()
            if (remaining <= 0) {
                gate.updateSessionActive()
                break
            }
            delay(remaining.coerceAtMost(1000L))
            gate.updateSessionActive()
        }
    }

    return gate
}

@Composable
fun RecoveryGateDialog(gate: RecoveryGate) {
    if (gate.isDialogVisible) {
        RecoveryConfirmDialog(
            onConfirm = { code -> gate.confirm(code) },
            onDismiss = { gate.dismiss() }
        )
    }

    if (gate.commitmentBlocked) {
        AlertDialog(
            onDismissRequest = { gate.acknowledgeCommitmentBlock() },
            title = { Text(stringResource(R.string.commitment_settings_locked_title)) },
            text = { Text(stringResource(R.string.commitment_settings_locked_body)) },
            confirmButton = {
                TextButton(onClick = { gate.acknowledgeCommitmentBlock() }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}
