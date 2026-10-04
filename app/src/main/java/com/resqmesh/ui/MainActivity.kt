package com.resqmesh.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.resqmesh.ResQMeshApp
import com.resqmesh.service.MeshService
import com.resqmesh.ui.theme.ResQMeshTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val graph = (application as ResQMeshApp).graph

        setContent {
            ResQMeshTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ResQMeshNavHost(graph)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (MeshPermissions.canStartService(this)) {
            MeshService.start(this)
        }
    }
}
