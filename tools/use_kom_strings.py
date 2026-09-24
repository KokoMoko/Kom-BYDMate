"""Kom-BYDMate-ի կոդում ամրացված ռուսերեն տեքստերը փոխարինում է stringResource(R.string.kom_*)-ով։"""
from pathlib import Path

D = Path(__file__).resolve().parent.parent / "app/src/main/kotlin/com/bydmate/app/ui/dashboard"


def patch(name, pairs, imports=()):
    p = D / name
    c = p.read_text(encoding="utf-8")
    for old, new in pairs:
        assert c.count(old) == 1, f"{name}: not found / not unique: {old[:60]}"
        c = c.replace(old, new)
    for imp in imports:
        if imp not in c:
            c = c.replace("import androidx.compose.ui.unit.sp\n", "import androidx.compose.ui.unit.sp\n" + imp + "\n", 1)
    p.write_text(c, encoding="utf-8")
    print("patched", name)


RES_IMPORTS = ("import androidx.compose.ui.res.stringResource", "import com.bydmate.app.R")

patch("DashboardScreen.kt", [
    ('emptyHint = "Добавить виджет (напр. Яндекс Музыка)",',
     'emptyHint = stringResource(R.string.kom_widget_hint_music),'),
    ('emptyHint = "Добавить виджет (напр. AccuWeather)",',
     'emptyHint = stringResource(R.string.kom_widget_hint_weather),'),
])

patch("DashboardWidgetSlot.kt", [
    ('    val noPermissionText = "Нет разрешения на виджеты. Выполните на компьютере:\\n\\n" +\n'
     '        "adb shell appwidget grantbind --package ${context.packageName}"',
     '    val noPermissionText = stringResource(\n'
     '        R.string.kom_widget_no_permission,\n'
     '        "adb shell appwidget grantbind --package ${context.packageName}",\n'
     '    )'),
    ('title = { Text("Выберите виджет", color = TextPrimary) },',
     'title = { Text(stringResource(R.string.kom_widget_pick_title), color = TextPrimary) },'),
    ('confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Отмена") } },',
     'confirmButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.kom_cancel)) } },'),
    ('title = { Text("Виджет", color = TextPrimary) },',
     'title = { Text(stringResource(R.string.kom_widget_menu_title), color = TextPrimary) },'),
    ('Text("Сменить виджет", color = AccentGreen,',
     'Text(stringResource(R.string.kom_widget_change), color = AccentGreen,'),
    ('Text("Убрать виджет", color = TextSecondary,',
     'Text(stringResource(R.string.kom_widget_remove), color = TextSecondary,'),
    ('confirmButton = { TextButton(onClick = { showMenu = false }) { Text("Отмена") } },',
     'confirmButton = { TextButton(onClick = { showMenu = false }) { Text(stringResource(R.string.kom_cancel)) } },'),
], RES_IMPORTS)

patch("DashboardContextCard.kt", [
    ('Text("🔌 Зарядка", color = AccentGreen,',
     'Text(stringResource(R.string.kom_ctx_charging), color = AccentGreen,'),
    ('Text("≈ ${"%.0f".format(it)} км запаса", color = TextSecondary, fontSize = 14.sp)',
     'Text(stringResource(R.string.kom_ctx_range_left, "%.0f".format(it)), color = TextSecondary, fontSize = 14.sp)'),
    ('Text("км/ч", color = TextSecondary,',
     'Text(stringResource(R.string.kom_ctx_speed_unit), color = TextSecondary,'),
    ('''            Stat("🚗 Поездка", buildString {
                append(state.tripDistanceKm?.let { "%.1f км".format(it) } ?: "— км")
                append(" · ")
                append(if (minutes >= 60) "${minutes / 60} ч ${minutes % 60} мин" else "$minutes мин")
            })
            Stat("⚡ Расход", state.consumption?.let { "%.1f кВт·ч/100".format(it) } ?: "—",''',
     '''            val km = stringResource(R.string.kom_ctx_km, state.tripDistanceKm?.let { "%.1f".format(it) } ?: "—")
            val time = if (minutes >= 60) {
                stringResource(R.string.kom_ctx_hours_min, (minutes / 60).toInt(), (minutes % 60).toInt())
            } else {
                stringResource(R.string.kom_ctx_min, minutes.toInt())
            }
            Stat(stringResource(R.string.kom_ctx_trip), "$km · $time")
            val cons = state.consumption?.let { stringResource(R.string.kom_ctx_kwh100, "%.1f".format(it)) } ?: "—"
            Stat(stringResource(R.string.kom_ctx_consumption), cons,'''),
    ('Text("🅿️ Стоянка", color = AccentBlue,',
     'Text(stringResource(R.string.kom_ctx_parked), color = AccentBlue,'),
    ('Text("км запаса · ${state.soc ?: "—"}%", color = TextSecondary, fontSize = 16.sp,',
     'Text(stringResource(R.string.kom_ctx_range_soc, state.soc?.toString() ?: "—"), color = TextSecondary, fontSize = 16.sp,'),
], RES_IMPORTS)
