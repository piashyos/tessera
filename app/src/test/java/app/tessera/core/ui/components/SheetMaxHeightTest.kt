package app.tessera.core.ui.components

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The card's height when it stands over a bottom inset (spec §4.3): 60 % at most, three rows (144 dp) at least. */
class SheetMaxHeightTest {
    private val density = Density(1f) // 1 px = 1 dp
    private val sixty = 480.dp

    @Test fun plentyOfRoomIsCappedAtSixtyPercent() =
        assertEquals(480.dp, sheetMaxHeight(containerPx = 1000, statusTopPx = 24, belowCardPx = 100, sixtyPercent = sixty, density = density))

    @Test fun roomBetweenTheMinimumAndSixtyPercentIsTheRoom() =
        assertEquals(300.dp, sheetMaxHeight(containerPx = 500, statusTopPx = 24, belowCardPx = 176, sixtyPercent = sixty, density = density))

    @Test fun lessRoomThanThreeRowsIsThreeRows() {
        assertEquals(144.dp, sheetMaxHeight(containerPx = 400, statusTopPx = 24, belowCardPx = 300, sixtyPercent = sixty, density = density))
        assertEquals(144.dp, sheetMaxHeight(containerPx = 400, statusTopPx = 24, belowCardPx = 500, sixtyPercent = sixty, density = density)) // negative room
    }
}
