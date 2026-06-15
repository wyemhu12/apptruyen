from PIL import Image
import os

# --- 1. Prepare logo for splash screen (keep original for high quality) ---
logo = Image.open('logo_extracted.png').convert('RGBA')

# Make it square by padding
w, h = logo.size
size = max(w, h)
square = Image.new('RGBA', (size, size), (0, 0, 0, 0))
square.paste(logo, ((size - w) // 2, (size - h) // 2), logo)

# Save splash logo at 512x512 for splash screen
splash_logo = square.resize((512, 512), Image.LANCZOS)
splash_logo.save('app/src/main/res/drawable-xxxhdpi/ic_splash_logo.png')
print("Saved ic_splash_logo.png (512x512) to drawable-xxxhdpi")

# --- 2. Prepare logo for launcher icon foreground ---
# Adaptive icon foreground must be 108dp. At each density:
# mdpi=108, hdpi=162, xhdpi=216, xxhdpi=324, xxxhdpi=432
# The logo should sit inside the 66dp safe zone (centered in 108dp)
# So we need padding around the logo

densities = {
    'mipmap-mdpi': 108,
    'mipmap-hdpi': 162,
    'mipmap-xhdpi': 216,
    'mipmap-xxhdpi': 324,
    'mipmap-xxxhdpi': 432,
}

for folder, px in densities.items():
    # Create foreground: logo centered with padding (safe zone = 66/108 = ~61%)
    fg = Image.new('RGBA', (px, px), (0, 0, 0, 0))
    # Logo should occupy about 60% of the icon
    logo_size = int(px * 0.60)
    logo_resized = square.resize((logo_size, logo_size), Image.LANCZOS)
    offset = (px - logo_size) // 2
    fg.paste(logo_resized, (offset, offset), logo_resized)
    
    dest = f'app/src/main/res/{folder}'
    os.makedirs(dest, exist_ok=True)
    fg.save(f'{dest}/ic_launcher_foreground.png')
    print(f"Saved {folder}/ic_launcher_foreground.png ({px}x{px})")
    
    # Also save monochrome version (same as foreground for now)
    fg.save(f'{dest}/ic_launcher_monochrome.png')

    # Background for launcher: dark solid
    bg_icon = Image.new('RGBA', (px, px), (10, 14, 23, 255))
    bg_icon.save(f'{dest}/ic_launcher_background.png')

    # Composite launcher preview (foreground on dark bg)
    composite = Image.new('RGBA', (px, px), (10, 14, 23, 255))
    composite = Image.alpha_composite(composite, fg)
    composite_rgb = composite.convert('RGB')
    composite_rgb.save(f'{dest}/ic_launcher.png', quality=95)
    print(f"Saved {folder}/ic_launcher.png ({px}x{px})")

# --- 3. Prepare background for splash screen ---
bg = Image.open('background_extracted.png').convert('RGB')
# Save at full resolution for splash background
bg_dest = 'app/src/main/res/drawable-xxxhdpi'
os.makedirs(bg_dest, exist_ok=True)
bg.save(f'{bg_dest}/splash_bg.png', quality=95)
print(f"Saved splash_bg.png ({bg.size[0]}x{bg.size[1]}) to drawable-xxxhdpi")

# Also save a smaller version for lower density screens
bg_small = bg.resize((bg.size[0] // 2, bg.size[1] // 2), Image.LANCZOS)
os.makedirs('app/src/main/res/drawable-xhdpi', exist_ok=True)
bg_small.save('app/src/main/res/drawable-xhdpi/splash_bg.png', quality=90)
print(f"Saved splash_bg.png ({bg_small.size[0]}x{bg_small.size[1]}) to drawable-xhdpi")

# Save splash logo for xhdpi too
splash_logo_small = square.resize((256, 256), Image.LANCZOS)
os.makedirs('app/src/main/res/drawable-xhdpi', exist_ok=True)
splash_logo_small.save('app/src/main/res/drawable-xhdpi/ic_splash_logo.png')
print("Saved ic_splash_logo.png (256x256) to drawable-xhdpi")

print("\nDone! All assets generated.")
