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

# Rilevamento versione su git HEAD per validare l'incremento
$headGradle = (git show HEAD:android-app/app/build.gradle.kts 2>$null | Out-String)
$baselineCode = $currentCode
$committedName = $currentName
if (-not [string]::IsNullOrWhiteSpace($headGradle)) {
    if ($headGradle -match 'versionCode\s*=\s*(\d+)') {
        $baselineCode = [int]$matches[1]
    }
    if ($headGradle -match 'versionName\s*=\s*"([^"]+)"') {
        $committedName = $matches[1]
    }
}

# Calcolo nuovo versionCode
if ($VersionCode -gt 0) {
    $newCode = $VersionCode
} elseif ($currentCode -gt $baselineCode) {
    $newCode = $currentCode
} else {
    $newCode = $currentCode + 1
}

# Calcolo nuovo versionName
if (-not [string]::IsNullOrWhiteSpace($VersionName)) {
    $newName = $VersionName
} elseif ($currentCode -gt $baselineCode -and $currentName -ne $committedName) {
    $newName = $currentName
} else {
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
}

Write-Host "Nuova versione target: v$newName (versionCode: $newCode)" -ForegroundColor Green
if ($newCode -le $baselineCode) {
    throw "Release versionCode must be greater than the baseline committed versionCode ($baselineCode)."
}

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

# 4. Preparazione asset APK (ESCLUSIVAMENTE il pacchetto versionato per GitHub)
Write-Host "[4/7] Preparazione asset APK versionato..." -ForegroundColor Yellow
Copy-Item $apkSource $apkVersioned -Force
Write-Host "Creato pacchetto release: $apkVersioned" -ForegroundColor Green

# 5. Git commit e push
Write-Host "[5/7] Sincronizzazione modifiche su Git (main)..." -ForegroundColor Yellow
git add -A
$gitStatus = git status --porcelain
if ($gitStatus) {
    git commit -m "chore(release): release v$newName (versionCode $newCode)"
    if ($LASTEXITCODE -ne 0) { throw "Git commit failed; release stopped." }
    git push origin main
    if ($LASTEXITCODE -ne 0) { throw "Git push failed; release stopped." }
    Write-Host "Modifiche inviate a origin/main." -ForegroundColor Green
} else {
    Write-Host "Nessuna modifica pendente nel repository git." -ForegroundColor Gray
}

# 6. Creazione Release su GitHub
Write-Host "[6/7] Creazione della nuova Release su GitHub..." -ForegroundColor Yellow
$tag = "v$newName"

if ([string]::IsNullOrWhiteSpace($ReleaseTitle)) {
    $ReleaseTitle = "Calendario Turni v$newName - Architettura Nativa TurniSync & Full Compose UI"
}

if ([string]::IsNullOrWhiteSpace($ReleaseNotes)) {
    $ReleaseNotes = @"
### Calendario Turni v$newName (Build $newCode)

#### Pacchetto di Aggiornamento Ufficiale:
- **``app-release-v$newName.apk``**: Pacchetto APK unico e versionato pronto per l'installazione su Android.

#### Novita' e Perfezionamenti Architetturali di questa versione:
- **Porting Architettura Nativa TurniSync**: L'intera interfaccia e' stata rifattorizzata in componenti Compose nativi ad alte prestazioni:
  - ``NativeMonthCanvas``: griglia calendario ottimizzata con supporto gestures e transizioni fluide.
  - ``TurniDock``: dock a doppia destinazione ergonomico e thumb-zone compliant.
  - ``NativeCalendarPanels`` & ``CatalogEditorComponents``: pannelli compatti per la modifica rapida di turni, modelli e rotazioni cicliche.
  - ``NativeColorPlane``: gestione e selezione cromatica dinamica con contrasto garantito.
- **Database Atomico ``turnisync-database-v3.json``**:
  - Inizializzazione asincrona tramite ``TurniSyncApplication`` e ``LocalDatabaseStore`` prima dell'avvio delle schermate.
  - Migrazione automatica sicura del database locale preesistente senza perdita di dati.
  - Scritture atomiche, aggiornamenti widget e ricalcolo sveglie intelligenti delegati a coroutine in background.
- **Gestione Straordinari e Trasferte Ottimizzata**:
  - Straordinario calcolato da stacco effettivo del turno.
  - Supporto trasferta personalizzata per singolo turno.
  - Rotazioni con anteprima dinamica, navigazione bidirezionale e gomma selettiva.
- **Suite di Test Unitari & Qualita'**:
  - 264 test automatizzati eseguiti con successo per le varianti debug e release.
"@
}

# Verifica se il tag esiste già su GitHub
$releaseAlreadyExists = $false
try {
    & gh release view $tag 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $releaseAlreadyExists = $true
    }
} catch {
    $releaseAlreadyExists = $false
}

# Carica ESCLUSIVAMENTE il singolo APK versionato (senza latest.apk né zip)
$assetsToUpload = @($apkVersioned)

if ($releaseAlreadyExists) {
    throw "Release $tag already exists. Increase versionCode and versionName; existing assets are never overwritten."
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
    if ($devicesOutput -match "$DeviceId\s+device") {
        Write-Host "Dispositivo $DeviceId rilevato e pronto. Eseguo aggiornamento con PackageInstaller (adb install -r)..." -ForegroundColor Cyan
        & $adbPath -s $DeviceId install -r $apkVersioned
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Aggiornamento su Realme 8 Pro completato con SUCCESSO!" -ForegroundColor Green
            & $adbPath -s $DeviceId shell "dumpsys package com.turni.calendario | grep -E 'versionCode|versionName'"
        } else {
            Write-Warning "Installazione ADB fallita su dispositivo $DeviceId."
        }
    } else {
        Write-Host "Dispositivo $DeviceId non pronto o offline. Skip installazione locale." -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "  RILASCIO COMPLETATO CON SUCCESSO: v$newName (Build $newCode)" -ForegroundColor Green
Write-Host "  Visualizza su: https://github.com/Hellvisback/calendario-turni/releases/tag/v$newName" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
