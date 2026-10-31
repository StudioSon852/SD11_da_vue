@echo off
setlocal
cd /d "%~dp0"
if not exist package.json (
  echo [ERROR] package.json not found. Run this file from the extracted SD_11 project.
  pause
  exit /b 2
)
if not exist backend\.env (
  echo [ERROR] backend\.env not found.
  echo Copy backend\.env.example to backend\.env and set your real SQL Server address and password first.
  pause
  exit /b 3
)
where node >nul 2>nul || (echo [ERROR] Node.js is not installed or not on PATH.& pause & exit /b 4)
where npm >nul 2>nul || (echo [ERROR] npm is not installed or not on PATH.& pause & exit /b 5)
where mvn >nul 2>nul || (echo [ERROR] Maven is not on PATH. Install Maven 3.9.16 and add its bin folder to PATH.& pause & exit /b 6)
if not exist node_modules\vite\bin\vite.js (
  echo Installing frontend packages first...
  call npm install || (echo [ERROR] npm install failed.& pause & exit /b 7)
)
start "SD_11 API - Spring Boot" powershell -NoExit -Command "Set-Location -LiteralPath '%~dp0'; npm run dev:api"
start "SD_11 WEB - Vue Vite" powershell -NoExit -Command "Set-Location -LiteralPath '%~dp0'; npm run dev:web"
echo Both terminals were opened. Keep them open while using the website.
echo Website: http://localhost:5173
echo API health: http://localhost:8080/api/health
pause
