package org.holidaylink.app

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.holidaylink.app.ui.HolidayLinkCallsPreviewLight
import org.holidaylink.app.ui.HolidayLinkChatThreadPreviewLight
import org.holidaylink.app.ui.HolidayLinkChatsPreviewDark
import org.holidaylink.app.ui.HolidayLinkChatsPreviewLight
import org.holidaylink.app.ui.HolidayLinkContactsPreviewLight
import org.holidaylink.app.ui.HolidayLinkMenuPreviewLight
import org.holidaylink.app.ui.HolidayLinkProfilePreviewLight
import org.holidaylink.app.ui.HolidayLinkSettingsPreviewLight
import org.holidaylink.app.ui.HolidayLinkStatusPreviewLight
import org.junit.Rule
import org.junit.Test

/** Renders the Compose preview gallery on the JVM (no emulator needed). */
class PreviewSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 1080, screenHeight = 2340)
    )

    @Test
    fun chatsLight() {
        paparazzi.snapshot { HolidayLinkChatsPreviewLight() }
    }

    @Test
    fun chatsDark() {
        paparazzi.snapshot { HolidayLinkChatsPreviewDark() }
    }

    @Test
    fun chatThread() {
        paparazzi.snapshot { HolidayLinkChatThreadPreviewLight() }
    }

    @Test
    fun calls() {
        paparazzi.snapshot { HolidayLinkCallsPreviewLight() }
    }

    @Test
    fun contacts() {
        paparazzi.snapshot { HolidayLinkContactsPreviewLight() }
    }

    @Test
    fun status() {
        paparazzi.snapshot { HolidayLinkStatusPreviewLight() }
    }

    @Test
    fun settings() {
        paparazzi.snapshot { HolidayLinkSettingsPreviewLight() }
    }

    @Test
    fun profile() {
        paparazzi.snapshot { HolidayLinkProfilePreviewLight() }
    }

    @Test
    fun menu() {
        paparazzi.snapshot { HolidayLinkMenuPreviewLight() }
    }
}
