package com.bydmate.app.ui.dashboard

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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Kom-BYDMate: Главная-ի էջերը՝ 0 = «Cluster» (լռելյայն), 1 = Classic։
 * «My Dashboard»-ը (խմբագրվող ցանց + Application սալիկ) հեռացված է․ «լողացող» պատուհանը այս
 * firmware-ում բախվում էր BYDMate-ի split-ի հետ (1/3 Music + 2/3 Navigator)։
 * Փոխվում է swipe-ով կամ «● ○ ○» կետերով․ խմբագրման ժամանակ swipe-ը անջատված է։
 */
@Composable
fun DashboardHost(onOpenTechPanel: () -> Unit, onOpenSettings: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val pager = rememberPagerState(initialPage = 0) { 2 }
    val scope = rememberCoroutineScope()

    // Split-ում (Panel)՝ միայն My Dashboard-ը, առանց swipe-ի և կետերի
    val inSplit = rememberInMultiWindow()
    androidx.compose.runtime.LaunchedEffect(inSplit) { if (inSplit && pager.currentPage != 0) pager.scrollToPage(0) }

    Box(Modifier.fillMaxSize()) {
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
