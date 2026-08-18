@echo off
cd /d "%~dp0"

echo FixMate Android repair
echo ----------------------
echo.
echo 1. Freeing Metro/Expo port 8081 if it is stuck...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ports = Get-NetTCPConnection -State Listen -LocalPort 8081 -ErrorAction SilentlyContinue; foreach ($port in $ports) { Stop-Process -Id $port.OwningProcess -Force -ErrorAction SilentlyContinue }"

echo.
echo 2. Closing stale emulator processes if port 5554 is broken...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Process emulator,qemu-system-x86_64,qemu-system-aarch64 -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue"

echo.
echo 3. Restarting Android Debug Bridge on port 5037...
adb kill-server
adb start-server
adb devices

echo.
echo 4. Building Android app...
call gradlew.bat clean assembleDebug lintDebug

echo.
echo Done. If build succeeded, open Android Studio using this folder only:
echo %~dp0
pause
