package com.sarim.app.activities

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.AuthenticationRequest
import androidx.biometric.AuthenticationResult
import androidx.biometric.AuthenticationResultCallback
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.compose.rememberAuthenticationLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sarim.app.BuildConfig
import com.sarim.app.R
import com.sarim.app.data.ApkUpdateInstaller
import com.sarim.app.data.DeviceOwnerRepository
import com.sarim.app.screens.challenges.ChallengeScaffold
import com.sarim.app.screens.challenges.MathChallenge
import com.sarim.app.screens.challenges.ReadingChallenge
import com.sarim.app.screens.challenges.TetrisChallenge
import com.sarim.app.screens.challenges.WordChallenge
import com.sarim.app.screens.common.ApkUpdateDialog
import com.sarim.app.screens.common.ApkUpdateIntroDialog
import com.sarim.app.security.CommitmentLock
import com.sarim.app.security.SecurityManager
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Entry gate of the app. Authentication is deliberately *not* triggered on open: the lock screen
 * is the first thing the user sees, and the biometric prompt only runs when they ask to get in.
 * Order is therefore: open -> "access app" -> biometrics -> challenge (if any).
 *
 * The commitment is the one thing this screen can't unlock: it is shown for orientation, and
 * nothing behind this gate can end it early.
 */
@Composable
fun LockScreen(onAuthenticated: () -> Unit) {
    var challenges by remember { mutableStateOf<List<SecurityManager.ChallengeType>?>(null) }
    var challengeIndex by remember { mutableIntStateOf(0) }
    var gaveUp by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val commitmentRemaining = remember { CommitmentLock.remainingMillis(context) }
    val commitmentPermanent = remember { CommitmentLock.isPermanent(context) }

    // Updating apps needs no authentication: only APKs of apps that are already installed go through.
    // Without the unknown sources restriction APKs install normally, so the updater is not offered.
    val apkUpdatesAvailable = remember {
        if (BuildConfig.PLAY_STORE_BUILD) {
            false
        } else {
            ApkUpdateInstaller.isUnknownSourcesRestricted(context)
        }
    }
    var showingApkIntro by remember { mutableStateOf(false) }
    var pickedApk by remember { mutableStateOf<Uri?>(null) }
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickedApk = uri
    }

    fun proceedAfterAuthentication() {
        authError = null
        gaveUp = false
        val required = SecurityManager.getAccessChallenges(context)
        if (required.isEmpty()) {
            onAuthenticated()
        } else {
            challengeIndex = 0
            challenges = required
        }
    }

    val launcher = rememberAuthenticationLauncher(
        resultCallback = object : AuthenticationResultCallback {
            override fun onAuthResult(result: AuthenticationResult) {
                when (result) {
                    is AuthenticationResult.Success -> proceedAfterAuthentication()
                    is AuthenticationResult.Error -> authError = result.errString.toString()
                    else -> {}
                }
            }

            override fun onAuthAttemptFailed() {
                authError = context.getString(R.string.biometric_error)
            }
        }
    )

    fun launchAuthentication() {
        val biometricManager = BiometricManager.from(context)
        val canUseBiometric = biometricManager.canAuthenticate(BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
        val canUseDeviceCredential = biometricManager.canAuthenticate(DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS

        when {
            canUseBiometric -> {
                val request = AuthenticationRequest.biometricRequest(
                    title = context.getString(R.string.biometric_title)
                ) {
                    setSubtitle(context.getString(R.string.biometric_subtitle))
                }
                launcher.launch(request)
            }

            canUseDeviceCredential -> {
                val request = AuthenticationRequest.credentialRequest(
                    title = context.getString(R.string.biometric_title)
                ) {
                    setSubtitle(context.getString(R.string.biometric_subtitle))
                }
                launcher.launch(request)
            }

            // No biometrics and no device credential configured: nothing to verify against.
            else -> proceedAfterAuthentication()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        val pending = challenges
        if (pending != null) {
            fun giveUp() {
                challenges = null
                gaveUp = true
            }

            fun completeCurrent() {
                if (challengeIndex + 1 >= pending.size) onAuthenticated() else challengeIndex++
            }

            BackHandler(onBack = ::giveUp)
            ChallengeScaffold(step = challengeIndex + 1, total = pending.size, onGiveUp = ::giveUp) {
                key(challengeIndex) {
                    when (pending[challengeIndex]) {
                        SecurityManager.ChallengeType.MATH -> MathChallenge(onSuccess = ::completeCurrent)
                        SecurityManager.ChallengeType.WORDS -> WordChallenge(onSuccess = ::completeCurrent)
                        SecurityManager.ChallengeType.READING -> ReadingChallenge(onSuccess = ::completeCurrent)
                        SecurityManager.ChallengeType.TETRIS -> TetrisChallenge(
                            minutes = SecurityManager.getTetrisMinutes(context),
                            onSuccess = ::completeCurrent
                        )
                    }
                }
            }
            return@Surface
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CommitmentBanner(
                    permanent = commitmentPermanent,
                    remaining = commitmentRemaining
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (gaveUp) {
                    Text(
                        stringResource(R.string.challenge_gave_up_message),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (DeviceOwnerRepository.isDeviceOwner()) {
                    SmallActionButton(
                        icon = Icons.Outlined.LockOpen,
                        title = stringResource(R.string.access_app),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = { launchAuthentication() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (apkUpdatesAvailable) {
                            QuietActionButton(
                                icon = Icons.Outlined.SystemUpdate,
                                title = stringResource(R.string.apk_update_button),
                                onClick = { showingApkIntro = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (authError != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        authError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (showingApkIntro) {
            ApkUpdateIntroDialog(
                onSelect = {
                    showingApkIntro = false
                    apkPicker.launch(arrayOf("application/vnd.android.package-archive"))
                },
                onDismiss = { showingApkIntro = false }
            )
        }

        pickedApk?.let { uri ->
            key(uri) { ApkUpdateDialog(uri = uri, onFinish = { pickedApk = null }) }
        }
    }
}

@Composable
private fun CommitmentBanner(permanent: Boolean, remaining: Long) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.HourglassBottom, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.commitment_active_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = if (permanent || remaining == Long.MAX_VALUE) {
                    stringResource(R.string.commitment_remaining_permanent)
                } else {
                    stringResource(
                        R.string.commitment_remaining_days,
                        (remaining / (24L * 60L * 60L * 1000L)).toInt()
                    )
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SmallActionButton(
    icon: ImageVector,
    title: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier.height(64.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QuietActionButton(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier.height(56.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}
