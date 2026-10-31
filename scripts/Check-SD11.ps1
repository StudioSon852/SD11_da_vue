$ErrorActionPreference = 'Continue'
Write-Host "=== SD_11 environment check ===" -ForegroundColor Cyan
Write-Host "Project: $((Get-Location).Path)"
if (-not (Test-Path '.\package.json')) { Write-Host 'ERROR: package.json not found. Open PowerShell in the project root (the folder that directly contains package.json).' -ForegroundColor Red; exit 2 }
Write-Host "[OK] package.json found" -ForegroundColor Green
node --version
npm --version
java -version
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { Write-Host 'ERROR: mvn not on PATH. Install Maven 3.9.16 and add its bin directory to PATH.' -ForegroundColor Red } else { mvn -version }
if (-not (Test-Path '.\backend\.env')) { Write-Host 'WARNING: backend\.env missing. Copy backend\.env.example to backend\.env and set DB_PASSWORD/DB_SERVER/DB_NAME.' -ForegroundColor Yellow }
else {
  $lines = Get-Content '.\backend\.env' | Where-Object { $_ -match '^\s*(DB_SERVER|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|DB_JDBC_URL)\s*=' }
  foreach ($line in $lines) { if ($line -match '^\s*DB_PASSWORD\s*=') { $v = $line -replace '^\s*DB_PASSWORD\s*=.*$', 'DB_PASSWORD=***'; Write-Host $v } else { Write-Host $line } }
}
foreach ($port in @(1433, 8080, 5173)) {
  $listener = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($listener) { Write-Host "Port $port is listening (PID $($listener.OwningProcess))" -ForegroundColor Green }
  else { Write-Host "Port $port is not listening" -ForegroundColor Yellow }
}
try {
  $health = Invoke-RestMethod -Uri 'http://localhost:8080/api/health' -TimeoutSec 5
  $health | ConvertTo-Json -Depth 5
} catch {
  if ($_.Exception.Response) {
    try { $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); Write-Host $reader.ReadToEnd() -ForegroundColor Yellow } catch {}
  } else { Write-Host 'API health endpoint not responding yet. Start backend with npm run dev:api.' -ForegroundColor Yellow }
}
Write-Host '=== End of check ===' -ForegroundColor Cyan
