package com.resqmesh.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.resqmesh.R
import com.resqmesh.di.AppGraph
import com.resqmesh.domain.model.NodeId
import com.resqmesh.domain.model.SosIncident
import com.resqmesh.ui.alerts.AlertsScreen
import com.resqmesh.ui.alerts.AlertsViewModel
import com.resqmesh.ui.chat.ChatScreen
import com.resqmesh.ui.chat.ChatViewModel
import com.resqmesh.ui.components.MapLauncher
import com.resqmesh.ui.diagnostics.DiagnosticsScreen
import com.resqmesh.ui.diagnostics.DiagnosticsViewModel
import com.resqmesh.ui.experiment.ExperimentScreen
import com.resqmesh.ui.experiment.ExperimentViewModel
import com.resqmesh.ui.history.HistoryScreen
import com.resqmesh.ui.history.HistoryViewModel
import com.resqmesh.ui.home.HomeScreen
import com.resqmesh.ui.home.HomeViewModel
import com.resqmesh.ui.nodes.NodesScreen
import com.resqmesh.ui.nodes.NodesViewModel

object Routes {
    const val HOME = "home"
    const val NODES = "nodes"
    const val HISTORY = "history"
    const val ALERTS = "alerts"
    const val EXPERIMENT = "experiment"
    const val DIAGNOSTICS = "diagnostics"
    const val CHAT = "chat/{peerId}"

    fun chat(peerId: NodeId) = "chat/${peerId.hex}"
}

@Composable
fun ResQMeshNavHost(
    graph: AppGraph,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current

    fun openMap(incident: SosIncident) {
        openIncidentMap(context, incident)
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(
                    repository = graph.repository,
                    locationSource = graph.locationSource,
                    readBattery = { graph.readBattery() },
                ),
            )
            HomeScreen(
                viewModel = viewModel,
                onOpenNodes = { navController.navigateSingleTop(Routes.NODES) },
                onOpenHistory = { navController.navigateSingleTop(Routes.HISTORY) },
                onOpenAlerts = { navController.navigateSingleTop(Routes.ALERTS) },
                onOpenExperiment = { navController.navigateSingleTop(Routes.EXPERIMENT) },
                onOpenDiagnostics = { navController.navigateSingleTop(Routes.DIAGNOSTICS) },
            )
        }

        composable(
            route = Routes.EXPERIMENT,
            enterTransition = { slideInHorizontally { it } + fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { slideOutHorizontally { it } + fadeOut() },
        ) {
            val viewModel: ExperimentViewModel = viewModel(
                factory = ExperimentViewModel.factory(graph.repository, graph.experimentLogger),
            )
            ExperimentScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.DIAGNOSTICS,
            enterTransition = { slideInHorizontally { it } + fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { slideOutHorizontally { it } + fadeOut() },
        ) {
            val viewModel: DiagnosticsViewModel = viewModel(
                factory = DiagnosticsViewModel.factory(graph.repository, graph.locationSource),
            )
            DiagnosticsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.ALERTS,
            enterTransition = { slideInHorizontally { it } + fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { slideOutHorizontally { it } + fadeOut() },
        ) {
            val viewModel: AlertsViewModel = viewModel(
                factory = AlertsViewModel.factory(graph.repository),
            )
            AlertsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenMap = ::openMap,
            )
        }

        composable(
            route = Routes.NODES,
            enterTransition = { slideInHorizontally { it } + fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { slideOutHorizontally { it } + fadeOut() },
        ) {
            val viewModel: NodesViewModel = viewModel(
                factory = NodesViewModel.factory(graph.repository),
            )
            NodesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenChat = { peerId -> navController.navigate(Routes.chat(peerId)) },
            )
        }

        composable(
            route = Routes.HISTORY,
            enterTransition = { slideInHorizontally { it } + fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { slideOutHorizontally { it } + fadeOut() },
        ) {
            val viewModel: HistoryViewModel = viewModel(
                factory = HistoryViewModel.factory(graph.repository),
            )
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument(ARG_PEER_ID) { type = NavType.StringType }),
            enterTransition = { slideInHorizontally { it } + fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { slideOutHorizontally { it } + fadeOut() },
        ) { entry ->
            val peerId = NodeId.fromHex(entry.arguments?.getString(ARG_PEER_ID).orEmpty())
            val viewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.factory(graph.repository, peerId),
            )
            ChatScreen(
                peerId = peerId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private const val ARG_PEER_ID = "peerId"

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
    }
}

private fun openIncidentMap(context: Context, incident: SosIncident) {
    val intent = MapLauncher.intentFor(incident)
    if (intent == null || !MapLauncher.hasHandler(context, intent)) {
        Toast.makeText(
            context,
            R.string.sos_no_map_app,
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    context.startActivity(intent)
}
