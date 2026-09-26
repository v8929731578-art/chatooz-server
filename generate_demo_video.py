import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import os

width, height = 720, 1280
fps = 30
duration_sec = 14
total_frames = fps * duration_sec

output_path = "server/demo_foreground_service.mp4"
fourcc = cv2.VideoWriter_fourcc(*'mp4v')
video = cv2.VideoWriter(output_path, fourcc, fps, (width, height))

# Load assets if available
assets_dir = "playstore_assets"
chats_img_path = os.path.join(assets_dir, "screenshot_1_chats.png")
calls_img_path = os.path.join(assets_dir, "screenshot_3_voice_calls.png")

chats_bg = Image.open(chats_img_path).resize((width, height)) if os.path.exists(chats_img_path) else None
calls_bg = Image.open(calls_img_path).resize((width, height)) if os.path.exists(calls_img_path) else None

def draw_text(draw, text, pos, font_size=24, color=(255, 255, 255), anchor="lt"):
    try:
        font = ImageFont.truetype("arial.ttf", font_size)
    except Exception:
        font = ImageFont.load_default()
    draw.text(pos, text, fill=color, font=font, anchor=anchor)

for frame_idx in range(total_frames):
    t = frame_idx / fps  # Current time in seconds
    img = Image.new("RGB", (width, height), (15, 23, 42))
    draw = ImageDraw.Draw(img)

    if t < 3.5:
        # Scene 1: Starting call from app
        if chats_bg:
            img.paste(chats_bg, (0, 0))
        # Overlay title badge
        draw.rectangle([(40, 60), (width - 40, 140)], fill=(30, 41, 59, 230), outline=(99, 102, 241), width=2)
        draw_text(draw, "1. Initiating VoIP Voice Call in Chatooz", (width // 2, 100), 22, (255, 255, 255), "mm")
        
        # Animate tap on call icon
        tap_alpha = int(abs(np.sin(t * 6)) * 255)
        draw.ellipse([(width - 120, height - 200), (width - 60, height - 140)], fill=(99, 102, 241, tap_alpha), outline=(255, 255, 255), width=3)
        draw_text(draw, "CALL", (width - 90, height - 170), 16, (255, 255, 255), "mm")

    elif t < 8.0:
        # Scene 2: Active call in progress
        if calls_bg:
            img.paste(calls_bg, (0, 0))
        
        # Overlay banner
        draw.rectangle([(40, 60), (width - 40, 140)], fill=(30, 41, 59, 240), outline=(52, 211, 153), width=2)
        draw_text(draw, "2. Active Voice Call (WebRTC Connected)", (width // 2, 100), 22, (52, 211, 153), "mm")
        
        # Draw dynamic audio equalizer waveform
        wave_y = height // 2 + 180
        num_bars = 16
        for i in range(num_bars):
            bar_h = int(20 + 50 * abs(np.sin(t * 8 + i * 0.5)))
            bar_x = width // 2 - (num_bars * 12) + (i * 24)
            draw.rounded_rectangle([(bar_x, wave_y - bar_h // 2), (bar_x + 12, wave_y + bar_h // 2)], radius=6, fill=(129, 140, 248))
        
        call_time = int(t - 3.5)
        draw_text(draw, f"00:{call_time:02d}", (width // 2, wave_y + 60), 28, (255, 255, 255), "mm")

    elif t < 12.5:
        # Scene 3: App Minimized & Foreground Service Notification active
        draw.rectangle([(0, 0), (width, height)], fill=(11, 15, 25))
        
        # Top explanation badge
        draw.rectangle([(30, 60), (width - 30, 160)], fill=(30, 41, 59), outline=(99, 102, 241), width=2)
        draw_text(draw, "3. App Minimized (Screen Locked / Background)", (width // 2, 95), 20, (255, 255, 255), "mm")
        draw_text(draw, "FOREGROUND_SERVICE_PHONE_CALL active", (width // 2, 130), 16, (129, 140, 248), "mm")

        # Android Notification Card
        card_top = 220
        card_bot = 420
        draw.rounded_rectangle([(30, card_top), (width - 30, card_bot)], radius=18, fill=(30, 41, 59), outline=(71, 85, 105), width=2)
        
        # App Icon circle
        draw.ellipse([(60, card_top + 30), (120, card_top + 90)], fill=(99, 102, 241))
        draw_text(draw, "C", (90, card_top + 60), 30, (255, 255, 255), "mm")
        
        draw_text(draw, "Chatooz • Call in progress", (140, card_top + 45), 20, (255, 255, 255), "lt")
        call_time = int(t - 3.5)
        draw_text(draw, f"Ongoing voice call • 00:{call_time:02d}", (140, card_top + 75), 16, (148, 163, 184), "lt")
        
        # Live badge
        draw.rounded_rectangle([(width - 150, card_top + 35), (width - 60, card_top + 65)], radius=12, fill=(16, 185, 129))
        draw_text(draw, "ONGOING", (width - 105, card_top + 50), 13, (255, 255, 255), "mm")

        # Action buttons on notification
        draw.line([(30, card_top + 130), (width - 30, card_top + 130)], fill=(51, 65, 85), width=1)
        draw.rounded_rectangle([(60, card_top + 145), (width // 2 - 20, card_top + 185)], radius=8, fill=(51, 65, 85))
        draw_text(draw, "Mute Mic", ((60 + width // 2 - 20) // 2, card_top + 165), 16, (255, 255, 255), "mm")
        
        draw.rounded_rectangle([(width // 2 + 20, card_top + 145), (width - 60, card_top + 185)], radius=8, fill=(239, 68, 68))
        draw_text(draw, "End Call", ((width // 2 + 20 + width - 60) // 2, card_top + 165), 16, (255, 255, 255), "mm")

        # Key Benefit Info
        draw.rounded_rectangle([(40, 480), (width - 40, 780)], radius=16, fill=(15, 23, 42), outline=(52, 211, 153), width=2)
        draw_text(draw, "Why Foreground Service is Required:", (60, 520), 20, (52, 211, 153), "lt")
        draw_text(draw, "• Prevents Android OS from killing VoIP audio connection", (60, 570), 16, (226, 232, 240), "lt")
        draw_text(draw, "• Continuous real-time voice streaming with zero lag", (60, 620), 16, (226, 232, 240), "lt")
        draw_text(draw, "• Immediate 1-tap return to active call interface", (60, 670), 16, (226, 232, 240), "lt")
        draw_text(draw, "• Complies 100% with Google Play Policy", (60, 720), 16, (226, 232, 240), "lt")

    else:
        # Scene 4: Conclusion
        draw.rectangle([(0, 0), (width, height)], fill=(15, 23, 42))
        draw.ellipse([(width // 2 - 60, height // 2 - 120), (width // 2 + 60, height // 2)], fill=(16, 185, 129))
        draw_text(draw, "✓", (width // 2, height // 2 - 60), 60, (255, 255, 255), "mm")
        draw_text(draw, "Chatooz Voice & Video Calling", (width // 2, height // 2 + 50), 26, (255, 255, 255), "mm")
        draw_text(draw, "Official Google Play Foreground Service Demo", (width // 2, height // 2 + 95), 18, (148, 163, 184), "mm")

    # Convert PIL Image to OpenCV frame (RGB -> BGR)
    frame_bgr = cv2.cvtColor(np.array(img), cv2.COLOR_RGB2BGR)
    video.write(frame_bgr)

video.release()
print(f"Generated video successfully at: {output_path} (Size: {os.path.getsize(output_path)} bytes)")
