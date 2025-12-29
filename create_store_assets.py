from PIL import Image, ImageOps

def create_assets():
    # Paths
    source_icon_path = "app/src/main/res/mipmap-hdpi/ic_launcher.png"
    icon_out_path = "play_store_icon.png"
    feature_graphic_out_path = "play_store_feature_graphic.png"

    try:
        # Load Original Icon (1024x1024)
        print(f"Loading icon from: {source_icon_path}")
        original_icon = Image.open(source_icon_path).convert("RGBA")

        # 1. Create Play Store Icon (512x512)
        print("Generating Play Store Icon (512x512)...")
        store_icon = original_icon.resize((512, 512), Image.Resampling.LANCZOS)
        store_icon.save(icon_out_path, "PNG")
        print(f"✅ Saved: {icon_out_path}")

        # 2. Create Feature Graphic (1024x500)
        print("Generating Feature Graphic (1024x500)...")
        # Brand Color: Teal #009688
        bg_color = (0, 150, 136, 255) 
        feature_graphic = Image.new("RGBA", (1024, 500), bg_color)

        # Calculate position to center the icon
        # Use a slightly smaller icon for the feature graphic so it fits nicely
        # Icon size: 300x300
        fg_icon = original_icon.resize((300, 300), Image.Resampling.LANCZOS)
        
        # Center coordinates
        x = (1024 - 300) // 2
        y = (500 - 300) // 2
        
        feature_graphic.paste(fg_icon, (x, y), fg_icon)
        
        # Save
        feature_graphic.save(feature_graphic_out_path, "PNG")
        print(f"✅ Saved: {feature_graphic_out_path}")

    except Exception as e:
        print(f"❌ Error: {e}")

if __name__ == "__main__":
    create_assets()
