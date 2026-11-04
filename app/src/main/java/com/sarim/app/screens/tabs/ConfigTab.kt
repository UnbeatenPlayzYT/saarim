package com.sarim.app.screens.tabs

import android.app.admin.DevicePolicyManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Adb
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.NoAdultContent
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sarim.app.R
import com.sarim.app.SarimAccessibilityService
import com.sarim.app.data.ProtectionDefaults
import com.sarim.app.screens.common.OnboardingCarousel
import com.sarim.app.screens.common.RecoveryGateDialog
import com.sarim.app.screens.common.RecoveryGenerateDialog
import com.sarim.app.screens.common.rememberRecoveryGate
import com.sarim.app.security.CommitmentLock
import com.sarim.app.security.SecurityManager
import com.sarim.app.utils.LocaleUtils
import com.sarim.app.viewmodels.DeviceOwnerViewModel
import com.sarim.app.viewmodels.NavigationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ConfigTab(
    viewModel: DeviceOwnerViewModel = viewModel(),
    navViewModel: NavigationViewModel = viewModel()
) {
    var showDnsDialog by remember { mutableStateOf(false) }
    var dnsErrorRes by remember { mutableStateOf<Int?>(null) }
    var isApplyingDns by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showKeyboardDialog by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var showHowItWorks by remember { mutableStateOf(false) }
    var showStartForcedRemovalDialog by remember { mutableStateOf(false) }
    var showCancelForcedRemovalDialog by remember { mutableStateOf(false) }
    var showFinishForcedRemovalDialog by remember { mutableStateOf(false) }
    var confirmForcedRemoval by remember { mutableStateOf(false) }
    val recoveryGate = rememberRecoveryGate()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val shizukuNotRunningMsg = stringResource(R.string.shizuku_not_running)
    val ownerPrivilegesFirstMsg = stringResource(R.string.get_owner_privileges_first)
    val advancedBlockingRequiredMsg = stringResource(R.string.advanced_blocking_required)

    var shuffleKeyboard by remember { mutableStateOf(SecurityManager.isShuffleKeyboardEnabled(context)) }
    val advancedBlocking = SarimAccessibilityService.isRunning

    val forcedRemovalRemaining by remember {
        mutableLongStateOf(SecurityManager.getForcedRemovalRemainingTime(context))
    }
    val permanent = CommitmentLock.isPermanent(context)
    val commitmentRemaining = CommitmentLock.remainingMillis(context)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.commitment_active_title)) },
                    supportingContent = {
                        Text(
                            when {
                                permanent -> stringResource(R.string.commitment_remaining_permanent)
                                commitmentRemaining > 0L -> {
                                    val daysLeft = (commitmentRemaining / (24L * 60L * 60L * 1000L)).toInt()
                                    pluralStringResource(
                                        R.plurals.commitment_remaining_days,
                                        daysLeft,
                                        daysLeft
                                    )
                                }
                                else -> stringResource(R.string.commitment_ended)
                            }
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.Security, "") }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.device_owner_state)) },
                    supportingContent = { Text(stringResource(R.string.device_owner_description)) },
                    leadingContent = { Icon(Icons.Outlined.Adb, "") },
                    trailingContent = {
                        val owner = viewModel.isDeviceOwner()
                        val badgeColor = if (owner) Color(26, 163, 63) else Color.Red
                        val badgeText = if (owner) stringResource(R.string.granted) else stringResource(R.string.not_granted)
                        Badge(containerColor = badgeColor, contentColor = Color.White) {
                            Text(badgeText, style = MaterialTheme.typography.bodyMedium)
                        }
                    },
                    modifier = Modifier.clickable(onClick = { navViewModel.navigateTo("setup_device_owner") })
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.advanced_blocking)) },
                    supportingContent = { Text(stringResource(R.string.activity_blocker_description)) },
                    leadingContent = { Icon(Icons.Outlined.Accessibility, "") },
                    trailingContent = {
                        Switch(advancedBlocking, onCheckedChange = { checked ->
                            if (!Shizuku.pingBinder()) {
                                scope.launch { snackbarHostState.showSnackbar(shizukuNotRunningMsg) }
                                return@Switch
                            }
                            // Turning it back on only adds protection, so a running commitment
                            // lets it through; turning it off does not.
                            recoveryGate.run(strengtheningAction = checked) {
                                if (!checked) SarimAccessibilityService.prepareServiceDisable()
                                viewModel.changeAccessibilityPermission(checked)
                            }
                        })
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.visual_blocking)) },
                    supportingContent = { Text(stringResource(R.string.always_on_desc)) },
                    leadingContent = { Icon(Icons.Outlined.ImageSearch, "") },
                    modifier = Modifier.clickable {
                        if (!advancedBlocking) {
                            scope.launch { snackbarHostState.showSnackbar(advancedBlockingRequiredMsg) }
                            return@clickable
                        }
                        navViewModel.navigateTo("visual_blocking")
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.blocked_words_feat)) },
                    supportingContent = { Text(stringResource(R.string.blocked_words_feat_description)) },
                    leadingContent = { Icon(Icons.Outlined.NoAdultContent, "") },
                    modifier = Modifier.clickable {
                        if (!advancedBlocking) {
                            scope.launch { snackbarHostState.showSnackbar(advancedBlockingRequiredMsg) }
                            return@clickable
                        }
                        navViewModel.navigateTo("blocked_words")
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.browser_restrictions)) },
                    supportingContent = { Text(stringResource(R.string.browser_restrictions_desc)) },
                    leadingContent = { Icon(Icons.Outlined.Web, "") },
                    modifier = Modifier.clickable { navViewModel.navigateTo("browser_restrictions") }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.dns_settings)) },
                    supportingContent = { Text(stringResource(R.string.dns_description)) },
                    leadingContent = { Icon(Icons.Outlined.Dns, "") },
                    modifier = Modifier.clickable {
                        if (!viewModel.isDeviceOwner()) {
                            scope.launch { snackbarHostState.showSnackbar(ownerPrivilegesFirstMsg) }
                            return@clickable
                        }
                        dnsErrorRes = null
                        showDnsDialog = true
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.system_restrictions)) },
                    supportingContent = { Text(stringResource(R.string.always_on_desc)) },
                    leadingContent = { Icon(Icons.Outlined.Block, "") },
                    modifier = Modifier.clickable { navViewModel.navigateTo("system_restrictions") }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.block_torrents)) },
                    supportingContent = { Text(stringResource(R.string.always_on_desc)) },
                    leadingContent = { Icon(Icons.Outlined.FileDownload, "") },
                    trailingContent = { Switch(checked = true, enabled = false, onCheckedChange = null) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.block_tor)) },
                    supportingContent = { Text(stringResource(R.string.always_on_desc)) },
                    leadingContent = { Icon(Icons.Outlined.Security, "") },
                    trailingContent = { Switch(checked = true, enabled = false, onCheckedChange = null) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.how_it_works)) },
                    supportingContent = { Text(stringResource(R.string.how_it_works_desc)) },
                    leadingContent = { Icon(Icons.Outlined.Info, null) },
                    modifier = Modifier.clickable { showHowItWorks = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.language_settings)) },
                    supportingContent = { Text(stringResource(R.string.language_description)) },
                    leadingContent = { Icon(Icons.Outlined.Language, "") },
                    modifier = Modifier.clickable { showLanguageDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                val keyboardLabel = if (shuffleKeyboard)
                    stringResource(R.string.keyboard_shuffle) else stringResource(R.string.keyboard_normal)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.keyboard_type)) },
                    supportingContent = { Text(keyboardLabel) },
                    leadingContent = { Icon(Icons.Outlined.Keyboard, "") },
                    modifier = Modifier.clickable { showKeyboardDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.change_recovery_code)) },
                    supportingContent = { Text(stringResource(R.string.change_recovery_code_desc)) },
                    leadingContent = { Icon(Icons.Outlined.VpnKey, "") },
                    modifier = Modifier.clickable { recoveryGate.run { showRecoveryDialog = true } }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            // A permanent commitment deliberately has no way out, so the escape hatch isn't even
            // listed for one.
            if (!permanent) {
                item {
                    val forcedRemovalText = when {
                        forcedRemovalRemaining > 0 -> {
                            val hours = (forcedRemovalRemaining / (1000 * 60 * 60)).toInt()
                            val minutes = ((forcedRemovalRemaining / (1000 * 60)) % 60).toInt()
                            if (hours > 0) stringResource(R.string.time_hours_minutes, hours, minutes)
                            else stringResource(R.string.time_minutes, minutes)
                        }
                        forcedRemovalRemaining == 0L -> stringResource(R.string.active)
                        else -> null
                    }

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.forced_removal)) },
                        supportingContent = { Text(stringResource(R.string.forced_removal_desc)) },
                        leadingContent = { Icon(Icons.Outlined.LockClock, "") },
                        trailingContent = forcedRemovalText?.let {
                            {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier.clickable {
                            when {
                                forcedRemovalRemaining < 0 -> showStartForcedRemovalDialog = true
                                forcedRemovalRemaining == 0L -> showFinishForcedRemovalDialog = true
                                else -> showCancelForcedRemovalDialog = true
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            } else {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.forced_removal)) },
                        supportingContent = { Text(stringResource(R.string.commitment_forced_removal_disabled)) },
                        leadingContent = { Icon(Icons.Outlined.LockClock, "") },
                        trailingContent = { Switch(checked = false, enabled = false, onCheckedChange = null) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showStartForcedRemovalDialog) {
        AlertDialog(
            onDismissRequest = { showStartForcedRemovalDialog = false },
            title = { Text(stringResource(R.string.forced_removal_dialog_title)) },
            text = { Text(stringResource(R.string.forced_removal_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        SecurityManager.startForcedRemoval(context)
                        showStartForcedRemovalDialog = false
                        confirmForcedRemoval = false
                        (context as? android.app.Activity)?.recreate()
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showStartForcedRemovalDialog = false; confirmForcedRemoval = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCancelForcedRemovalDialog) {
        AlertDialog(
            onDismissRequest = { showCancelForcedRemovalDialog = false },
            title = { Text(stringResource(R.string.forced_removal_cancel_dialog_title)) },
            text = { Text(stringResource(R.string.forced_removal_cancel_dialog_text)) },
            confirmButton = {
                TextButton(onClick = {
                    SecurityManager.cancelForcedRemoval(context)
                    showCancelForcedRemovalDialog = false
                    (context as? android.app.Activity)?.recreate()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelForcedRemovalDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showFinishForcedRemovalDialog) {
        AlertDialog(
            onDismissRequest = {
                showFinishForcedRemovalDialog = false
                confirmForcedRemoval = false
            },
            title = { Text(stringResource(R.string.forced_removal_finish_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.forced_removal_finish_dialog_text))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { confirmForcedRemoval = !confirmForcedRemoval }
                            .padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = confirmForcedRemoval, onCheckedChange = { confirmForcedRemoval = it })
                        Text(
                            stringResource(R.string.forced_removal_confirm_checkbox),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = confirmForcedRemoval,
                    onClick = {
                        viewModel.removeDeviceOwner()
                        showFinishForcedRemovalDialog = false
                        confirmForcedRemoval = false
                        (context as? android.app.Activity)?.recreate()
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    SecurityManager.cancelForcedRemoval(context)
                    showFinishForcedRemovalDialog = false
                    confirmForcedRemoval = false
                    (context as? android.app.Activity)?.recreate()
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showDnsDialog) {
        val currentDns = viewModel.getPrivateDNS()
        val externalScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        DnsSelectionDialog(
            currentDns = currentDns,
            errorRes = dnsErrorRes,
            isLoading = isApplyingDns,
            onDismiss = { if (!isApplyingDns) showDnsDialog = false },
            onApply = { host ->
                recoveryGate.run {
                    dnsErrorRes = null
                    isApplyingDns = true
                    scope.launch {
                        val result = withTimeoutOrNull(15000.milliseconds) {
                            externalScope.async { viewModel.setPrivateDNS(host) }.await()
                        }
                        isApplyingDns = false
                        if (result == DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR) {
                            showDnsDialog = false
                        } else {
                            dnsErrorRes = when (result) {
                                DevicePolicyManager.PRIVATE_DNS_SET_ERROR_HOST_NOT_SERVING ->
                                    R.string.dns_error_not_serving
                                else -> R.string.dns_error_failure
                            }
                        }
                    }
                }
            }
        )
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            onDismiss = { showLanguageDialog = false },
            onApply = { languageCode ->
                LocaleUtils.setLocale(context, languageCode)
                showLanguageDialog = false
            }
        )
    }

    if (showKeyboardDialog) {
        KeyboardTypeDialog(
            currentShuffle = shuffleKeyboard,
            onDismiss = { showKeyboardDialog = false },
            onApply = { isShuffle ->
                shuffleKeyboard = isShuffle
                SecurityManager.setShuffleKeyboardEnabled(context, isShuffle)
                showKeyboardDialog = false
            }
        )
    }

    if (showRecoveryDialog) {
        RecoveryGenerateDialog(
            onDismiss = { showRecoveryDialog = false },
            onConfirm = { code ->
                SecurityManager.saveRecoveryCode(context, code)
                showRecoveryDialog = false
            }
        )
    }

    if (showHowItWorks) {
        OnboardingCarousel(onFinish = { showHowItWorks = false })
    }

    RecoveryGateDialog(recoveryGate)
}

/**
 * The private-DNS picker, limited to family/adult-filtering resolvers. There is deliberately no
 * "automatic", no "off" and no free-text host: each of those is a way to point the device at a
 * resolver that ignores the filtering, and the device-owner restriction already forbids changing
 * it from Android's own settings.
 */
@Composable
fun DnsSelectionDialog(
    currentDns: String?,
    errorRes: Int? = null,
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var selectedHost by remember {
        mutableStateOf(
            currentDns?.takeIf { it in ProtectionDefaults.FILTERING_DNS_HOSTS }
                ?: ProtectionDefaults.FILTERING_DNS_HOST
        )
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(stringResource(R.string.dns_settings)) },
        text = {
            Column {
                ProtectionDefaults.FILTERING_DNS_HOSTS.forEach { host ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isLoading) { selectedHost = host }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedHost == host,
                            onClick = { selectedHost = host },
                            enabled = !isLoading
                        )
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(
                                ProtectionDefaults.FILTERING_DNS_LABELS[host] ?: host,
                                fontWeight = FontWeight.Bold
                            )
                            Text(host, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (errorRes != null) {
                    Text(
                        text = stringResource(errorRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading,
                onClick = { onApply(selectedHost) }
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.apply))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun LanguageSelectionDialog(
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    val context = LocalContext.current
    val currentLocale = LocaleUtils.getLocale(context)
    val options = listOf(
        "en" to "English",
        "pt" to "Português",
        "ar" to "العربية"
    )

    var selectedOption by remember {
        mutableStateOf(
            options.firstOrNull { currentLocale.startsWith(it.first) }?.first
                ?: LocaleUtils.DEFAULT_LANGUAGE
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_settings)) },
        text = {
            Column {
                options.forEach { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = code }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedOption == code, onClick = { selectedOption = code })
                        Text(name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(selectedOption) }) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
fun KeyboardTypeDialog(
    currentShuffle: Boolean,
    onDismiss: () -> Unit,
    onApply: (Boolean) -> Unit
) {
    var shuffle by remember { mutableStateOf(currentShuffle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keyboard_type)) },
        text = {
            Column {
                Text(stringResource(R.string.keyboard_description))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { shuffle = false }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = !shuffle, onClick = { shuffle = false })
                    Text(stringResource(R.string.keyboard_normal), modifier = Modifier.padding(start = 8.dp))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { shuffle = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = shuffle, onClick = { shuffle = true })
                    Text(stringResource(R.string.keyboard_shuffle), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(shuffle) }) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
