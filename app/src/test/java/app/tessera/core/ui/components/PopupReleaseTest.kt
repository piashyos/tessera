package app.tessera.core.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupReleaseTest {
    private val threshold = 120f
    private val fling = 500f

    @Test fun slowPastTheThresholdCloses() = assertTrue(releaseDismisses(200f, 50f, threshold, fling))

    @Test fun slowShortOfTheThresholdReturns() = assertFalse(releaseDismisses(60f, 50f, threshold, fling))

    @Test fun shortFastFlingDownCloses() = assertTrue(releaseDismisses(40f, 900f, threshold, fling))

    @Test fun pastTheThresholdButFlungUpReturns() = assertFalse(releaseDismisses(200f, -900f, threshold, fling))

    @Test fun exactlyOnTheThresholdCloses() = assertTrue(releaseDismisses(threshold, 0f, threshold, fling))
}
