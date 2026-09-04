param(
    [Parameter(Mandatory = $true)] [string]$Experiment,
    [Parameter(Mandatory = $true)] [string]$BaselineSha,
    [Parameter(Mandatory = $true)] [string]$VariantSha,
    [Parameter(Mandatory = $true)] [string]$Dataset,
    [Parameter(Mandatory = $true)] [string]$QueryManifest,
    [Parameter(Mandatory = $true)] [string]$Config,
    [Parameter(Mandatory = $true)] [string]$Output
)

$ErrorActionPreference = 'Stop'
$datasetPath = (Resolve-Path -LiteralPath $Dataset).Path
$queryPath = (Resolve-Path -LiteralPath $QueryManifest).Path
$configPath = (Resolve-Path -LiteralPath $Config).Path
$outputPath = [IO.Path]::GetFullPath($Output)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outputPath) | Out-Null

$operatingSystem = Get-CimInstance Win32_OperatingSystem
$processor = Get-CimInstance Win32_Processor
$computer = Get-CimInstance Win32_ComputerSystem
$javaVersion = (& java -version 2>&1) -join "`n"
$gitVersion = (& git --version) -join "`n"

$metadata = [ordered]@{
    schemaVersion = 1
    experiment = $Experiment
    capturedAt = (Get-Date).ToUniversalTime().ToString('o')
    baselineSha = $BaselineSha
    variantSha = $VariantSha
    environment = [ordered]@{
        os = "$($operatingSystem.Caption) $($operatingSystem.Version) build $($operatingSystem.BuildNumber)"
        cpu = ($processor.Name -join '; ').Trim()
        logicalProcessors = ($processor.NumberOfLogicalProcessors | Measure-Object -Sum).Sum
        ramBytes = [int64]$computer.TotalPhysicalMemory
        javaHome = $env:JAVA_HOME
        javaVersion = $javaVersion
        jvmOptions = $env:JAVA_TOOL_OPTIONS
        gitVersion = $gitVersion
    }
    inputs = [ordered]@{
        dataset = [ordered]@{ file = $datasetPath; sha256 = (Get-FileHash -LiteralPath $datasetPath -Algorithm SHA256).Hash }
        queryManifest = [ordered]@{ file = $queryPath; sha256 = (Get-FileHash -LiteralPath $queryPath -Algorithm SHA256).Hash }
        config = [ordered]@{ file = $configPath; sha256 = (Get-FileHash -LiteralPath $configPath -Algorithm SHA256).Hash }
    }
}

$metadata | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $outputPath -Encoding utf8NoBOM
Write-Host "Wrote $outputPath"
