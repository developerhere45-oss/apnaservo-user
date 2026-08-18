@echo off
set "PROJECT_DIR=%~dp0"
set "STUDIO_EXE=%ProgramFiles%\Android\Android Studio\bin\studio64.exe"

if not exist "%STUDIO_EXE%" (
  echo Android Studio was not found at:
  echo %STUDIO_EXE%
  pause
  exit /b 1
)

echo Opening the Gradle project folder:
echo %PROJECT_DIR%
echo.
echo Do not open MainActivity.java directly. Android Studio must open this folder.
start "" "%STUDIO_EXE%" "%PROJECT_DIR%"
