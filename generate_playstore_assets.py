import os
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUTPUT_DIR = r"C:\Users\DELL\Downloads\Chatooz\joyful-hawking\playstore_assets"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# ─── REAL CHATOOZ APP COLOR PALETTE ───
DARK_BG        = (8, 12, 20)       # 0xFF080C14
DARK_SURFACE   = (15, 23, 42)      # 0xFF0F172A
DARK_CARD      = (22, 32, 50)      # 0xFF162032
DARK_DIVIDER   = (30, 41, 59)      # 0xFF1E293B
INDIGO_PRIMARY = (99, 102, 241)    # 0xFF6366F1
INDIGO_DARK    = (79, 70, 229)     # 0xFF4F46E5
INDIGO_LIGHT   = (129, 140, 248)   # 0xFF818CF8
EMERALD_GREEN  = (16, 185, 129)    # 0xFF10B981
TEXT_PRIMARY   = (248, 250, 252)   # 0xFFF8FAFC
TEXT_SECONDARY = (148, 163, 184)   # 0xFF94A3B8
TEXT_MUTED     = (100, 116, 139)   # 0xFF64748B

def get_font(size, bold=False):
    font_names = [
        "segoeuib.ttf" if bold else "segoeui.ttf",
        "arialbd.ttf" if bold else "arial.ttf",
        "calibrib.ttf" if bold else "calibri.ttf",
    ]
    for fn in font_names:
        try:
            return ImageFont.truetype(fn, size)
        except Exception:
            continue
    return ImageFont.load_default()

# ─── VECTOR ICONS (Zero Missing Glyphs) ───

def draw_chatooz_logo(draw, x, y, size):
    # Purple gradient rounded box with white speech bubble + purple bone/pill
    draw.rounded_rectangle([x, y, x + size, y + size], radius=int(size*0.28), fill=(88, 56, 220, 255), outline=(147, 112, 219, 255), width=max(1, int(size*0.035)))
    b_pad = int(size * 0.15)
    draw.rounded_rectangle([x + b_pad, y + b_pad, x + size - b_pad, y + size - b_pad], radius=int(size*0.22), fill=(255, 255, 255, 255))
    cx, cy = x + size // 2, y + size // 2
    pill_w = int(size * 0.44)
    pill_h = max(2, int(size * 0.13))
    # Diagonal bone
    x1, y1 = cx - int(pill_w*0.35), cy + int(pill_w*0.35)
    x2, y2 = cx + int(pill_w*0.35), cy - int(pill_w*0.35)
    draw.line([(x1, y1), (x2, y2)], fill=(88, 56, 220, 255), width=pill_h)
    draw.ellipse([x1 - pill_h, y1 - pill_h, x1 + pill_h, y1 + pill_h], fill=(88, 56, 220, 255))
    draw.ellipse([x2 - pill_h, y2 - pill_h, x2 + pill_h, y2 + pill_h], fill=(88, 56, 220, 255))

def draw_search_icon(draw, cx, cy, size, color=TEXT_PRIMARY):
    r = size // 2 - 3
    draw.ellipse([cx - r, cy - r, cx + r - 3, cy + r - 3], outline=color, width=2)
    draw.line([(cx + r - 5, cy + r - 5), (cx + r + 4, cy + r + 4)], fill=color, width=3)

