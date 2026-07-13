@echo off
setlocal

set CONFIG_TYPE=local
if not "%~1"=="" set CONFIG_TYPE=%~1

echo ----
echo Initializing development environment for UI-only development.
echo ----

copy /Y "configs\version.js" "version.js" >nul || (echo Failed to copy configs\version.js to version.js & exit /b 1)
copy /Y "configs\config-%CONFIG_TYPE%.js" "config.js" >nul || (echo Failed to copy configs\config-%CONFIG_TYPE%.js to config.js & exit /b 1)

echo Done.  Try:  'npm run dev'
