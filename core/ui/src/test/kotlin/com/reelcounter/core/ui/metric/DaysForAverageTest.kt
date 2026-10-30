package com.reelcounter.core.ui.metric

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DaysForAverageTest {

    private val today = LocalDate.of(2026, 10, 30)

    private fun day(back: Long, count: Int?) =
        DaySummary(date = today.minusDays(back), count = count, countLow = count, countHigh = count, appTime = null, isEstimate = false)

    @Test
    fun `today is left out while it is still in progress`() {
        val week = listOf(day(2, null), day(1, 277), day(0, 1))

        assertEquals(listOf(277), week.daysForAverage { it.count != null }.map { it.count })
    }

    @Test
    fun `today alone is used when no earlier day has data`() {
        val week = listOf(day(2, null), day(1, null), day(0, 5))

        assertEquals(listOf(5), week.daysForAverage { it.count != null }.map { it.count })
    }

    @Test
    fun `no data at all gives no days`() {
        val week = listOf(day(1, null), day(0, null))

        assertEquals(emptyList(), week.daysForAverage { it.count != null })
    }
}
