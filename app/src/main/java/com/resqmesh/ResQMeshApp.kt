package com.resqmesh

import android.app.Application
import com.resqmesh.di.AppGraph

class ResQMeshApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.ensureSelfNode()
    }

    override fun onTerminate() {
        graph.close()
        super.onTerminate()
    }
}
