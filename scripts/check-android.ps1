[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string[]]$Tasks = @('assembleBeta', 'testDebugUnitTest')
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($JavaHome) -or !(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'Set JAVA_HOME to JDK 17 or pass -JavaHome <jdk17>.'
}
$env:JAVA_HOME = $JavaHome
if ([string]::IsNullOrWhiteSpace($env:ANDROID_HOME) -and [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
    $taskDefaultSdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
    if (Test-Path -LiteralPath $taskDefaultSdk) { $env:ANDROID_HOME = $taskDefaultSdk }
}
if ($env:TURNISYNC_WINDOWS_SYSTEM_TRUST -eq '1') {
    $env:JAVA_TOOL_OPTIONS = (($env:JAVA_TOOL_OPTIONS + ' -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE').Trim())
}
$taskHeap = if ($env:TURNISYNC_GRADLE_HEAP) { $env:TURNISYNC_GRADLE_HEAP } else { '3g' }
Push-Location (Join-Path $taskRoot 'android-app')
try {
    & .\gradlew.bat --no-daemon --console=plain "-Dorg.gradle.jvmargs=-Xmx$taskHeap -XX:+UseParallelGC -Dfile.encoding=UTF-8" '-Pkotlin.compiler.execution.strategy=in-process' @Tasks
    $taskExit = $LASTEXITCODE
} finally { Pop-Location }
exit $taskExit
