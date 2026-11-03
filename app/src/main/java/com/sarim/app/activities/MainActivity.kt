package com.sarim.app.activities

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.AppBlocking
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sarim.app.AppPrefs
import com.sarim.app.R
import com.sarim.app.data.ApkUpdateInstaller
import com.sarim.app.screens.common.OnboardingCarousel
import com.sarim.app.screens.setup.SetupCommitment
import com.sarim.app.screens.setup.SetupDeviceOwnerPrivileges
import com.sarim.app.screens.setup.SetupRecovery
import com.sarim.app.screens.tabs.*
import com.sarim.app.security.CommitmentLock
import com.sarim.app.security.SecurityManager
import com.sarim.app.ui.theme.SarimTheme
import com.sarim.app.viewmodels.DeviceOwnerViewModel
import com.sarim.app.viewmodels.NavigationViewModel
import kotlinx.coroutines.delay

class MainActivity : LocalizedActivity() {

    private val authenticated = mutableStateOf(false)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SecurityManager.consumeDebugAutoStartSession(this)) authenticated.value = true
        SecurityManager.consumeDebugRestoreUnknownSourcesRestriction(this)
        ApkUpdateInstaller.restoreUnknownSourcesRestrictionsIfIdle(this)
        enableEdgeToEdge()
        setContent {
            SarimTheme {
                val viewModel: DeviceOwnerViewModel = viewModel()
                viewModel.addShizukuListener()
                val navViewModel: NavigationViewModel = viewModel()

                var onboardingCompleted by remember {
                    mutableStateOf(AppPrefs.isOnboardingCompleted(this))
                }

                // Written once, by the setup screen; held in state so starting a commitment
                // advances the flow without needing a restart.
                var commitmentStarted by remember {
                    mutableStateOf(CommitmentLock.hasStarted(this))
                }

                val currentScreen = navViewModel.selectedTab()
                val isRoot = currentScreen in listOf("protection", "apps", "config")

                BackHandler(enabled = !isRoot) {
                    navViewModel.goBack()
                }

                var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        currentTime = System.currentTimeMillis()
                        delay(1000)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.app_name)) },
                            navigationIcon = {
                                if (!isRoot) {
                                    IconButton(onClick = { navViewModel.goBack() }) {
                                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                                    }
                                }
                            },
                            actions = {
                                if (SecurityManager.isSessionActive()) {
                                    val remaining = SecurityManager.sessionEndTime - currentTime
                                    val minutes = (remaining / 1000) / 60
                                    val seconds = (remaining / 1000) % 60

                                    // Session countdown as a compact pill, then end-of-session button.
                                    Surface(
                                        shape = MaterialTheme.shapes.extraLarge,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp, vertical = 4.dp
                                            )
                                        ) {
                                            Icon(
                                                Icons.Outlined.LockClock,
                                                contentDescription = null,
                                                modifier = Modifier.padding(end = 6.dp)
                                            )
                                            Text(
                                                text = "%02d:%02d".format(minutes, seconds),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                    IconButton(onClick = { SecurityManager.renewSession() }) {
                                        Icon(Icons.Outlined.Refresh, stringResource(R.string.renew_session))
                                    }
                                    IconButton(onClick = { SecurityManager.endSession() }) {
                                        Icon(Icons.AutoMirrored.Outlined.Logout, null)
                                    }
                                }
                            }
                        )
                    },
                    bottomBar = {
                        if (!SecurityManager.isRecoveryCodeSet(this) || !authenticated.value) return@Scaffold

                        val tabs = listOf(
                            Triple("protection", R.string.protection, Icons.Outlined.Security),
                            Triple("apps", R.string.apps, Icons.Filled.AppBlocking),
                            Triple("config", R.string.config, Icons.Outlined.Settings)
                        )

                        val selectedBaseTab = when (currentScreen) {
                            "apps" -> "apps"
                            "config", "setup_device_owner", "system_restrictions",
                            "browser_restrictions", "blocked_words", "visual_blocking" -> "config"
                            else -> "protection"
                        }

                        NavigationBar {
                            tabs.forEach { (route, labelRes, icon) ->
                                NavigationBarItem(
                                    selected = selectedBaseTab == route,
                                    onClick = { navViewModel.navigateTo(route) },
                                    label = { Text(stringResource(labelRes)) },
                                    icon = { Icon(icon, contentDescription = null) }
                                )
                            }
                        }
                    }
                ) { innerPadding ->

                    when {
                        !SecurityManager.isRecoveryCodeSet(this) -> SetupRecovery(innerPadding)

                        // Owner privileges need ADB, and the commit-state restrictions close ADB,
                        // so they are granted before the commitment is chosen, never after.
                        !viewModel.isDeviceOwner() -> SetupDeviceOwnerPrivileges()

                        !commitmentStarted -> SetupCommitment(
                            paddingValues = innerPadding,
                            onStarted = { commitmentStarted = true }
                        )

                        !authenticated.value -> Box(
                            modifier = Modifier
                                .padding(innerPadding)
                                .consumeWindowInsets(innerPadding)
                        ) {
                            LockScreen(onAuthenticated = { authenticated.value = true })
                        }

                        !onboardingCompleted -> OnboardingCarousel(
                            onFinish = {
                                AppPrefs.markOnboardingCompleted(this)
                                onboardingCompleted = true
                            }
                        )

                        else -> Box(modifier = Modifier.padding(innerPadding)) {
                            when (currentScreen) {
                                "protection" -> ProtectionTab()
                                "apps" -> AppsTab()
                                "config" -> ConfigTab()
                                "setup_device_owner" -> SetupDeviceOwnerPrivileges()
                                "system_restrictions" -> RestrictionsTab()
                                "browser_restrictions" -> BrowserRestrictionsScreen()
                                "blocked_words" -> BlockedWordsScreen()
                                "visual_blocking" -> VisualBlockingScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SecurityManager.endSession()
    }
}
