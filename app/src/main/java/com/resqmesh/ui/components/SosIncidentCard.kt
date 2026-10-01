package com.resqmesh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resqmesh.R
import com.resqmesh.domain.model.SosIncident

/**
 * Kartu satu insiden darurat.
 *
 * Ada insiden yang belum punya koordinat, karena SOS_DETAIL tiba lebih dulu.
 * Kasus itu tetap ditampilkan lengkap dengan statusnya, daripada disembunyikan
 * sampai lokasi datang: penolong perlu tahu ada darurat sebelum tahu di mana.
 */
@Composable
fun SosIncidentCard(
    incident: SosIncident,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = incident.kind.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (incident.victimCount <= 0) {
                        stringResource(R.string.sos_card_victims_self)
                    } else {
                        stringResource(R.string.sos_card_victims, incident.victimCount)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (incident.hazardLabel.isNotEmpty()) {
                Text(text = incident.hazardLabel, style = MaterialTheme.typography.bodyMedium)
            }

            when {
                incident.point != null -> {
                    Text(
                        text = incident.point.format(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = onOpenMap) {
                        Text(stringResource(R.string.sos_card_open_map))
                    }
                }

                else -> {
                    Text(
                        text = stringResource(R.string.sos_card_waiting_location),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (incident.hasDetail) {
                Text(text = incident.text, style = MaterialTheme.typography.bodyMedium)
            } else if (incident.hasLocation) {
                Text(
                    text = stringResource(R.string.sos_card_detail_pending),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = stringResource(R.string.status_hops, incident.hopCount),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
