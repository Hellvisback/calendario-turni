<#
.SYNOPSIS
    Automated Release and GitHub Publisher for Calendario Turni.
.DESCRIPTION
    Automates the entire release cycle:
    1. Bumps versionCode and versionName in build.gradle.kts and package.json.
    2. Compiles assembleDebug using the correct JDK.
    3. Produces versioned APK and latest APK assets.
    4. Regenerates the project export ZIP package.
    5. Commits and pushes changes to git main.
    6. Creates a real GitHub Release (gh release create vX.Y.Z) with Italian release notes.
    7. Optionally updates the app on connected Android physical device via ADB.
.EXAMPLE
    .\scripts\release.ps1
    .\scripts\release.ps1 -VersionName "1.1.0" -VersionCode 2
    .\scripts\release.ps1 -Patch
#>

[CmdletBinding()]
param(
    [string]$VersionName,
    [int]$VersionCode = 0,
    [switch]$Patch,
    [string]$ReleaseTitle,
    [string]$ReleaseNotes,
    [switch]$SkipBuild,
    [switch]$SkipDeviceInstall,
    [string]$DeviceId = "cb08bc57"
)

$ErrorActionPreference = "Stop"

$projectRoot = "c:\Users\marco\Antigravity Progetti\Calediario Turni"
$gradleFile = "$projectRoot\android-app\app\build.gradle.kts"
$packageJsonFile = "$projectRoot\package.json"
$exportScript = "$projectRoot\scripts\export_project.ps1"
$adbPath = "C:\Users\marco\AppData\Local\Android\Sdk\platform-tools\adb.exe"

Set-Location $projectRoot

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  CALENDARIO TURNI - PROCEDURA DI RILASCIO AUTOMATICA     " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Verifica autenticazione GitHub CLI (gh)
Write-Host "[1/7] Verifica autenticazione GitHub CLI..." -ForegroundColor Yellow
$ghAuth = gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error "GitHub CLI non e' autenticato! Esegui prima 'gh auth login'."
    exit 1
}
Write-Host "GitHub CLI autenticato con successo." -ForegroundColor Green

# 2. Lettura e calcolo nuova versione
Write-Host "[2/7] Analisi versione corrente in build.gradle.kts..." -ForegroundColor Yellow
$gradleContent = Get-Content $gradleFile -Raw

if ($gradleContent -match 'versionCode\s*=\s*(\d+)') {
    $currentCode = [int]$matches[1]
} else {
    Write-Error "Impossibile trovare versionCode in $gradleFile"
    exit 1
}

if ($gradleContent -match 'versionName\s*=\s*"([^"]+)"') {
    $currentName = $matches[1]
} else {
    Write-Error "Impossibile trovare versionName in $gradleFile"
    exit 1
}

Write-Host "Versione attuale: versionName = '$currentName', versionCode = $currentCode" -ForegroundColor Gray

# Calcolo nuovo versionCode
if ($VersionCode -gt 0) {
    $newCode = $VersionCode
} else {
    $newCode = $currentCode + 1
}

# Calcolo nuovo versionName
if ([string]::IsNullOrWhiteSpace($VersionName)) {
    $parts = $currentName.Split('.')
    if ($parts.Length -eq 3) {
        $major = [int]$parts[0]
        $minor = [int]$parts[1]
        $rev   = [int]$parts[2]
        if ($Patch) {
            $rev++
        } else {
            $minor++
            $rev = 0
        }
        $newName = "$major.$minor.$rev"
    } else {
        $newName = "$currentName.1"
    }
} else {
    $newName = $VersionName
}

Write-Host "Nuova versione target: v$newName (versionCode: $newCode)" -ForegroundColor Green

