package com.resqmesh.crash

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeviceBuildInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val osRelease: String,
    val sdkInt: Int,
    val versionName: String,
)

object CrashReporter {

    private const val TAG = "CrashReporter"

    fun install(context: Context) {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        val appContext = context.applicationContext

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val versionName = getVersionName(appContext)
                val buildInfo = DeviceBuildInfo(
                    manufacturer = Build.MANUFACTURER ?: "Unknown",
                    brand = Build.BRAND ?: "Unknown",
                    model = Build.MODEL ?: "Unknown",
                    osRelease = Build.VERSION.RELEASE ?: "Unknown",
                    sdkInt = Build.VERSION.SDK_INT,
                    versionName = versionName,
                )
                val timestampMs = System.currentTimeMillis()
                val reportText = buildReport(
                    threadName = thread.name,
                    throwable = throwable,
                    timestampMs = timestampMs,
                    buildInfo = buildInfo,
                )
                saveReportToFile(appContext, timestampMs, reportText)
            }.onFailure { e ->
                Log.e(TAG, "Gagal menyimpan laporan crash: ${e.message}", e)
            }

            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun buildReport(
        threadName: String,
        throwable: Throwable,
        timestampMs: Long,
        buildInfo: DeviceBuildInfo,
    ): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestampMs))
        val stackTraceWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stackTraceWriter))
        val stackTrace = stackTraceWriter.toString()

        return """
            === ResQMesh Crash Report ===
            Waktu       : $dateStr ($timestampMs)
            Thread      : $threadName
            Aplikasi    : ${buildInfo.versionName}
            Perangkat   : ${buildInfo.manufacturer} ${buildInfo.brand} ${buildInfo.model}
            OS Version  : Android ${buildInfo.osRelease} (SDK ${buildInfo.sdkInt})

            === Stack Trace ===
            $stackTrace
        """.trimIndent()
    }

    fun getCrashDir(context: Context): File {
        val extDir = runCatching { context.getExternalFilesDir("crash") }.getOrNull()
        return if (extDir != null) {
            if (!extDir.exists()) extDir.mkdirs()
            extDir
        } else {
            File(context.filesDir, "crash").apply { if (!exists()) mkdirs() }
        }
    }

    fun listCrashFiles(context: Context): List<File> {
        val dir = getCrashDir(context)
        return dir.listFiles { _, name -> name.startsWith("crash_") && name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun clearCrashFiles(context: Context) {
        listCrashFiles(context).forEach { runCatching { it.delete() } }
    }

    private fun saveReportToFile(context: Context, timestampMs: Long, content: String) {
        val dir = getCrashDir(context)
        val file = File(dir, "crash_$timestampMs.txt")
        file.writeText(content, Charsets.UTF_8)
    }

    private fun getVersionName(context: Context): String {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("1.0.0")
    }
}
