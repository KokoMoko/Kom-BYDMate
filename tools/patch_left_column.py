"""Kom-BYDMate: Главная-ի ձախ սյունակը՝ կոմպակտ տող + Phone widget + ինսայթ + Music widget։

GaugeYieldingColumn-ը (մեծ SOC + 1.1/1.2 տողեր) փոխարինվում է պարզ Column-ով․ popup-ները մնում են։
"""
from pathlib import Path

p = Path(__file__).resolve().parent.parent / "app/src/main/kotlin/com/bydmate/app/ui/dashboard/DashboardScreen.kt"
src = p.read_text(encoding="utf-8")

start = src.index("                GaugeYieldingColumn(")
end = src.index("                    // Pop-up dialogs")
new = '''                // Kom-BYDMate: կոմպակտ տող (SOC | պաշար | ջերմաստիճան) + Phone widget + ինսայթ + Music widget
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DashboardTopRow(state = state)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DashboardWidgetSlot(
                            slot = DashboardWidgets.SLOT_PHONE,
                            emptyHint = stringResource(R.string.kom_widget_hint_phone),
                            requestGrant = { cb -> viewModel.grantWidgetBind(cb) },
                            modifier = Modifier.fillMaxWidth().height(84.dp),
                        )
                        val insightColor = when (state.effectiveInsightTone) {
                            "critical" -> SocRed
                            "warning" -> SocYellow
                            else -> AccentGreen
                        }
                        InsightCard(
                            title = state.insightTitle,
                            summary = state.insightSummary,
                            borderColor = insightColor,
                            onClick = { viewModel.toggleInsightExpanded() }
                        )
                        DashboardWidgetSlot(
                            slot = DashboardWidgets.SLOT_LEFT,
                            emptyHint = stringResource(R.string.kom_widget_hint_music),
                            requestGrant = { cb -> viewModel.grantWidgetBind(cb) },
                            modifier = Modifier.fillMaxWidth().height(190.dp),
                        )
                    }

'''
src = src[:start] + new + src[end:]
p.write_text(src, encoding="utf-8")
print("patched")
