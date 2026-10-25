"""Recolor achievement trophy PNGs to Pocket Quest brand teal and coral."""

from __future__ import annotations

import colorsys
import shutil
from pathlib import Path

from PIL import Image

# Brand palette (matches achievement UI: #0F6B5C / #E07A5F)
TEAL = (15, 107, 92)
TEAL_DARK = (9, 70, 60)
CORAL = (224, 122, 95)
CORAL_DARK = (196, 102, 78)
def _lerp(a: int, b: int, t: float) -> int:
    return int(a + (b - a) * t)

ASSETS_DIR = Path(__file__).resolve().parents[1] / "assets" / "achievements"
DRAWABLE_DIR = (
    Path(__file__).resolve().parents[1]
    / "app"
    / "src"
    / "main"
    / "res"
    / "drawable"
)


def _remap_rgb(r: int, g: int, b: int) -> tuple[int, int, int]:
    h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
    if s < 0.14 or v < 0.08:
        return r, g, b

    deg = h * 360.0
    if deg <= 48.0 or deg >= 332.0:
        t = min(1.0, max(0.0, (v - 0.25) / 0.55))
        return (
            _lerp(CORAL_DARK[0], CORAL[0], t),
            _lerp(CORAL_DARK[1], CORAL[1], t),
            _lerp(CORAL_DARK[2], CORAL[2], t),
        )

    if 145.0 <= deg <= 215.0:
        t = min(1.0, max(0.0, (v - 0.18) / 0.5))
        return (
            _lerp(TEAL_DARK[0], TEAL[0], t),
            _lerp(TEAL_DARK[1], TEAL[1], t),
            _lerp(TEAL_DARK[2], TEAL[2], t),
        )

    return r, g, b


def recolor_image(src: Path, dest: Path) -> None:
    im = Image.open(src).convert("RGBA")
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            nr, ng, nb = _remap_rgb(r, g, b)
            h, s, v = colorsys.rgb_to_hsv(nr / 255.0, ng / 255.0, nb / 255.0)
            if s < 0.16:
                px[x, y] = (0, 0, 0, 0)
                continue
            if a < 255 and s < 0.28 and v > 0.65:
                a = int(a * min(1.0, s / 0.28))
            px[x, y] = (nr, ng, nb, a)
    dest.parent.mkdir(parents=True, exist_ok=True)
    im.save(dest, optimize=True)


def main() -> None:
    for src in sorted(ASSETS_DIR.glob("achievement-*.png")):
        dest_name = src.stem.replace("-", "_") + ".png"
        asset_out = src  # overwrite source of truth
        drawable_out = DRAWABLE_DIR / dest_name
        recolor_image(src, asset_out)
        shutil.copy2(asset_out, drawable_out)
        print(f"Updated {src.name} -> {dest_name}")


if __name__ == "__main__":
    main()
