@echo off
setlocal
set "T=%LOCALAPPDATA%\Elghaliz Medicine"
if exist "%T%" rmdir /S /Q "%T%" >nul 2>&1
mkdir "%T%" >nul 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%~dp0ElghalizMedicine-App.zip' -DestinationPath $env:T -Force"
powershell -NoProfile -Command "$ws=New-Object -ComObject WScript.Shell; $s=$ws.CreateShortcut(([Environment]::GetFolderPath('Desktop'))+'\Elghaliz Medicine.lnk'); $s.TargetPath=$env:T+'\ElghalizMedicine.exe'; $s.WorkingDirectory=$env:T; $s.Save()"
start "" "%T%\ElghalizMedicine.exe"
