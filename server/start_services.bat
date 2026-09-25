@echo off
setlocal

set "PYTHON_EXE=C:\Users\DELL\AppData\Local\Programs\Python\Python312\python.exe"
set "SERVER_PY=C:\Users\DELL\Downloads\Chatooz\joyful-hawking\server\chatooz_online_server.py"
set "CLOUDFLARED_EXE=C:\Program Files (x86)\cloudflared\cloudflared.exe"
set "LOG_FILE=C:\Users\DELL\Downloads\Chatooz\joyful-hawking\server\autostart.log"

echo [%DATE% %TIME%] Chatooz Auto-Start check >> "%LOG_FILE%"

:: 1. Check if Python Server is listening on port 8080
netstat -ano | findstr :8080 | findstr LISTENING >nul
if errorlevel 1 (
    echo [%DATE% %TIME%] Starting Python Backend on port 8080... >> "%LOG_FILE%"
    start "" /B "%PYTHON_EXE%" "%SERVER_PY%" 8080 >> "%LOG_FILE%" 2>&1
) else (
    echo [%DATE% %TIME%] Python Backend is already running on port 8080. >> "%LOG_FILE%"
)

:: Wait 3 seconds cleanly without timeout command
ping -n 4 127.0.0.1 >nul

:: 2. Check if cloudflared is already running
tasklist /FI "IMAGENAME eq cloudflared.exe" 2>nul | findstr /i "cloudflared.exe" >nul
if errorlevel 1 (
    echo [%DATE% %TIME%] Starting Cloudflared Tunnel... >> "%LOG_FILE%"
    start "" /B "%CLOUDFLARED_EXE%" tunnel --url http://127.0.0.1:8080 >> "%LOG_FILE%" 2>&1
) else (
    echo [%DATE% %TIME%] Cloudflared Tunnel is already running. >> "%LOG_FILE%"
)

echo [%DATE% %TIME%] Auto-Start check finished. >> "%LOG_FILE%"
exit /b 0
