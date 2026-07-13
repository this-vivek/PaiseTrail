package com.paisetrail.app.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfTransferDetectorTest {
    @Test
    fun `full name variant is detected regardless of case`() {
        assertTrue(SelfTransferDetector.isSelfTransfer("VIVEK SINGH RAWAT"))
        assertTrue(SelfTransferDetector.isSelfTransfer("Vivek Singh Rawat"))
    }

    @Test
    fun `short name variant is detected`() {
        assertTrue(SelfTransferDetector.isSelfTransfer("Vivek Rawat"))
    }

    @Test
    fun `a real merchant or other person is not flagged`() {
        assertFalse(SelfTransferDetector.isSelfTransfer("Sharma Tea Stall"))
        assertFalse(SelfTransferDetector.isSelfTransfer("Amit Kumar"))
    }

    @Test
    fun `null or blank payee is not flagged`() {
        assertFalse(SelfTransferDetector.isSelfTransfer(null))
        assertFalse(SelfTransferDetector.isSelfTransfer(""))
        assertFalse(SelfTransferDetector.isSelfTransfer("   "))
    }
}
