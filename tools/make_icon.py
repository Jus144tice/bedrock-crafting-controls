"""One-off generator for the mod icon. Draws a 3x3 crafting grid with a single
highlighted cell (the Bedrock crafting-controls motif). Supersampled for clean edges."""
from PIL import Image, ImageDraw, ImageFont

S = 4               # supersampling factor
N = 256             # final size
W = N * S

img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
d = ImageDraw.Draw(img)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


# --- background: vertical gradient inside a rounded square ------------------
top = (38, 42, 56)
bot = (24, 27, 38)
bg = Image.new("RGBA", (W, W), (0, 0, 0, 0))
bgd = ImageDraw.Draw(bg)
for y in range(W):
    bgd.line([(0, y), (W, y)], fill=lerp(top, bot, y / W) + (255,))
mask = Image.new("L", (W, W), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, W - 1, W - 1], radius=52 * S, fill=255)
img.paste(bg, (0, 0), mask)

# subtle border
d.rounded_rectangle([2 * S, 2 * S, W - 2 * S, W - 2 * S],
                    radius=50 * S, outline=(70, 78, 104, 255), width=2 * S)

# --- 3x3 grid --------------------------------------------------------------
margin = 46 * S
grid = W - 2 * margin
gap = 12 * S
cell = (grid - 2 * gap) / 3.0
radius = 9 * S

accent = (61, 199, 142)        # emerald highlight
accent_dark = (38, 150, 104)
neutral = (62, 69, 92)
neutral_hi = (78, 86, 112)

HIGHLIGHT = (1, 1)             # (col, row) of the single crafted cell

for row in range(3):
    for col in range(3):
        x0 = margin + col * (cell + gap)
        y0 = margin + row * (cell + gap)
        x1, y1 = x0 + cell, y0 + cell
        if (col, row) == HIGHLIGHT:
            # glow behind the highlighted cell
            glow = Image.new("RGBA", (W, W), (0, 0, 0, 0))
            ImageDraw.Draw(glow).rounded_rectangle(
                [x0 - 8 * S, y0 - 8 * S, x1 + 8 * S, y1 + 8 * S],
                radius=radius + 6 * S, fill=accent + (90,))
            glow = glow.filter(__import__("PIL.ImageFilter", fromlist=["GaussianBlur"]).GaussianBlur(6 * S))
            img.alpha_composite(glow)
            # vertical gradient fill for the cell
            cellimg = Image.new("RGBA", (int(cell), int(cell)), (0, 0, 0, 0))
            cd = ImageDraw.Draw(cellimg)
            for yy in range(int(cell)):
                cd.line([(0, yy), (int(cell), yy)],
                        fill=lerp(accent, accent_dark, yy / cell) + (255,))
            cmask = Image.new("L", (int(cell), int(cell)), 0)
            ImageDraw.Draw(cmask).rounded_rectangle(
                [0, 0, int(cell) - 1, int(cell) - 1], radius=radius, fill=255)
            img.paste(cellimg, (int(x0), int(y0)), cmask)
        else:
            d.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=neutral + (255,))
            # top highlight strip for a little depth
            d.rounded_rectangle([x0, y0, x1, y0 + cell * 0.5],
                                radius=radius, fill=neutral_hi + (90,))

# --- "1" inside the highlighted cell ---------------------------------------
hx = margin + HIGHLIGHT[0] * (cell + gap)
hy = margin + HIGHLIGHT[1] * (cell + gap)
cx, cy = hx + cell / 2, hy + cell / 2
font = None
for path in (r"C:\Windows\Fonts\arialbd.ttf", r"C:\Windows\Fonts\seguisb.ttf",
             r"C:\Windows\Fonts\segoeuib.ttf"):
    try:
        font = ImageFont.truetype(path, int(cell * 0.78))
        break
    except OSError:
        continue
if font is not None:
    d.text((cx, cy + 2 * S), "1", font=font, fill=(255, 255, 255, 255), anchor="mm")
else:
    # geometric fallback "1" so we never depend on a font being present
    bw = cell * 0.16
    d.rectangle([cx - bw / 2, hy + cell * 0.2, cx + bw / 2, hy + cell * 0.8], fill=(255, 255, 255, 255))
    d.polygon([(cx - bw / 2, hy + cell * 0.34), (cx - bw * 1.4, hy + cell * 0.42),
               (cx - bw / 2, hy + cell * 0.2)], fill=(255, 255, 255, 255))
    d.rectangle([cx - bw * 1.5, hy + cell * 0.78, cx + bw * 1.5, hy + cell * 0.8], fill=(255, 255, 255, 255))

# --- downscale -------------------------------------------------------------
out = img.resize((N, N), Image.LANCZOS)
out.save(r"C:\Users\jenny\bedrock-crafting-controls\src\main\resources\bedrockcraftingcontrols.png")
print("wrote icon")
