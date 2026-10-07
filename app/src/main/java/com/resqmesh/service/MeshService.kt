package com.resqmesh.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.resqmesh.R
import com.resqmesh.ResQMeshApp
import com.resqmesh.core.MeshConfig
import com.resqmesh.domain.model.MeshState
import com.resqmesh.ui.MainActivity

class MeshService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: MeshConfig.ACTION_START_MESH
        if (action == MeshConfig.ACTION_STOP_MESH) {
            stopMesh()
            return START_NOT_STICKY
        }

        val foregroundStarted = runCatching {
            startForegroundServiceWithNotification()
        }.onFailure { e ->
            Log.e(TAG, "Gagal startForeground, menghentikan service agar tidak crash: ${e.message}", e)
            stopSelf()
        }.isSuccess

        if (!foregroundStarted) {
            return START_NOT_STICKY
        }

        val meshManager = (application as ResQMeshApp).graph.meshManager
        runCatching {
            meshManager.start()
            if (meshManager.state.value.phase == MeshState.Phase.ERROR) {
                Log.w(TAG, "MeshManager dalam keadaan Gangguan, menghentikan foreground service")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.onFailure { e ->
            Log.e(TAG, "Gagal meshManager.start(): ${e.message}", e)
            meshManager.setError(e.message ?: "Gagal menjalankan mesh manager")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                MeshConfig.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(MeshConfig.NOTIFICATION_ID, notification)
        }
    }

    private fun stopMesh() {
        runCatching {
            val meshManager = (application as ResQMeshApp).graph.meshManager
            meshManager.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }.onFailure { e ->
            Log.e(TAG, "Gagal menghentikan MeshService: ${e.message}", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MeshConfig.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, MeshService::class.java).apply {
            action = MeshConfig.ACTION_STOP_MESH
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, MeshConfig.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_stat_mesh)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(
                0,
                getString(R.string.notification_action_stop),
                stopPendingIntent,
            )
            .build()
    }

    companion object {
        private const val TAG = "MeshService"

        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, MeshService::class.java).apply {
                    action = MeshConfig.ACTION_START_MESH
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { e ->
                Log.e(TAG, "Gagal memulai MeshService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            runCatching {
                val intent = Intent(context, MeshService::class.java).apply {
                    action = MeshConfig.ACTION_STOP_MESH
                }
                context.startService(intent)
            }.onFailure { e ->
                Log.e(TAG, "Gagal menghentikan MeshService: ${e.message}", e)
            }
        }
    }
}