# Aggiornamento build.gradle.kts se necessario
if ($currentCode -ne $newCode -or $currentName -ne $newName) {
    Write-Host "Aggiornamento di $gradleFile..." -ForegroundColor Gray
    $updatedGradle = $gradleContent -replace 'versionCode\s*=\s*\d+', "versionCode = $newCode"
    $updatedGradle = $updatedGradle -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$newName`""
    Set-Content -Path $gradleFile -Value $updatedGradle -NoNewline
    Write-Host "build.gradle.kts aggiornato." -ForegroundColor Green
}

# Aggiornamento package.json se presente
if (Test-Path $packageJsonFile) {
    $pkgContent = Get-Content $packageJsonFile -Raw
    $pkgContent = $pkgContent -replace '"version":\s*"[^"]+"', "`"version`": `"$newName`""
    Set-Content -Path $packageJsonFile -Value $pkgContent -NoNewline
    Write-Host "package.json aggiornato a $newName." -ForegroundColor Green
}

# 3. Compilazione APK
$apkOutputDir = "$projectRoot\android-app\app\build\outputs\apk\debug"
$apkSource = "$apkOutputDir\app-debug.apk"
$apkLatest = "$apkOutputDir\app-release-latest.apk"
$apkVersioned = "$apkOutputDir\app-release-v$newName.apk"

if (-not $SkipBuild) {
    Write-Host "[3/7] Compilazione APK (assembleDebug)..." -ForegroundColor Yellow
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jre"
    Push-Location "$projectRoot\android-app"
    try {
        & .\gradlew.bat assembleDebug
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Compilazione Gradle fallita con codice $LASTEXITCODE"
            exit 1
        }
    } finally {
        Pop-Location
    }
    Write-Host "Compilazione completata con successo!" -ForegroundColor Green
} else {
    Write-Host "[3/7] Skip compilazione richiesto. Uso APK esistente." -ForegroundColor Gray
}

if (-not (Test-Path $apkSource)) {
    Write-Error "File APK $apkSource non trovato!"
    exit 1
}

# 4. Preparazione asset APK
Write-Host "[4/7] Preparazione asset APK per il rilascio..." -ForegroundColor Yellow
Copy-Item $apkSource $apkLatest -Force
Copy-Item $apkSource $apkVersioned -Force
Write-Host "Creati:" -ForegroundColor Gray
Write-Host " - $apkLatest" -ForegroundColor Gray
Write-Host " - $apkVersioned" -ForegroundColor Gray

# Esportazione pacchetto ZIP se lo script esiste
$zipExport = "$projectRoot\Calendario_Turni_Antigravity_Export.zip"
if (Test-Path $exportScript) {
    Write-Host "Aggiornamento pacchetto ZIP di esportazione..." -ForegroundColor Gray
    try {
        & powershell -ExecutionPolicy Bypass -File $exportScript | Out-Null
        Write-Host "Pacchetto ZIP aggiornato." -ForegroundColor Green
    } catch {
        Write-Warning "Impossibile aggiornare ZIP di esportazione: $_"
    }
}

# 5. Git commit e push
Write-Host "[5/7] Sincronizzazione modifiche su Git (main)..." -ForegroundColor Yellow
git add -A
$gitStatus = git status --porcelain
if ($gitStatus) {
    git commit -m "chore(release): release v$newName (versionCode $newCode)"
    git push origin main
    Write-Host "Modifiche inviate a origin/main." -ForegroundColor Green
} else {
    Write-Host "Nessuna modifica pendente nel repository git." -ForegroundColor Gray
}

# 6. Creazione Release su GitHub
Write-Host "[6/7] Creazione della nuova Release su GitHub..." -ForegroundColor Yellow
$tag = "v$newName"

if ([string]::IsNullOrWhiteSpace($ReleaseTitle)) {
    $ReleaseTitle = "Calendario Turni v$newName - Release Stabile"
}

