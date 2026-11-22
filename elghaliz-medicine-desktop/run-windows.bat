@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

set "NODE=node"
where node >nul 2>nul
if errorlevel 1 (
  if exist "C:\Users\naz\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe" (
    set "NODE=C:\Users\naz\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe"
  ) else (
    echo Node.js not found. Please install Node.js from https://nodejs.org
    pause
    exit /b 1
  )
)

if not exist "dist\index.html" (
  echo Building the app for the first time...
  "%NODE%" "node_modules\vite\bin\vite.js" build
)

echo Starting Elghaliz Medicine...
start "ElghalizServer" /min "%NODE%" server.mjs
timeout /t 2 >nul

set "EDGE=C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
if not exist "%EDGE%" set "EDGE=C:\Program Files\Microsoft\Edge\Application\msedge.exe"
if exist "%EDGE%" (
  start "" "%EDGE%" --app=http://localhost:8787 --window-size=1366,900
) else (
  start "" http://localhost:8787
)
endlocal

