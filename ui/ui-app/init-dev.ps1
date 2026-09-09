Write-Host "----"
Write-Host "Initializing development environment for UI-only development."
Write-Host "----"

$ConfigType = $args[0]
if ([string]::IsNullOrEmpty($ConfigType)) {
    $ConfigType = "local"
}

Copy-Item "configs\version.js" "version.js" -Force
Copy-Item "configs\config-$ConfigType.js" "config.js" -Force

Write-Host "Done.  Try:  'npm run dev'"