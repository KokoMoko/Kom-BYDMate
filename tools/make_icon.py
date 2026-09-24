"""Kom-BYDMate-ի icon՝ մոխրաբեժ Sealion 06 (վերևից) կանաչ SOC աղեղով, BYDMate-ի մուգ ոճով։

Մեքենան վերցնում է n8n/tools/make_car.py-ից (նույնը, ինչ Telegram բոտի /tires նկարում)։
  python tools/make_icon.py [--preview]
"""
import importlib.util
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
CAR_SCRIPT = ROOT.parent / "n8n" / "tools" / "make_car.py"
N = 1024

spec = importlib.util.spec_from_file_location("make_car", CAR_SCRIPT)
car_mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(car_mod)


def render_car() -> Image.Image:
    """Մեքենան թափանցիկ ֆոնի վրա, կտրված իր սահմաններով։"""
    S = car_mod.S
    big = Image.new("RGBA", (car_mod.W * S, car_mod.H * S), (0, 0, 0, 0))
    car_mod.draw_car(ImageDraw.Draw(big))
    img = big.resize((car_mod.W, car_mod.H), Image.LANCZOS)
    # Հեռացնել ուղղության սլաքը (վերևի 90 px)
    img.paste((0, 0, 0, 0), (0, 0, car_mod.W, 90))
    return img.crop(img.getbbox())


def draw() -> Image.Image:
    # Մուգ կլորացված ֆոն (BYDMate-ի icon-ի նման)
    bg = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    grad = Image.new("RGB", (N, N))
    gd = ImageDraw.Draw(grad)
    for y in range(N):
        t = y / (N - 1)
        gd.line([(0, y), (N, y)], fill=(int(30 - 10 * t), int(42 - 12 * t), int(54 - 14 * t)))
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, N - 1, N - 1), radius=230, fill=255)
    bg.paste(grad, (0, 0), mask)

    # Կանաչ SOC աղեղ (≈80%)՝ փափուկ փայլով
    ring = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    rd = ImageDraw.Draw(ring)
    box = (150, 150, N - 150, N - 150)
    rd.arc(box, start=135, end=405, fill=(61, 220, 132, 60), width=70)       # հետք
    rd.arc(box, start=135, end=351, fill=(61, 220, 132, 255), width=70)      # լիցք
    glow = ring.filter(ImageFilter.GaussianBlur(18))
    bg = Image.alpha_composite(bg, glow)
    bg = Image.alpha_composite(bg, ring)

    # Մեքենան՝ մեջտեղում, ստվերով
    car = render_car()
    h = 560
    car = car.resize((int(car.width * h / car.height), h), Image.LANCZOS)
    x, y = (N - car.width) // 2, (N - car.height) // 2 + 10
    shadow = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 150), (x + 10, y + 18), car.split()[3])
    bg = Image.alpha_composite(bg, shadow.filter(ImageFilter.GaussianBlur(20)))
    bg.alpha_composite(car, (x, y))
    return bg


def main():
    icon = draw()
    # Կլոր տարբերակը՝ նույն պատկերը շրջանով կտրված
    round_mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(round_mask).ellipse((0, 0, N - 1, N - 1), fill=255)
    icon_round = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    icon_round.paste(icon, (0, 0), round_mask)

    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for dens, px in sizes.items():
        d = RES / f"mipmap-{dens}"
        icon.resize((px, px), Image.LANCZOS).save(d / "ic_launcher.png", optimize=True)
        icon_round.resize((px, px), Image.LANCZOS).save(d / "ic_launcher_round.png", optimize=True)
    if "--preview" in sys.argv:
        icon.resize((256, 256), Image.LANCZOS).save(ROOT / "tools" / "icon-preview.png")
    print("ok")


if __name__ == "__main__":
    main()
