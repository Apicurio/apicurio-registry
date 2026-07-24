@echo off
setlocal
if not defined ACR_INSTALL_PATH set "ACR_INSTALL_PATH=%USERPROFILE%\.apicurio\apicurio-registry-cli"
set "SCRIPT_DIR=%~dp0"
if exist "%SCRIPT_DIR%acr_runner.exe" (
    set "ACR_CURRENT_HOME=%SCRIPT_DIR:~0,-1%"
) else if defined ACR_HOME if exist "%ACR_HOME%\acr_runner.exe" (
    set "ACR_CURRENT_HOME=%ACR_HOME%"
)
if not defined ACR_CURRENT_HOME (
    echo [Error] Apicurio Registry CLI binary not found. Exiting. 1>&2
    exit /b 1
)
"%ACR_CURRENT_HOME%\acr_runner.exe" %*
endlocal & exit /b %ERRORLEVEL%
