#!/usr/bin/env python3
"""Convert JPEG files incorrectly named as PNG to actual PNG format."""

from PIL import Image
import os

# List of files to convert
files_to_convert = [
    "app/src/main/res/mipmap-hdpi/ic_launcher.png",
    "app/src/main/res/mipmap-mdpi/ic_launcher.png",
    "app/src/main/res/mipmap-xhdpi/ic_launcher.png",
    "app/src/main/res/mipmap-xxhdpi/ic_launcher.png",
    "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
    "app/src/main/res/drawable/ic_splash_logo.png",
]

def convert_to_png(filepath):
    """Convert image to PNG format."""
    try:
        # Open the image (regardless of actual format)
        img = Image.open(filepath)
        
        # Convert to RGB if necessary (PNG supports RGB and RGBA)
        if img.mode not in ('RGB', 'RGBA'):
            img = img.convert('RGB')
        
        # Save as PNG
        temp_path = filepath + ".temp"
        img.save(temp_path, 'PNG', optimize=True)
        
        # Replace original file
        os.replace(temp_path, filepath)
        
        print(f"✓ Converted: {filepath}")
        return True
    except Exception as e:
        print(f"✗ Failed to convert {filepath}: {e}")
        return False

def main():
    """Main conversion function."""
    print("Converting JPEG files to PNG format...\n")
    
    success_count = 0
    fail_count = 0
    
    for filepath in files_to_convert:
        if os.path.exists(filepath):
            if convert_to_png(filepath):
                success_count += 1
            else:
                fail_count += 1
        else:
            print(f"✗ File not found: {filepath}")
            fail_count += 1
    
    print(f"\n{'='*50}")
    print(f"Conversion complete!")
    print(f"Success: {success_count} | Failed: {fail_count}")
    print(f"{'='*50}")

if __name__ == "__main__":
    main()
