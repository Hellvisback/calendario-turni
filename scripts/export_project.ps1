$ErrorActionPreference = "Stop"

$sourceDir = "c:\Users\marco\Antigravity Progetti\Calediario Turni"
$tempExportDir = "c:\Users\marco\Antigravity Progetti\Calediario_Turni_Export_Temp"
$zipDestination1 = "C:\Users\marco\OneDrive\Calendario_Turni_Antigravity_Export.zip"
$zipDestination2 = "c:\Users\marco\Antigravity Progetti\Calediario Turni\Calendario_Turni_Antigravity_Export.zip"

Write-Host "Creating export package..."

if (Test-Path $tempExportDir) {
    Remove-Item -Recurse -Force $tempExportDir
}
New-Item -ItemType Directory -Path "$tempExportDir\Calediario Turni" -Force | Out-Null

# Robocopy to copy all project files, skipping heavy build folders and zip
& robocopy $sourceDir "$tempExportDir\Calediario Turni" /E /XD build .gradle .idea /XF *.zip *.apk /NFL /NDL /NJH /NJS /nc /ns /np
if ($LASTEXITCODE -ge 8) {
    Write-Warning "Robocopy exited with code $LASTEXITCODE"
}

# Copy latest compiled APK into export root for instant installation on new PC/phone
$latestApk = "$sourceDir\android-app\app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $latestApk) {
    Copy-Item $latestApk "$tempExportDir\Calediario Turni\app-release-latest.apk" -Force
}

# Remove existing zip files
if (Test-Path $zipDestination1) { Remove-Item -Force $zipDestination1 }
if (Test-Path $zipDestination2) { Remove-Item -Force $zipDestination2 }

Write-Host "Compressing archive to $zipDestination1..."
Compress-Archive -Path "$tempExportDir\Calediario Turni" -DestinationPath $zipDestination1 -CompressionLevel Optimal

Write-Host "Copying to local project root $zipDestination2..."
Copy-Item $zipDestination1 $zipDestination2 -Force

# Clean up temp directory
Remove-Item -Recurse -Force $tempExportDir

Write-Host "Export package created successfully!"
Get-Item $zipDestination1, $zipDestination2 | Select-Object FullName, Length, LastWriteTime | Format-Table -AutoSize
