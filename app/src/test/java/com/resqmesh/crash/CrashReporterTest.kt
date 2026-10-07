package com.resqmesh.crash

import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReporterTest {

    @Test
    fun `buildReport_menghasilkan_laporan_lengkap_termasuk_stacktrace_dan_perangkat`() {
        val buildInfo = DeviceBuildInfo(
            manufacturer = "Samsung",
            brand = "Galaxy",
            model = "SM-G991B",
            osRelease = "14",
            sdkInt = 34,
            versionName = "1.0.0-test",
        )
        val cause = IllegalStateException("Root cause exception")
        val throwable = RuntimeException("Crash simulation", cause)
        val timestamp = 1_700_000_000_000L

        val report = CrashReporter.buildReport(
            threadName = "main-thread",
            throwable = throwable,
            timestampMs = timestamp,
            buildInfo = buildInfo,
        )

        assertTrue(report.contains("=== ResQMesh Crash Report ==="))
        assertTrue(report.contains("Thread      : main-thread"))
        assertTrue(report.contains("Aplikasi    : 1.0.0-test"))
        assertTrue(report.contains("Perangkat   : Samsung Galaxy SM-G991B"))
        assertTrue(report.contains("OS Version  : Android 14 (SDK 34)"))
        assertTrue(report.contains("RuntimeException: Crash simulation"))
        assertTrue(report.contains("Caused by: java.lang.IllegalStateException: Root cause exception"))
    }
}
