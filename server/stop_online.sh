#!/bin/bash
# Chatooz Online Server band karo
echo "Chatooz Online server band kar raha hoon..."
pkill -f "chatooz_online_server.py" 2>/dev/null && echo "✅ Server band" || echo "Server pehle se band tha"
pkill -f "cloudflared" 2>/dev/null && echo "✅ Tunnel band" || echo "Tunnel pehle se band tha"
rm -f /tmp/chatooz_tunnel_url.txt /tmp/chatooz_server.pid /tmp/chatooz_cf.pid
echo "Done!"
