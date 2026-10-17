import os
from PIL import Image, ImageDraw

res_drawable_dir = r"android-app/app/src/main/res/drawable"
os.makedirs(res_drawable_dir, exist_ok=True)

brain_dir = r"C:\Users\marco\.gemini\antigravity\brain\208e7b54-1ea6-4362-9ea9-116c8f44a3bb"
img1 = Image.open(os.path.join(brain_dir, "device_screen_widgets_page_calediario.png"))
img2 = Image.open(os.path.join(brain_dir, "device_screen_widgets_page_calediario_month.png"))

def apply_rounded_corners(im, radius=32):
    mask = Image.new("L", im.size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle([(0, 0), (im.size[0] - 1, im.size[1] - 1)], radius=radius, fill=255)
    result = Image.new("RGBA", im.size, (0, 0, 0, 0))
    result.paste(im, (0, 0), mask)
    return result

# 1. Week Row (4x1 / 5x1)
# Si trova tra y=345 e y=495 (altezza ~150px), x da 40 a 1040
crop_week_row = img1.crop((44, 345, 1036, 498))
# Applica maschera con angoli arrotondati
prev_week_row = apply_rounded_corners(crop_week_row, radius=36)
p_week = os.path.join(res_drawable_dir, "preview_widget_week_row.png")
prev_week_row.save(p_week, "PNG")
print(f"Saved {p_week} ({prev_week_row.size})")

# 2. Today / Tomorrow (2x2)
# Si trova tra y=580 e y=1100, x da 44 a 530
crop_today = img1.crop((44, 582, 530, 1102))
prev_today = apply_rounded_corners(crop_today, radius=42)
p_today = os.path.join(res_drawable_dir, "preview_widget_today_tomorrow.png")
prev_today.save(p_today, "PNG")
print(f"Saved {p_today} ({prev_today.size})")

# 3. Weeks Panorama (4x2 / 5x2)
# Si trova tra y=1195 e y=1610, x da 44 a 1036
crop_panorama = img1.crop((44, 1198, 1036, 1608))
prev_panorama = apply_rounded_corners(crop_panorama, radius=38)
p_pano = os.path.join(res_drawable_dir, "preview_widget_weeks_panorama.png")
prev_panorama.save(p_pano, "PNG")
print(f"Saved {p_pano} ({prev_panorama.size})")

# 4. Month Grid (4x3 / 5x3)
# In img2: si trova tra y=318 e y=1298, x da 44 a 1036
crop_month = img2.crop((44, 318, 1036, 1298))
prev_month = apply_rounded_corners(crop_month, radius=42)
p_month = os.path.join(res_drawable_dir, "preview_widget_month_grid.png")
prev_month.save(p_month, "PNG")
print(f"Saved {p_month} ({prev_month.size})")

print("All 4 widget previews generated successfully!")
