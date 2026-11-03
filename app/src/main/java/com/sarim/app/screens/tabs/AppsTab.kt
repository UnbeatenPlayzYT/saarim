package com.sarim.app.screens.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sarim.app.R
import com.sarim.app.models.AppItem
import com.sarim.app.screens.common.RecoveryGateDialog
import com.sarim.app.screens.common.rememberRecoveryGate
import com.sarim.app.viewmodels.AppsViewModel

/**
 * The app list: block an app (hide it), suspend it, or lock its uninstall. No time limits and no
 * usage tracking — the point of this screen is to take a route to adult content away, not to
 * ration screen time.
 *
 * The one piece of automation on top of the list is the store's own 18+ rating: an app the store
 * labels explicit is suspended on install by the accessibility service, and shows up here flagged
 * so the block can be made permanent.
 */
@Composable
fun AppsTab(viewModel: AppsViewModel = viewModel()) {
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var onlyBlocked by remember { mutableStateOf(false) }
    var onlyExplicit by remember { mutableStateOf(false) }
    val recoveryGate = rememberRecoveryGate()

    val filteredApps = remember(
        viewModel.apps, searchQuery, showSystemApps, onlyBlocked, onlyExplicit
    ) {
        viewModel.apps.filter { app ->
            (showSystemApps || !app.isSystem) &&
                (!onlyBlocked || app.isHidden || app.isSuspended || app.isUninstallBlocked) &&
                (!onlyExplicit || app.hasExplicitContent) &&
                (searchQuery.isBlank() ||
                    app.name.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.search_apps)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = showSystemApps,
                onClick = { showSystemApps = !showSystemApps },
                label = { Text(stringResource(R.string.show_system_apps)) }
            )
            FilterChip(
                selected = onlyBlocked,
                onClick = { onlyBlocked = !onlyBlocked },
                label = { Text(stringResource(R.string.blocked)) }
            )
            FilterChip(
                selected = onlyExplicit,
                onClick = { onlyExplicit = !onlyExplicit },
                label = { Text(stringResource(R.string.explicit_content)) }
            )
        }

        if (viewModel.isLoading && viewModel.apps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredApps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_apps))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        onBlock = { hidden ->
                            // Blocking is strengthening; unblocking is exactly what a commitment freezes.
                            recoveryGate.run(strengtheningAction = hidden) {
                                viewModel.blockApp(app.packageName, hidden)
                            }
                        },
                        onSuspend = { suspended ->
                            recoveryGate.run(strengtheningAction = suspended) {
                                viewModel.suspendApp(app.packageName, suspended)
                            }
                        },
                        onUninstallBlocked = { block ->
                            recoveryGate.run { viewModel.setUninstallBlocked(app.packageName, block) }
                        }
                    )
                }
            }
        }
    }

    RecoveryGateDialog(recoveryGate)
}

@Composable
private fun AppRow(
    app: AppItem,
    onBlock: (Boolean) -> Unit,
    onSuspend: (Boolean) -> Unit,
    onUninstallBlocked: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.name, fontWeight = FontWeight.Bold)
                    if (app.hasExplicitContent) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                "18+",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            supportingContent = {
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            leadingContent = {
                Image(
                    bitmap = app.icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            },
            trailingContent = {
                Row {
                    IconToggleButton(
                        checked = app.isHidden,
                        onCheckedChange = onBlock
                    ) {
                        Icon(
                            if (app.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(
                                if (app.isHidden) R.string.unblock else R.string.block
                            ),
                            tint = if (app.isHidden) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconToggleButton(
                        checked = app.isSuspended,
                        onCheckedChange = onSuspend
                    ) {
                        Icon(
                            Icons.Default.PauseCircle,
                            contentDescription = stringResource(
                                if (app.isSuspended) R.string.unsuspend else R.string.suspend
                            ),
                            tint = if (app.isSuspended) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconToggleButton(
                        checked = app.isUninstallBlocked,
                        onCheckedChange = onUninstallBlocked
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = stringResource(R.string.uninstall_blocked),
                            tint = if (app.isUninstallBlocked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}
