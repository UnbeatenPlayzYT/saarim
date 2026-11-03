package com.sarim.app.screens.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sarim.app.R
import com.sarim.app.data.BrowserRestrictionsManager
import com.sarim.app.screens.common.RecoveryGateDialog
import com.sarim.app.screens.common.rememberRecoveryGate
import com.sarim.app.data.ProtectionDefaults
import com.sarim.app.models.BlockedList
import com.sarim.app.viewmodels.AppsViewModel
import com.sarim.app.viewmodels.BrowserRestrictionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserRestrictionsScreen(
    viewModel: BrowserRestrictionsViewModel = viewModel(),
    appsViewModel: AppsViewModel = viewModel()
) {
    var showEditDialog by remember { mutableStateOf<BlockedList?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    val recoveryGate = rememberRecoveryGate()
    val context = LocalContext.current

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.allowed_browsers),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        viewModel.browsers.forEachIndexed { index, browser ->
                            val manager = BrowserRestrictionsManager(context)
                            val notSupported = !manager.supportsRestrictions(browser.packageName)
                            ListItem(
                                headlineContent = { Text(browser.name) },
                                supportingContent = {
                                    Column {
                                        Text(browser.packageName)
                                        if (notSupported) {
                                            Text(
                                                stringResource(R.string.browser_support_warning),
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                },
                                leadingContent = {
                                    Image(
                                        bitmap = browser.icon.toBitmap().asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp)
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = !browser.isSuspended,
                                        onCheckedChange = { checked ->
                                            val wasSuspended = browser.isSuspended
                                            viewModel.browsers[index] = browser.copy(isSuspended = !checked)
                                            recoveryGate.run(
                                                onCancel = {
                                                    viewModel.browsers[index] = browser.copy(isSuspended = wasSuspended)
                                                }
                                            ) {
                                                appsViewModel.suspendApp(browser.packageName, !checked)
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.blocked_sites),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = { 
                        isCreatingNew = true
                        showEditDialog = BlockedList("", "", emptyList())
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
            }

            items(viewModel.blockedLists, key = { "url_${it.id}" }) { list ->
                BlockedListAccordion(
                    list = list,
                    mandatory = list.id == ProtectionDefaults.MANDATORY_BROWSER_LIST_ID,
                    onEdit = { 
                        isCreatingNew = false
                        showEditDialog = list 
                    },
                    onDelete = {
                        recoveryGate.run { viewModel.removeList(list.id) }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

        }
    }

    if (showEditDialog != null) {
        val currentList = showEditDialog!!
        var title by remember { mutableStateOf(currentList.title) }
        var sites by remember { mutableStateOf(currentList.sites.joinToString("\n")) }
        
        AlertDialog(
            onDismissRequest = { showEditDialog = null },
            title = { 
                Text(
                    if (isCreatingNew) {
                        stringResource(R.string.add_site)
                    } else currentList.title
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.list_title)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sites,
                        onValueChange = { sites = it },
                        label = { Text(stringResource(R.string.sites_lines)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank()) {
                        recoveryGate.run {
                            viewModel.saveOrUpdateList(
                                if (isCreatingNew) null else currentList.id,
                                title,
                                sites
                            )
                        }
                    }
                    showEditDialog = null
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    RecoveryGateDialog(recoveryGate)
}

@Composable
fun BlockedListAccordion(
    list: BlockedList,
    mandatory: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(list.title, style = MaterialTheme.typography.titleMedium)
                    Text("${list.sites.size} sites", style = MaterialTheme.typography.labelSmall)
                }
                if (mandatory) {
                    Text(
                        stringResource(R.string.always_on),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "")
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider()
                    list.sites.forEach { site ->
                        Text(
                            site,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
