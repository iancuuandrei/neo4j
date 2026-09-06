param(
    [Parameter(Mandatory = $true)] [string]$RuntimeDistribution,
    [Parameter(Mandatory = $true)] [string]$DataDistribution,
    [Parameter(Mandatory = $true)] [string]$TargetDistribution
)

$ErrorActionPreference = 'Stop'
$runtime = (Resolve-Path -LiteralPath $RuntimeDistribution).Path
$dataSource = (Resolve-Path -LiteralPath $DataDistribution).Path
$target = [IO.Path]::GetFullPath($TargetDistribution)
if ([IO.Path]::GetPathRoot($target) -ne 'D:\') { throw "Overlay must remain on D:, not $target" }
if (Test-Path -LiteralPath $target) { throw "Append-only target already exists: $target" }

New-Item -ItemType Directory -Path $target -Force | Out-Null
foreach ($name in @('bin', 'lib', 'plugins', 'products', 'certificates', 'licenses', 'web', 'import', 'labs')) {
    $source = Join-Path $runtime $name
    if (Test-Path -LiteralPath $source) {
        New-Item -ItemType Junction -Path (Join-Path $target $name) -Target $source | Out-Null
    }
}
New-Item -ItemType Junction -Path (Join-Path $target 'data') -Target (Join-Path $dataSource 'data') | Out-Null
foreach ($name in @('conf', 'logs', 'run')) {
    New-Item -ItemType Directory -Path (Join-Path $target $name) | Out-Null
}
Get-ChildItem -LiteralPath (Join-Path $runtime 'conf') -File | Copy-Item -Destination (Join-Path $target 'conf')
Get-ChildItem -LiteralPath $runtime -File | Copy-Item -Destination $target

$runtimeJar = Get-ChildItem -LiteralPath (Join-Path $runtime 'lib') -Filter 'neo4j-cypher-runtime-util-*.jar' | Select-Object -First 1
if (-not $runtimeJar) { throw 'Runtime-util jar not found.' }
Write-Host "Prepared D:-only overlay at $target using runtime $($runtimeJar.Name) and shared dataset $($dataSource)\data"
