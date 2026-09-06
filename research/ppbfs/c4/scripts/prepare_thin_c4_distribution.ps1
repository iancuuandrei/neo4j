param(
    [Parameter(Mandatory = $true)] [string]$C4BaseDistribution,
    [Parameter(Mandatory = $true)] [string]$SourceDistribution,
    [Parameter(Mandatory = $true)] [string]$TargetDistribution
)

$ErrorActionPreference = 'Stop'
$base = (Resolve-Path -LiteralPath $C4BaseDistribution).Path
$source = (Resolve-Path -LiteralPath $SourceDistribution).Path
$target = [IO.Path]::GetFullPath($TargetDistribution)
if ([IO.Path]::GetPathRoot($target) -ne 'D:\') { throw "C4 stores must remain on D:, not $target" }
if (Test-Path -LiteralPath $target) { throw "Append-only target already exists: $target" }

New-Item -ItemType Directory -Path $target -Force | Out-Null
foreach ($name in @('bin', 'lib', 'plugins', 'products', 'certificates', 'licenses', 'web', 'import', 'labs')) {
    New-Item -ItemType Junction -Path (Join-Path $target $name) -Target (Join-Path $base $name) | Out-Null
}
foreach ($name in @('conf', 'logs', 'run')) {
    New-Item -ItemType Directory -Path (Join-Path $target $name) | Out-Null
}
Get-ChildItem -LiteralPath (Join-Path $base 'conf') -File | Copy-Item -Destination (Join-Path $target 'conf')
Get-ChildItem -LiteralPath $base -File | Copy-Item -Destination $target

& robocopy (Join-Path $source 'data') (Join-Path $target 'data') /E /COPY:DAT /DCOPY:DAT /R:2 /W:1 /NFL /NDL /NJH /NJS /NP
if ($LASTEXITCODE -gt 7) { throw "robocopy failed with exit code $LASTEXITCODE" }

$sourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $source 'data\databases\neo4j') -File -Recurse)
$targetFiles = @(Get-ChildItem -LiteralPath (Join-Path $target 'data\databases\neo4j') -File -Recurse)
if ($sourceFiles.Count -ne $targetFiles.Count) {
    throw "Store file count mismatch: source=$($sourceFiles.Count), target=$($targetFiles.Count)"
}
$sourceBytes = [int64](($sourceFiles | Measure-Object Length -Sum).Sum)
$targetBytes = [int64](($targetFiles | Measure-Object Length -Sum).Sum)
if ($sourceBytes -ne $targetBytes) { throw "Store byte count mismatch: source=$sourceBytes, target=$targetBytes" }

Write-Host "Prepared thin C4 distribution at $target ($targetBytes database bytes; shared immutable runtime junctions)."
