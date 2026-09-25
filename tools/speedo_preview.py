"""Սպիդոմետրի ոճերի նախադիտում (72 կմ/ժ, սահմանափակում 60)՝ օգտատիրոջ ընտրության համար։"""
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

S = 3
W, H = 460, 250
BG = (22, 32, 50)
CARD = (29, 41, 64)
GREEN, YELLOW, RED = (74, 222, 128), (251, 191, 36), (239, 68, 68)
TEXT, MUTED = (226, 232, 240), (100, 116, 139)
SPEED, LIMIT, VMAX = 72, 60, 180


def font(sz, bold=True):
    return ImageFont.truetype("arialbd.ttf" if bold else "arial.ttf", sz * S)


def canvas():
    im = Image.new("RGB", (W * S, H * S), CARD)
    return im, ImageDraw.Draw(im)


def lerp(a, b, t):
    return tuple(int(x + (y - x) * t) for x, y in zip(a, b))


def speed_color(v):
    if v <= LIMIT:
        return GREEN
    return YELLOW if v <= LIMIT + 10 else RED


def limit_sign(d, cx, cy, r):
    d.ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], fill=(255, 255, 255), outline=RED, width=int(r * 0.22 * S))
    d.text((cx * S, cy * S), str(LIMIT), font=font(int(r * 0.9)), fill=(20, 20, 20), anchor="mm")


def style_classic():
    """1. Դասական՝ սլաքով, նշաձողերով (ինչպես մեքենայի վահանակին)։"""
    im, d = canvas()
    cx, cy, r = 150, 150, 115
    a0, a1 = 150, 390  # աստիճաններ
    d.arc([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], a0, a1, fill=(60, 75, 100), width=4 * S)
    for v in range(0, VMAX + 1, 10):
        a = math.radians(a0 + (a1 - a0) * v / VMAX)
        big = v % 20 == 0
        r1 = r - (18 if big else 10)
        d.line([(cx + r1 * math.cos(a)) * S, (cy + r1 * math.sin(a)) * S, (cx + r * math.cos(a)) * S, (cy + r * math.sin(a)) * S],
               fill=TEXT if big else MUTED, width=(3 if big else 2) * S)
        if big:
            rt = r - 34
            d.text(((cx + rt * math.cos(a)) * S, (cy + rt * math.sin(a)) * S), str(v), font=font(12, False), fill=MUTED, anchor="mm")
    # կարմիր գոտի սահմանափակումից վեր
    la = a0 + (a1 - a0) * LIMIT / VMAX
    d.arc([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], la, la + 6, fill=RED, width=8 * S)
    a = math.radians(a0 + (a1 - a0) * SPEED / VMAX)
    d.line([cx * S, cy * S, (cx + (r - 8) * math.cos(a)) * S, (cy + (r - 8) * math.sin(a)) * S], fill=RED, width=5 * S)
    d.ellipse([(cx - 10) * S, (cy - 10) * S, (cx + 10) * S, (cy + 10) * S], fill=(200, 205, 215))
    d.text((cx * S, (cy + 45) * S), f"{SPEED}", font=font(30), fill=TEXT, anchor="mm")
    d.text((cx * S, (cy + 70) * S), "km/h", font=font(12, False), fill=MUTED, anchor="mm")
    limit_sign(d, 360, 110, 38)
    return im


def style_modern():
    """2. Ժամանակակից աղեղ՝ SOC-ի շրջանի ոճով, կանաչ→դեղին→կարմիր, մեծ թիվ մեջտեղում։"""
    im, d = canvas()
    cx, cy, r = 170, 140, 110
    a0, a1 = 135, 405
    box = [(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S]
    d.arc(box, a0, a1, fill=(45, 60, 85), width=18 * S)
    end = a0 + (a1 - a0) * SPEED / VMAX
    steps = 60
    for i in range(steps):
        t0 = a0 + (end - a0) * i / steps
        t1 = a0 + (end - a0) * (i + 1) / steps + 0.5
        v = SPEED * i / steps
        col = lerp(GREEN, YELLOW, min(1, v / LIMIT)) if v <= LIMIT else lerp(YELLOW, RED, min(1, (v - LIMIT) / 20))
        d.arc(box, t0, t1, fill=col, width=18 * S)
    d.text((cx * S, (cy - 5) * S), f"{SPEED}", font=font(58), fill=TEXT, anchor="mm")
    d.text((cx * S, (cy + 42) * S), "km/h", font=font(14, False), fill=MUTED, anchor="mm")
    limit_sign(d, 380, 110, 38)
    return im


def style_bar():
    """3. Թվային + LED սանդղակ՝ հորիզոնական հատվածներով։"""
    im, d = canvas()
    d.text((40 * S, 95 * S), f"{SPEED}", font=font(80), fill=speed_color(SPEED), anchor="lm")
    d.text((200 * S, 115 * S), "km/h", font=font(18, False), fill=MUTED, anchor="lm")
    segs, x0, y0, sw, gap = 30, 40, 175, 11, 2
    lit = round(segs * SPEED / VMAX)
    for i in range(segs):
        v = VMAX * (i + 1) / segs
        col = (GREEN if v <= LIMIT else YELLOW if v <= LIMIT + 20 else RED) if i < lit else (45, 60, 85)
        h = 18 + i * 0.9
        d.rounded_rectangle([(x0 + i * (sw + gap)) * S, (y0 - h) * S, (x0 + i * (sw + gap) + sw) * S, y0 * S], radius=2 * S, fill=col)
    limit_sign(d, 380, 95, 38)
    return im


def style_minimal():
    """4. Մինիմալ՝ մեծ թիվ շրջանակի մեջ, որը լցվում է ըստ արագության (Tesla-ի ոճ)։"""
    im, d = canvas()
    cx, cy, r = 150, 125, 95
    d.ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], outline=(45, 60, 85), width=6 * S)
    d.arc([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], -90, -90 + 360 * SPEED / VMAX, fill=speed_color(SPEED), width=6 * S)
    d.text((cx * S, (cy - 8) * S), f"{SPEED}", font=font(64), fill=TEXT, anchor="mm")
    d.text((cx * S, (cy + 40) * S), "km/h", font=font(14, False), fill=MUTED, anchor="mm")
    limit_sign(d, 360, 110, 38)
    return im


def main():
    styles = [("1. Classic", style_classic()), ("2. Modern arc", style_modern()),
              ("3. Digital + LED", style_bar()), ("4. Minimal ring", style_minimal())]
    pad = 16
    sheet = Image.new("RGB", (2 * W + 3 * pad, 2 * (H + 34) + 3 * pad), BG)
    d = ImageDraw.Draw(sheet)
    for i, (name, im) in enumerate(styles):
        x = pad + (i % 2) * (W + pad)
        y = pad + (i // 2) * (H + 34 + pad)
        d.text((x + 6, y + 4), name, font=ImageFont.truetype("arialbd.ttf", 20), fill=TEXT)
        sheet.paste(im.resize((W, H), Image.LANCZOS), (x, y + 34))
    out = Path(__file__).resolve().parent / "speedo-preview.png"
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
