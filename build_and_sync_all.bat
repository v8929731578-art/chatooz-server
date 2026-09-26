@echo off
setlocal
echo =======================================================
echo     Chatooz - Unified Build & Sync (Play Store + APK)
echo =======================================================

set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo.
echo [1/3] Building Signed Play Store Bundle (AAB) & Release APK...
call .\gradlew.bat bundleRelease assembleRelease

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Gradle build failed!
    exit /b %ERRORLEVEL%
)

echo.
echo [2/3] Syncing artifacts to root & server directory...
copy /y "app\build\outputs\bundle\release\app-release.aab" "Chatooz_PlayStore.aab"
copy /y "app\build\outputs\apk\release\app-release.apk" "chatooz_app.apk"
copy /y "app\build\outputs\apk\release\app-release.apk" "server\chatooz_app.apk"

echo.
echo [3/3] Build & Sync Complete!
echo -------------------------------------------------------
echo  Play Store Bundle : Chatooz_PlayStore.aab
echo  Admin Download APK: server\chatooz_app.apk
echo  Root Direct APK   : chatooz_app.apk
echo -------------------------------------------------------
echo.
echo Done!
