import os
from PIL import Image, ImageOps

input_path = r"C:/Users/marco/.gemini/antigravity/brain/208e7b54-1ea6-4362-9ea9-116c8f44a3bb/.user_uploaded/uploaded_media_0_1792222329311.jpg"
out_dir = r"android-app/app/src/main/res"

img = Image.open(input_path).convert("RGBA")
print(f"Original size: {img.size}")

# Campiona colore dello sfondo dagli angoli
corner_pixels = [
    img.getpixel((0, 0)),
    img.getpixel((img.width - 1, 0)),
    img.getpixel((0, img.height - 1)),
    img.getpixel((img.width - 1, img.height - 1)),
    img.getpixel((10, 10)),
    img.getpixel((img.width - 11, 10))
]
bg_r = sum(p[0] for p in corner_pixels) // len(corner_pixels)
bg_g = sum(p[1] for p in corner_pixels) // len(corner_pixels)
bg_b = sum(p[2] for p in corner_pixels) // len(corner_pixels)
print(f"Detected background color: RGB({bg_r}, {bg_g}, {bg_b})")

# Rimuovi lo sfondo calcolando la distanza di colore e applicando smooth alpha ai bordi
data = img.getdata()
new_data = []

# Soglie per una transizione morbida e pulita senza aloni bianchi
# Il logo ha due anelli scuri: blu scuro (circa #1b2f56 -> ~27, 47, 86) e teal scuro (#225e68 -> ~34, 94, 104)
# Lo sfondo è crema chiaro (circa 245-250)
for item in data:
    r, g, b, a = item
    # Distanza euclidea dal colore di sfondo
    dist = ((r - bg_r) ** 2 + (g - bg_g) ** 2 + (b - bg_b) ** 2) ** 0.5
    
    # Se molto vicino allo sfondo: completamente trasparente
    if dist < 25:
        new_data.append((r, g, b, 0))
    elif dist < 55:
        # Transizione antialiased morbida
        alpha = int(255 * (dist - 25) / (55 - 25))
        new_data.append((r, g, b, alpha))
    else:
        new_data.append((r, g, b, 255))

img.putdata(new_data)

# Ritaglia al bounding box del logo non trasparente
bbox = img.getbbox()
print(f"Bounding box: {bbox}")
cropped = img.crop(bbox)

# Aggiungi padding proporzionato attorno al logo (ad esempio 15% per lato) per renderlo quadrato perfetto
max_dim = max(cropped.width, cropped.height)
square_size = int(max_dim * 1.30)
square_img = Image.new("RGBA", (square_size, square_size), (0, 0, 0, 0))
offset_x = (square_size - cropped.width) // 2
offset_y = (square_size - cropped.height) // 2
square_img.paste(cropped, (offset_x, offset_y), cropped)

# Salva logo trasparente ad alta risoluzione
master_logo_path = os.path.join(out_dir, "drawable", "app_logo_transparent.png")
os.makedirs(os.path.dirname(master_logo_path), exist_ok=True)
square_img.save(master_logo_path, "PNG")
print(f"Saved master logo to {master_logo_path} with size {square_img.size}")

# Genera anche un'icona dell'app perfetta per Android:
# 1. ic_launcher legacy con sfondo dark Obsidian (o trasparente per round)
# 2. ic_launcher_foreground.png per adaptive icon
# 3. Varie densità mipmap (mdpi=48, hdpi=72, xhdpi=96, xxhdpi=144, xxxhdpi=192)

# Per adaptive icon, l'area sicura (safe zone) è il cerchio centrale di raggio 66% (diametro 72dp su 108dp)
adaptive_size = 432  # standard 108dp * 4
foreground = Image.new("RGBA", (adaptive_size, adaptive_size), (0, 0, 0, 0))
# Ridimensiona il logo in modo che occupi circa il 65% dell'adaptive icon
target_logo_size = int(adaptive_size * 0.62)
resized_for_adaptive = square_img.resize((target_logo_size, target_logo_size), Image.Resampling.LANCZOS)
fg_offset = (adaptive_size - target_logo_size) // 2
foreground.paste(resized_for_adaptive, (fg_offset, fg_offset), resized_for_adaptive)

fg_path = os.path.join(out_dir, "drawable", "ic_launcher_foreground.png")
foreground.save(fg_path, "PNG")
print(f"Saved adaptive foreground to {fg_path}")

# Dimensioni standard Android per mipmap
sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

for folder, sz in sizes.items():
    folder_path = os.path.join(out_dir, folder)
    os.makedirs(folder_path, exist_ok=True)
    
    # 1. ic_launcher standard con sfondo Obsidian Dark (elegante e moderno)
    icon_bg = Image.new("RGBA", (sz, sz), (11, 15, 23, 255)) # ObsidianBackground #0B0F17
    # Ridimensiona logo dentro l'icona con respiro (75%)
    logo_sz = int(sz * 0.78)
    scaled_logo = square_img.resize((logo_sz, logo_sz), Image.Resampling.LANCZOS)
    pos = (sz - logo_sz) // 2
    icon_bg.paste(scaled_logo, (pos, pos), scaled_logo)
    
    icon_path = os.path.join(folder_path, "ic_launcher.png")
    icon_bg.save(icon_path, "PNG")
    
    # 2. ic_launcher_round.png circolare
    round_mask = Image.new("L", (sz, sz), 0)
    from PIL import ImageDraw
    draw = ImageDraw.Draw(round_mask)
    draw.ellipse((0, 0, sz, sz), fill=255)
    
    round_icon = Image.new("RGBA", (sz, sz), (0, 0, 0, 0))
    round_icon.paste(icon_bg, (0, 0), round_mask)
    
    round_icon_path = os.path.join(folder_path, "ic_launcher_round.png")
    round_icon.save(round_icon_path, "PNG")

print("All mipmap icons generated successfully!")
