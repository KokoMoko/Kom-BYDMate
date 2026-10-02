package com.bydmate.app.helper

import org.junit.Assert.*
import org.junit.Test

class CombinedFreeformPlacementTest {
    private val ops = mutableListOf<String>()
    private var live = TaskModeState(WINDOWING_MODE_FULLSCREEN, 0)
    private fun place(
        type: Int = ACTIVITY_TYPE_STANDARD,
        move: (Int, Int) -> Unit = { _, d -> ops += "move"; live = live.copy(displayId = d) },
        apply: (Int, Int, Int, Int, Int) -> Unit = { _, _, _, _, _ ->
            ops += "wct"; live = live.copy(windowingMode = WINDOWING_MODE_FREEFORM)
        },
        state: (Int) -> TaskModeState? = { live },
        task: Int = 12,
        right: Int = 1280,
        park: (Int, Int, Int, Int, Int) -> Unit = { _, _, _, _, _ ->
            ops += "park"; live = live.copy(windowingMode = WINDOWING_MODE_FREEFORM)
        },
        visible: (Int) -> Boolean? = { false },
        stopped: (Int) -> Boolean? = { true },
        sleep: (Long) -> Unit = {},
        back: (Int) -> Unit = { ops += "back" },
    ) = tryCombinedFreeformPlacement(
        task, 4, 0, 38, right, 441, ACTIVITY_TYPE_STANDARD,
        { type }, move, apply, state,
        { ops += "focus" }, {}, sleep, back, park, visible, stopped,
    )

    @Test fun waitsForTheStopBeforeTheMove() {
        var checks = 0
        assertTrue(place(
            stopped = { checks++; if (checks >= 3) { ops += "stopped"; true } else false },
            sleep = { ops += "wait" },
        ))
        assertEquals(listOf("back", "wait", "wait", "stopped", "park", "move", "wct", "focus", "wait"), ops)
    }

    @Test fun stopWaitIsBounded() {
        var waits = 0
        assertTrue(place(stopped = { false }, sleep = { waits++ }))
        // 25 stop polls plus the confirmation poll after the move.
        assertEquals(26, waits)
    }

    @Test fun hidesThenParksTheTaskBeforeTheMove() {
        assertTrue(place())
        assertEquals(listOf("back", "park", "move", "wct", "focus"), ops)
    }

    @Test fun failedHideSkipsTheParkAndUsesTheOldOrder() {
        assertTrue(place(back = { throw IllegalStateException("no WCT") }))
        assertEquals(listOf("move", "wct", "focus"), ops)
    }

    @Test fun parkGetsTheFinalBounds() {
        val sent = mutableListOf<List<Int>>()
        assertTrue(place(park = { _, l, t, r, b -> sent += listOf(l, t, r, b) }))
        assertEquals(listOf(listOf(0, 38, 1280, 441)), sent)
    }

    @Test fun failedParkFallsBackToMoveThenSize() {
        assertTrue(place(park = { _, _, _, _, _ -> throw IllegalStateException("no WCT") }))
        assertEquals(listOf("back", "move", "wct", "focus"), ops)
    }

    @Test fun doesNotParkOrMoveTaskAlreadyOnTarget() {
        live = live.copy(displayId = 4)
        assertTrue(place())
        assertEquals(listOf("wct", "focus"), ops)
    }

    @Test fun incompatibleTypeUsesFallbackWithoutChangingTask() {
        assertFalse(place(type = ACTIVITY_TYPE_RECENTS))
        assertTrue(ops.isEmpty())
    }

    @Test fun failedWctUsesFallback() {
        assertFalse(place(apply = { _, _, _, _, _ -> throw IllegalStateException("no API") }))
    }

    @Test fun unreadableStateCannotReportSuccess() {
        assertFalse(place(state = { null }))
    }

    @Test fun coercedFullscreenCannotReportSuccess() {
        assertFalse(place(
            park = { _, _, _, _, _ -> ops += "park ignored" },
            apply = { _, _, _, _, _ -> ops += "wct ignored" },
        ))
    }

    @Test fun failedMoveDoesNotApplyTheFinalWct() {
        assertFalse(place(move = { _, _ -> throw IllegalStateException("refused") }))
        assertEquals(listOf("back", "park"), ops)
    }

    @Test fun invalidInputDoesNotMutateTask() {
        assertFalse(place(task = -1))
        assertFalse(place(right = 0))
        assertTrue(ops.isEmpty())
    }
}
