package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.media.ScreenshotDiscovery
import com.example.core.model.ImageSource
import com.example.core.model.MediaPermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class UniversalImageDiscoveryTest {

    private lateinit var context: Context
    private lateinit var discovery: ScreenshotDiscovery

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        discovery = ScreenshotDiscovery(context)
    }

    @Test
    fun testDetermineImageSourceForMultipleFoldersAndCollections() {
        // Camera photos
        assertEquals(
            ImageSource.CAMERA,
            ScreenshotDiscovery.determineImageSource("IMG_20261013_120000.jpg", "DCIM/Camera/", "Camera")
        )
        assertEquals(
            ImageSource.CAMERA,
            ScreenshotDiscovery.determineImageSource("PXL_20261013_portrait.jpg", "DCIM/Camera/", "Camera")
        )
        assertEquals(
            ImageSource.CAMERA,
            ScreenshotDiscovery.determineImageSource("DSC_0042.jpg", "DCIM/100CANON/", "100CANON")
        )

        // Screenshots
        assertEquals(
            ImageSource.SCREENSHOT,
            ScreenshotDiscovery.determineImageSource("Screenshot_2026-10-13.png", "Pictures/Screenshots/", "Screenshots")
        )
        assertEquals(
            ImageSource.SCREENSHOT,
            ScreenshotDiscovery.determineImageSource("screencap_game.png", "DCIM/Screenshots/", "Screenshots")
        )

        // Downloads
        assertEquals(
            ImageSource.DOWNLOAD,
            ScreenshotDiscovery.determineImageSource("infographic_ai.png", "Download/", "Download")
        )
        assertEquals(
            ImageSource.DOWNLOAD,
            ScreenshotDiscovery.determineImageSource("receipt_tax_2026.pdf.jpg", "Download/Invoices/", "Invoices")
        )

        // Social and Messaging
        assertEquals(
            ImageSource.SOCIAL_MESSAGING,
            ScreenshotDiscovery.determineImageSource("meme_cat.jpg", "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/", "WhatsApp Images")
        )
        assertEquals(
            ImageSource.SOCIAL_MESSAGING,
            ScreenshotDiscovery.determineImageSource("telegram_photo_482.jpg", "Pictures/Telegram/", "Telegram")
        )
        assertEquals(
            ImageSource.SOCIAL_MESSAGING,
            ScreenshotDiscovery.determineImageSource("insta_story.jpg", "Pictures/Instagram/", "Instagram")
        )

        // Documents and Notes
        assertEquals(
            ImageSource.DOCUMENTS_NOTES,
            ScreenshotDiscovery.determineImageSource("lecture_whiteboard_note.jpg", "Documents/Study/", "Study")
        )
        assertEquals(
            ImageSource.DOCUMENTS_NOTES,
            ScreenshotDiscovery.determineImageSource("scan_receipt_lunch.png", "Pictures/Scans/", "Scans")
        )

        // Imported
        assertEquals(
            ImageSource.IMPORTED,
            ScreenshotDiscovery.determineImageSource("imported_photo.jpg", "Imported", "Imported")
        )

        // Other generic pictures
        assertEquals(
            ImageSource.OTHER,
            ScreenshotDiscovery.determineImageSource("wallpaper_landscape.jpg", "Pictures/Wallpapers/", "Wallpapers")
        )
    }

    @Test
    fun testIsScreenshotAndIsCameraPhotoHelpers() {
        assertTrue(ScreenshotDiscovery.isScreenshot("Screenshot_92.png", "Pictures/Screenshots"))
        assertFalse(ScreenshotDiscovery.isScreenshot("IMG_2026.jpg", "DCIM/Camera"))

        assertTrue(ScreenshotDiscovery.isCameraPhoto("IMG_2026.jpg", "DCIM/Camera"))
        assertTrue(ScreenshotDiscovery.isCameraPhoto("PXL_99.jpg", "DCIM/Camera"))
        assertFalse(ScreenshotDiscovery.isCameraPhoto("Screenshot_92.png", "Pictures/Screenshots"))
    }

    @Test
    fun testPermissionStateCheck() {
        val permState = discovery.getMediaPermissionState()
        // In Robolectric environment with manifest = Config.NONE or standard shadows
        assertTrue(
            permState == MediaPermissionState.DENIED ||
            permState == MediaPermissionState.FULL_ACCESS ||
            permState == MediaPermissionState.PARTIAL_ACCESS
        )
    }

    @Test
    fun testIsUriAccessibleOnInvalidUri() {
        val accessible = discovery.isUriAccessible("content://media/invalid/999999999")
        assertFalse(accessible)
    }
}
