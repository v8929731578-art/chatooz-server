@echo off
title Chatooz - Stop Server
taskkill /F /IM python.exe 2>nul
taskkill /F /IM cloudflared.exe 2>nul
echo Chatooz Server and Tunnel stopped successfully.
timeout /t 2 >nul
