import os
from PIL import Image, ImageDraw

res_drawable_dir = r"android-app/app/src/main/res/drawable"
os.makedirs(res_drawable_dir, exist_ok=True)

brain_dir = r"C:\Users\marco\.gemini\antigravity\brain\208e7b54-1ea6-4362-9ea9-116c8f44a3bb"
img1 = Image.open(os.path.join(brain_dir, "home_screen_v120_main.png"))
img2 = Image.open(os.path.join(brain_dir, "home_screen_v120_month_fixed.png"))

def apply_rounded_corners(im, radius=36):
    mask = Image.new("L", im.size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle([(0, 0), (im.size[0] - 1, im.size[1] - 1)], radius=radius, fill=255)
    result = Image.new("RGBA", im.size, (0, 0, 0, 0))
    result.paste(im, (0, 0), mask)
    return result

# 1. Week Row (4x1)
crop_week_row = img1.crop((66, 196, 1014, 438))
prev_week_row = apply_rounded_corners(crop_week_row, radius=36)
p_week = os.path.join(res_drawable_dir, "preview_widget_week_row.png")
prev_week_row.save(p_week, "PNG")
print(f"Saved {p_week} ({prev_week_row.size})")

# 2. Today / Tomorrow (2x2)
crop_today = img1.crop((66, 1093, 616, 1634))
prev_today = apply_rounded_corners(crop_today, radius=38)
p_today = os.path.join(res_drawable_dir, "preview_widget_today_tomorrow.png")
prev_today.save(p_today, "PNG")
print(f"Saved {p_today} ({prev_today.size})")

# 3. Weeks Panorama (4x2)
crop_panorama = img1.crop((66, 495, 1014, 1036))
prev_panorama = apply_rounded_corners(crop_panorama, radius=38)
p_pano = os.path.join(res_drawable_dir, "preview_widget_weeks_panorama.png")
prev_panorama.save(p_pano, "PNG")
print(f"Saved {p_pano} ({prev_panorama.size})")

# 4. Month Grid (4x3)
crop_month = img2.crop((66, 196, 1014, 739))
prev_month = apply_rounded_corners(crop_month, radius=38)
p_month = os.path.join(res_drawable_dir, "preview_widget_month_grid.png")
prev_month.save(p_month, "PNG")
print(f"Saved {p_month} ({prev_month.size})")

print("All 4 widget previews updated successfully for v1.2.0!")
