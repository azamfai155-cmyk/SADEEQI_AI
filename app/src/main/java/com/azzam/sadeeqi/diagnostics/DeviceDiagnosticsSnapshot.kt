package com.azzam.sadeeqi.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Debug
import android.net.TrafficStats

/**
 * Point-in-time, app-scoped diagnostics. Values are device observations, not
 * benchmark conclusions. Take two snapshots around a repeatable workload to
 * calculate deltas; TrafficStats counters are cumulative since boot/app UID use.
 */
data class DeviceDiagnosticsSnapshot(
    val capturedAtEpochMs: Long,
    val batteryPercent: Int?,
    val batteryTemperatureC: Double?,
    val processPssKb: Int,
    val javaHeapUsedBytes: Long,
    val javaHeapMaxBytes: Long,
    val appRxBytes: Long,
    val appTxBytes: Long,
    val totalDeviceMemoryBytes: Long,
    val availableDeviceMemoryBytes: Long,
    val lowMemory: Boolean
) {
    companion object {
        fun capture(context: Context): DeviceDiagnosticsSnapshot {
            val app = context.applicationContext
            val battery = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val activity = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memory = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
            val processMemory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
            val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                .takeIf { it in 0..100 }
            val temperature = app.registerReceiver(
                null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                ?.takeIf { it >= 0 }?.div(10.0)
            val runtime = Runtime.getRuntime()
            return DeviceDiagnosticsSnapshot(
                capturedAtEpochMs = System.currentTimeMillis(),
                batteryPercent = level,
                batteryTemperatureC = temperature,
                processPssKb = processMemory.totalPss,
                javaHeapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
                javaHeapMaxBytes = runtime.maxMemory(),
                appRxBytes = TrafficStats.getUidRxBytes(android.os.Process.myUid()),
                appTxBytes = TrafficStats.getUidTxBytes(android.os.Process.myUid()),
                totalDeviceMemoryBytes = memory.totalMem,
                availableDeviceMemoryBytes = memory.availMem,
                lowMemory = memory.lowMemory
            )
        }
    }

    fun deltaSince(earlier: DeviceDiagnosticsSnapshot): Delta = Delta(
        elapsedMs = (capturedAtEpochMs - earlier.capturedAtEpochMs).coerceAtLeast(0),
        batteryPercentagePoints = if (batteryPercent != null && earlier.batteryPercent != null) batteryPercent - earlier.batteryPercent else null,
        processPssDeltaKb = processPssKb - earlier.processPssKb,
        appRxDeltaBytes = counterDelta(appRxBytes, earlier.appRxBytes),
        appTxDeltaBytes = counterDelta(appTxBytes, earlier.appTxBytes)
    )

    private fun counterDelta(now: Long, before: Long): Long? =
        if (now < 0 || before < 0 || now < before) null else now - before

    data class Delta(
        val elapsedMs: Long,
        val batteryPercentagePoints: Int?,
        val processPssDeltaKb: Int,
        val appRxDeltaBytes: Long?,
        val appTxDeltaBytes: Long?
    )
}
