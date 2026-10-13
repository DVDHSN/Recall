package com.example

import com.example.core.classification.ScreenshotClassifier
import com.example.core.model.ScreenshotCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotClassifierTest {

    @Test
    fun testClassifyRecipe() {
        val recipeText = "Creamy coconut chickpea curry ingredients: 2 cans garbanzo beans, 1 cup coconut milk, sauté onion"
        val result = ScreenshotClassifier.classify(recipeText)
        assertEquals(ScreenshotCategory.RECIPES, result.primaryCategory)
        assertTrue(result.confidence > 0.4f)
    }

    @Test
    fun testClassifyCodeError() {
        val errorText = "Traceback (most recent call last): File app.py line 44 java.lang.NullPointerException AttributeError"
        val result = ScreenshotClassifier.classify(errorText)
        assertEquals(ScreenshotCategory.CODE_ERROR, result.primaryCategory)
        assertTrue(result.confidence > 0.5f)
    }

    @Test
    fun testClassifyNetwork() {
        val wifiText = "SSID: Campus_Wifi Password: BlueRiver#9281 WPA3 Gateway: 192.168.1.1"
        val result = ScreenshotClassifier.classify(wifiText)
        assertEquals(ScreenshotCategory.NETWORK_TECH, result.primaryCategory)
    }

    @Test
    fun testClassifyStudyNotes() {
        val biologyText = "Lecture 14 Phototropism: plant shoots grow toward light stimulus mediated by auxin hormone physiology"
        val result = ScreenshotClassifier.classify(biologyText)
        assertEquals(ScreenshotCategory.STUDY_NOTES, result.primaryCategory)
    }

    @Test
    fun testClassifyFinance() {
        val leaseText = "Apartment Lease Contract Monthly Rent: $1,450.00 due on 1st of every month Security Deposit held in escrow"
        val result = ScreenshotClassifier.classify(leaseText)
        assertEquals(ScreenshotCategory.FINANCE_RECEIPTS, result.primaryCategory)
    }
}
