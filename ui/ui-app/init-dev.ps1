param(
    [string]$ConfigType = "local"
)

Write-Host "----"
Write-Host "Initializing development environment for UI-only development."
Write-Host "----"

Copy-Item -Path "configs/version.js" -Destination "version.js" -Force
Copy-Item -Path "configs/config-$ConfigType.js" -Destination "config.js" -Force

Write-Host "Done.  Try:  'npm run dev'"