if ([string]::IsNullOrWhiteSpace($ReleaseNotes)) {
    $ReleaseNotes = @"
### Calendario Turni v$newName (Build $newCode)

#### Pacchetti di Installazione Disponibili:
- **`app-release-latest.apk`**: Pacchetto APK principale sempre aggiornato all'ultima versione.
- **`app-release-v$newName.apk`**: Pacchetto APK specifico per questa release.
- **`Calendario_Turni_Antigravity_Export.zip`**: Archivio completo di codice sorgente e risorse.

#### Novita' Principali:
- **Suite Widget Android (Material 3 Expressive)**: 4 AppWidget per schermata home (Panorama 14 giorni, Oggi e Domani, Striscia Settimanale, Mini Griglia Mensile) con pulsanti di navigazione interattivi mese/settimana e contrasto visivo ottimizzato.
- **Importazione Turni Multimodale con Gemini AI & Prompt Maker**: Riconoscimento istantaneo di tabelloni orari da immagini e PDF, con sanificazione avanzata del testo e assistente prompt per AI esterne.
- **Straordinari Dettagliati per Singolo Turno**: Calcolo ore effettive di stacco e straordinario da timbratura reale e inserimento manuale con incrementi rapidi ±15min e ±1h.
- **Sveglie Intelligenti & Reperibilita' Preventiva**: Sveglia serale il giorno precedente per i turni in reperibilita' e allarmi sincronizzati con i turni.
- **Gestione Lavoro Universale & Allegato CCNL in PDF**: Personalizzazione profili di lavoro semplici o avanzati, e visualizzatore integrato per il proprio contratto collettivo.
- **Esportazione PDF ad Alta Risoluzione**: Prospetto mensile ed annuale rinnovato in stile SuperShift, esportabile e condivisibile in un tap.
- **Sincronizzazione Google Calendar**: Gestione sicura con cooldown di sicurezza e overlay animato dal basso.
"@
}

# Verifica se il tag esiste già su GitHub
$tagExists = gh release view $tag 2>&1
$assetsToUpload = @($apkLatest, $apkVersioned)
if (Test-Path $zipExport) {
    $assetsToUpload += $zipExport
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "La release per il tag $tag esiste gia'. Aggiornamento asset con --clobber..." -ForegroundColor Yellow
    foreach ($asset in $assetsToUpload) {
        gh release upload $tag $asset --clobber
    }
} else {
    Write-Host "Creazione nuova release $tag su GitHub..." -ForegroundColor Yellow
    # Scrivi le note in un file temporaneo per preservare accenti e markdown
    $tempNotesFile = [System.IO.Path]::GetTempFileName()
    [System.IO.File]::WriteAllText($tempNotesFile, $ReleaseNotes, [System.Text.Encoding]::UTF8)
    try {
        & gh release create $tag @assetsToUpload --title $ReleaseTitle --notes-file $tempNotesFile --latest
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Creazione GitHub release fallita con codice $LASTEXITCODE"
            exit 1
        }
    } finally {
        if (Test-Path $tempNotesFile) { Remove-Item -Force $tempNotesFile }
    }
}
Write-Host "Release pubblicata con successo su GitHub!" -ForegroundColor Green

# 7. Test e aggiornamento su dispositivo fisico collegato (se presente)
Write-Host "[7/7] Verifica dispositivo fisico ADB..." -ForegroundColor Yellow
if (-not $SkipDeviceInstall -and (Test-Path $adbPath)) {
    $devicesOutput = & $adbPath devices
    if ($devicesOutput -match $DeviceId) {
        Write-Host "Dispositivo $DeviceId rilevato. Eseguo aggiornamento con PackageInstaller (adb install -r)..." -ForegroundColor Cyan
        & $adbPath -s $DeviceId install -r $apkLatest
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Aggiornamento su Realme 8 Pro completato con SUCCESSO!" -ForegroundColor Green
            & $adbPath -s $DeviceId shell "dumpsys package com.turni.calendario | grep -E 'versionCode|versionName'"
        } else {
            Write-Warning "Installazione ADB fallita su dispositivo $DeviceId."
        }
    } else {
        Write-Host "Dispositivo $DeviceId non connesso. Skip installazione locale." -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "  RILASCIO COMPLETATO CON SUCCESSO: v$newName (Build $newCode)" -ForegroundColor Green
Write-Host "  Visualizza su: https://github.com/Hellvisback/calendario-turni/releases/tag/v$newName" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
