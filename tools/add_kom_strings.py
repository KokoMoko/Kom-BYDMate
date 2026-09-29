"""Ավելացնում է Kom-BYDMate-ի տեքստերը values (ru) և values-en strings.xml-ում (կրկնակի չի ավելացնում)։"""
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app/src/main/res"

STRINGS = {
    # name: (ru, en)
    "kom_widget_hint_music": ("Добавить виджет (напр. Яндекс Музыка)", "Add a widget (e.g. Yandex Music)"),
    "kom_widget_hint_weather": ("Добавить виджет (напр. AccuWeather)", "Add a widget (e.g. AccuWeather)"),
    "kom_widget_no_permission": (
        "Нет разрешения на виджеты. Выполните на компьютере:\\n\\n%1$s",
        "No permission to host widgets. Run on a computer:\\n\\n%1$s",
    ),
    "kom_widget_pick_title": ("Выберите виджет", "Choose a widget"),
    "kom_widget_menu_title": ("Виджет", "Widget"),
    "kom_widget_change": ("Сменить виджет", "Change widget"),
    "kom_widget_remove": ("Убрать виджет", "Remove widget"),
    "kom_widget_configure": ("Настроить виджет", "Configure widget"),
    "kom_widget_size": ("Размер виджета", "Widget size"),
    "kom_widget_size_hint": (
        "Меньше — виджет покажет больше информации мелким шрифтом. Больше — крупнее.",
        "Smaller — the widget shows more content in smaller text. Larger — bigger text.",
    ),
    "kom_done": ("Готово", "Done"),
    "kom_speedo_style": ("Стиль спидометра", "Speedometer style"),
    "kom_speedo_modern": ("Современная дуга", "Modern arc"),
    "kom_speedo_classic": ("Классический", "Classic"),
    "kom_speedo_digital": ("Цифровой + LED", "Digital + LED"),
    "kom_speedo_minimal": ("Минималистичное кольцо", "Minimal ring"),
    "kom_speedo_linear": ("Линейный", "Linear"),
    "kom_speedo_speed_label": ("Скорость", "Speed"),
    "kom_speedo_settings": ("Спидометр", "Speedometer"),
    "kom_speedo_max_speed": ("Макс. скорость: %1$d км/ч", "Max speed: %1$d km/h"),
    "kom_speedo_tolerance": ("Допуск: %1$d%% (до %2$d км/ч)", "Tolerance: %1$d%% (up to %2$d km/h)"),
    "kom_speedo_nav_hint": (
        "Если навигатор знает ограничение, используется оно. Стиль меняется свайпом влево/вправо.",
        "When the navigator knows the limit, it is used instead. Swipe left/right to change the style.",
    ),
    "kom_widget_hint_phone": ("Добавить виджет (напр. Телефон)", "Add a widget (e.g. Phone)"),
    "kom_ctx_route": ("📍 Маршрут", "📍 Route"),
    "kom_ctx_arrival": ("🕒 Прибытие", "🕒 Arrival"),
    "kom_ctx_arrival_soc": ("🔋 При прибытии", "🔋 On arrival"),
    "kom_ctx_odometer": ("🛣 Пробег", "🛣 Odometer"),
    "kom_ctx_today": ("📅 Сегодня", "📅 Today"),
    "kom_mydash_edit": ("Изменить", "Edit"),
    "kom_mydash_add": ("Добавить", "Add"),
    "kom_mydash_reset": ("Сбросить", "Reset"),
    "kom_mydash_add_title": ("Добавить плитку", "Add a tile"),
    "kom_mydash_no_space": ("Нет свободного места — уменьшите или удалите плитку.", "No free space — shrink or remove a tile."),
    "kom_tile_widget": ("Виджет Android", "Android widget"),
    "kom_tile_soc": ("Заряд (SOC)", "Charge (SOC)"),
    "kom_tile_range": ("Запас хода", "Range"),
    "kom_tile_temps": ("Температура", "Temperature"),
    "kom_widget_hint_generic": ("Добавить виджет", "Add a widget"),
    "kom_welcome_title": ("Привет, %1$s", "Welcome %1$s"),
    "kom_tile_app": ("Приложение", "Application"),
    "kom_about_copyright": ("© 2026 KomS", "© 2026 KomS"),
    "kom_about_inspired": ("Вдохновлено BYDMate от AndyShaman", "Inspired by AndyShaman BYDMate"),
    "kom_about_inspired_prefix": ("Вдохновлено\\u0020", "Inspired by\\u0020"),
    "kom_tile_app_pick": ("Выберите приложение", "Choose an application"),
    "kom_tile_app_hint": ("Откроется здесь после «Готово»", "Opens here after “Done”"),
    "kom_speedo_tolerance_kmh": ("Допуск: +%1$d км/ч (до %2$d км/ч)", "Tolerance: +%1$d km/h (up to %2$d km/h)"),
    "kom_tile_app_panel_hint": ("Нажмите — откроется рядом (1/3 + 2/3)", "Tap to open side by side (1/3 + 2/3)"),
    "kom_panel_failed": ("Не удалось открыть split: %1$s", "Couldn't open split: %1$s"),
    "kom_autostart_title": ("При включении машины", "When the car starts"),
    "kom_autostart_navi": ("Запустить Яндекс Навигатор в фоне", "Start Yandex Navigator in the background"),
    "kom_autostart_music": ("Подключить Яндекс Музыку", "Connect Yandex Music"),
    "kom_autostart_music_play": ("…и начать воспроизведение", "…and start playback"),
    "kom_autostart_hint": (
        "Приложения запускаются и остаются в фоне, на экране остаётся Главная.",
        "The apps start and stay in the background; the dashboard stays on screen.",
    ),
    "kom_capture_button": ("📷 Снимок экрана через 10 с", "📷 Screenshot in 10 s"),
    "kom_capture_wait": (
        "Откройте главный экран DiLink с машиной (вид сзади) — снимок через 10 с…",
        "Open the DiLink home screen with the car (rear view) — screenshot in 10 s…",
    ),
    "kom_capture_saved": ("Сохранено: %1$s", "Saved: %1$s"),
    "kom_capture_failed": ("Не удалось сделать снимок (ADB)", "Screenshot failed (ADB)"),
    "kom_tile_app_open_hint": ("Нажмите — открыть на весь экран", "Tap to open full screen"),
    "kom_panel_exit": ("Весь экран", "Full screen"),
    "kom_cluster_charging_blocked": (
        "Во время зарядки навигатор на экран водителя не выводится — там экран зарядки",
        "Navigation on the driver display is unavailable while charging",
    ),
    "kom_can_icon_test": ("Тест значков CAN (HUD / экран водителя)", "CAN icon test (HUD / driver display)"),
    "kom_cluster_settings": ("Кластер", "Cluster"),
    "kom_cluster_top_height": ("Высота верхней части: %1$d%%", "Top area height: %1$d%%"),
    "kom_cluster_style_hint": (
        "Нажмите на прибор, чтобы сменить его стиль: стрелка, дуга, цифры, полоса.",
        "Tap a gauge to change its style: needle, arc, digital, bar.",
    ),
    "kom_cluster_power": ("мощность", "power"),
    "kom_cluster_regen": ("рекуперация", "regen"),
    "kom_cluster_charging": ("зарядка", "charging"),
    "kom_tile_app_split": (
        "В режиме split недоступно — откройте на весь экран",
        "Not available in split — open full screen",
    ),
    "kom_settings_welcome_name": ("Имя для приветствия", "Welcome name"),
    "kom_settings_welcome_hint": (
        "Показывается вверху Главной вместо MyBYD, например «Привет, Kom». Пусто — MyBYD.",
        "Shown at the top of the dashboard instead of MyBYD, e.g. “Welcome Kom”. Empty — MyBYD.",
    ),
    "kom_cancel": ("Отмена", "Cancel"),
    "kom_ctx_charging": ("🔌 Зарядка", "🔌 Charging"),
    "kom_ctx_range_left": ("≈ %1$s км запаса", "≈ %1$s km range"),
    "kom_ctx_speed_unit": ("км/ч", "km/h"),
    "kom_ctx_trip": ("🚗 Поездка", "🚗 Trip"),
    "kom_ctx_consumption": ("⚡ Расход", "⚡ Consumption"),
    "kom_ctx_km": ("%1$s км", "%1$s km"),
    "kom_ctx_hours_min": ("%1$d ч %2$d мин", "%1$d h %2$d min"),
    "kom_ctx_min": ("%1$d мин", "%1$d min"),
    "kom_ctx_kwh100": ("%1$s кВт·ч/100", "%1$s kWh/100"),
    "kom_ctx_parked": ("🅿️ Стоянка", "🅿️ Parked"),
    "kom_ctx_range_soc": ("км запаса · %1$s%%", "km range · %1$s%%"),
}


def esc(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")


for folder, idx in (("values", 0), ("values-en", 1)):
    p = RES / folder / "strings.xml"
    c = p.read_text(encoding="utf-8")
    add = [f'    <string name="{k}">{esc(v[idx])}</string>' for k, v in STRINGS.items() if f'name="{k}"' not in c]
    if add:
        block = "    <!-- Kom-BYDMate -->\n" + "\n".join(add) + "\n"
        c = c.replace("</resources>", block + "</resources>")
        p.write_text(c, encoding="utf-8")
    print(folder, "added", len(add))
