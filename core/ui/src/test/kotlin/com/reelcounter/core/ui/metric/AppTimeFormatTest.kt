package com.reelcounter.core.ui.metric

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AppTimeFormatTest {

    @Test
    fun `splits hours and minutes, truncating seconds`() {
        assertEquals(AppTimeParts(hours = 1, minutes = 12, isUnderOneMinute = false), appTimeParts(72.minutes + 59.seconds))
    }

    @Test
    fun `whole hours have zero minutes`() {
        assertEquals(AppTimeParts(hours = 2, minutes = 0, isUnderOneMinute = false), appTimeParts(2.hours))
    }

    @Test
    fun `a few seconds of use is under one minute, not zero`() {
        assertEquals(AppTimeParts(hours = 0, minutes = 0, isUnderOneMinute = true), appTimeParts(20.seconds))
    }

    @Test
    fun `no use at all is zero minutes`() {
        assertEquals(AppTimeParts(hours = 0, minutes = 0, isUnderOneMinute = false), appTimeParts(Duration.ZERO))
    }

    @Test
    fun `a 25 hour DST day still formats`() {
        assertEquals(AppTimeParts(hours = 25, minutes = 0, isUnderOneMinute = false), appTimeParts(25.hours))
    }
}
