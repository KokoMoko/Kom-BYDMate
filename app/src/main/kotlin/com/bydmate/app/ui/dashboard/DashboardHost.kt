package com.bydmate.app.ui.dashboard

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Kom-BYDMate: Главная-ի էջերը՝ 0 = «Cluster» (լռելյայն), 1 = Classic։
 * «My Dashboard»-ը (խմբագրվող ցանց + Application սալիկ) հեռացված է․ «լողացող» պատուհանը այս
 * firmware-ում բախվում էր BYDMate-ի split-ի հետ (1/3 Music + 2/3 Navigator)։
 * Փոխվում է swipe-ով կամ «● ○ ○» կետերով․ խմբագրման ժամանակ swipe-ը անջատված է։
 * Վերնագրի տողից ներքև swipe-ը app-ը թաքցնում է (տակի քարտեզը երևում է)։
 */
@Composable
fun DashboardHost(onOpenTechPanel: () -> Unit, onOpenSettings: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val pager = rememberPagerState(initialPage = 0) { 2 }
    val scope = rememberCoroutineScope()

    // Split-ում (Panel)՝ միայն My Dashboard-ը, առանց swipe-ի և կետերի
    val inSplit = rememberInMultiWindow()
    androidx.compose.runtime.LaunchedEffect(inSplit) { if (inSplit && pager.currentPage != 0) pager.scrollToPage(0) }

    val context = LocalContext.current
    Box(Modifier.fillMaxSize().swipeDownToHide { context.findActivity()?.moveTaskToBack(true) }) {
        HorizontalPager(state = pager, userScrollEnabled = !editing && !inSplit, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> ClusterScreen()
                else -> DashboardScreen(onOpenTechPanel = onOpenTechPanel, onOpenSettings = onOpenSettings)
            }
        }
        // Վերևի կենտրոնում (վերնագիրը «Welcome <անուն>» կարող է երկար լինել)
        if (!inSplit) DashboardPageDots(
            count = 2,
            current = pager.currentPage,
            onSelect = { i -> if (!editing) scope.launch { pager.animateScrollToPage(i) } },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 11.dp),
        )
    }
}

/**
 * Kom-BYDMate: a downward swipe that starts on the title row puts the app away (moveTaskToBack),
 * so whatever lies under it — the navigator's map — comes back without the car's Home / Back
 * keys. The row starts a little below the screen edge, so BYD's own pull-down shade keeps its
 * gesture. Pointer events are only watched, never consumed: buttons, dots and the pager work
 * exactly as before.
 */
private fun Modifier.swipeDownToHide(onHide: () -> Unit): Modifier = pointerInput(Unit) {
    val zone = TITLE_ZONE_DP.dp.toPx()
    val needed = HIDE_SWIPE_DP.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.position.y > zone) return@awaitEachGesture
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val dy = change.position.y - down.position.y
            val dx = kotlin.math.abs(change.position.x - down.position.x)
            if (dy >= needed && dy > dx * 1.5f) { onHide(); break }
        }
    }
}

private const val TITLE_ZONE_DP = 64
private const val HIDE_SWIPE_DP = 90

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
