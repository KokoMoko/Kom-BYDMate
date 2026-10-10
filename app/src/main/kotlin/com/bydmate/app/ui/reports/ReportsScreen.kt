package com.bydmate.app.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bydmate.app.R
import com.bydmate.app.ui.charges.ChargesScreen
import com.bydmate.app.ui.dashboard.TripCounterButton
import com.bydmate.app.ui.dashboard.TripCounterDialog
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.CardSurface
import com.bydmate.app.ui.theme.NavyDark
import com.bydmate.app.ui.theme.TextMuted
import com.bydmate.app.ui.trips.TripsScreen

/**
 * Kom-BYDMate: «Reports» — trips and charges in one place, two tabs. The trips tab carries
 * TRIP 1 / TRIP 2 in its header: a tap opens the details (with «reset after charging»),
 * a long press resets the counter.
 */
@Composable
fun ReportsScreen(
    onOpenTemperature: () -> Unit,
    onNavigateSettings: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(NavyDark)) {
        Row(
            Modifier.fillMaxWidth().height(78.dp).padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReportsTab(stringResource(R.string.nav_tab_trips), tab == 0) { tab = 0 }
            Spacer(Modifier.width(8.dp))
            ReportsTab(stringResource(R.string.nav_tab_charges), tab == 1) { tab = 1 }
            Spacer(Modifier.weight(1f))
            if (tab == 0) TripCounters(Modifier.width(420.dp).fillMaxHeight(), horizontal = true)
        }
        Box(Modifier.weight(1f)) {
            if (tab == 0) TripsScreen(onOpenTemperature = onOpenTemperature)
            else ChargesScreen(onNavigateSettings = onNavigateSettings)
        }
    }
}

@Composable
private fun ReportsTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentGreen.copy(alpha = 0.18f) else CardSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (selected) AccentGreen else TextMuted, fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

/**
 * TRIP 1 and TRIP 2 side by side ([horizontal]) or stacked, with their details popup.
 * Also the dashboard's built-in «TRIP» card ([TripCountersCard]).
 */
@Composable
fun TripCounters(modifier: Modifier, horizontal: Boolean, vm: TripCountersViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val t1 = stringResource(R.string.dashboard_trip1_label)
    val t2 = stringResource(R.string.dashboard_trip2_label)
    if (horizontal) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TripCounterButton(t1, s.trip1, Modifier.weight(1f).fillMaxHeight(), { vm.open(1) }, { vm.reset(1) })
            TripCounterButton(t2, s.trip2, Modifier.weight(1f).fillMaxHeight(), { vm.open(2) }, { vm.reset(2) })
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TripCounterButton(t1, s.trip1, Modifier.weight(1f).fillMaxWidth(), { vm.open(1) }, { vm.reset(1) })
            TripCounterButton(t2, s.trip2, Modifier.weight(1f).fillMaxWidth(), { vm.open(2) }, { vm.reset(2) })
        }
    }
    val open = when (s.expanded) { 1 -> s.trip1; 2 -> s.trip2; else -> null }
    if (open != null) TripCounterDialog(
        if (s.expanded == 1) t1 else t2, open, s.currencySymbol,
        mode = if (s.expanded == 1) s.mode1 else s.mode2,
        onModeChange = { vm.setMode(s.expanded, it) },
    ) { vm.close() }
}

/** The dashboard's built-in «TRIP» widget: the two counters, laid out to fit the slot. */
@Composable
fun TripCountersCard(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.padding(6.dp)) {
        TripCounters(Modifier.fillMaxSize(), horizontal = maxWidth > maxHeight * 1.4f)
    }
}
