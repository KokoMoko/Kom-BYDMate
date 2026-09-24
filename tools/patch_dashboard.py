"""Մեկանգամյա patch՝ Главная-ը դարձնում է Kom-BYDMate-ի dashboard-v2 դասավորությունը։"""
from pathlib import Path

p = Path(__file__).resolve().parent.parent / "app/src/main/kotlin/com/bydmate/app/ui/dashboard/DashboardScreen.kt"
src = p.read_text(encoding="utf-8")

# 1) Ձախ․ SoH/12V քարտ + TRIP 1/2 → A widget-ի սլոտ
left_start = src.index("                        // Battery card — 3 значения")
left_end = src.index("                    // Pop-up dialogs")
left_new = '''                        // Kom-BYDMate: A — widget-ի սլոտ (օր․ Yandex Music)՝ SoH/12V և TRIP 1/2-ի փոխարեն
                        DashboardWidgetSlot(
                            slot = DashboardWidgets.SLOT_LEFT,
                            emptyHint = "Добавить виджет (напр. Яндекс Музыка)",
                            requestGrant = { cb -> viewModel.grantWidgetBind(cb) },
                            modifier = Modifier.fillMaxWidth().height(190.dp),
                        )
                    }

'''
src = src[:left_start] + left_new + src[left_end:]

# 2) Աջ․ ժամանակահատվածներ + 4 քարտ + ուղևորություններ → B քարտ + եղանակի widget
right_start = src.index("            // RIGHT COLUMN — period filter + 4 cards + recent trips")
right_end = src.index("private val GaugeMaxSize")
right_new = '''            // Kom-BYDMate RIGHT COLUMN — B (իրավիճակային քարտ) + widget (օր․ AccuWeather)
            Column(
                modifier = Modifier.weight(0.6f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardContextCard(state = state, modifier = Modifier.fillMaxWidth().weight(0.36f))
                DashboardWidgetSlot(
                    slot = DashboardWidgets.SLOT_RIGHT,
                    emptyHint = "Добавить виджет (напр. AccuWeather)",
                    requestGrant = { cb -> viewModel.grantWidgetBind(cb) },
                    modifier = Modifier.fillMaxWidth().weight(0.64f),
                )
            }
        }
    }
}

'''
src = src[:right_start] + right_new + src[right_end:]
p.write_text(src, encoding="utf-8")
print("patched")
