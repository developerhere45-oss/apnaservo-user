@echo off
set "STUDIO_EXE=%ProgramFiles%\Android\Android Studio\bin\studio64.exe"

if not exist "%STUDIO_EXE%" (
  echo Android Studio was not found at:
  echo %STUDIO_EXE%
  echo.
  echo Open Android Studio manually and choose this folder:
  echo %~dp0
  pause
  exit /b 1
)

start "" "%STUDIO_EXE%" "%~dp0"
