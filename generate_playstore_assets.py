import os
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUTPUT_DIR = r"C:\Users\DELL\Downloads\Chatooz\joyful-hawking\playstore_assets"
os.makedirs(OUTPUT_DIR, exist_ok=True)

def get_font(size, bold=False):
    font_names = [
        "arialbd.ttf" if bold else "arial.ttf",
        "seguiemb.ttf" if bold else "segoeui.ttf",
        "calibrib.ttf" if bold else "calibri.ttf",
        "tahomabd.ttf" if bold else "tahoma.ttf",
    ]
    for fn in font_names:
        try:
            return ImageFont.truetype(fn, size)
        except Exception:
            continue
    return ImageFont.load_default()

def create_gradient_bg(width, height, color_top, color_bottom, color_mid=None):
    base = Image.new("RGBA", (width, height), color_top)
    draw = ImageDraw.Draw(base)
    for y in range(height):
        t = y / float(height)
        if color_mid and t < 0.5:
            sub_t = t * 2.0
            r = int(color_top[0] * (1 - sub_t) + color_mid[0] * sub_t)
            g = int(color_top[1] * (1 - sub_t) + color_mid[1] * sub_t)
            b = int(color_top[2] * (1 - sub_t) + color_mid[2] * sub_t)
        elif color_mid:
            sub_t = (t - 0.5) * 2.0
            r = int(color_mid[0] * (1 - sub_t) + color_bottom[0] * sub_t)
            g = int(color_mid[1] * (1 - sub_t) + color_bottom[1] * sub_t)
            b = int(color_mid[2] * (1 - sub_t) + color_bottom[2] * sub_t)
        else:
            r = int(color_top[0] * (1 - t) + color_bottom[0] * t)
            g = int(color_top[1] * (1 - t) + color_bottom[1] * t)
            b = int(color_top[2] * (1 - t) + color_bottom[2] * t)
        draw.line([(0, y), (width, y)], fill=(r, g, b, 255))
    return base

def draw_phone_frame(draw, x, y, w, h, radius=36, border_color=(70, 80, 110, 255), screen_bg=(15, 20, 32, 255)):
    # Outer bezel shadow & border
    draw.rounded_rectangle([x-4, y-4, x+w+4, y+h+4], radius=radius+4, fill=(25, 30, 45, 255), outline=border_color, width=3)
    draw.rounded_rectangle([x, y, x+w, y+h], radius=radius, fill=screen_bg)
    # Speaker / camera notch
    notch_w = int(w * 0.35)
    notch_x = x + (w - notch_w) // 2
    draw.rounded_rectangle([notch_x, y+8, notch_x+notch_w, y+24], radius=8, fill=(10, 12, 18, 255))
    # Camera lens dot
    draw.ellipse([notch_x + notch_w - 24, y+12, notch_x + notch_w - 14, y+20], fill=(25, 35, 60, 255))

