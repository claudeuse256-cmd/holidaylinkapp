@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-gradle-wrapper.ps1"
if errorlevel 1 (
  echo.
  echo Wrapper setup failed. Check your internet connection and try again.
  pause
  exit /b 1
)
pause
