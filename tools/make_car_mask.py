"""Builds the body mask of a rear car image: low-saturation, not-too-dark opaque pixels (paint),
minus the plate box and anything red (lights, logo). Used for the built-in Sealion 06 pack."""
import sys
from PIL import Image, ImageFilter
import numpy as np

def body_mask(car_png, plate_box, out_png):
    car = np.asarray(Image.open(car_png).convert("RGBA")).astype(np.float32) / 255
    rgb, a = car[..., :3], car[..., 3]
    mx, mn = rgb.max(2), rgb.min(2)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    lum = 0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]
    H, W = a.shape
    yy, xx = np.mgrid[0:H, 0:W]
    l, t, w, h = plate_box
    plate = (xx >= l - 5) & (xx <= l + w + 5) & (yy >= t - 6) & (yy <= t + h + 6)
    body = (a > 0.5) & (sat < 0.25) & (lum > 0.20) & ~plate
    m = Image.fromarray((body * 255).astype(np.uint8))
    m = m.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.MinFilter(3))
    m = m.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.MaxFilter(3))
    red = (sat > 0.3) & (rgb[..., 0] > rgb[..., 1] * 1.3)
    red_img = Image.fromarray((red * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(5))
    m = np.asarray(m).astype(np.float32) * (1 - np.asarray(red_img).astype(np.float32) / 255)
    m = Image.fromarray(m.astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8))
    mk = (np.asarray(m).astype(np.float32) / 255) * a
    Image.fromarray((mk * 255).astype(np.uint8), "L").save(out_png)
    return float(np.median(lum[body]))

if __name__ == "__main__":
    print(body_mask(sys.argv[1], tuple(map(int, sys.argv[2].split(","))), sys.argv[3]))
