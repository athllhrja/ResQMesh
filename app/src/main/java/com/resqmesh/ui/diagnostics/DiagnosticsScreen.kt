package com.resqmesh.ui.diagnostics

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resqmesh.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val info by viewModel.info.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh(context)
    }

    val handleShare = {
        info?.let { diag ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Ringkasan Diagnostik ResQMesh")
                putExtra(Intent.EXTRA_TEXT, diag.toFormattedText())
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.diagnostics_action_share)))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            info?.let { data ->
                DiagnosticsSection(title = "Aplikasi & Perangkat") {
                    DiagRow("Application ID", data.applicationId)
                    DiagRow("Versi Aplikasi", "${data.versionName} (${data.versionCode})")
                    DiagRow("Perangkat", "${data.manufacturer} ${data.brand} ${data.model}")
                    DiagRow("Versi Android", "Android ${data.androidVersion} (SDK ${data.sdkInt})")
                }

                DiagnosticsSection(title = "Perangkat Hardis & Bluetooth") {
                    DiagRow("Bluetooth Didukung", if (data.bluetoothSupported) "Ya" else "Tidak")
                    DiagRow("Bluetooth Aktif", if (data.bluetoothEnabled) "Ya" else "Tidak")
                    DiagRow("Multiple Advertising", if (data.multipleAdvertisementSupported) "Didukung" else "Tidak")
                    DiagRow("Extended Advertising", if (data.extendedAdvertisingSupported) "Didukung" else "Tidak")
                }

                DiagnosticsSection(title = "Izin Runtime") {
                    DiagRow("Izin BLE", if (data.hasBluetoothPermissions) "Diberikan" else "Ditolak")
                    DiagRow("Izin Lokasi", if (data.hasLocationPermissions) "Diberikan" else "Ditolak")
                    DiagRow("Izin Notifikasi", if (data.hasNotificationPermissions) "Diberikan" else "Ditolak")
                }

                DiagnosticsSection(title = "Layanan & Hemat Daya") {
                    DiagRow("GPS / Layanan Lokasi", if (data.isLocationProviderEnabled) "Aktif" else "Mati")
                    DiagRow("Abaikan Hemat Baterai", if (data.isIgnoringBatteryOptimizations) "Ya" else "Tidak")
                    DiagRow("MeshService Status", if (data.isMeshServiceRunning) "Berjalan" else "Berhenti")
                }

                DiagnosticsSection(title = "Status Mesh") {
                    DiagRow("Fase Mesh", data.meshPhase)
                    DiagRow("Last Error", data.meshLastError ?: "-")
                }

                Button(
                    onClick = { handleShare() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.diagnostics_action_share))
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
