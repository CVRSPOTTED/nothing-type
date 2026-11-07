package dev.nothingtype.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class PlacementPolicyTest {
    @Test fun reservesSpaceEvenWhenOemReportsZero() { assertEquals(56, PlacementPolicy.bottomPadding(0, 56)) }
    @Test fun largerSystemBarWinsWithoutAddingReserveTwice() { assertEquals(80, PlacementPolicy.bottomPadding(80, 56)) }
    @Test fun largerManualReserveWins() { assertEquals(78, PlacementPolicy.bottomPadding(24, 78)) }
    @Test fun disabledReserveStillHonoursSystemBar() { assertEquals(24, PlacementPolicy.bottomPadding(24, 0)) }
    @Test fun invalidInsetsCannotCreateNegativePadding() { assertEquals(0, PlacementPolicy.bottomPadding(-20, -1)) }
    @Test fun oneHandWidthFitsInsideSideMargins() { assertEquals(270, PlacementPolicy.keyboardWidth(400, 20, 75)) }
    @Test fun fullWidthUsesAvailableSpace() { assertEquals(360, PlacementPolicy.keyboardWidth(400, 20, 100)) }
    @Test fun tinyWindowStillHasPositiveWidth() { assertEquals(1, PlacementPolicy.keyboardWidth(10, 24, 75)) }
}
