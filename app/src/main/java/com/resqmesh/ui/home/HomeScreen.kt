package com.resqmesh.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resqmesh.R
import com.resqmesh.core.MeshConfig
import com.resqmesh.domain.model.MeshState
import com.resqmesh.ui.components.SosComposerDialog
import com.resqmesh.ui.components.StatusDot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenNodes: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAlerts: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            viewModel.resolveLocation()
        }
    }

    val handleRetryLocation = {
        if (com.resqmesh.ui.MeshPermissions.hasLocation(context)) {
            viewModel.resolveLocation()
        } else {
            locationPermissionLauncher.launch(com.resqmesh.ui.MeshPermissions.locationPermissions)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                    )
                },
            )
        },
    ) { padding ->
        HomeContent(
            state = state,
            viewModel = viewModel,
            onOpenNodes = onOpenNodes,
            onOpenHistory = onOpenHistory,
            onOpenAlerts = onOpenAlerts,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }

    if (state.isSosDialogVisible) {
        SosComposerDialog(
            composer = state.sos,
            onSelectKind = viewModel::selectSosKind,
            onToggleHazard = viewModel::toggleHazard,
            onChangeVictimCount = viewModel::changeVictimCount,
            onTextChange = viewModel::setSosText,
            onRetryLocation = handleRetryLocation,
            onSend = viewModel::sendSos,
            onDismiss = viewModel::dismissSosDialog,
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    viewModel: HomeViewModel,
    onOpenNodes: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAlerts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LabelValue(
                    stringResource(R.string.home_node),
                    state.self?.displayName ?: stringResource(R.string.home_unknown),
                )
                LabelValue(
                    stringResource(R.string.home_status),
                    state.phase.label(),
                    dotColor = state.phase.dotColor(),
                )
                LabelValue(
                    stringResource(R.string.home_neighbors),
                    state.neighborCount.toString(),
                )
                if (state.pendingCount > 0) {
                    LabelValue(
                        stringResource(R.string.home_pending),
                        state.pendingCount.toString(),
                    )
                }
            }
        }

        TtlSelector(selected = state.ttl, onSelect = viewModel::setTtl)

        Button(
            onClick = viewModel::showSosDialog,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(
                text = stringResource(R.string.home_sos),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onOpenNodes, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.nav_nodes))
            }
            OutlinedButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.nav_history))
            }
        }

        OutlinedButton(onClick = onOpenAlerts, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sos_alerts_title))
        }

        state.error?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TtlSelector(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${stringResource(R.string.home_ttl_label)} = $selected",
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..MeshConfig.MAX_TTL).forEach { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(value.toString()) },
                )
            }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String, dotColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (dotColor != null) {
                StatusDot(color = dotColor, size = 8.dp)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun MeshState.Phase.label(): String = when (this) {
    MeshState.Phase.STOPPED -> stringResource(R.string.home_stopped)
    MeshState.Phase.IDLE -> stringResource(R.string.home_idle)
    MeshState.Phase.SCANNING -> stringResource(R.string.home_scanning)
    MeshState.Phase.ACTIVE -> stringResource(R.string.home_active)
    MeshState.Phase.ERROR -> stringResource(R.string.home_error)
}

@Composable
internal fun MeshState.Phase.dotColor(): Color = when (this) {
    MeshState.Phase.STOPPED,
    MeshState.Phase.IDLE,
    -> Color(0xFF9E9E9E)

    MeshState.Phase.SCANNING -> Color(0xFF9A8C24)
    MeshState.Phase.ACTIVE -> Color(0xFF2E7D5B)
    MeshState.Phase.ERROR -> MaterialTheme.colorScheme.error
}
