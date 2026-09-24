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
