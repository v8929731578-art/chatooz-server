"""
Chatooz Online Server & Tunnel Supervisor
Manages local backend server + Cloudflare Tunnel + GitHub endpoint sync.
"""

import os
import sys
import time
import json
import re
import subprocess
import urllib.request
import urllib.error

ROOT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.dirname(ROOT_DIR)
PYTHON_EXE = sys.executable
SERVER_PY = os.path.join(ROOT_DIR, "chatooz_online_server.py")
CLOUDFLARED_EXE = os.path.join(ROOT_DIR, "cloudflared.exe")
ENDPOINT_JSON = os.path.join(ROOT_DIR, "endpoint.json")
LOG_FILE = os.path.join(ROOT_DIR, "cf_live.log")

def check_server_health(port=8080):
    try:
        req = urllib.request.Request(f"http://127.0.0.1:{port}/health")
        with urllib.request.urlopen(req, timeout=3) as resp:
            return resp.status == 200
    except Exception:
        return False

def start_backend():
    if check_server_health():
        print("[+] Python backend is already running on port 8080.")
        return None
    print("[*] Starting Python backend on port 8080...")
    proc = subprocess.Popen(
        [PYTHON_EXE, SERVER_PY, "8080"],
        cwd=ROOT_DIR,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL
    )
    for _ in range(10):
        time.sleep(1)
        if check_server_health():
            print("[+] Python backend started successfully!")
            return proc
    print("[!] Warning: Backend health check took longer than expected.")
    return proc

def update_endpoint_file(url):
    data = {
        "server_url": url,
        "updated_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    }
    with open(ENDPOINT_JSON, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
    print(f"[+] Updated endpoint.json -> {url}")
    
    # Try syncing to Git repository
    try:
        subprocess.run(["git", "add", "server/endpoint.json"], cwd=PROJECT_DIR, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        subprocess.run(["git", "commit", "-m", f"Auto-update tunnel endpoint to {url}"], cwd=PROJECT_DIR, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        subprocess.run(["git", "push", "origin", "main"], cwd=PROJECT_DIR, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15)
        print("[+] Git auto-synced endpoint to GitHub repository.")
    except Exception as e:
        print(f"[*] Note: Git sync skipped ({e})")

def run_tunnel():
    print("===============================================================")
    print("               CHATOOZ ONLINE SERVER LAUNCHER                  ")
    print("===============================================================")
    
    start_backend()
    
    print("[*] Launching Cloudflare Tunnel...")
    if os.path.exists(LOG_FILE):
        try:
            os.remove(LOG_FILE)
        except Exception:
            pass

    cf_proc = subprocess.Popen(
        [CLOUDFLARED_EXE, "tunnel", "--url", "http://127.0.0.1:8080", "--logfile", LOG_FILE],
        cwd=ROOT_DIR,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL
    )

    tunnel_url = None
    url_pattern = re.compile(r"https://[a-zA-Z0-9-]+\.trycloudflare\.com")
    
    print("[*] Waiting for public tunnel URL...")
    for _ in range(30):
        time.sleep(1)
        if os.path.exists(LOG_FILE):
            try:
                with open(LOG_FILE, "r", encoding="utf-8", errors="ignore") as f:
                    content = f.read()
                    matches = url_pattern.findall(content)
                    if matches:
                        tunnel_url = matches[-1]
                        break
            except Exception:
                pass

    if tunnel_url:
        update_endpoint_file(tunnel_url)
        print("")
        print("===============================================================")
        print("  ONLINE STATUS: LIVE & CONNECTED")
        print(f"  Public URL:   {tunnel_url}")
        print(f"  Health Check: {tunnel_url}/health")
        print("===============================================================")
        print("  Your Android App is now connected to your laptop server!")
        print("  Keep this window open to maintain 24/7 connectivity.")
        print("===============================================================")
    else:
        print("[!] Could not auto-detect tunnel URL. Cloudflare may still be starting.")

    try:
        while True:
            time.sleep(10)
            if not check_server_health():
                print("[!] Backend crashed. Restarting...")
                start_backend()
    except KeyboardInterrupt:
        print("\n[*] Stopping tunnel and server...")
        cf_proc.terminate()
        sys.exit(0)

if __name__ == "__main__":
    run_tunnel()
