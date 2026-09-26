@echo off
title Chatooz Online Server (Laptop Host)
color 0A
cd /d "%~dp0"

set PYTHON="%LOCALAPPDATA%\Programs\Python\Python312\python.exe"
if not exist %PYTHON% set PYTHON=python

echo Starting Chatooz Laptop Online Server...
%PYTHON% server\run_tunnel_service.py
pause
