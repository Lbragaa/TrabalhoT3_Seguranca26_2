[CmdletBinding()]
param([int] $Repetitions = 1, [string] $JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $JdkHome) { $JdkHome = 'C:\Program Files\Java\jdk-24' }
& (Join-Path $PSScriptRoot 'maven.ps1') -JdkHome $JdkHome -MavenArgs '-o','-B','-ntp','-DskipTests','test-compile'
if ($LASTEXITCODE -ne 0) { throw 'Benchmark compilation failed.' }
[xml] $pom = Get-Content -Raw (Join-Path $projectRoot 'pom.xml')
$bc = $pom.project.dependencies.dependency | Where-Object artifactId -EQ 'bcprov-jdk18on'
$version = [string] $bc.version
$bcJar = Join-Path $projectRoot ".tools/m2/org/bouncycastle/bcprov-jdk18on/$version/bcprov-jdk18on-$version.jar"
$classpath = @((Join-Path $projectRoot 'target/classes'), (Join-Path $projectRoot 'target/test-classes'), $bcJar) -join [IO.Path]::PathSeparator
& (Join-Path $JdkHome 'bin/java.exe') -cp $classpath 'br.pucrio.inf1416.cofre.auth.KeypadBenchmark' $Repetitions (Join-Path $projectRoot 'target/keypad-benchmark.csv')
exit $LASTEXITCODE
