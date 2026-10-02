# Car packs: the car drawn on the cluster and dashboard road

Kom-BYDMate draws a rear view of the car on the cluster themes and on the dashboard road. Which car
and which paint is chosen in **Settings → Application → My car**. Every car is a *pack*; the
built-in ones ship with the app, and anyone can capture their own car and share it.

## Capturing your car

1. Settings → Application → My car → **Capture my car** → Next.
2. DiLink's vehicle app with the 3D car opens. Turn the car so it is seen **straight from behind**,
   wait 5–6 seconds until the white hotspots on the model fade out, and tap **📷 Capture** at the
   top of the screen. Capture while the car is not charging, with doors and boot closed: the 3D
   model mirrors the real car.
3. Back in Kom-BYDMate, frame the car tightly (leave the shadow under the bumper outside), tune
   the background removal, drag the white box onto the licence plate and name the model.
4. Choose whether to show the new car right away; it can be picked later in My car too.

The background is removed automatically: the DiLink 3D scene behind the car is a vertical gradient,
so every pixel connected to the frame's edge that matches its row's background colour is dropped.
Small islands left over from the scene (peaks, glints, hotspots beside the car) are dropped too.
The paint mask (for recolouring) and the brake-light layer are derived from the result.

## Sharing a car

**Share** writes `Download/kom_car_<name>.kcar.zip` (and opens the share sheet when the head unit
has one). Send that file to the author, for example in a
[GitHub issue](https://github.com/KokoMoko/Kom-BYDMate/issues), and it can be added to the app for
everyone. **Import** adds a `.kcar.zip` from the file picker or from the Download folder.

## Pack format

A pack is a folder (built-in: `app/src/main/assets/cars/<id>/`, captured or imported:
`filesDir/cars/<id>/`) or a zip of the same files:

| File | Required | Content |
|---|---|---|
| `meta.json` | yes | name, plate box, colours (below) |
| `car.png` | yes | rear view, transparent background, ~520 px wide |
| `brake.png` | no | same size; only the lit brake lights, transparent elsewhere |
| `mask.png` | no | same size; paint mask, grey 0–255 (white = paint); without it the car keeps its own colour |

```json
{
  "format": 1,
  "id": "sealion06",
  "name": { "en": "BYD Sealion 06 EV", "zh": "海狮06 EV" },
  "author": "KomS",
  "dilink": "5.0",
  "plate": [185, 378, 146, 40],
  "refLum": 0.402,
  "colors": [
    { "id": "xueyu_white", "rgb": "#F2F4F6",
      "name": { "en": "Snow White", "ru": "Снежно-белый", "hy": "Ձյունաճերմակ", "zh": "雪域白" } }
  ]
}
```

- `plate`: left, top, width, height of the licence plate in `car.png` pixels; the app draws the
  plate text from Settings there. Omit it to draw no plate.
- `refLum`: median luminance of the paint (0–1), the reference for recolouring; computed when absent.
- `colors`: the model's official paints. Packs without colours offer a generic palette.
- `id`: letters, digits, `_` and `-` only. Imported packs get a fresh local id.

## Adding a shared car to the app

1. Unzip the `.kcar.zip` into `app/src/main/assets/cars/<model_id>/` and set `"id"` in `meta.json`
   to the folder name.
2. Optionally add the model's official colours and an `author`.
3. For a built-in pack the mask can be regenerated or improved offline with
   `tools/make_car_mask.py car.png <l,t,w,h> mask.png`.
