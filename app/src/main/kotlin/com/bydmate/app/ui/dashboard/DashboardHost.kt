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
 * Kom-BYDMate: Главная-ի երկու էջ՝ 0 = «My Dashboard» (լռելյայն), 1 = Classic։
 * Փոխվում է swipe-ով կամ «MyBYD ● ○» կետերով․ խմբագրման ժամանակ swipe-ը անջատված է։
 */
@Composable
fun DashboardHost(onOpenTechPanel: () -> Unit, onOpenSettings: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val pager = rememberPagerState(initialPage = 0) { 2 }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, userScrollEnabled = !editing, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 0) {
                MyDashboardScreen(
                    editing = editing,
                    onEditingChange = { editing = it },
                    // «Application» սալիկի պատուհանը՝ միայն երբ My Dashboard-ը հանգիստ վիճակում է
                    pageVisible = pager.currentPage == 0 && !pager.isScrollInProgress,
                )
            } else {
                DashboardScreen(onOpenTechPanel = onOpenTechPanel, onOpenSettings = onOpenSettings)
            }
        }
        // Վերևի կենտրոնում (վերնագիրը «Welcome <անուն>» կարող է երկար լինել)
        DashboardPageDots(
            count = 2,
            current = pager.currentPage,
            onSelect = { i -> if (!editing) scope.launch { pager.animateScrollToPage(i) } },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 11.dp),
        )
    }
}
