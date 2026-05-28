"""
从单张源图生成 Android 各密度启动图标与自适应前景图。
用法: python scripts/generate_launcher_icons.py [源图路径]
"""
from __future__ import annotations

import os
import sys
from pathlib import Path

from PIL import Image

# (mipmap 目录名, 传统 48dp 图标边长 px, 自适应前景 108dp 画布边长 px)
DENSITY_LAYERS: list[tuple[str, int, int]] = [
    ("mipmap-mdpi", 48, 108),
    ("mipmap-hdpi", 72, 162),
    ("mipmap-xhdpi", 96, 216),
    ("mipmap-xxhdpi", 144, 324),
    ("mipmap-xxxhdpi", 192, 432),
]

DEFAULT_SRC = Path(
    r"C:\Users\Squema-Mini\.cursor\projects\e-Talk-Trae-TalkChatBot\assets"
    r"\c__Users_Squema-Mini_AppData_Roaming_Cursor_User_workspaceStorage_07951986f3ecdbd9beaacc928e5d078a_images____APP____-b61d4ed9-1c70-481b-99ba-cfa0de3d115d.png"
)


def square_crop_center(im: Image.Image) -> Image.Image:
    w, h = im.size
    side = min(w, h)
    left = (w - side) // 2
    top = (h - side) // 2
    return im.crop((left, top, left + side, top + side))


def main() -> None:
    repo = Path(__file__).resolve().parents[1]
    res = repo / "app" / "src" / "main" / "res"
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_SRC
    if not src.is_file():
        print(f"源图不存在: {src}", file=sys.stderr)
        sys.exit(1)

    img = Image.open(src).convert("RGBA")
    img = square_crop_center(img)

    for folder, legacy_px, fg_px in DENSITY_LAYERS:
        out_dir = res / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        legacy = img.resize((legacy_px, legacy_px), Image.Resampling.LANCZOS)
        legacy.save(out_dir / "ic_launcher.png", "PNG", optimize=True)
        legacy.save(out_dir / "ic_launcher_round.png", "PNG", optimize=True)
        fg = img.resize((fg_px, fg_px), Image.Resampling.LANCZOS)
        fg.save(out_dir / "ic_launcher_foreground.png", "PNG", optimize=True)

    play = repo / "app" / "src" / "main" / "ic_launcher-playstore.png"
    img.resize((512, 512), Image.Resampling.LANCZOS).save(play, "PNG", optimize=True)
    print(f"已写入 {res} 下各 mipmap-*dpi，以及 {play}")


if __name__ == "__main__":
    main()
