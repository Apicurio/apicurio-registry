param(
    [string]$ConfigType = "local"
)

Write-Host "----"
Write-Host "Initializing development environment for UI-only development."
Write-Host "----"

Copy-Item -LiteralPath "configs/version.js" -Destination "version.js" -Force -ErrorAction Stop
Copy-Item -LiteralPath "configs/config-$ConfigType.js" -Destination "config.js" -Force -ErrorAction Stop

Write-Host "Done.  Try:  'npm run dev'"
