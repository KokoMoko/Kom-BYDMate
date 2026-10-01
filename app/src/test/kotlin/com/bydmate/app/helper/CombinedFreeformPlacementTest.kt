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
    ) = tryCombinedFreeformPlacement(
        task, 4, 0, 38, right, 441, ACTIVITY_TYPE_STANDARD,
        { type }, move, apply, state,
        { ops += "focus" }, {}, {},
    )

    @Test fun combinesModeAndBoundsAfterMove() {
        assertTrue(place())
        assertEquals(listOf("move", "wct", "focus"), ops)
    }

    @Test fun doesNotMoveTaskAlreadyOnTarget() {
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
        assertFalse(place(apply = { _, _, _, _, _ -> ops += "wct ignored" }))
    }
    @Test fun failedMoveDoesNotApplyWct() {
        assertFalse(place(move = { _, _ -> throw IllegalStateException("refused") }))
        assertTrue(ops.isEmpty())
    }
    @Test fun invalidInputDoesNotMutateTask() {
        assertFalse(place(task = -1))
        assertFalse(place(right = 0))
        assertTrue(ops.isEmpty())
    }
}
