"""生成 CodecChecker 各 mipmap 密度 WebP 图标"""

import os
import sys
from PIL import Image, ImageDraw

# ── 配置 ──────────────────────────────────────────
DENSITIES = {
    "mdpi":   48,
    "hdpi":   72,
    "xhdpi":  96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

BASE_DIR = r"D:\Project\Android_CodecChecker\app\src\main\res"
BG_TOP   = (0x1A, 0x23, 0x7E)  # #1A237E
BG_BOT   = (0x4A, 0x14, 0x8C)  # #4A148C
FG_COLOR = (255, 255, 255, 255)

# 前景形状在 108dp viewport 中的坐标
PLAY_TRI = [(32, 34), (32, 74), (62, 54)]
BARS     = [(72, 45, 76, 63), (79, 36, 83, 72), (86, 41, 90, 67)]


def make_gradient(size):
    """对角线性渐变背景"""
    img = Image.new("RGBA", (size, size))
    max_xy = 2 * size - 2
    for y in range(size):
        for x in range(size):
            t = (x + y) / max_xy  # [0, 1]
            r = int(BG_TOP[0] + (BG_BOT[0] - BG_TOP[0]) * t)
            g = int(BG_TOP[1] + (BG_BOT[1] - BG_TOP[1]) * t)
            b = int(BG_TOP[2] + (BG_BOT[2] - BG_TOP[2]) * t)
            img.putpixel((x, y), (r, g, b, 255))
    return img


def draw_foreground(draw, scale):
    """绘制白色前景（播放键 + 波形柱）"""
    pts = [(int(x * scale), int(y * scale)) for x, y in PLAY_TRI]
    draw.polygon(pts, fill=FG_COLOR)
    for x1, y1, x2, y2 in BARS:
        draw.rectangle(
            [int(x1 * scale), int(y1 * scale), int(x2 * scale), int(y2 * scale)],
            fill=FG_COLOR,
        )


def create_icon(size):
    img = make_gradient(size)
    draw = ImageDraw.Draw(img)
    scale = size / 108.0
    draw_foreground(draw, scale)
    return img


def save_icon(img, folder):
    os.makedirs(folder, exist_ok=True)
    for name in ("ic_launcher.webp", "ic_launcher_round.webp"):
        path = os.path.join(folder, name)
        img.save(path, "WEBP", quality=95, lossless=False)
        print(f"  ✔ {path}")


def main():
    print("生成 CodecChecker 图标...\n")
    for density, size in DENSITIES.items():
        folder = os.path.join(BASE_DIR, f"mipmap-{density}")
        print(f"[{density}] {size}x{size}")
        icon = create_icon(size)
        save_icon(icon, folder)
    print("\n全部完成！")


if __name__ == "__main__":
    main()
