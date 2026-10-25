"""Generate two launcher PNGs (low + high density) from assets/pocket-quest-app-icon.png."""

from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets" / "pocket-quest-app-icon.png"
RES = ROOT / "app" / "src" / "main" / "res"

# Same resource name; Android picks the folder closest to the device density.
LOW_PX = 96   # drawable-mdpi
HIGH_PX = 192  # drawable-xxhdpi


def fit_center(im: Image.Image, size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    scale = min(size / im.width, size / im.height) * 0.92
    w, h = int(im.width * scale), int(im.height * scale)
    resized = im.resize((w, h), Image.Resampling.LANCZOS)
    canvas.paste(resized, ((size - w) // 2, (size - h) // 2), resized)
    return canvas


def main() -> None:
    im = Image.open(SOURCE).convert("RGBA")
    for folder, px in (("drawable-mdpi", LOW_PX), ("drawable-xxhdpi", HIGH_PX)):
        out = RES / folder / "ic_launcher.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        fit_center(im, px).save(out, "PNG", optimize=True)
        print(f"Wrote {out} ({px}x{px})")


if __name__ == "__main__":
    main()
