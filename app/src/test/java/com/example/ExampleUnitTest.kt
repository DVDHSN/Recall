package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testScreenshotIdentification() {
    assertTrue(com.example.core.media.ScreenshotDiscovery.isScreenshot("Screenshot_20261012.png", "Pictures/Screenshots"))
    assertTrue(com.example.core.media.ScreenshotDiscovery.isScreenshot("Screen_shot_wifi.jpg", "DCIM"))
    assertFalse(com.example.core.media.ScreenshotDiscovery.isScreenshot("IMG_20261012_Beach.jpg", "DCIM/Camera"))
  }

  @Test
  fun testCameraPhotoIdentification() {
    assertTrue(com.example.core.media.ScreenshotDiscovery.isCameraPhoto("IMG_20261012_Beach.jpg", "DCIM/Camera"))
    assertTrue(com.example.core.media.ScreenshotDiscovery.isCameraPhoto("PXL_20261012_Sunset.jpg", "Pictures"))
    assertFalse(com.example.core.media.ScreenshotDiscovery.isCameraPhoto("Receipt_Document.pdf", "Documents"))
  }
}
