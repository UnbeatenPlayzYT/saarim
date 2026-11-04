package com.sarim.app.screens.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sarim.app.R
import com.sarim.app.SarimAccessibilityService
import com.sarim.app.data.VisualBlockingSettings
import com.sarim.app.screens.common.RecoveryGateDialog
import com.sarim.app.screens.common.rememberRecoveryGate
import com.sarim.app.viewmodels.VisualBlockingViewModel

/**
 * Visual blocking, shown as what it is: a layer that is always on. There is no switch — the only
 * settings left are the ones that make it *more* effective (which extra apps to watch, how
 * sensitive it is, how quickly a repeat offender is suspended).
 */
@Composable
fun VisualBlockingScreen(viewModel: VisualBlockingViewModel = viewModel()) {
    var showAppSelectionDialog by remember { mutableStateOf(false) }
    val recoveryGate = rememberRecoveryGate()
    val sessionActive = recoveryGate.isSessionActive
    val accessibilityActive = SarimAccessibilityService.isRunning

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                stringResource(R.string.visual_blocking_explanation),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.visual_blocking_enable)) },
                supportingContent = {
                    if (!accessibilityActive) {
                        Text(
                            stringResource(R.string.visual_blocking_requires_accessibility),
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(stringResource(R.string.always_on_desc))
                    }
                },
                leadingContent = { Icon(Icons.Outlined.ImageSearch, null) },
                trailingContent = {
                    Switch(checked = true, enabled = false, onCheckedChange = null)
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.visual_blocking_apps_section)) },
                supportingContent = {
                    val selectedCount = viewModel.targetPackages.size
                    Text(
                        pluralStringResource(
                            R.plurals.visual_blocking_apps_selected,
                            selectedCount,
                            selectedCount
                        )
                    )
                },
                trailingContent = {
                    Button(onClick = { recoveryGate.run { showAppSelectionDialog = true } }) {
                        Text(stringResource(R.string.select_apps))
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }

        item {
            Text(
                stringResource(R.string.visual_blocking_categories_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
            )
            Text(
                stringResource(R.string.visual_blocking_categories_desc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        items(VisualBlockingSettings.SELECTABLE_CATEGORIES) { category ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = viewModel.selectedCategories.contains(category),
                    enabled = false,
                    onCheckedChange = null
                )
                Text(categoryLabel(category), modifier = Modifier.padding(start = 8.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.always_on),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

        item {
            Text(
                stringResource(R.string.visual_blocking_threshold_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                stringResource(R.string.visual_blocking_threshold_desc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = viewModel.threshold,
                    enabled = sessionActive,
                    onValueChange = { viewModel.updateThreshold(it) },
                    // The upper bound keeps the detector from being numbed into uselessness; the
                    // lower one stops a blank wall from being flagged as adult on its own.
                    valueRange = 0.3f..0.7f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${(viewModel.threshold * 100).toInt()}%",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.visual_blocking_suspend)) },
                supportingContent = { Text(stringResource(R.string.visual_blocking_suspend_desc)) },
                leadingContent = { Icon(Icons.Outlined.Timer, null) },
                trailingContent = {
                    Checkbox(checked = true, enabled = false, onCheckedChange = null)
                }
            )

            NumberSettingField(
                label = stringResource(R.string.visual_blocking_suspend_count),
                supportingText = stringResource(R.string.visual_blocking_suspend_count_desc),
                value = viewModel.suspendBlockCount,
                enabled = sessionActive,
                onValueChange = { viewModel.updateSuspendBlockCount(it) }
            )
            NumberSettingField(
                label = stringResource(R.string.visual_blocking_suspend_window),
                supportingText = stringResource(R.string.visual_blocking_suspend_window_desc),
                value = viewModel.suspendWindowMinutes,
                enabled = sessionActive,
                onValueChange = { viewModel.updateSuspendWindowMinutes(it) }
            )
            NumberSettingField(
                label = stringResource(R.string.visual_blocking_suspend_duration),
                supportingText = stringResource(R.string.visual_blocking_suspend_duration_desc),
                value = viewModel.suspendDurationMinutes,
                enabled = sessionActive,
                onValueChange = { viewModel.updateSuspendDurationMinutes(it) }
            )
            Text(
                pluralStringResource(
                    R.plurals.visual_blocking_suspend_summary,
                    viewModel.suspendBlockCount,
                    viewModel.suspendBlockCount,
                    viewModel.suspendWindowMinutes,
                    viewModel.suspendDurationMinutes
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showAppSelectionDialog) {
        MonitoredAppsDialog(
            viewModel = viewModel,
            onDismiss = { showAppSelectionDialog = false }
        )
    }

    RecoveryGateDialog(recoveryGate)
}

/** App picker for the monitored list: the built-in defaults are shown but can't be deselected. */
@Composable
private fun MonitoredAppsDialog(
    viewModel: VisualBlockingViewModel,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }

    val filteredApps = remember(searchQuery, viewModel.apps, showSystemApps) {
        viewModel.apps
            .filter {
                (showSystemApps || !it.isSystem) &&
                    (it.name.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true))
            }
            .sortedBy { it.name.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.visual_blocking_apps_section)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.search_apps)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )
                    FilterChip(
                        selected = showSystemApps,
                        onClick = { showSystemApps = !showSystemApps },
                        label = { Text(stringResource(R.string.show_system_apps)) },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                if (viewModel.isLoadingApps) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(filteredApps) { app ->
                            val mandatory =
                                app.packageName in VisualBlockingSettings.DEFAULT_TARGET_PACKAGES
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !mandatory) {
                                        viewModel.toggleAppSelection(app.packageName)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = viewModel.targetPackages.contains(app.packageName),
                                    enabled = !mandatory,
                                    onCheckedChange = { viewModel.toggleAppSelection(app.packageName) }
                                )
                                Column(modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)) {
                                    Text(app.name, fontWeight = FontWeight.Bold)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                }
                                if (mandatory) {
                                    Text(
                                        stringResource(R.string.always_on),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}

/**
 * Label + numeric text field row used by the suspension settings. The field is allowed to go
 * empty while the user retypes a number — [onValueChange] then reports 0, which the view model
 * treats as "not a usable value yet" and doesn't persist.
 */
@Composable
private fun NumberSettingField(
    label: String,
    supportingText: String,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    var textFieldValue by remember(value) { mutableStateOf(value.toString()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(supportingText, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = textFieldValue,
            enabled = enabled,
            onValueChange = { newValue ->
                if (newValue.all { it.isDigit() } && newValue.length <= 4) {
                    textFieldValue = newValue
                    onValueChange(newValue.toIntOrNull() ?: 0)
                }
            },
            modifier = Modifier
                .width(96.dp)
                .padding(start = 8.dp),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
    }
}

@Composable
private fun categoryLabel(category: String): String = when (category) {
    "drawings" -> stringResource(R.string.nsfw_category_drawings)
    "hentai" -> stringResource(R.string.nsfw_category_hentai)
    "porn" -> stringResource(R.string.nsfw_category_porn)
    "sexy" -> stringResource(R.string.nsfw_category_sexy)
    else -> category
}
