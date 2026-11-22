@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

set "NODE=node"
where node >nul 2>nul || set "NODE=C:\Users\naz\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe"

if not exist "dist\index.html" (
  echo [1/3] Building web app...
  "%NODE%" "node_modules\vite\bin\vite.js" build
)

if not exist "electron-dist" (
  if exist "electron-v45.3.0-win32-x64.zip" (
    echo [2/3] Extracting Electron runtime...
    powershell -NoProfile -Command "Expand-Archive -LiteralPath 'electron-v45.3.0-win32-x64.zip' -DestinationPath 'electron-dist' -Force"
  ) else (
    echo Electron runtime zip not found. Place electron-v45.3.0-win32-x64.zip in this folder first,
    echo or run: pnpm add -D electron electron-builder ^&^& pnpm dist
    pause
    exit /b 1
  )
)

echo [3/3] Packaging Windows app...
call "node_modules\.bin\electron-builder.cmd" --win --dir
echo.
echo Done. Output: release\win-unpacked\Elghaliz Medicine.exe
pause
endlocal
