package com.resqmesh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resqmesh.R
import com.resqmesh.domain.model.LocationFix
import com.resqmesh.domain.model.SosHazard
import com.resqmesh.domain.model.SosKind
import com.resqmesh.ui.home.LocationNote
import com.resqmesh.ui.home.SosComposer

/**
 * Dialog penyusun SOS.
 *
 * Isinya sengaja pendek: jenis darurat, kondisi korban, dan catatan. Makin
 * banyak isian, makin lama operator menunda tombol kirim, dan setiap detik itu
 * menentukan jarak yang harus ditempuh penolong.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosComposerDialog(
    composer: SosComposer,
    onSelectKind: (SosKind) -> Unit,
    onToggleHazard: (Int) -> Unit,
    onChangeVictimCount: (Int) -> Unit,
    onTextChange: (String) -> Unit,
    onRetryLocation: () -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sos_composer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SosKindPicker(selected = composer.kind, onSelect = onSelectKind)
                VictimCountStepper(
                    count = composer.victimCount,
                    onChange = onChangeVictimCount,
                )
                HazardPicker(selected = composer.hazards, onToggle = onToggleHazard)
                LocationPreview(fix = composer.fix, note = composer.note, onRetry = onRetryLocation)
                OutlinedTextField(
                    value = composer.text,
                    onValueChange = onTextChange,
                    label = { Text(stringResource(R.string.sos_detail_label)) },
                    placeholder = { Text(stringResource(R.string.sos_detail_hint)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSend, enabled = !composer.isSending) {
                if (composer.isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(stringResource(R.string.sos_send_now))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.home_sos_cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SosKindPicker(selected: SosKind, onSelect: (SosKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.sos_kind_label),
            style = MaterialTheme.typography.labelMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SosKind.entries.forEach { kind ->
                FilterChip(
                    selected = kind == selected,
                    onClick = { onSelect(kind) },
                    label = { Text(kind.label) },
                )
            }
        }
    }
}

@Composable
private fun VictimCountStepper(count: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.sos_victims_label),
            style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onChange(-1) }, enabled = count > 0) { Text("-") }
            Text(
                text = if (count == 0) stringResource(R.string.sos_victims_only_self) else count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = { onChange(1) }, enabled = count < 255) { Text("+") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HazardPicker(selected: Int, onToggle: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.sos_hazard_label),
            style = MaterialTheme.typography.labelMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SosHazard.NAMES.forEach { (bit, label) ->
                FilterChip(
                    selected = selected and bit != 0,
                    onClick = { onToggle(bit) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun LocationPreview(fix: LocationFix?, note: LocationNote, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (fix != null) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when {
                fix != null -> {
                    Text(
                        text = stringResource(R.string.sos_location_ready),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = fix.point.format(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.sos_location_accuracy,
                            fix.accuracyMeters ?: 0,
                            fix.ageSeconds,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                else -> {
                    Text(
                        text = stringResource(R.string.sos_location_missing),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = stringResource(note.labelRes()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.sos_location_retry))
                    }
                }
            }
        }
    }
}

@Composable
internal fun LocationNote.labelRes(): Int = when (this) {
    LocationNote.NONE -> R.string.sos_location_ready
    LocationNote.PERMISSION_MISSING -> R.string.sos_location_note_permission
    LocationNote.PROVIDER_DISABLED -> R.string.sos_location_note_provider
    LocationNote.NO_FRESH_FIX -> R.string.sos_location_note_stale
}
