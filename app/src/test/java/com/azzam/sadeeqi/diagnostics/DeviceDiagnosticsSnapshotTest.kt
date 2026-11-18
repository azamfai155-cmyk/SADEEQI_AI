package com.azzam.sadeeqi.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceDiagnosticsSnapshotTest {
    private fun snapshot(
        time: Long,
        battery: Int?,
        pss: Int,
        rx: Long,
        tx: Long
    ) = DeviceDiagnosticsSnapshot(
        capturedAtEpochMs = time,
        batteryPercent = battery,
        batteryTemperatureC = 30.0,
        processPssKb = pss,
        javaHeapUsedBytes = 10_000,
        javaHeapMaxBytes = 20_000,
        appRxBytes = rx,
        appTxBytes = tx,
        totalDeviceMemoryBytes = 1_000_000,
        availableDeviceMemoryBytes = 500_000,
        lowMemory = false
    )

    @Test
    fun calculatesBatteryMemoryAndNetworkDeltas() {
        val before = snapshot(1_000, 85, 12_000, 1_000, 2_000)
        val after = snapshot(6_000, 82, 12_800, 1_700, 2_900)

        val delta = after.deltaSince(before)

        assertEquals(5_000L, delta.elapsedMs)
        assertEquals(-3, delta.batteryPercentagePoints)
        assertEquals(800, delta.processPssDeltaKb)
        assertEquals(700L, delta.appRxDeltaBytes)
        assertEquals(900L, delta.appTxDeltaBytes)
    }

    @Test
    fun marksUnsupportedOrResetNetworkCountersAsUnavailable() {
        val before = snapshot(1_000, null, 100, -1, 2_000)
        val after = snapshot(2_000, null, 120, 50, 1_500)

        val delta = after.deltaSince(before)

        assertNull(delta.batteryPercentagePoints)
        assertNull(delta.appRxDeltaBytes)
        assertNull(delta.appTxDeltaBytes)
    }
}
