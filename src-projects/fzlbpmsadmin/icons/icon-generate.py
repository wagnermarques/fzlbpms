from PIL import Image, ImageDraw
import os

# Create icons directory if it doesn't exist
os.makedirs("icons", exist_ok=True)

def create_base_image():
    """Create a base image with your desired design"""
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Draw a simple design (blue circle with white inner)
    draw.ellipse((10, 10, 118, 118), fill=(65, 105, 225, 255))  # Royal blue
    draw.ellipse((25, 25, 103, 103), fill=(255, 255, 255, 255))  # White inner
    draw.ellipse((40, 40, 88, 88), fill=(65, 105, 225, 255))     # Blue center
    
    return img

# Create base image
base_img = create_base_image()

# Create PNG icons
for size in [32, 128]:
    resized = base_img.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(f"{size}x{size}.png")

# Create ICO file (multiple sizes in one file)
base_img.resize((256, 256), Image.Resampling.LANCZOS).save(
    "icon.ico",
    format="ICO",
    sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
)

# Create ICNS file (macOS icon set)
base_img.resize((1024, 1024), Image.Resampling.LANCZOS).save(
    "icon.icns",
    format="ICNS",
    sizes=[
        (16, 16),
        (32, 32),
        (64, 64),
        (128, 128),
        (256, 256),
        (512, 512),
        (1024, 1024)
    ]
)

print("Icons created successfully in icons/ folder:")
print("- 32x32.png")
print("- 128x128.png") 
print("- icon.icns")
print("- icon.ico")