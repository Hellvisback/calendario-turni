$ErrorActionPreference = "Stop"

$sourceDir = "c:\Users\marco\Antigravity Progetti\Calediario Turni"
$tempExportDir = "c:\Users\marco\Antigravity Progetti\Calediario_Turni_Export_Temp"
$zipDestination = "$sourceDir\Calendario_Turni_Export.zip"

Write-Host "Creating clean export package for Codium / Qodo..."

if (Test-Path $tempExportDir) {
    Remove-Item -Recurse -Force $tempExportDir
}
New-Item -ItemType Directory -Path "$tempExportDir\Calediario Turni" -Force | Out-Null

# Robocopy to copy all project files, skipping heavy build folders and archives
& robocopy $sourceDir "$tempExportDir\Calediario Turni" /E /XD build .gradle .idea /XF *.zip *.apk /NFL /NDL /NJH /NJS /nc /ns /np
if ($LASTEXITCODE -ge 8) {
    Write-Warning "Robocopy exited with code $LASTEXITCODE"
}

# Remove existing zip file
if (Test-Path $zipDestination) { Remove-Item -Force $zipDestination }

Write-Host "Compressing archive to $zipDestination..."
Compress-Archive -Path "$tempExportDir\Calediario Turni\*" -DestinationPath $zipDestination -CompressionLevel Optimal

# Clean up temp directory
Remove-Item -Recurse -Force $tempExportDir

Write-Host "Export package created successfully at $zipDestination!"
Get-Item $zipDestination | Select-Object FullName, @{Name="SizeMB";Expression={[math]::Round($_.Length/1MB, 2)}}, LastWriteTime | Format-Table -AutoSize

