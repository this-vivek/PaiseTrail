package com.paisetrail.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountTextTest {

    @Test
    fun `formats amount under 1000 without grouping`() {
        assertEquals("₹450", formatIndianRupees(45000L))
    }

    @Test
    fun `formats thousands with a single comma`() {
        assertEquals("₹1,200", formatIndianRupees(120000L))
    }

    @Test
    fun `formats lakhs with Indian digit grouping`() {
        assertEquals("₹1,45,300", formatIndianRupees(14530000L))
    }

    @Test
    fun `formats crores with Indian digit grouping`() {
        assertEquals("₹1,23,45,678", formatIndianRupees(1234567800L))
    }

    @Test
    fun `formats zero`() {
        assertEquals("₹0", formatIndianRupees(0L))
    }

    @Test
    fun `drops paise fraction`() {
        assertEquals("₹450", formatIndianRupees(45050L))
    }

    @Test
    fun `formats negative amounts with sign before the rupee symbol`() {
        assertEquals("-₹450", formatIndianRupees(-45000L))
    }
}
