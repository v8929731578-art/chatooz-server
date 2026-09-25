#!/usr/bin/env zsh
# Build and install on both devices
./gradlew assembleDebug
adb -s RZCW11V1J6W install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
# Placeholder for UI test steps – you can expand with UIAutomator or Espresso scripts.
# Example: launch app, log in, block user, unblock, send friend request, accept, send messages, verify UI.
