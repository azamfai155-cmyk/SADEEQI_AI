package com.azzam.sadeeqi.trading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradingMathTest {
    @Test fun calculatesCashRiskAndPositionUnits() {
        assertEquals(10.0, TradingMath.riskAmount(1000.0, 1.0), 0.0001)
        assertEquals(0.5, TradingMath.positionSizeByStop(10.0, 20.0), 0.0001)
    }

    @Test fun calculatesRewardRiskRatio() {
        assertEquals(2.0, TradingMath.rewardRisk(100.0, 95.0, 110.0), 0.0001)
    }

    @Test fun calculatesWinRateAndProfitFactor() {
        val stats = TradingMath.backtest(20, 10, 2.0, 1.0)
        assertEquals(30, stats.trades)
        assertEquals(66.6667, stats.winRatePercent, 0.001)
        assertEquals(4.0, stats.profitFactor!!, 0.0001)
    }

    @Test fun profitFactorIsUndefinedWithoutRecordedLosses() {
        val stats = TradingMath.backtest(5, 0, 1.5, 1.0)
        assertTrue(stats.profitFactor == null)
    }
}
