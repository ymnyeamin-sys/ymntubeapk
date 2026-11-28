/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ServiceThemeTest {

    @Test
    fun testDefaultServiceIsYouTube() = runComposeUiTest {
        assertEquals(currentService(), Service.YOUTUBE)
    }

    @Test
    fun testOnlyYouTubeIsAvailable() = runComposeUiTest {
        assertEquals(Service.entries, listOf(Service.YOUTUBE))
    }
}