# ==============================================================================
# 1. FEATURE GRAPHIC (1024 x 500 px)
# ==============================================================================
def generate_feature_graphic():
    W, H = 1024, 500
    img = create_gradient_bg(W, H, (11, 15, 28), (20, 28, 55), (14, 20, 38))
    draw = ImageDraw.Draw(img)

    # Ambient glowing circles in background
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.ellipse([650, 40, 980, 370], fill=(99, 102, 241, 45))
    glow_draw.ellipse([50, 250, 380, 580], fill=(16, 185, 129, 35))
    glow_draw.ellipse([400, 100, 750, 450], fill=(139, 92, 246, 30))
    glow = glow.filter(ImageFilter.GaussianBlur(60))
    img = Image.alpha_composite(img, glow)
    draw = ImageDraw.Draw(img)

    # Left Section: Branding & Slogan
    # App Logo Badge
    draw.rounded_rectangle([80, 75, 150, 145], radius=20, fill=(79, 70, 229, 255), outline=(129, 140, 248, 255), width=2)
    f_logo = get_font(38, bold=True)
    draw.text((98, 86), "C", font=f_logo, fill=(255, 255, 255, 255))
    
    # Title
    f_title = get_font(52, bold=True)
    draw.text((168, 80), "Chatooz", font=f_title, fill=(255, 255, 255, 255))
    
    # Tagline
    f_sub = get_font(23, bold=True)
    draw.text((80, 175), "Fast, Secure & Modern Instant Messenger", font=f_sub, fill=(199, 210, 254, 255))

    # Highlights Badges
    badges = [
        ("⚡ Instant Cloud OTP", (16, 185, 129, 255), (6, 78, 59, 200)),
        ("📞 Crystal Clear HD Calls", (99, 102, 241, 255), (49, 46, 129, 200)),
        ("🔒 End-to-End Privacy", (236, 72, 153, 255), (131, 24, 67, 200)),
        ("✨ Real-time Chat & Media", (245, 158, 11, 255), (120, 53, 15, 200)),
    ]
    f_badge = get_font(16, bold=True)
    bx, by = 80, 240
    for text, text_color, bg_color in badges:
        draw.rounded_rectangle([bx, by, bx + 260, by + 42], radius=12, fill=bg_color, outline=text_color, width=1)
        draw.text((bx + 16, by + 10), text, font=f_badge, fill=text_color)
        by += 54

    # Right Section: Stylized Phone UI Showcase
    px, py, pw, ph = 640, 50, 290, 480
    draw_phone_frame(draw, px, py, pw, ph, radius=28)

    # Top App Bar in Phone
    draw.rectangle([px+2, py+28, px+pw-2, py+75], fill=(22, 28, 48, 255))
    f_phone_header = get_font(18, bold=True)
    draw.text((px + 20, py + 40), "Chatooz", font=f_phone_header, fill=(255, 255, 255, 255))
    draw.ellipse([px + pw - 45, py + 38, px + pw - 20, py + 63], fill=(79, 70, 229, 255))
    draw.text((px + pw - 37, py + 41), "V", font=get_font(14, bold=True), fill=(255, 255, 255, 255))

    # Stories Row
    story_x = px + 15
    for initial, col in [("A", (236, 72, 153)), ("R", (16, 185, 129)), ("M", (245, 158, 11)), ("S", (99, 102, 241)), ("K", (168, 85, 247))]:
        draw.ellipse([story_x-2, py+86, story_x+42, py+130], outline=col, width=2)
        draw.ellipse([story_x, py+88, story_x+40, py+128], fill=(35, 42, 68, 255))
        draw.text((story_x+13, py+98), initial, font=get_font(13, bold=True), fill=(255, 255, 255, 255))
        story_x += 52

    # Chat List Items
    chats_data = [
        ("Aarav Sharma", "Sent you the project update! 🚀", "11:42 AM", (236, 72, 153), "2"),
        ("Rahul Verma", "Call me when you're free", "10:15 AM", (16, 185, 129), None),
        ("Chatooz Support", "Your OTP is verified instantly", "Yesterday", (79, 70, 229), None),
        ("Design Team", "Sneak peek of the new UI ✨", "Yesterday", (245, 158, 11), "5"),
    ]
    cy = py + 145
    for name, msg, time, avatar_color, badge in chats_data:
        # Avatar
        draw.ellipse([px + 16, cy, px + 58, cy + 42], fill=avatar_color)
        draw.text((px + 30, cy + 10), name[0], font=get_font(16, bold=True), fill=(255, 255, 255, 255))
        # Name & Last Message
        draw.text((px + 68, cy + 2), name, font=get_font(14, bold=True), fill=(240, 240, 250, 255))
        draw.text((px + 68, cy + 22), msg[:22] + "...", font=get_font(11), fill=(150, 160, 185, 255))
        # Time
        draw.text((px + pw - 68, cy + 4), time, font=get_font(10), fill=(130, 140, 165, 255))
        if badge:
            draw.ellipse([px + pw - 32, cy + 20, px + pw - 14, cy + 38], fill=(16, 185, 129, 255))
            draw.text((px + pw - 26, cy + 23), badge, font=get_font(10, bold=True), fill=(255, 255, 255, 255))
        cy += 54

    # Active Call Banner
    draw.rounded_rectangle([px + 12, cy + 8, px + pw - 12, cy + 56], radius=14, fill=(16, 185, 129, 230))
    draw.text((px + 24, cy + 18), "📞 Ongoing HD Audio Call", font=get_font(12, bold=True), fill=(255, 255, 255, 255))
    draw.text((px + 24, cy + 34), "04:32 • Encrypted", font=get_font(10), fill=(230, 255, 245, 255))

    out_path = os.path.join(OUTPUT_DIR, "feature_graphic.png")
    img.convert("RGB").save(out_path, "PNG", quality=95)
    print(f"Generated Feature Graphic: {out_path} ({W}x{H})")

