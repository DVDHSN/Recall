package com.example

import com.example.core.model.ScreenshotItem
import com.example.core.qa.calculator.ReceiptCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptCalculatorTest {

    private fun createFakeScreenshot(id: Long, name: String, ocr: String): ScreenshotItem {
        return ScreenshotItem(
            id = id,
            mediaStoreId = id * 100,
            contentUri = "content://media/external/images/media/$id",
            displayName = name,
            relativePath = "Pictures/Receipts",
            mimeType = "image/jpeg",
            width = 1080,
            height = 1920,
            sizeBytes = 204800,
            dateAdded = 1700000000L,
            dateModified = 1700000000L,
            ocrText = ocr
        )
    }

    @Test
    fun testFindSpecificAmount_RM80() {
        val receipt1 = createFakeScreenshot(
            id = 1L,
            name = "lunch_receipt.jpg",
            ocr = "Nasi Kandar Pelita\nSubtotal: RM 45.00\nSST: RM 2.70\nTOTAL: RM 80.00\nThank you!"
        )
        val receipt2 = createFakeScreenshot(
            id = 2L,
            name = "groceries.jpg",
            ocr = "Jaya Grocer\nMilk RM 7.50\nEggs RM 12.00\nTOTAL RM 19.50\nPAID VIA TNG"
        )

        val breakdown = ReceiptCalculator.findSpecificAmount(
            targetAmount = 80.0,
            targetCurrency = "RM",
            candidates = listOf(receipt1, receipt2)
        )

        assertNotNull(breakdown.winningAmount)
        assertEquals(1L, breakdown.winningAmount!!.screenshotId)
        assertEquals("RM", breakdown.winningAmount!!.currency)
        assertEquals(80.0, breakdown.winningAmount!!.amount, 0.001)
        assertTrue(breakdown.winningAmount!!.isExplicitTotal)
        assertTrue(breakdown.explanation.contains("lunch_receipt.jpg"))
    }

    @Test
    fun testFindLowestTotal_MultipleReceipts() {
        val receiptA = createFakeScreenshot(
            id = 10L,
            name = "receipt_high.jpg",
            ocr = "Dinner with team\nTOTAL: RM 150.00"
        )
        val receiptB = createFakeScreenshot(
            id = 11L,
            name = "receipt_lowest.jpg",
            ocr = "Coffee shop\nTOTAL: RM 14.50"
        )
        val receiptC = createFakeScreenshot(
            id = 12L,
            name = "receipt_mid.jpg",
            ocr = "Pharmacy\nTOTAL: RM 52.80"
        )

        val breakdown = ReceiptCalculator.findLowestTotal(listOf(receiptA, receiptB, receiptC))

        assertNotNull(breakdown.winningAmount)
        assertEquals(11L, breakdown.winningAmount!!.screenshotId)
        assertEquals("receipt_lowest.jpg", breakdown.winningAmount!!.imageDisplayName)
        assertEquals(14.50, breakdown.winningAmount!!.amount, 0.001)
        assertTrue(breakdown.calculationSteps.any { it.contains("14.50 is the minimum") })
    }

    @Test
    fun testFindLowestTotal_NoValidNumbers_PreservesUncertainty() {
        val blankReceipt = createFakeScreenshot(
            id = 20L,
            name = "blurred_receipt.jpg",
            ocr = "Unreadable text blurry store"
        )

        val breakdown = ReceiptCalculator.findLowestTotal(listOf(blankReceipt))

        assertNull(breakdown.winningAmount)
        assertTrue(breakdown.calculationSteps.any { it.contains("Could not extract any valid receipt totals") })
    }

    @Test
    fun testFindHighestTotal() {
        val receiptA = createFakeScreenshot(
            id = 31L,
            name = "stationery.jpg",
            ocr = "Notebooks $25.00\nTOTAL: $25.00"
        )
        val receiptB = createFakeScreenshot(
            id = 32L,
            name = "electronics.jpg",
            ocr = "Mechanical Keyboard $189.99\nTOTAL DUE: $189.99"
        )

        val breakdown = ReceiptCalculator.findHighestTotal(listOf(receiptA, receiptB))

        assertNotNull(breakdown.winningAmount)
        assertEquals(32L, breakdown.winningAmount!!.screenshotId)
        assertEquals(189.99, breakdown.winningAmount!!.amount, 0.001)
    }

    @Test
    fun testIsCalculationQuery() {
        assertTrue(ReceiptCalculator.isCalculationQuery("Find the receipt that shows RM80"))
        assertTrue(ReceiptCalculator.isCalculationQuery("Which receipt has the lowest total?"))
        assertTrue(ReceiptCalculator.isCalculationQuery("Cheapest receipt from last week"))
        assertTrue(ReceiptCalculator.isCalculationQuery("What was the highest bill?"))

        assertFalse(ReceiptCalculator.isCalculationQuery("Find screenshot with error 404"))
        assertFalse(ReceiptCalculator.isCalculationQuery("Transformer revision diagram"))
    }
}
