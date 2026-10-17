# Project-local toolchain: no machine-wide PATH or registry changes.
[CmdletBinding()]
param(
    [string] $JdkHome = $env:JAVA_HOME,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $MavenArgs = @('verify')
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsRoot = Join-Path $projectRoot '.tools'
$mavenVersion = '3.9.9'
if (-not $JdkHome) {
    $candidate = 'C:\Program Files\Java\jdk-24'
    if (Test-Path -LiteralPath (Join-Path $candidate 'bin/javac.exe')) { $JdkHome = $candidate }
}
if (-not $JdkHome -or -not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin/javac.exe'))) {
    throw 'Set JAVA_HOME or pass -JdkHome pointing to a JDK 17 or newer.'
}
$mavenHome = Join-Path $toolsRoot "apache-maven-$mavenVersion"
$mavenCommand = Join-Path $mavenHome 'bin/mvn.cmd'
if (-not (Test-Path -LiteralPath $mavenCommand)) {
    New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null
    $archive = Join-Path $toolsRoot "apache-maven-$mavenVersion-bin.zip"
    $uri = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$mavenVersion/apache-maven-$mavenVersion-bin.zip"
    Invoke-WebRequest -Uri $uri -OutFile $archive
    # Pinned from Maven Central's distribution SHA-512, independently verified at setup.
    $expected = '8beac8d11ef208f1e2a8df0682b9448a9a363d2ad13ca74af43705549e72e74c9378823bf689287801cbbfc2f6ea9596201d19ccacfdfb682ee8a2ff4c4418ba'
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash -ine $expected) { throw 'Maven SHA-512 mismatch.' }
    Expand-Archive -LiteralPath $archive -DestinationPath $toolsRoot -Force
}
$previousJavaHome = $env:JAVA_HOME
Push-Location $projectRoot
try {
    $env:JAVA_HOME = $JdkHome
    & $mavenCommand "-Dmaven.repo.local=$toolsRoot/m2" @MavenArgs
    $result = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $previousJavaHome
    Pop-Location
}
exit $result