def draw_qr_icon(draw, cx, cy, size, color=TEXT_PRIMARY):
    draw.rounded_rectangle([cx - size//2, cy - size//2, cx + size//2, cy + size//2], radius=3, outline=color, width=2)
    draw.rounded_rectangle([cx - size//4, cy - size//4, cx + size//4, cy + size//4], radius=2, fill=color)

def draw_sun_icon(draw, cx, cy, size, color=TEXT_PRIMARY):
    r = size // 3
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)
    for angle in range(0, 360, 45):
        rad = math.radians(angle)
        x1 = cx + int((r + 3) * math.cos(rad))
        y1 = cy + int((r + 3) * math.sin(rad))
        x2 = cx + int((r + 7) * math.cos(rad))
        y2 = cy + int((r + 7) * math.sin(rad))
        draw.line([(x1, y1), (x2, y2)], fill=color, width=2)

def draw_phone_icon(draw, cx, cy, size, color=TEXT_PRIMARY):
    draw.rounded_rectangle([cx - size//3, cy - size//2, cx + size//3, cy + size//2], radius=4, outline=color, width=2)
    draw.line([(cx - size//6, cy - size//2 + 4), (cx + size//6, cy - size//2 + 4)], fill=color, width=2)
    draw.ellipse([cx - 2, cy + size//2 - 6, cx + 2, cy + size//2 - 2], fill=color)

def draw_video_icon(draw, cx, cy, size, color=TEXT_PRIMARY):
    draw.rounded_rectangle([cx - size//2, cy - size//3, cx + size//6, cy + size//3], radius=3, fill=color)
    draw.polygon([(cx + size//5, cy - size//6), (cx + size//2, cy - size//3), (cx + size//2, cy + size//3), (cx + size//5, cy + size//6)], fill=color)

def draw_call_receiver_icon(draw, cx, cy, size, color=TEXT_PRIMARY):
    draw.rounded_rectangle([cx - size//2 + 2, cy - 5, cx + size//2 - 2, cy + 5], radius=4, fill=color)
    draw.rounded_rectangle([cx - size//2 + 2, cy - 8, cx - size//4, cy + 8], radius=3, fill=color)
    draw.rounded_rectangle([cx + size//4, cy - 8, cx + size//2 - 2, cy + 8], radius=3, fill=color)

def draw_pencil_icon(draw, cx, cy, size, color=INDIGO_LIGHT):
    draw.line([(cx - size//3, cy + size//3), (cx + size//3, cy - size//3)], fill=color, width=3)
    draw.polygon([(cx - size//3 - 3, cy + size//3 + 3), (cx - size//3 + 2, cy + size//3 + 2), (cx - size//3 - 2, cy + size//3 - 2)], fill=color)

def draw_mail_icon(draw, cx, cy, size, color=TEXT_MUTED):
    draw.rounded_rectangle([cx - size//2, cy - size//3, cx + size//2, cy + size//3], radius=3, outline=color, width=2)
    draw.line([(cx - size//2 + 2, cy - size//3 + 2), (cx, cy + 2)], fill=color, width=2)
    draw.line([(cx + size//2 - 2, cy - size//3 + 2), (cx, cy + 2)], fill=color, width=2)

def draw_location_icon(draw, cx, cy, size, color=TEXT_MUTED):
    r = size // 3
    draw.ellipse([cx - r, cy - size//2, cx + r, cy - size//2 + 2*r], outline=color, width=2)
    draw.polygon([(cx - r + 1, cy - size//2 + r), (cx + r - 1, cy - size//2 + r), (cx, cy + size//2 - 2)], fill=color)
    draw.ellipse([cx - 2, cy - size//2 + r - 2, cx + 2, cy - size//2 + r + 2], fill=color)

def draw_cloud_sync_icon(draw, cx, cy, size, color=EMERALD_GREEN):
    draw.ellipse([cx - size//3, cy - size//4, cx + size//4, cy + size//3], fill=color)
    draw.ellipse([cx - size//2, cy - 2, cx, cy + size//3], fill=color)
    draw.ellipse([cx, cy - 4, cx + size//2, cy + size//3], fill=color)

def draw_refresh_icon(draw, cx, cy, size, color=INDIGO_LIGHT):
    r = size // 2 - 2
    draw.arc([cx - r, cy - r, cx + r, cy + r], start=30, end=300, fill=color, width=2)
    draw.polygon([(cx + r - 2, cy - 4), (cx + r + 5, cy + 3), (cx + r - 6, cy + 3)], fill=color)

def draw_chat_bubble_icon(draw, cx, cy, size, color=INDIGO_LIGHT):
    draw.rounded_rectangle([cx - size//2, cy - size//3, cx + size//2, cy + size//3], radius=5, fill=color)
    draw.polygon([(cx - size//4, cy + size//3 - 1), (cx - size//2, cy + size//2), (cx - size//8, cy + size//3 - 1)], fill=color)

def draw_friends_icon(draw, cx, cy, size, color=TEXT_MUTED):
    # Two people silhouettes
    draw.ellipse([cx - 6, cy - 8, cx + 2, cy], fill=color)
    draw.rounded_rectangle([cx - 10, cy + 2, cx + 6, cy + 12], radius=4, fill=color)
    draw.ellipse([cx + 4, cy - 5, cx + 10, cy + 1], fill=color)
    draw.rounded_rectangle([cx + 2, cy + 3, cx + 14, cy + 11], radius=3, fill=color)

def draw_profile_icon(draw, cx, cy, size, color=TEXT_MUTED):
    draw.ellipse([cx - 5, cy - 8, cx + 5, cy + 2], fill=color)
    draw.rounded_rectangle([cx - 9, cy + 4, cx + 9, cy + 14], radius=4, fill=color)

def draw_share_icon(draw, cx, cy, size, color=INDIGO_LIGHT):
    # 3 dots connected by 2 lines
    p1 = (cx + size//3, cy - size//3)
    p2 = (cx - size//3, cy)
    p3 = (cx + size//3, cy + size//3)
    draw.line([p2, p1], fill=color, width=2)
    draw.line([p2, p3], fill=color, width=2)
    draw.ellipse([p1[0]-4, p1[1]-4, p1[0]+4, p1[1]+4], fill=color)
    draw.ellipse([p2[0]-4, p2[1]-4, p2[0]+4, p2[1]+4], fill=color)
    draw.ellipse([p3[0]-4, p3[1]-4, p3[0]+4, p3[1]+4], fill=color)

def draw_checkmark_double(draw, cx, cy, color=(160, 200, 255, 255)):
    draw.line([(cx - 8, cy), (cx - 4, cy + 4), (cx + 2, cy - 4)], fill=color, width=2)
    draw.line([(cx - 3, cy), (cx + 1, cy + 4), (cx + 7, cy - 4)], fill=color, width=2)

def draw_status_bar(draw, px, py, pw):
    draw.text((px + 35, py + 18), "7:16", font=get_font(22, bold=True), fill=TEXT_PRIMARY)
    draw.text((px + pw - 130, py + 18), "5G  97%", font=get_font(20, bold=True), fill=TEXT_PRIMARY)
    draw.rounded_rectangle([px + pw - 60, py + 22, px + pw - 35, py + 36], radius=3, outline=TEXT_PRIMARY, width=1)
    draw.rectangle([px + pw - 58, py + 24, px + pw - 39, py + 34], fill=EMERALD_GREEN)

def draw_bottom_nav(draw, px, py, pw, ph, active_tab="Chats"):
    nav_y = py + ph - 100
    draw.rectangle([px + 2, nav_y, px + pw - 2, py + ph - 2], fill=DARK_BG)
    draw.line([(px + 2, nav_y), (px + pw - 2, nav_y)], fill=DARK_DIVIDER, width=1)

    tabs = [
        ("Chats", draw_chat_bubble_icon, px + int(pw * 0.2)),
        ("Friends", draw_friends_icon, px + int(pw * 0.5)),
        ("Profile", draw_profile_icon, px + int(pw * 0.8)),
    ]

    for label, icon_fn, cx in tabs:
        is_active = (label == active_tab)
        if is_active:
            draw.rounded_rectangle([cx - 50, nav_y + 12, cx + 50, nav_y + 54], radius=21, fill=(35, 45, 80, 255))
            icon_fn(draw, cx, nav_y + 33, 24, INDIGO_LIGHT)
            draw.text((cx, nav_y + 72), label, font=get_font(18, bold=True), fill=INDIGO_LIGHT, anchor="mm")
        else:
            icon_fn(draw, cx, nav_y + 33, 24, TEXT_MUTED)
            draw.text((cx, nav_y + 72), label, font=get_font(18), fill=TEXT_MUTED, anchor="mm")

def draw_phone_bezel(draw, px, py, pw, ph, radius=46):
    draw.rounded_rectangle([px - 6, py - 6, px + pw + 6, py + ph + 6], radius=radius + 6, fill=(15, 18, 28, 255), outline=(50, 60, 85, 255), width=3)
    draw.rounded_rectangle([px, py, px + pw, py + ph], radius=radius, fill=DARK_BG)

# ==============================================================================
# SCREENSHOT 1: REAL CHATOOZ HOMESCREEN (MESSAGES & STORIES)
# ==============================================================================
def render_real_screen_home(draw, px, py, pw, ph):
    draw_phone_bezel(draw, px, py, pw, ph)
    draw_status_bar(draw, px, py, pw)

    # Real App Header
    draw_chatooz_logo(draw, px + 35, py + 65, 62)
    draw.text((px + 112, py + 65), "Chatooz", font=get_font(34, bold=True), fill=TEXT_PRIMARY)
    draw.ellipse([px + 265, py + 78, px + 277, py + 90], fill=EMERALD_GREEN)
    draw.text((px + 112, py + 105), "v6.0.1 • HyperConnect", font=get_font(18, bold=True), fill=INDIGO_LIGHT)

    # Top Action Icons
    draw_search_icon(draw, px + pw - 200, py + 90, 24, TEXT_PRIMARY)
    draw_qr_icon(draw, px + pw - 145, py + 90, 22, TEXT_PRIMARY)
    draw_sun_icon(draw, px + pw - 95, py + 90, 22, TEXT_PRIMARY)
    # User DP in Header
    draw.ellipse([px + pw - 55, py + 70, px + pw - 20, py + 105], fill=(234, 138, 0, 255))
    draw.text((px + pw - 38, py + 87), "V", font=get_font(18, bold=True), fill=TEXT_PRIMARY, anchor="mm")

    # Story Section
    draw_chatooz_logo(draw, px + 35, py + 155, 84)
    # "+" badge on story
    draw.ellipse([px + 95, py + 215, px + 125, py + 245], fill=INDIGO_PRIMARY, outline=DARK_BG, width=2)
    draw.text((px + 110, py + 228), "+", font=get_font(22, bold=True), fill=TEXT_PRIMARY, anchor="mm")
    draw.text((px + 77, py + 258), "Your Story", font=get_font(20, bold=True), fill=TEXT_PRIMARY, anchor="mm")

    # Messages Title
    draw.text((px + 35, py + 305), "Messages", font=get_font(32, bold=True), fill=TEXT_PRIMARY)

    # Real Chat List Items from the actual app!
    real_chats = [
        ("Dada Ji", "Voice call (1m 0s)", "11:52 am", (13, 148, 136), "D", False, True, False),
        ("Nazar Mohammad", "Video call (4m 12s)", "12:56 pm", (6, 182, 212), "N", False, False, True),
        ("vijaay verse", "Shared cloud project link", "12:56 pm", (99, 102, 241), "V", True, False, False),
        ("Rricha", "Voice call (1m 0s)", "11:52 am", (16, 185, 129), "R", True, True, False),
        ("Chatooz Support", "Your OTP is verified instantly", "10:30 am", (147, 51, 234), "C", False, False, False),
        ("Bhavesh", "You are now friends! Say hello!", "Sept 23", (234, 138, 0), "B", False, False, False),
        ("Family Group", "Maa: Call me when free", "Sept 22", (236, 72, 153), "F", False, False, False),
    ]

    cy = py + 365
    for name, msg, time, color, init, unread, is_voice, is_video in real_chats:
        # Avatar Circle
        draw.ellipse([px + 35, cy, px + 125, cy + 90], fill=color)
        draw.text((px + 80, cy + 45), init, font=get_font(36, bold=True), fill=TEXT_PRIMARY, anchor="mm")
        
        # Name
        draw.text((px + 145, cy + 10), name, font=get_font(27, bold=True), fill=TEXT_PRIMARY)
        
        # Subtitle icon + text
        sub_x = px + 145
        if is_voice:
            draw_call_receiver_icon(draw, sub_x + 10, cy + 62, 18, TEXT_MUTED)
            sub_x += 28
        elif is_video:
            draw_video_icon(draw, sub_x + 10, cy + 62, 18, TEXT_MUTED)
            sub_x += 28
            
        draw.text((sub_x, cy + 50), msg, font=get_font(21), fill=TEXT_SECONDARY)
        
        # Time
        draw.text((px + pw - 35, cy + 12), time, font=get_font(19), fill=TEXT_MUTED, anchor="ra")
        
        if unread:
            draw.ellipse([px + pw - 50, cy + 50, px + pw - 32, cy + 68], fill=INDIGO_PRIMARY)
            
        cy += 115

    # Floating Action Button: "+ New Group" (Indigo rounded pill)
    draw.rounded_rectangle([px + pw - 270, py + ph - 200, px + pw - 35, py + ph - 130], radius=24, fill=INDIGO_PRIMARY)
    draw_friends_icon(draw, px + pw - 235, py + ph - 165, 22, TEXT_PRIMARY)
    draw.text((px + pw - 138, py + ph - 165), "+ New Group", font=get_font(23, bold=True), fill=TEXT_PRIMARY, anchor="mm")

    # Bottom Navigation
    draw_bottom_nav(draw, px, py, pw, ph, active_tab="Chats")

# ==============================================================================
# SCREENSHOT 2: REAL CHATOOZ CHAT CONVERSATION SCREEN
# ==============================================================================
def render_real_screen_chat(draw, px, py, pw, ph):
    draw_phone_bezel(draw, px, py, pw, ph)
    draw_status_bar(draw, px, py, pw)

    # Chat Top App Bar
    draw.rectangle([px + 2, py + 56, px + pw - 2, py + 165], fill=DARK_SURFACE)
    draw.line([(px + 2, py + 165), (px + pw - 2, py + 165)], fill=DARK_DIVIDER, width=1)

    # Back arrow
    draw.text((px + 30, py + 100), "←", font=get_font(36, bold=True), fill=TEXT_PRIMARY)
    
    # Avatar
    draw.ellipse([px + 80, py + 78, px + 155, py + 153], fill=(13, 148, 136))
    draw.text((px + 117, py + 114), "D", font=get_font(30, bold=True), fill=TEXT_PRIMARY, anchor="mm")
    draw.ellipse([px + 138, py + 134, px + 154, py + 150], fill=EMERALD_GREEN, outline=DARK_SURFACE, width=2)

    # Name & Status
    draw.text((px + 170, py + 86), "Dada Ji", font=get_font(28, bold=True), fill=TEXT_PRIMARY)
    draw.text((px + 170, py + 124), "@dada • Online", font=get_font(20), fill=EMERALD_GREEN)

    # Action Icons
    draw_phone_icon(draw, px + pw - 140, py + 110, 24, TEXT_PRIMARY)
    draw_video_icon(draw, px + pw - 85, py + 110, 24, TEXT_PRIMARY)
    draw.text((px + pw - 35, py + 98), "⋮", font=get_font(26, bold=True), fill=TEXT_PRIMARY)

    # Date Header Pill
    draw.rounded_rectangle([px + pw//2 - 90, py + 185, px + pw//2 + 90, py + 225], radius=14, fill=DARK_CARD)
    draw.text((px + pw//2, py + 205), "TODAY", font=get_font(18, bold=True), fill=TEXT_SECONDARY, anchor="mm")

    # Incoming Bubble 1 (DarkCard)
    draw.rounded_rectangle([px + 35, py + 250, px + 580, py + 365], radius=22, fill=DARK_CARD)
    draw.text((px + 60, py + 275), "Vijay, naya Chatooz cloud build\nkaisa chal raha hai?", font=get_font(23), fill=TEXT_PRIMARY)
    draw.text((px + 500, py + 332), "10:14 am", font=get_font(17), fill=TEXT_MUTED)

    # Outgoing Bubble 1 (Indigo Gradient)
    draw.rounded_rectangle([px + 210, py + 390, px + pw - 35, py + 525], radius=22, fill=INDIGO_PRIMARY)
    draw.text((px + 235, py + 415), "Bahut badhiya! OTP delivery ab\n0.3 second me instant ho gayi hai!", font=get_font(23), fill=TEXT_PRIMARY)
    draw.text((px + pw - 130, py + 490), "10:15 am", font=get_font(17), fill=(210, 225, 255))
    draw_checkmark_double(draw, px + pw - 60, py + 498, (210, 225, 255))

    # Incoming Voice Note Bubble
    draw.rounded_rectangle([px + 35, py + 550, px + 620, py + 670], radius=22, fill=DARK_CARD)
    draw.ellipse([px + 60, py + 578, px + 125, py + 643], fill=EMERALD_GREEN)
    draw.polygon([(px + 86, py + 597), (px + 104, py + 610), (px + 86, py + 623)], fill=TEXT_PRIMARY)
    
    # Audio bars
    bx = px + 150
    for bh in [18, 32, 50, 28, 44, 30, 20, 48, 55, 34, 22, 40, 26, 18, 36, 24, 42, 20]:
        draw.line([(bx, py + 610 - bh//2), (bx, py + 610 + bh//2)], fill=EMERALD_GREEN if bx < px + 360 else TEXT_MUTED, width=4)
        bx += 20
    draw.text((px + 150, py + 640), "0:42 / 1:15", font=get_font(17), fill=TEXT_SECONDARY)
    draw.text((px + 540, py + 640), "10:16 am", font=get_font(17), fill=TEXT_MUTED)

    # Outgoing Message with Photo Card
    draw.rounded_rectangle([px + 210, py + 695, px + pw - 35, py + 1040], radius=22, fill=INDIGO_PRIMARY)
    draw.rounded_rectangle([px + 228, py + 712, px + pw - 53, py + 950], radius=16, fill=(35, 45, 75, 255))
    draw.text((px + pw//2 + 75, py + 820), "Chatooz Play Store Update", font=get_font(24, bold=True), fill=TEXT_PRIMARY, anchor="mm")
    draw.text((px + 240, py + 975), "Live sync test complete!", font=get_font(23), fill=TEXT_PRIMARY)
    draw.text((px + pw - 130, py + 1008), "10:17 am", font=get_font(17), fill=(210, 225, 255))
    draw_checkmark_double(draw, px + pw - 60, py + 1016, (210, 225, 255))

    # Bottom Input Bar
    input_y = py + ph - 130
    draw.rectangle([px + 2, input_y, px + pw - 2, py + ph - 2], fill=DARK_SURFACE)
    draw.line([(px + 2, input_y), (px + pw - 2, input_y)], fill=DARK_DIVIDER, width=1)
    
    # Input field
    draw.rounded_rectangle([px + 25, input_y + 20, px + pw - 110, input_y + 90], radius=26, fill=DARK_CARD)
    draw.text((px + 50, input_y + 44), "Type a message...", font=get_font(24), fill=TEXT_MUTED)
    # Paperclip icon
    draw.line([(px + pw - 155, input_y + 60), (px + pw - 145, input_y + 45), (px + pw - 135, input_y + 65)], fill=TEXT_SECONDARY, width=2)
    
    # Mic / Send Button
    draw.ellipse([px + pw - 95, input_y + 20, px + pw - 25, input_y + 90], fill=INDIGO_PRIMARY)
    draw_phone_receiver_mic = True
    draw.ellipse([px + pw - 64, input_y + 42, px + pw - 56, input_y + 56], fill=TEXT_PRIMARY)
    draw.line([(px + pw - 60, input_y + 56), (px + pw - 60, input_y + 68)], fill=TEXT_PRIMARY, width=2)

# ==============================================================================
# SCREENSHOT 3: REAL CHATOOZ FRIENDS & CONTACTS SCREEN
# ==============================================================================
def render_real_screen_friends(draw, px, py, pw, ph):
    draw_phone_bezel(draw, px, py, pw, ph)
    draw_status_bar(draw, px, py, pw)

    # Top Header
    draw.rectangle([px + 2, py + 56, px + pw - 2, py + 150], fill=DARK_BG)
    draw.text((px + 35, py + 85), "←", font=get_font(36, bold=True), fill=TEXT_PRIMARY)
    draw.text((px + 95, py + 82), "Friends & Contacts", font=get_font(34, bold=True), fill=TEXT_PRIMARY)
    draw_share_icon(draw, px + pw - 45, py + 100, 24, INDIGO_LIGHT)

    # Tabs Row
    tab_y = py + 160
    draw.line([(px + 2, tab_y + 48), (px + pw - 2, tab_y + 48)], fill=DARK_DIVIDER, width=2)
    
    # Active Tab "Friends (3)" with Indigo Underline
    draw.text((px + 65, tab_y + 10), "Friends", font=get_font(24, bold=True), fill=INDIGO_LIGHT)
    draw.rounded_rectangle([px + 155, tab_y + 10, px + 185, tab_y + 38], radius=10, fill=INDIGO_PRIMARY)
    draw.text((px + 170, tab_y + 23), "3", font=get_font(18, bold=True), fill=TEXT_PRIMARY, anchor="mm")
    draw.line([(px + 30, tab_y + 48), (px + 210, tab_y + 48)], fill=INDIGO_PRIMARY, width=4)

    # Inactive Tabs
    draw.text((px + 250, tab_y + 10), "Phone Contacts", font=get_font(22), fill=TEXT_MUTED)
    draw.text((px + 490, tab_y + 10), "Add Friend", font=get_font(22), fill=TEXT_MUTED)
    draw.text((px + 660, tab_y + 10), "Requests", font=get_font(22), fill=TEXT_MUTED)

    # Real Friends List from user's app
    real_friends = [
        ("Dada Ji", "@dada", "Hey, I'm on Chatooz!", (13, 148, 136), "D"),
        ("bhavesh", "@bhaxesh", "Hey, I'm on Chatooz!", (6, 182, 212), "B"),
        ("Bhavesh", "@bhavesh", "Hey, I'm on Chatooz!", (234, 138, 0), "B"),
        ("Nazar Mohammad", "@nazar", "Active now on Chatooz", (99, 102, 241), "N"),
        ("Rricha", "@rricha", "Available for calls", (16, 185, 129), "R"),
    ]

    fy = py + 240
    for name, uname, status, col, init in real_friends:
        # Avatar
        draw.ellipse([px + 35, fy, px + 125, fy + 90], fill=col)
        draw.text((px + 80, fy + 45), init, font=get_font(36, bold=True), fill=TEXT_PRIMARY, anchor="mm")
        
        # Name & Username
        draw.text((px + 145, fy + 5), name, font=get_font(27, bold=True), fill=TEXT_PRIMARY)
        draw.text((px + 145, fy + 38), uname, font=get_font(21), fill=INDIGO_LIGHT)
        draw.text((px + 145, fy + 68), status, font=get_font(19), fill=TEXT_MUTED)

        # Message Icon Button on right (Purple rounded button)
        draw.rounded_rectangle([px + pw - 105, fy + 15, px + pw - 35, fy + 75], radius=18, fill=(35, 45, 80, 255))
        draw_chat_bubble_icon(draw, px + pw - 70, fy + 45, 24, INDIGO_LIGHT)

        # Divider
        draw.line([(px + 145, fy + 110), (px + pw - 35, fy + 110)], fill=DARK_DIVIDER, width=1)
        fy += 135

    # Bottom Navigation
    draw_bottom_nav(draw, px, py, pw, ph, active_tab="Friends")

# ==============================================================================
# SCREENSHOT 4: REAL CHATOOZ PROFILE & CLOUD SYNC SCREEN
# ==============================================================================
def render_real_screen_profile(draw, px, py, pw, ph):
    draw_phone_bezel(draw, px, py, pw, ph)
    draw_status_bar(draw, px, py, pw)

    # Top Header
    draw.rectangle([px + 2, py + 56, px + pw - 2, py + 150], fill=DARK_BG)
    draw.text((px + 35, py + 85), "←", font=get_font(36, bold=True), fill=TEXT_PRIMARY)
    draw.text((px + 95, py + 82), "Profile", font=get_font(34, bold=True), fill=TEXT_PRIMARY)
    draw_pencil_icon(draw, px + pw - 50, py + 95, 24, INDIGO_LIGHT)

    # Real Profile Avatar Section
    av_cx, av_cy = px + pw//2, py + 225
    draw_chatooz_logo(draw, av_cx - 55, av_cy - 55, 110)
    # Camera Badge
    draw.ellipse([av_cx + 25, av_cy + 25, av_cx + 65, av_cy + 65], fill=INDIGO_PRIMARY, outline=DARK_BG, width=2)
    draw.rounded_rectangle([av_cx + 37, av_cy + 40, av_cx + 53, av_cy + 52], radius=2, fill=TEXT_PRIMARY)
    draw.ellipse([av_cx + 42, av_cy + 43, av_cx + 48, av_cy + 49], fill=INDIGO_PRIMARY)

    # Name, Username, Bio
    draw.text((av_cx, py + 300), "Vijaay Bhardwaj", font=get_font(34, bold=True), fill=TEXT_PRIMARY, anchor="mm")
    draw.text((av_cx, py + 340), "@vijaay", font=get_font(23, bold=True), fill=INDIGO_LIGHT, anchor="mm")
    draw.text((av_cx, py + 375), "Hey, I'm on Chatooz!", font=get_font(21), fill=TEXT_SECONDARY, anchor="mm")

    # "Edit Profile Information" Button
    draw.rounded_rectangle([px + 100, py + 415, px + pw - 100, py + 475], radius=18, fill=(35, 45, 80, 255))
    draw_pencil_icon(draw, px + 150, py + 445, 20, INDIGO_LIGHT)
    draw.text((av_cx + 10, py + 443), "Edit Profile Information", font=get_font(22, bold=True), fill=INDIGO_LIGHT, anchor="mm")

    # Card 1: Multi-Device Cloud Sync
    draw.rounded_rectangle([px + 35, py + 505, px + pw - 35, py + 625], radius=22, fill=DARK_CARD)
    draw.ellipse([px + 60, py + 535, px + 120, py + 595], fill=(6, 78, 59, 255))
    draw_cloud_sync_icon(draw, px + 90, py + 565, 28, EMERALD_GREEN)
    
    draw.text((px + 140, py + 532), "Multi-Device Cloud Sync", font=get_font(24, bold=True), fill=TEXT_PRIMARY)
    draw.text((px + 140, py + 570), "Connected • All accounts synced across devices", font=get_font(18), fill=TEXT_SECONDARY)
    draw_refresh_icon(draw, px + pw - 75, py + 565, 24, INDIGO_LIGHT)

    # Card 2: Account Details
    draw.rounded_rectangle([px + 35, py + 650, px + pw - 35, py + 1080], radius=22, fill=DARK_CARD)
    draw.text((px + 65, py + 680), "Account Details", font=get_font(26, bold=True), fill=TEXT_PRIMARY)
    draw.text((px + pw - 65, py + 680), "Edit", font=get_font(20, bold=True), fill=INDIGO_LIGHT, anchor="ra")

    # Gmail
    draw_mail_icon(draw, px + 80, py + 755, 24, TEXT_MUTED)
    draw.text((px + 115, py + 730), "Gmail Address", font=get_font(18), fill=TEXT_MUTED)
    draw.text((px + 115, py + 758), "v8929731578@gmail.com", font=get_font(22, bold=True), fill=TEXT_PRIMARY)

    # Username
    draw.text((px + 72, py + 830), "@", font=get_font(26, bold=True), fill=TEXT_MUTED)
    draw.text((px + 115, py + 815), "Chatooz Username", font=get_font(18), fill=TEXT_MUTED)
    draw.text((px + 115, py + 843), "@vijaay", font=get_font(22, bold=True), fill=INDIGO_LIGHT)

    # Mobile
    draw_phone_icon(draw, px + 80, py + 920, 24, TEXT_MUTED)
    draw.text((px + 115, py + 900), "Mobile Number", font=get_font(18), fill=TEXT_MUTED)
    draw.text((px + 115, py + 928), "8929731578", font=get_font(22, bold=True), fill=TEXT_PRIMARY)

    # Location
    draw_location_icon(draw, px + 80, py + 1005, 24, TEXT_MUTED)
    draw.text((px + 115, py + 985), "Address / Location", font=get_font(18), fill=TEXT_MUTED)
    draw.text((px + 115, py + 1013), "2071, Sector-23, Sonipat, Haryana", font=get_font(20, bold=True), fill=TEXT_PRIMARY)

    # Bottom Navigation
    draw_bottom_nav(draw, px, py, pw, ph, active_tab="Profile")

# ==============================================================================
# MAIN GENERATORS (EXACT GOOGLE PLAY STORE RESOLUTIONS)
# ==============================================================================

def make_screenshot_image(title_tag, headline, subhead, render_fn, filename):
    W, H = 1080, 1920
    img = Image.new("RGBA", (W, H), DARK_BG)
    draw = ImageDraw.Draw(img)

    # Subtle ambient gradient lights
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.ellipse([W//2 - 350, 350, W//2 + 350, 1050], fill=(79, 70, 229, 35))
    glow_draw.ellipse([100, 1100, 800, 1750], fill=(16, 185, 129, 25))
    glow = glow.filter(ImageFilter.GaussianBlur(100))
    img = Image.alpha_composite(img, glow)
    draw = ImageDraw.Draw(img)

    # Top Header Titles
    draw.text((W//2, 110), title_tag, font=get_font(30, bold=True), fill=INDIGO_LIGHT, anchor="mm")
    draw.text((W//2, 175), headline, font=get_font(58, bold=True), fill=TEXT_PRIMARY, anchor="mm")
    draw.text((W//2, 235), subhead, font=get_font(26), fill=TEXT_SECONDARY, anchor="mm")

    # Render Phone Frame with exact In-App Screen
    px, py, pw, ph = 140, 300, 800, 1530
    render_fn(draw, px, py, pw, ph)

    out_path = os.path.join(OUTPUT_DIR, filename)
    img.convert("RGB").save(out_path, "PNG", quality=95)
    print(f"Generated Play Store Graphic: {out_path} ({W}x{H})")

def generate_exact_feature_graphic():
    W, H = 1024, 500
    img = Image.new("RGBA", (W, H), DARK_BG)
    draw = ImageDraw.Draw(img)

    # Ambient glow
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.ellipse([600, 50, 950, 400], fill=(79, 70, 229, 45))
    glow_draw.ellipse([80, 200, 400, 500], fill=(16, 185, 129, 35))
    glow = glow.filter(ImageFilter.GaussianBlur(60))
    img = Image.alpha_composite(img, glow)
    draw = ImageDraw.Draw(img)

    # Left Section: Real Chatooz Branding
    draw_chatooz_logo(draw, 50, 60, 76)
    draw.text((140, 64), "Chatooz", font=get_font(44, bold=True), fill=TEXT_PRIMARY)
    draw.ellipse((325, 78, 337, 90), fill=EMERALD_GREEN)
    draw.text((140, 112), "v6.0.1 • HyperConnect Cloud", font=get_font(18, bold=True), fill=INDIGO_LIGHT)

    draw.text((50, 168), "Real-time Chat, HD Calls & Cloud Sync", font=get_font(21, bold=True), fill=TEXT_PRIMARY)

    # Feature Badges
    badges = [
        ("0.3s Instant Cloud OTP Verification", EMERALD_GREEN, (6, 78, 59, 200)),
        ("High-Definition Voice & Video Calling", INDIGO_LIGHT, (35, 45, 80, 200)),
        ("Multi-Device Cloud Sync & Privacy", (236, 72, 153), (131, 24, 67, 200)),
    ]
    by = 230
    for text, text_col, bg_col in badges:
        draw.rounded_rectangle([50, by, 50 + 460, by + 46], radius=14, fill=bg_col, outline=text_col, width=1)
        draw.text((70, by + 12), text, font=get_font(17, bold=True), fill=text_col)
        by += 60

    # Right Section: Exact Real Phone Mockup
    px, py, pw, ph = 560, 30, 390, 480
    draw_phone_bezel(draw, px, py, pw, ph, radius=32)
    
    # Real App Header inside mini phone
    draw_chatooz_logo(draw, px + 20, py + 30, 42)
    draw.text((px + 72, py + 30), "Chatooz", font=get_font(24, bold=True), fill=TEXT_PRIMARY)
    draw.text((px + 72, py + 56), "v6.0.1", font=get_font(14, bold=True), fill=INDIGO_LIGHT)

    # Real Chat Items
    mini_chats = [
        ("Dada Ji", "Voice call (1m 0s)", "11:52 am", (13, 148, 136), "D"),
        ("vijaay verse", "Shared cloud project link", "12:56 pm", (99, 102, 241), "V"),
        ("Rricha", "Hey, check out the new build!", "11:52 am", (16, 185, 129), "R"),
        ("Bhavesh", "You are now friends! Say hello!", "Sept 23", (234, 138, 0), "B"),
    ]
    cy = py + 95
    for name, msg, time, col, init in mini_chats:
        draw.ellipse([px + 20, cy, px + 62, cy + 42], fill=col)
        draw.text((px + 41, cy + 21), init, font=get_font(18, bold=True), fill=TEXT_PRIMARY, anchor="mm")
        draw.text((px + 75, cy + 4), name, font=get_font(15, bold=True), fill=TEXT_PRIMARY)
        draw.text((px + 75, cy + 24), msg[:20] + "...", font=get_font(12), fill=TEXT_SECONDARY)
        draw.text((px + pw - 20, cy + 4), time, font=get_font(11), fill=TEXT_MUTED, anchor="ra")
        cy += 56

    # Floating Action Button: "+ New Group"
    draw.rounded_rectangle([px + pw - 140, py + ph - 65, px + pw - 15, py + ph - 25], radius=16, fill=INDIGO_PRIMARY)
    draw.text((px + pw - 77, py + ph - 45), "+ New Group", font=get_font(12, bold=True), fill=TEXT_PRIMARY, anchor="mm")

    out_path = os.path.join(OUTPUT_DIR, "feature_graphic.png")
    img.convert("RGB").save(out_path, "PNG", quality=95)
    print(f"Generated Real Feature Graphic: {out_path} ({W}x{H})")

if __name__ == "__main__":
    generate_exact_feature_graphic()
    make_screenshot_image(
        "REAL-TIME MESSAGING",
        "Fast & Modern Cloud Chats",
        "Instant messaging, stories & real-time delivery",
        render_real_screen_home,
        "screenshot_1_chats.png"
    )
    make_screenshot_image(
        "SEAMLESS CONVERSATIONS",
        "Rich Chat & Voice Notes",
        "Voice notes, media sharing, reactions & instant OTP",
        render_real_screen_chat,
        "screenshot_2_messaging.png"
    )
    make_screenshot_image(
        "CONNECT WITH CONTACTS",
        "Friends & Phone Contacts",
        "One-tap messaging, contact sync & friend requests",
        render_real_screen_friends,
        "screenshot_3_voice_calls.png"
    )
    make_screenshot_image(
        "CLOUD & PRIVACY",
        "Multi-Device Cloud Sync",
        "Encrypted multi-device sync, barcode sharing & account security",
        render_real_screen_profile,
        "screenshot_4_privacy_security.png"
    )
    print("ALL REAL CHATOOZ PLAY STORE ASSETS COMPLETED!")
