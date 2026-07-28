"""Regenerate the Play Store feature graphic (1024x500).

Run: python tool/feature_graphic.py   (needs Pillow)
Output: assets/store/feature-graphic-1024x500.png
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

REPO = Path(__file__).resolve().parent.parent
BG = (62, 62, 62)          # #3E3E3E, same as ic_launcher_background
FG = (237, 239, 242)       # AppColors.onSurface
MUTED = (168, 173, 179)

W, H = 1024, 500
img = Image.new("RGB", (W, H), BG)

MARK = 240
SPACE = 58          # mark to text
GAP = 20            # title to tagline
TITLE = "Bedrock"
TAGLINE = "Block the feed. Keep the messages."

d = ImageDraw.Draw(img)
title_font = ImageFont.truetype(str(REPO / "assets/fonts/PTSerif-Bold.ttf"), 100)
tag_font = ImageFont.truetype("C:/Windows/Fonts/segoeuisl.ttf", 34)

# Measure with tight ink boxes, not the font line box: PT Serif's descender
# would otherwise push the whole lockup visibly low and left.
t_box = d.textbbox((0, 0), TITLE, font=title_font)
g_box = d.textbbox((0, 0), TAGLINE, font=tag_font)
text_w = max(t_box[2] - t_box[0], g_box[2] - g_box[0])

left = (W - (MARK + SPACE + text_w)) // 2

# The launcher icon carries a faint drop shadow (63-64 on a 62 field) that reads
# as a rounded-square halo once pasted onto the flat canvas. Flatten near-BG
# pixels before resampling so the mark sits on the background invisibly.
mark = Image.open(REPO / "assets/store/icon-512.png").convert("RGB")
mark.putdata([BG if all(abs(c - b) <= 5 for c, b in zip(px, BG)) else px
              for px in mark.getdata()])
img.paste(mark.resize((MARK, MARK), Image.LANCZOS), (left, (H - MARK) // 2))

text_x = left + MARK + SPACE
block_h = (t_box[3] - t_box[1]) + GAP + (g_box[3] - g_box[1])
top = (H - block_h) // 2

d.text((text_x - t_box[0], top - t_box[1]), TITLE, font=title_font, fill=FG)
d.text((text_x - g_box[0], top + (t_box[3] - t_box[1]) + GAP - g_box[1]),
       TAGLINE, font=tag_font, fill=MUTED)

# Play crops the feature graphic in some placements; keep the lockup off the edges.
right_edge = text_x + text_w
assert left > 60 and right_edge < W - 60, f"lockup too wide: {left}..{right_edge}"

out = REPO / "assets/store/feature-graphic-1024x500.png"
img.save(out)
print(out, img.size)
