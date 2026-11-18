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
    [string]$DeviceId = "cb08bc57",
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$PublishExistingArtifact,
    [string]$ExistingApk,
    [string]$ExpectedSha256,
    [string]$ExpectedSignerSha256,
    [string]$SourceCommit,
    [ValidateSet('com.turni.calendario.setupqa', 'com.turni.calendario')]
    [string]$ExpectedPackage = 'com.turni.calendario.setupqa',
    [string]$Repository = 'Hellvisback/calendario-turni',
    [switch]$VerifyOnly
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot

# Publish a prebuilt APK without a second version bump or rebuild.
# The source bump, build and commit must already have been completed.
if ($PublishExistingArtifact) {
    if (-not $ExistingApk -or $ExpectedSha256 -notmatch '^[a-fA-F0-9]{64}$' -or
        $ExpectedSignerSha256 -notmatch '^[a-fA-F0-9]{64}$' -or
        $SourceCommit -notmatch '^[a-fA-F0-9]{40}$' -or
        $VersionName -notmatch '^\d+\.\d+\.\d+(?:-[a-zA-Z0-9.-]+)?$' -or
        $VersionCode -le 0 -or
        $Repository -notmatch '^[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+$') {
        throw 'Validated publishing requires APK/hash/certificate/source/version and version metadata.'
    }
    if ($ExpectedPackage -eq 'com.turni.calendario' -and
        $ExpectedSignerSha256 -ne 'cf09967400ac4931dbfa169b9bc23c6a8314ef0256285c3399f4912bee735772') {
        throw 'The personal package requires the original TurniSync signing certificate.'
    }
    $artifact = (Resolve-Path -LiteralPath $ExistingApk).Path
    if ((Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash -ne $ExpectedSha256) {
        throw 'APK checksum mismatch.'
    }
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
    if (-not $sdk) { throw 'ANDROID_HOME or ANDROID_SDK_ROOT is required.' }
    $exe = if ($IsWindows -or $env:OS -eq 'Windows_NT') { '.exe' } else { '' }
    $bat = if ($exe) { '.bat' } else { '' }
    $aapt = Join-Path $sdk ("build-tools/35.0.0/aapt" + $exe)
    $signer = Join-Path $sdk ("build-tools/35.0.0/apksigner" + $bat)
    $badging = (& $aapt dump badging $artifact 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $badging -notmatch "package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'") {
        throw 'Cannot verify APK metadata.'
    }
    $apkPackage = $matches[1]; $apkCode = [int]$matches[2]; $apkName = $matches[3]
    if ($apkPackage -ne $ExpectedPackage -or $apkCode -ne $VersionCode -or $apkName -ne $VersionName) {
        throw 'APK package/version mismatch.'
    }
    $certificates = (& $signer verify --print-certs $artifact 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $certificates -notmatch 'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]{64})') {
        throw 'APK signature verification failed.'
    }
    if ($matches[1] -ne $ExpectedSignerSha256) { throw 'Unexpected signing certificate.' }
    $branch = (& git -C $projectRoot rev-parse --abbrev-ref HEAD | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or ($branch -ne 'main' -and -not ($branch -eq 'HEAD' -and $env:GITHUB_ACTIONS -eq 'true'))) {
        throw 'Publish from main, or a detached GitHub Actions checkout only.'
    }
    & git -C $projectRoot merge-base --is-ancestor $SourceCommit HEAD
    if ($LASTEXITCODE -ne 0) { throw 'SourceCommit is not an ancestor of the checked-out source.' }
    $sourceGradle = (& git -C $projectRoot show "${SourceCommit}:android-app/app/build.gradle.kts" 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or $sourceGradle -notmatch "versionCode\s*=\s*$VersionCode\b" -or
        $sourceGradle -notmatch ('versionName\s*=\s*"' + [regex]::Escape($VersionName) + '"')) {
        throw 'The source snapshot does not describe this APK version.'
    }
    [pscustomobject]@{ package=$apkPackage; versionName=$apkName; versionCode=$apkCode; sha256=$ExpectedSha256; signer=$ExpectedSignerSha256; source=$SourceCommit } | ConvertTo-Json
    if ($VerifyOnly) { return }
    $remoteCommit = (& gh api "repos/$Repository/commits/$SourceCommit" --jq '.sha' | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $remoteCommit -ne $SourceCommit) { throw 'Source snapshot not found in the target repository.' }
    $tag = "v$VersionName"
    $releases = (& gh release list --repo $Repository --limit 100 --json tagName | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect releases.' }
    if (($releases | ConvertFrom-Json).tagName -contains $tag) { throw 'Release already exists; inspect it manually, nothing overwritten.' }
    $remoteTag = (& git -C $projectRoot ls-remote "https://github.com/$Repository.git" "refs/tags/$tag" | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $remoteTag) { throw 'Tag already exists or cannot be checked; nothing published.' }
    $output = Join-Path $projectRoot 'artifacts'
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    $versioned = Join-Path $output "app-release-v$VersionName.apk"
    if ([IO.Path]::GetFullPath($artifact) -ne [IO.Path]::GetFullPath($versioned)) {
        Copy-Item -LiteralPath $artifact -Destination $versioned -Force
    }
    if ((Get-FileHash -LiteralPath $versioned -Algorithm SHA256).Hash -ne $ExpectedSha256) { throw 'Packaged APK checksum mismatch.' }
    if (-not $ReleaseTitle) { $ReleaseTitle = "TurniSync $VersionName ($apkPackage)" }
    if (-not $ReleaseNotes) { throw 'Provide explicit release notes describing the package and update compatibility.' }
    $notesFile = [IO.Path]::GetTempFileName()
    try {
        [IO.File]::WriteAllText($notesFile, $ReleaseNotes, [Text.Encoding]::UTF8)
        & gh release create $tag $versioned --repo $Repository --target $SourceCommit --title $ReleaseTitle --notes-file $notesFile --prerelease
        if ($LASTEXITCODE -ne 0) { throw 'GitHub release creation failed.' }
    } finally {
        Remove-Item -LiteralPath $notesFile -Force -ErrorAction SilentlyContinue
    }
    return
}
if ($VerifyOnly -or $ExistingApk) { throw 'ExistingApk and VerifyOnly require PublishExistingArtifact.' }
$gradleFile = "$projectRoot\android-app\app\build.gradle.kts"
$packageJsonFile = "$projectRoot\package.json"
$exportScript = "$projectRoot\scripts\export_project.ps1"
$taskSdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$adbPath = Join-Path $taskSdkRoot 'platform-tools/adb.exe'

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
$betaApk = "$projectRoot\android-app\app\build\outputs\apk\beta\app-beta.apk"
$debugApk = "$projectRoot\android-app\app\build\outputs\apk\debug\app-debug.apk"

if ($newName -match 'beta' -and (Test-Path $betaApk)) {
    $apkOutputDir = "$projectRoot\android-app\app\build\outputs\apk\beta"
    $apkSource = $betaApk
    Write-Host "Rilevato pacchetto Beta ottimizzato: $betaApk" -ForegroundColor Cyan
} else {
    $apkOutputDir = "$projectRoot\android-app\app\build\outputs\apk\debug"
    $apkSource = $debugApk
}
$apkLatest = "$apkOutputDir\app-release-latest.apk"
$apkVersioned = "$apkOutputDir\app-release-v$newName.apk"

if (-not $SkipBuild) {
    Write-Host "[3/7] Compilazione APK (assembleBeta)..." -ForegroundColor Yellow
    if ([string]::IsNullOrWhiteSpace($JavaHome) -or !(Test-Path "$JavaHome/bin/java.exe")) {
        throw "Imposta JAVA_HOME su un JDK 17 o successivo, oppure usa -JavaHome."
    }
    $env:JAVA_HOME = $JavaHome
    Push-Location "$projectRoot\android-app"
    try {
        & .\gradlew.bat assembleBeta testDebugUnitTest
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
git add -- android-app scripts docs design .github .gitignore .gitattributes AGENTS.md README.md HANDOVER_NOTES.md HANDOVER_CODIUM.md package.json
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
  - Risultati dei test e delle verifiche documentati nei verbali della versione nel repository.
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
        if ($newName -match 'beta|alpha|rc') {
            & gh release create $tag @assetsToUpload --title $ReleaseTitle --notes-file $tempNotesFile --prerelease
        } else {
            & gh release create $tag @assetsToUpload --title $ReleaseTitle --notes-file $tempNotesFile --latest
        }
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
