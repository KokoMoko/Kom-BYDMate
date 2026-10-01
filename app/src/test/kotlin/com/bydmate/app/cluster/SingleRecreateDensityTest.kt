package com.bydmate.app.cluster

import org.junit.Assert.assertEquals
import org.junit.Test

class SingleRecreateDensityTest {
    @Test fun `native density on the 720-px cluster is raised above sw320dp`() {
        // 720 px * 160 / 361 = 319 dp: full display and the 532-px window share the <320 bucket.
        assertEquals(361, singleRecreateDensity(requested = 0, nativeDpi = 320, displayHeightPx = 720))
    }

    @Test fun `a scale already above the floor is sent unchanged`() {
        assertEquals(371, singleRecreateDensity(requested = 371, nativeDpi = 320, displayHeightPx = 720))
    }

    @Test fun `a smaller scale is raised to the floor`() {
        assertEquals(361, singleRecreateDensity(requested = 160, nativeDpi = 320, displayHeightPx = 720))
    }

    @Test fun `a short panel whose native density already clears the floor keeps native`() {
        assertEquals(0, singleRecreateDensity(requested = 0, nativeDpi = 320, displayHeightPx = 480))
    }

    @Test fun `unknown display height leaves the request alone`() {
        assertEquals(0, singleRecreateDensity(requested = 0, nativeDpi = 320, displayHeightPx = 0))
    }
}
