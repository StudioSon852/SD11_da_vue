@echo off
setlocal
cd /d "%~dp0"
if not exist package.json (
  echo [ERROR] package.json not found. Run this file from the extracted SD11 project root.
  pause
  exit /b 2
)
where node >nul 2>nul || (echo [ERROR] Node.js is not installed or not on PATH.& pause & exit /b 3)
where npm >nul 2>nul || (echo [ERROR] npm is not installed or not on PATH.& pause & exit /b 4)
if not exist node_modules\vite\bin\vite.js (
  echo Installing Vue frontend dependencies...
  call npm.cmd install || (echo [ERROR] npm install failed.& pause & exit /b 5)
)
echo Starting Vue frontend at http://localhost:5173
call npm.cmd run dev:web
