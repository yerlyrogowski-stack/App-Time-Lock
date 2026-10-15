param([string[]]$Tasks = @(':core:test', ':app:assembleDebug', ':app:lintDebug'))
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'D:\PyCharm 2023.2\jbr' }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = 'D:\.codex\tools\android-sdk' }
$env:ANDROID_USER_HOME = 'D:\.codex\config\android-user'
$env:GRADLE_USER_HOME = 'D:\.codex\cache\gradle'
$env:GRADLE_OPTS = '-Djava.io.tmpdir=D:\.codex\tmp'
$env:TEMP = 'D:\.codex\tmp'
$env:TMP = $env:TEMP
Push-Location $PSScriptRoot
try {
    $launcher = if (Test-Path -LiteralPath 'D:\.codex\tools\gradle-8.13\bin\gradle.bat') { 'D:\.codex\tools\gradle-8.13\bin\gradle.bat' } else { "$PSScriptRoot\gradlew.bat" }
    $networkArgs = @()
    $proxyConfig = Get-ItemProperty -LiteralPath 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -ErrorAction SilentlyContinue
    if ($proxyConfig.ProxyEnable -eq 1 -and $proxyConfig.ProxyServer -match '^([^:;=]+):(\d+)$') {
        $networkArgs = @("-Dhttp.proxyHost=$($Matches[1])", "-Dhttp.proxyPort=$($Matches[2])", "-Dhttps.proxyHost=$($Matches[1])", "-Dhttps.proxyPort=$($Matches[2])")
    }
    & $launcher @Tasks @networkArgs --max-workers=4 --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle exited with $LASTEXITCODE" }
} finally { Pop-Location }
