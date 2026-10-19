# Keep Windows PowerShell's native-output decoder aligned with logView's UTF-8 bytes.
[CmdletBinding()]
param(
    [Parameter(Position = 0)] [string] $KeyPath,
    [string] $Database = 'runtime/cofre.db',
    [string] $JavaPath,
    [switch] $Check
)
$ErrorActionPreference = 'Stop'
if (-not $Check -and [string]::IsNullOrWhiteSpace($KeyPath)) {
    throw 'Supply the encrypted administrator key path. The passphrase is requested securely by logView.'
}
if (-not $JavaPath) {
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
        $JavaPath = Join-Path $env:JAVA_HOME 'bin/java.exe'
    } elseif (Test-Path -LiteralPath 'C:\Program Files\Java\jdk-24\bin\java.exe') {
        $JavaPath = 'C:\Program Files\Java\jdk-24\bin\java.exe'
    } else {
        $JavaPath = (Get-Command java.exe -ErrorAction Stop).Source
    }
}
$projectDirectory = Split-Path -Parent $PSScriptRoot
# chcp alone can leave PowerShell's cached decoder unchanged. These are session-only settings.
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
Push-Location -LiteralPath $projectDirectory
try {
    $jar = Join-Path $projectDirectory 'target/logView.jar'
    if ($Check) { & $JavaPath -jar $jar --check }
    else { & $JavaPath -jar $jar $KeyPath --database $Database }
    $viewerResult = $LASTEXITCODE
} finally { Pop-Location }
exit $viewerResult
