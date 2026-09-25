@echo off
title Chatooz Fixed Online Server
cls
echo ===================================================================
echo                     CHATOOZ FIXED ONLINE SERVER
echo ===================================================================
echo Fixed Permanent Domain: https://chatooz-server-live.loca.lt
echo ===================================================================
echo.

set PYTHON="%LOCALAPPDATA%\Programs\Python\Python312\python.exe"

echo 1. Stopping any previous servers...
taskkill /F /IM python.exe 2>nul
taskkill /F /IM node.exe 2>nul
taskkill /F /IM cloudflared.exe 2>nul
timeout /t 1 /nobreak >nul

echo 2. Starting Python Server on port 8080...
start /B "" %PYTHON% "%~dp0chatooz_online_server.py" 8080 > "%~dp0server.log" 2>&1

timeout /t 2 /nobreak >nul

echo 3. Starting Permanent Tunnel (chatooz-server-live.loca.lt)...
echo App connects to: https://chatooz-server-live.loca.lt
echo Keep this window open!
echo.

cmd /c "lt --port 8080 --subdomain chatooz-server-live"
pause
