import cv2
import numpy as np

# Load image
img_path = r"c:\Users\wyemh\.gemini\antigravity\brain\55efd2fe-c82d-4f04-a0c5-50bdf830cb66\splash_screen_concept_1781394445051.png"
img = cv2.imread(img_path)

if img is None:
    print("Could not load image")
    exit(1)

H, W, _ = img.shape

# 1. Extract Background (Remove logo and text)
# Convert to grayscale to find bright regions (logo + text)
gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

# Threshold to find the logo and text. They are bright, background is dark.
# The waves are faint, so a threshold of ~60 should separate them well.
_, mask = cv2.threshold(gray, 50, 255, cv2.THRESH_BINARY)

# Dilate the mask slightly to ensure we cover the soft glow edges of the logo
kernel = np.ones((15, 15), np.uint8)
mask_dilated = cv2.dilate(mask, kernel, iterations=2)

# Use inpaint to fill the masked regions using surrounding pixels (the dark waves)
bg_extracted = cv2.inpaint(img, mask_dilated, inpaintRadius=20, flags=cv2.INPAINT_TELEA)

cv2.imwrite("background_extracted.png", bg_extracted)
print("Saved background_extracted.png")

# 2. Extract Logo
# We want to isolate the logo. Let's crop to the top half/center where logo is.
# Logo is roughly in the middle. We can use the mask to find the bounding box.
coords = cv2.findNonZero(mask)
x, y, w, h = cv2.boundingRect(coords)

# The text is also bright and will be included in the bounding box.
# We can separate them by finding contours.
contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

# Find the largest contour which should be the logo
max_area = 0
logo_rect = None
for cnt in contours:
    x_c, y_c, w_c, h_c = cv2.boundingRect(cnt)
    area = w_c * h_c
    if area > max_area:
        max_area = area
        logo_rect = (x_c, y_c, w_c, h_c)

if logo_rect:
    x_l, y_l, w_l, h_l = logo_rect
    # Add some padding
    pad = 40
    x_start = max(0, x_l - pad)
    y_start = max(0, y_l - pad)
    x_end = min(W, x_l + w_l + pad)
    y_end = min(H, y_l + h_l + pad)
    
    logo_crop = img[y_start:y_end, x_start:x_end].copy()
    
    # Convert to RGBA
    b, g, r = cv2.split(logo_crop)
    
    # We want a transparent background but keep the neon glow.
    # The image is essentially on a black background.
    # We can use the max(R,G,B) as the alpha channel!
    # And to avoid dark fringes, we can normalize the RGB channels by the alpha.
    max_rgb = np.maximum(np.maximum(r, g), b)
    
    # But wait, there is some faint wave background behind the logo too!
    # Let's subtract a constant dark value to remove the waves.
    dark_thresh = 25
    alpha = np.clip(max_rgb.astype(np.int16) - dark_thresh, 0, 255).astype(np.uint8)
    
    # Boost alpha a bit to keep it bright
    alpha = cv2.convertScaleAbs(alpha, alpha=1.2, beta=0)
    
    # Create RGBA
    logo_rgba = cv2.merge((b, g, r, alpha))
    
    # Optional: ensure we have a square crop for the logo
    size = max(x_end - x_start, y_end - y_start)
    # We will just pad to 512x512 to be perfect for Android icon!
    pad_w = 512 - (x_end - x_start)
    pad_h = 512 - (y_end - y_start)
    
    if pad_w > 0 and pad_h > 0:
        top = pad_h // 2
        bottom = pad_h - top
        left = pad_w // 2
        right = pad_w - left
        logo_rgba_padded = cv2.copyMakeBorder(logo_rgba, top, bottom, left, right, cv2.BORDER_CONSTANT, value=[0,0,0,0])
        cv2.imwrite("logo_extracted.png", logo_rgba_padded)
        print("Saved logo_extracted.png (512x512)")
    else:
        cv2.imwrite("logo_extracted.png", logo_rgba)
        print("Saved logo_extracted.png (original size)")
else:
    print("Could not find logo")
