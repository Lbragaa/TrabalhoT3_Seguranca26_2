# Assemble only reviewable project/source files and the two verified executable artifacts.
[CmdletBinding()]
param([string] $OutputDirectory = 'output/delivery')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$destination = if ([IO.Path]::IsPathRooted($OutputDirectory)) { $OutputDirectory } else { Join-Path $projectRoot $OutputDirectory }
$destination = [IO.Path]::GetFullPath($destination)
if (Test-Path -LiteralPath $destination) {
    if (@(Get-ChildItem -Force -LiteralPath $destination).Count) { throw 'Delivery directory is not empty. Choose a new -OutputDirectory; existing files are never replaced.' }
}
$allowed = @('src','scripts','docs','Pacote-T3','pom.xml','README.md','PLAN.md','REQUIREMENTS.md','AGENTS.md','FINAL-DELIVERY-CHECKLIST.md','.gitignore')
foreach ($item in $allowed) { if (-not (Test-Path -LiteralPath (Join-Path $projectRoot $item))) { throw "Missing delivery input: $item" } }
foreach ($name in @('cofre-digital.jar','logView.jar')) {
    if (-not (Test-Path -LiteralPath (Join-Path $projectRoot "target/$name"))) { throw 'Build both executable JARs before packaging.' }
}
$staging = Join-Path $projectRoot ('.tools/delivery-staging-' + [guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $staging | Out-Null
foreach ($item in $allowed) { Copy-Item -LiteralPath (Join-Path $projectRoot $item) -Destination $staging -Recurse }
New-Item -ItemType Directory -Path (Join-Path $staging 'target') | Out-Null
New-Item -ItemType Directory -Path $destination -Force | Out-Null
foreach ($name in @('cofre-digital.jar','logView.jar')) {
    Copy-Item -LiteralPath (Join-Path $projectRoot "target/$name") -Destination (Join-Path $staging 'target')
    Copy-Item -LiteralPath (Join-Path $projectRoot "target/$name") -Destination $destination
}
# Fail if any unexpected local/runtime folder was introduced inside an allowlisted source folder.
foreach ($file in Get-ChildItem -LiteralPath $staging -Recurse -Force) {
    $relative = [IO.Path]::GetRelativePath($staging,$file.FullName).Replace('\','/')
    if ($relative -match '(^|/)(\.local|\.tools|runtime|output|\.git)(/|$)' -or ($file.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Unexpected local data or link in staging; archive refused.' }
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = Join-Path $destination 'cofre-digital-submission.zip'
[IO.Compression.ZipFile]::CreateFromDirectory($staging,$archive)
$hashes = foreach ($name in @('cofre-digital.jar','logView.jar','cofre-digital-submission.zip')) {
    $hash = (Get-FileHash -LiteralPath (Join-Path $destination $name) -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $name"
}
$hashes | Set-Content -LiteralPath (Join-Path $destination 'SHA256SUMS.txt') -Encoding utf8
Write-Output "Delivery prepared: $destination"
Write-Output 'Includes unchanged encrypted professor fixtures; excludes local credentials, runtime data, generated keys and tools.'
