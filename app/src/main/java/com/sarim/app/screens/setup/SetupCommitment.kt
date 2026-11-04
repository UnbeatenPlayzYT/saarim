package com.sarim.app.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sarim.app.R
import com.sarim.app.data.DeviceOwnerRepository
import com.sarim.app.security.CommitmentLock

/**
 * The one screen that can't be skipped: the commitment is chosen before the app is usable, and
 * from that point [CommitmentLock] has no release path.
 *
 * "Permanent" is offered as its own choice rather than a very large number of days, because the
 * consequences differ: a timed commitment can still be ended through the 48-hour forced removal,
 * a permanent one cannot.
 */
@Composable
fun SetupCommitment(paddingValues: PaddingValues, onStarted: () -> Unit) {
    val context = LocalContext.current
    var selectedDays by remember { mutableStateOf<Int?>(CommitmentLock.DURATION_PRESETS_DAYS.first()) }
    var acknowledged by remember { mutableStateOf(false) }
    // The commit-state restrictions close ADB, which is how the owner privileges are granted;
    // granting them afterwards would be impossible until the period ended.
    val isDeviceOwner = DeviceOwnerRepository.isDeviceOwner()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Security,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(R.string.commitment_setup_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            stringResource(R.string.commitment_setup_intro),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 8.dp)) {
                CommitmentLock.DURATION_PRESETS_DAYS.forEach { days ->
                    DurationRow(
                        label = pluralStringResource(R.plurals.commitment_duration_days, days, days),
                        selected = selectedDays == days,
                        onSelect = { selectedDays = days }
                    )
                }

                DurationRow(
                    label = stringResource(R.string.commitment_duration_permanent),
                    supporting = stringResource(R.string.commitment_permanent_note),
                    selected = selectedDays == null,
                    onSelect = { selectedDays = null }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.commitment_pins_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.commitment_pins_body),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = acknowledged, onClick = { acknowledged = !acknowledged })
                .padding(vertical = 8.dp)
        ) {
            Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
            Text(
                stringResource(R.string.commitment_acknowledge),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        if (!isDeviceOwner) {
            Text(
                stringResource(R.string.protection_needs_owner),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        Button(
            onClick = {
                CommitmentLock.start(context, selectedDays)
                onStarted()
            },
            enabled = acknowledged && isDeviceOwner,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                stringResource(R.string.commitment_start_button),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DurationRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    supporting: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.padding(start = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
