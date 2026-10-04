package com.resqmesh.experiment

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.content.FileProvider
import com.resqmesh.domain.model.NodeId
import java.io.File
import java.io.FileWriter
import java.util.concurrent.Executors

enum class ExperimentEvent {
    SEND,
    TX,
    RX,
    RX_COMPLETE,
    RELAY,
    ACK_TX,
    ACK_RX,
    DELIVERED,
}

class ExperimentLogger(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()

    @Volatile var activeRunId: String = "RUN_01"
    @Volatile var activeScenario: String = "Default_Scenario"

    private val logDir: File
        get() = File(context.getExternalFilesDir("experiments"), "").apply { if (!exists()) mkdirs() }

    private fun currentLogFile(): File {
        return File(logDir, "experiment_${activeRunId}.csv")
    }

    fun logEvent(
        nodeId: NodeId,
        event: ExperimentEvent,
        messageKey: Long = 0L,
        fragIndex: Int = 0,
        hop: Int = 0,
        ttl: Int = 0,
        rssi: Int = 0,
        batteryPct: Int = -1,
        runId: String = activeRunId,
        scenario: String = activeScenario,
    ) {
        val wallClockMs = System.currentTimeMillis()
        val elapsedRealtimeMs = SystemClock.elapsedRealtime()

        executor.execute {
            runCatching {
                val file = currentLogFile()
                val isNew = !file.exists() || file.length() == 0L
                FileWriter(file, true).use { writer ->
                    if (isNew) {
                        writer.append("runId,scenario,nodeId,event,messageKey,fragIndex,hop,ttl,rssi,batteryPct,wallClockMs,elapsedRealtimeMs\n")
                    }
                    writer.append("$runId,$scenario,${nodeId.hex},$event,$messageKey,$fragIndex,$hop,$ttl,$rssi,$batteryPct,$wallClockMs,$elapsedRealtimeMs\n")
                }
            }.onFailure { e ->
                Log.e(TAG, "Gagal menulis log eksperimen ke CSV: ${e.message}", e)
            }
        }
    }

    fun getLogFiles(): List<File> {
        val dir = logDir
        return dir.listFiles { file -> file.extension.lowercase() == "csv" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    fun getShareUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    fun clearLogs(): Boolean {
        return runCatching {
            getLogFiles().forEach { it.delete() }
            true
        }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "ExperimentLogger"
    }
}
