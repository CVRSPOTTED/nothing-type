package dev.keyloom.keyboard

/** Values are pixels here; callers convert user-facing dp before entering this policy. */
object PlacementPolicy {
    fun bottomPadding(systemBottom: Int, minimumReserve: Int, lift: Int): Int =
        maxOf(0, systemBottom, minimumReserve) + lift.coerceAtLeast(0)

    fun keyboardWidth(available: Int, side: Int, percent: Int): Int =
        ((available - side.coerceAtLeast(0) * 2).coerceAtLeast(1) * percent.coerceIn(75, 100) / 100).coerceAtLeast(1)
}
