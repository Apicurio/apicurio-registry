@echo off
setlocal

set CONFIG_TYPE=local
if not "%~1"=="" set CONFIG_TYPE=%~1

echo ----
echo Initializing development environment for UI-only development.
echo ----

copy /Y configs\version.js version.js >nul
copy /Y configs\config-%CONFIG_TYPE%.js config.js >nul

echo Done.  Try:  'npm run dev'
