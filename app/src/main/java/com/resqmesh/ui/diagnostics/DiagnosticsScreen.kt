package com.resqmesh.ui.diagnostics

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val info by viewModel.info.collectAsStateWithLifecycle()
    val isTestRunning by viewModel.isTestRunning.collectAsStateWithLifecycle()
    val countdown by viewModel.testCountdownSeconds.collectAsStateWithLifecycle()
    val interpretationResult by viewModel.testInterpretationResult.collectAsStateWithLifecycle()

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

                DiagnosticsSection(title = "Perangkat Hardware & Bluetooth") {
                    DiagRow("Bluetooth Didukung", if (data.bluetoothSupported) "Ya" else "Tidak")
                    DiagRow("Bluetooth Aktif", if (data.bluetoothEnabled) "Ya" else "Tidak")
                    DiagRow("Multiple Advertising", if (data.multipleAdvertisementSupported) "Didukung" else "Tidak")
                    DiagRow("Extended Advertising", if (data.extendedAdvertisingSupported) "Didukung" else "Tidak")
                }

                DiagnosticsSection(title = stringResource(R.string.diagnostics_ble_section)) {
                    val stats = data.bleStats
                    DiagRow("Status Start BLE", stats.bluetoothStartResult)
                    DiagRow("Mode Advertising", stats.advertiseMode)
                    DiagRow("Pancaran Sukses (Adv)", stats.advertiseStartSuccessCount.toString())
                    DiagRow(
                        "Pancaran Gagal (Adv)",
                        "${stats.advertiseFailureCount} (Err: ${stats.lastAdvertiseErrorDescription})",
                    )
                    DiagRow("Total Scan Callback", stats.scanCallbackCount.toString())
                    DiagRow("Frame Cocok (Company ID)", stats.matchedFrameCount.toString())
                    DiagRow("Gagal Urai (Parse Err)", stats.parseFailureCount.toString())
                    DiagRow(
                        "Scan Gagal (Scan Err)",
                        "${stats.scanFailCount} (Code: ${if (stats.lastScanErrorCode >= 0) stats.lastScanErrorCode else "-"})",
                    )
                    val lastBeaconStr = if (stats.lastBeaconReceivedAtMs > 0) {
                        val date = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(stats.lastBeaconReceivedAtMs))
                        "$date (${stats.lastBeaconRssi} dBm)"
                    } else {
                        "Belum ada"
                    }
                    DiagRow("Beacon Terakhir", lastBeaconStr)

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Keterangan Arti Angka:\n" +
                            "• Total Scan > 0 & Cocok = 0: HP menerima sinyal BLE lain tetapi bukan dari ResQMesh yang sama versinya.\n" +
                            "• Cocok > 0 & Gagal Urai > 0: Sinyal ResQMesh diterima tetapi versi APK/payload berbeda.\n" +
                            "• Adv Gagal > 0: Chipset BLE HP ini menolak/tidak mendukung penyiaran data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                DiagnosticsSection(title = "Izin Runtime") {
                    DiagRow("Izin BLE", if (data.hasBluetoothPermissions) "Diberikan" else "Ditolak")
                    DiagRow("Izin Lokasi Presisi", if (data.hasPreciseLocationPermissions) "Diberikan" else "Ditolak")
                    DiagRow("Izin Lokasi Perkiraan", if (data.hasApproximateLocationPermissions) "Diberikan" else "Ditolak")
                    DiagRow("Izin Notifikasi", if (data.hasNotificationPermissions) "Diberikan" else "Ditolak")
                }

                DiagnosticsSection(title = "Layanan Lokasi & GPS") {
                    val gpsFixStr = if (data.lastGpsFixAgeSeconds != null) "${data.lastGpsFixAgeSeconds}s lalu" else "Belum ada"
                    val netFixStr = if (data.lastNetworkFixAgeSeconds != null) "${data.lastNetworkFixAgeSeconds}s lalu" else "Belum ada"
                    DiagRow("GPS Provider", if (data.isGpsProviderEnabled) "Aktif" else "Mati")
                    DiagRow("Network Provider", if (data.isNetworkProviderEnabled) "Aktif" else "Mati")
                    DiagRow("GPS Last Fix", gpsFixStr)
                    DiagRow("Network Last Fix", netFixStr)
                    DiagRow("Hasil Cari Terakhir", data.lastLocationSearchResult)
                }

                DiagnosticsSection(title = "Layanan & Hemat Daya") {
                    DiagRow("Abaikan Hemat Baterai", if (data.isIgnoringBatteryOptimizations) "Ya" else "Tidak")
                    DiagRow("MeshService Status", if (data.isMeshServiceRunning) "Berjalan" else "Berhenti")
                }

                DiagnosticsSection(title = "Status Mesh") {
                    DiagRow("Fase Mesh", data.meshPhase)
                    DiagRow("Last Error", data.meshLastError ?: "-")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.resetStats(context) },
                        modifier = Modifier.weight(1f),
                        enabled = !isTestRunning,
                    ) {
                        Text(stringResource(R.string.diagnostics_reset_stats))
                    }

                    Button(
                        onClick = { viewModel.start30SecBleTest(context) },
                        modifier = Modifier.weight(1f),
                        enabled = !isTestRunning,
                    ) {
                        Text(
                            if (isTestRunning) {
                                stringResource(R.string.diagnostics_testing_ble, countdown)
                            } else {
                                stringResource(R.string.diagnostics_start_test)
                            },
                        )
                    }
                }

                interpretationResult?.let { resultText ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.diagnostics_test_result_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = resultText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
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
