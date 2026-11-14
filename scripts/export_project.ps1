[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$taskPrefix = $taskRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
$taskDestination = Join-Path $taskRoot 'Calendario_Turni_Export.zip'
$taskTemporary = Join-Path $taskRoot ('.source-export-' + [guid]::NewGuid().ToString('N') + '.zip')
$taskScopes = @('android-app','scripts','docs','design','esApp','stitch_assets','.github','.gitignore','.gitattributes','README.md','AGENTS.md','HANDOVER_NOTES.md','HANDOVER_CODIUM.md','package.json')
$taskFiles = & git -C $taskRoot ls-files -z --cached --others --exclude-standard -- @taskScopes
if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate project source files.' }
$taskNames = (($taskFiles -join "`n") -split "`0") | Where-Object { $_ } | Sort-Object -Unique
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskArchive = [IO.Compression.ZipFile]::Open($taskTemporary,[IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($taskName in $taskNames) {
        $taskPath = [IO.Path]::GetFullPath((Join-Path $taskRoot $taskName))
        if (!$taskPath.StartsWith($taskPrefix,[StringComparison]::OrdinalIgnoreCase)) { throw 'Export path escaped the project.' }
        if (Test-Path -LiteralPath $taskPath -PathType Leaf) {
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskArchive,$taskPath,('TurniSync/' + $taskName.Replace('\','/')),[IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    }
} finally { $taskArchive.Dispose() }
Move-Item -LiteralPath $taskTemporary -Destination $taskDestination -Force
Write-Host "Source archive created: $taskDestination"