# ==============================================================================
# 2. SCREENSHOT 1: Modern Chats & Stories (1080 x 1920 px)
# ==============================================================================
def generate_screenshot_1():
    W, H = 1080, 1920
    img = create_gradient_bg(W, H, (10, 14, 26), (18, 24, 48), (14, 18, 36))
    draw = ImageDraw.Draw(img)

    # Ambient glow
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.ellipse([W//2 - 300, 400, W//2 + 300, 1000], fill=(79, 70, 229, 40))
    glow_draw.ellipse([100, 1200, 700, 1800], fill=(16, 185, 129, 30))
    glow = glow.filter(ImageFilter.GaussianBlur(100))
    img = Image.alpha_composite(img, glow)
    draw = ImageDraw.Draw(img)

    # Top Feature Header
    f_tag = get_font(32, bold=True)
    draw.text((W//2, 120), "LIGHTNING FAST MESSAGING", font=f_tag, fill=(129, 140, 248, 255), anchor="mm")
    f_headline = get_font(64, bold=True)
    draw.text((W//2, 195), "Connect Instantly with Friends", font=f_headline, fill=(255, 255, 255, 255), anchor="mm")
    f_sub = get_font(30)
    draw.text((W//2, 260), "Real-time sync, stories & seamless cloud chat", font=f_sub, fill=(180, 195, 225, 255), anchor="mm")

    # Phone Mockup
    px, py, pw, ph = 140, 340, 800, 1500
    draw_phone_frame(draw, px, py, pw, ph, radius=48)

    # Status Bar
    draw.text((px + 50, py + 22), "11:42", font=get_font(22, bold=True), fill=(255, 255, 255, 255))
    draw.text((px + pw - 120, py + 22), "5G  100%", font=get_font(20, bold=True), fill=(255, 255, 255, 255))

    # App Header
    draw.rectangle([px+2, py+58, px+pw-2, py+160], fill=(22, 28, 48, 255))
    draw.text((px + 40, py + 85), "Chatooz", font=get_font(44, bold=True), fill=(255, 255, 255, 255))
    draw.ellipse([px + pw - 100, py + 80, px + pw - 40, py + 140], fill=(79, 70, 229, 255))
    draw.text((px + pw - 82, py + 88), "V", font=get_font(32, bold=True), fill=(255, 255, 255, 255))

    # Search Bar inside Phone
    draw.rounded_rectangle([px + 30, py + 180, px + pw - 30, py + 250], radius=20, fill=(30, 38, 62, 255), outline=(50, 62, 95, 255))
    draw.text((px + 60, py + 200), "🔍  Search chats, calls or messages...", font=get_font(24), fill=(140, 155, 185, 255))

    # Stories Row
    draw.text((px + 35, py + 280), "Stories & Updates", font=get_font(26, bold=True), fill=(220, 230, 250, 255))
    stories = [
        ("My Status", "+", (79, 70, 229), True),
        ("Aarav", "A", (236, 72, 153), False),
        ("Pooja", "P", (16, 185, 129), False),
        ("Rohan", "R", (245, 158, 11), False),
        ("Neha", "N", (168, 85, 247), False),
    ]
    sx = px + 35
    for name, init, color, is_self in stories:
        draw.ellipse([sx-3, py+327, sx+93, py+423], outline=color if not is_self else (79, 70, 229), width=3)
        draw.ellipse([sx, py+330, sx+90, py+420], fill=(40, 48, 76, 255))
        draw.text((sx+30, py+350), init, font=get_font(34, bold=True), fill=(255, 255, 255, 255))
        draw.text((sx+8, py+435), name[:8], font=get_font(19, bold=True), fill=(200, 210, 235, 255))
        sx += 148

    # Chat List
    draw.text((px + 35, py + 490), "Recent Chats", font=get_font(26, bold=True), fill=(220, 230, 250, 255))
    chats = [
        ("Aarav Sharma", "Awesome! Voice calls are super crisp! 🎧", "11:40 AM", (236, 72, 153), "3", True),
        ("Tech Discussion Hub", "Vijay: New cloud server build deployed 🚀", "11:25 AM", (79, 70, 229), "12", True),
        ("Pooja Mehta", "See you tomorrow at the office!", "10:05 AM", (16, 185, 129), None, False),
        ("Rahul Verma", "Sent an image 📷", "09:40 AM", (245, 158, 11), "1", True),
        ("Chatooz Team", "Welcome to Chatooz! Enjoy private chats.", "Yesterday", (168, 85, 247), None, False),
        ("Family Group ❤️", "Maa: Have your lunch on time 🍱", "Yesterday", (14, 165, 233), None, False),
        ("Rohan Joshi", "Shared a link: https://chatooz...", "23 Sep", (234, 88, 12), None, False),
    ]
    cy = py + 540
    for name, msg, time, color, badge, online in chats:
        # Avatar
        draw.ellipse([px + 35, cy, px + 125, cy + 90], fill=color)
        draw.text((px + 62, cy + 22), name[0], font=get_font(34, bold=True), fill=(255, 255, 255, 255))
        if online:
            draw.ellipse([px + 102, cy + 65, px + 124, cy + 87], fill=(16, 185, 129, 255), outline=(15, 20, 32, 255), width=2)
        # Text
        draw.text((px + 145, cy + 8), name, font=get_font(28, bold=True), fill=(255, 255, 255, 255))
        draw.text((px + 145, cy + 46), msg[:38] + ("..." if len(msg) > 38 else ""), font=get_font(22), fill=(160, 175, 205, 255))
        # Time & Badge
        draw.text((px + pw - 150, cy + 10), time, font=get_font(19), fill=(130, 145, 175, 255))
        if badge:
            draw.rounded_rectangle([px + pw - 85, cy + 44, px + pw - 35, cy + 78], radius=16, fill=(16, 185, 129, 255))
            draw.text((px + pw - 68, cy + 48), badge, font=get_font(18, bold=True), fill=(255, 255, 255, 255), anchor="mm")
        # Divider line
        draw.line([(px + 145, cy + 105), (px + pw - 30, cy + 105)], fill=(30, 38, 60, 255), width=1)
        cy += 120

    out_path = os.path.join(OUTPUT_DIR, "screenshot_1_chats.png")
    img.convert("RGB").save(out_path, "PNG", quality=95)
    print(f"Generated Screenshot 1: {out_path} ({W}x{H})")

# ==============================================================================
# 3. SCREENSHOT 2: Rich Chat & Media Sharing (1080 x 1920 px)
# ==============================================================================
def generate_screenshot_2():
    W, H = 1080, 1920
    img = create_gradient_bg(W, H, (14, 18, 36), (10, 14, 26), (16, 22, 44))
    draw = ImageDraw.Draw(img)

    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.ellipse([W//2 - 300, 300, W//2 + 300, 900], fill=(236, 72, 153, 35))
    glow_draw.ellipse([150, 1100, 850, 1750], fill=(99, 102, 241, 35))
    glow = glow.filter(ImageFilter.GaussianBlur(100))
    img = Image.alpha_composite(img, glow)
    draw = ImageDraw.Draw(img)

    # Top Feature Header
    f_tag = get_font(32, bold=True)
    draw.text((W//2, 120), "SEAMLESS CONVERSATIONS", font=f_tag, fill=(244, 114, 182, 255), anchor="mm")
    f_headline = get_font(64, bold=True)
    draw.text((W//2, 195), "Express Yourself Freely", font=f_headline, fill=(255, 255, 255, 255), anchor="mm")
    f_sub = get_font(30)
    draw.text((W//2, 260), "Voice notes, reactions, media & instant delivery", font=f_sub, fill=(180, 195, 225, 255), anchor="mm")

    # Phone Mockup
    px, py, pw, ph = 140, 340, 800, 1500
    draw_phone_frame(draw, px, py, pw, ph, radius=48)

    # Header with User Info & Call Actions
    draw.rectangle([px+2, py+58, px+pw-2, py+170], fill=(24, 30, 52, 255))
    draw.ellipse([px + 30, py + 75, px + 105, py + 150], fill=(236, 72, 153, 255))
    draw.text((px + 52, py + 92), "A", font=get_font(32, bold=True), fill=(255, 255, 255, 255))
    draw.ellipse([px + 88, py + 130, px + 104, py + 146], fill=(16, 185, 129, 255), outline=(24, 30, 52, 255), width=2)

    draw.text((px + 125, py + 82), "Aarav Sharma", font=get_font(28, bold=True), fill=(255, 255, 255, 255))
    draw.text((px + 125, py + 120), "🟢 Online • Typing...", font=get_font(20), fill=(16, 185, 129, 255))

    # Call action buttons in header
    draw.rounded_rectangle([px + pw - 170, py + 85, px + pw - 105, py + 140], radius=16, fill=(38, 48, 78, 255))
    draw.text((px + pw - 148, py + 98), "📞", font=get_font(22), fill=(255, 255, 255, 255))
    draw.rounded_rectangle([px + pw - 90, py + 85, px + pw - 25, py + 140], radius=16, fill=(38, 48, 78, 255))
    draw.text((px + pw - 68, py + 98), "📹", font=get_font(22), fill=(255, 255, 255, 255))

    # Messages Area
    # Date Pill
    draw.rounded_rectangle([px + pw//2 - 90, py + 195, px + pw//2 + 90, py + 235], radius=12, fill=(28, 36, 58, 255))
    draw.text((px + pw//2, py + 215), "TODAY", font=get_font(18, bold=True), fill=(160, 175, 205, 255), anchor="mm")

    # Incoming Message 1
    draw.rounded_rectangle([px + 30, py + 260, px + 520, py + 360], radius=20, fill=(32, 40, 68, 255))
    draw.text((px + 55, py + 280), "Hey! Did you check out the new\nChatooz cloud update? 🚀", font=get_font(22), fill=(240, 245, 255, 255))
    draw.text((px + 440, py + 328), "11:38 AM", font=get_font(16), fill=(140, 155, 185, 255))

    # Outgoing Message 1
    draw.rounded_rectangle([px + 280, py + 385, px + pw - 30, py + 485], radius=20, fill=(79, 70, 229, 255))
    draw.text((px + 305, py + 405), "Yes! OTP delivery is now 0.3s\nand super fast! ⚡", font=get_font(22), fill=(255, 255, 255, 255))
    draw.text((px + pw - 130, py + 453), "11:39 AM  ✓✓", font=get_font(16), fill=(210, 220, 255, 255))

    # Incoming Voice Note Bubble
    draw.rounded_rectangle([px + 30, py + 510, px + 580, py + 620], radius=20, fill=(32, 40, 68, 255))
    draw.ellipse([px + 50, py + 535, px + 105, py + 590], fill=(236, 72, 153, 255))
    draw.text((px + 70, py + 548), "▶", font=get_font(22, bold=True), fill=(255, 255, 255, 255))
    # Waveform
    wave_x = px + 125
    for h_bar in [15, 30, 45, 25, 50, 35, 20, 40, 55, 30, 25, 45, 20, 15, 35, 25, 40, 20]:
        draw.line([(wave_x, py + 565 - h_bar//2), (wave_x, py + 565 + h_bar//2)], fill=(236, 72, 153, 255) if wave_x < px + 300 else (100, 115, 150, 255), width=4)
        wave_x += 18
    draw.text((px + 125, py + 592), "0:28 / 1:15", font=get_font(16), fill=(160, 175, 205, 255))
    draw.text((px + 500, py + 592), "11:40 AM", font=get_font(16), fill=(140, 155, 185, 255))

    # Outgoing Message with Photo Preview
    draw.rounded_rectangle([px + 240, py + 645, px + pw - 30, py + 970], radius=20, fill=(79, 70, 229, 255))
    # Mock Photo inside Bubble
    draw.rounded_rectangle([px + 255, py + 660, px + pw - 45, py + 890], radius=16, fill=(45, 55, 95, 255))
    draw.text((px + pw//2 + 80, py + 750), "🏔️ Beautiful Sunset", font=get_font(24, bold=True), fill=(255, 255, 255, 255), anchor="mm")
    draw.text((px + 265, py + 910), "Just clicked this view! 📸", font=get_font(22), fill=(255, 255, 255, 255))
    draw.text((px + pw - 130, py + 935), "11:41 AM  ✓✓", font=get_font(16), fill=(210, 220, 255, 255))

    # Message Input Bar
    draw.rectangle([px+2, py+ph-120, px+pw-2, py+ph-2], fill=(22, 28, 48, 255))
    draw.rounded_rectangle([px + 25, py + ph - 100, px + pw - 110, py + ph - 25], radius=24, fill=(34, 42, 68, 255))
    draw.text((px + 55, py + ph - 73), "Type a message...", font=get_font(24), fill=(140, 155, 185, 255))
    draw.text((px + pw - 165, py + ph - 73), "📎", font=get_font(24), fill=(160, 175, 205, 255))
    # Mic Button
    draw.ellipse([px + pw - 95, py + ph - 100, px + pw - 25, py + ph - 30], fill=(79, 70, 229, 255))
    draw.text((px + pw - 68, py + ph - 74), "🎙️", font=get_font(22), fill=(255, 255, 255, 255), anchor="mm")

    out_path = os.path.join(OUTPUT_DIR, "screenshot_2_messaging.png")
    img.convert("RGB").save(out_path, "PNG", quality=95)
    print(f"Generated Screenshot 2: {out_path} ({W}x{H})")

# ==============================================================================
# 4. SCREENSHOT 3: Crystal Clear HD Voice Calls (1080 x 1920 px)
# ==============================================================================
def generate_screenshot_3():
    W, H = 1080, 1920
    img = create_gradient_bg(W, H, (8, 24, 32), (12, 18, 36), (10, 28, 38))
    draw = ImageDraw.Draw(img)

    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.ellipse([W//2 - 320, 450, W//2 + 320, 1100], fill=(16, 185, 129, 45))
    glow_draw.ellipse([100, 1100, 800, 1750], fill=(6, 182, 212, 35))
    glow = glow.filter(ImageFilter.GaussianBlur(100))
    img = Image.alpha_composite(img, glow)
    draw = ImageDraw.Draw(img)

    # Top Feature Header
    f_tag = get_font(32, bold=True)
    draw.text((W//2, 120), "CRYSTAL CLEAR HD AUDIO", font=f_tag, fill=(52, 211, 153, 255), anchor="mm")
    f_headline = get_font(64, bold=True)
    draw.text((W//2, 195), "High-Definition Voice Calls", font=f_headline, fill=(255, 255, 255, 255), anchor="mm")
    f_sub = get_font(30)
    draw.text((W//2, 260), "Real-time low-latency calling with noise suppression", font=f_sub, fill=(180, 215, 210, 255), anchor="mm")

    # Phone Mockup
    px, py, pw, ph = 140, 340, 800, 1500
    draw_phone_frame(draw, px, py, pw, ph, radius=48, screen_bg=(14, 22, 36, 255))

    # In-Call Screen UI
    # Top Security Shield
    draw.rounded_rectangle([px + pw//2 - 140, py + 80, px + pw//2 + 140, py + 125], radius=14, fill=(6, 78, 59, 200), outline=(16, 185, 129, 255), width=1)
    draw.text((px + pw//2, py + 102), "🔒 End-to-End Encrypted", font=get_font(18, bold=True), fill=(167, 243, 208, 255), anchor="mm")

    # Big Profile Avatar with Pulsing Rings
    av_cx, av_cy = px + pw//2, py + 380
    for r_size, col_a in [(170, 30), (140, 60), (110, 100)]:
        draw.ellipse([av_cx - r_size, av_cy - r_size, av_cx + r_size, av_cy + r_size], outline=(16, 185, 129, col_a), width=3)
    
    draw.ellipse([av_cx - 90, av_cy - 90, av_cx + 90, av_cy + 90], fill=(236, 72, 153, 255), outline=(255, 255, 255, 255), width=4)
    draw.text((av_cx, av_cy), "A", font=get_font(72, bold=True), fill=(255, 255, 255, 255), anchor="mm")

    # Name & Call Status
    draw.text((av_cx, py + 540), "Aarav Sharma", font=get_font(42, bold=True), fill=(255, 255, 255, 255), anchor="mm")
    draw.text((av_cx, py + 600), "04 : 32", font=get_font(36, bold=True), fill=(52, 211, 153, 255), anchor="mm")
    draw.text((av_cx, py + 650), "HD Audio • Crystal Clear", font=get_font(22), fill=(160, 195, 190, 255), anchor="mm")

    # Audio Equalizer Visualizer
    eq_x = px + 120
    eq_y = py + 760
    eq_heights = [20, 45, 75, 110, 85, 130, 95, 60, 120, 140, 80, 105, 135, 70, 40, 90, 60, 30]
    for h_bar in eq_heights:
        draw.rounded_rectangle([eq_x, eq_y - h_bar//2, eq_x + 18, eq_y + h_bar//2], radius=8, fill=(16, 185, 129, 240))
        eq_x += 32

    # Call Controls Grid
    ctrl_y = py + 920
    controls = [
        ("🔇", "Mute", (40, 50, 75)),
        ("🔊", "Speaker", (16, 185, 129)),
        ("📹", "Video", (40, 50, 75)),
        ("💬", "Message", (40, 50, 75)),
    ]
    cx_pos = px + 100
    for icon, label, bg_col in controls:
        draw.ellipse([cx_pos - 40, ctrl_y - 40, cx_pos + 40, ctrl_y + 40], fill=bg_col)
        draw.text((cx_pos, ctrl_y), icon, font=get_font(30), fill=(255, 255, 255, 255), anchor="mm")
        draw.text((cx_pos, ctrl_y + 60), label, font=get_font(18, bold=True), fill=(200, 215, 235, 255), anchor="mm")
        cx_pos += 195

    # End Call Button (Big Red)
    end_cy = py + 1220
    draw.ellipse([px + pw//2 - 60, end_cy - 60, px + pw//2 + 60, end_cy + 60], fill=(239, 68, 68, 255), outline=(248, 113, 113, 255), width=3)
    draw.text((px + pw//2, end_cy), "📞", font=get_font(42), fill=(255, 255, 255, 255), anchor="mm")

    out_path = os.path.join(OUTPUT_DIR, "screenshot_3_voice_calls.png")
    img.convert("RGB").save(out_path, "PNG", quality=95)
    print(f"Generated Screenshot 3: {out_path} ({W}x{H})")

# ==============================================================================
# 5. SCREENSHOT 4: Security, Privacy & Profile (1080 x 1920 px)
# ==============================================================================
def generate_screenshot_4():
    W, H = 1080, 1920
    img = create_gradient_bg(W, H, (12, 14, 28), (20, 16, 40), (16, 14, 34))
    draw = ImageDraw.Draw(img)

    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.ellipse([W//2 - 300, 400, W//2 + 300, 1000], fill=(139, 92, 246, 40))
    glow_draw.ellipse([100, 1200, 700, 1800], fill=(236, 72, 153, 30))
    glow = glow.filter(ImageFilter.GaussianBlur(100))
    img = Image.alpha_composite(img, glow)
    draw = ImageDraw.Draw(img)

    # Top Feature Header
    f_tag = get_font(32, bold=True)
    draw.text((W//2, 120), "PRIVACY & SECURITY FIRST", font=f_tag, fill=(196, 181, 253, 255), anchor="mm")
    f_headline = get_font(64, bold=True)
    draw.text((W//2, 195), "Your Chats, Always Protected", font=f_headline, fill=(255, 255, 255, 255), anchor="mm")
    f_sub = get_font(30)
    draw.text((W//2, 260), "End-to-end security, instant OTP verification & privacy controls", font=f_sub, fill=(200, 190, 230, 255), anchor="mm")

    # Phone Mockup
    px, py, pw, ph = 140, 340, 800, 1500
    draw_phone_frame(draw, px, py, pw, ph, radius=48)

    # Header
    draw.rectangle([px+2, py+58, px+pw-2, py+160], fill=(26, 22, 46, 255))
    draw.text((px + 40, py + 90), "Settings & Privacy", font=get_font(36, bold=True), fill=(255, 255, 255, 255))

    # User Profile Card
    draw.rounded_rectangle([px + 30, py + 185, px + pw - 30, py + 330], radius=24, fill=(35, 30, 62, 255), outline=(65, 55, 105, 255))
    draw.ellipse([px + 55, py + 210, px + 145, py + 300], fill=(79, 70, 229, 255))
    draw.text((px + 82, py + 232), "V", font=get_font(42, bold=True), fill=(255, 255, 255, 255))
    draw.text((px + 170, py + 220), "Vijay", font=get_font(32, bold=True), fill=(255, 255, 255, 255))
    draw.text((px + 170, py + 265), "v8929731578@gmail.com", font=get_font(20), fill=(180, 170, 215, 255))

    # Verified Cloud Badge
    draw.rounded_rectangle([px + pw - 180, py + 215, px + pw - 50, py + 258], radius=14, fill=(6, 78, 59, 220), outline=(16, 185, 129, 255))
    draw.text((px + pw - 115, py + 236), "✓ VERIFIED", font=get_font(14, bold=True), fill=(167, 243, 208, 255), anchor="mm")

    # Privacy Settings List
    settings = [
        ("🔒", "End-to-End Encryption", "Enabled for all 1-on-1 and group chats", (16, 185, 129), True),
        ("⚡", "Instant 2-Step OTP", "Secure email authentication via Cloud SSL", (79, 70, 229), True),
        ("🛡️", "App Lock & Biometrics", "Fingerprint / PIN protection", (139, 92, 246), True),
        ("👁️", "Last Seen & Online Status", "Custom privacy visibility rules", (245, 158, 11), False),
        ("🗑️", "Auto-Delete Messages", "Disappearing messages timer", (236, 72, 153), False),
        ("☁️", "Cloud Backup & Sync", "Encrypted backup on secure cloud", (14, 165, 233), True),
    ]
    sy = py + 360
    for icon, title, desc, col, toggle_on in settings:
        draw.rounded_rectangle([px + 30, sy, px + pw - 30, sy + 115], radius=20, fill=(30, 26, 52, 255))
        # Icon box
        draw.rounded_rectangle([px + 48, sy + 20, px + 120, sy + 92], radius=16, fill=(45, 38, 75, 255))
        draw.text((px + 84, sy + 56), icon, font=get_font(28), fill=(255, 255, 255, 255), anchor="mm")
        # Text
        draw.text((px + 140, sy + 25), title, font=get_font(24, bold=True), fill=(255, 255, 255, 255))
        draw.text((px + 140, sy + 65), desc, font=get_font(18), fill=(170, 160, 200, 255))
        # Toggle Switch
        if toggle_on:
            draw.rounded_rectangle([px + pw - 110, sy + 38, px + pw - 50, sy + 76], radius=19, fill=(16, 185, 129, 255))
            draw.ellipse([px + pw - 84, sy + 41, px + pw - 53, sy + 73], fill=(255, 255, 255, 255))
        else:
            draw.rounded_rectangle([px + pw - 110, sy + 38, px + pw - 50, sy + 76], radius=19, fill=(60, 52, 90, 255))
            draw.ellipse([px + pw - 107, sy + 41, px + pw - 76, sy + 73], fill=(160, 150, 190, 255))
        sy += 135

    out_path = os.path.join(OUTPUT_DIR, "screenshot_4_privacy_security.png")
    img.convert("RGB").save(out_path, "PNG", quality=95)
    print(f"Generated Screenshot 4: {out_path} ({W}x{H})")

if __name__ == "__main__":
    generate_feature_graphic()
    generate_screenshot_1()
    generate_screenshot_2()
    generate_screenshot_3()
    generate_screenshot_4()
    print("ALL PLAY STORE GRAPHICS COMPLETED SUCCESSFULLY!")
