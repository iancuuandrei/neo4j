param(
    [Parameter(Mandatory = $true)] [string]$Experiment,
    [Parameter(Mandatory = $true)] [string]$BaselineSha,
    [Parameter(Mandatory = $true)] [string]$VariantSha,
    [Parameter(Mandatory = $true)] [string]$Distribution,
    [Parameter(Mandatory = $true)] [string]$Dataset,
    [Parameter(Mandatory = $true)] [string]$QueryManifest,
    [Parameter(Mandatory = $true)] [string]$Config,
    [Parameter(Mandatory = $true)] [string]$Output,
    [int]$Warmups = 1,
    [int]$Repetitions = 1,
    [int]$Seed = 20260904
)

$ErrorActionPreference = 'Stop'
$datasetPath = (Resolve-Path -LiteralPath $Dataset).Path
$queryPath = (Resolve-Path -LiteralPath $QueryManifest).Path
$configPath = (Resolve-Path -LiteralPath $Config).Path
$distributionPath = (Resolve-Path -LiteralPath $Distribution).Path
$outputPath = [IO.Path]::GetFullPath($Output)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outputPath) | Out-Null

$runtimeJars = @(Get-ChildItem -LiteralPath (Join-Path $distributionPath 'lib') -Filter '*runtime-util*.jar' -File)
if ($runtimeJars.Count -ne 1) {
    throw "Expected exactly one runtime-util JAR in $distributionPath, found $($runtimeJars.Count)"
}

$databasePath = Join-Path $distributionPath 'data\databases\neo4j'
$databaseFiles = @(Get-ChildItem -LiteralPath $databasePath -Recurse -File | Sort-Object FullName)
$databaseManifest = @($databaseFiles | ForEach-Object {
    [ordered]@{
        path = [IO.Path]::GetRelativePath($databasePath, $_.FullName).Replace('\', '/')
        bytes = $_.Length
        sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    }
})
$manifestLines = $databaseManifest | ForEach-Object { "$($_.path)|$($_.bytes)|$($_.sha256)" }
$manifestBytes = [Text.Encoding]::UTF8.GetBytes(($manifestLines -join "`n"))
$manifestHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($manifestBytes))

$operatingSystem = Get-CimInstance Win32_OperatingSystem
$processor = Get-CimInstance Win32_Processor
$computer = Get-CimInstance Win32_ComputerSystem
$javaVersion = (& java -version 2>&1) -join "`n"
$gitVersion = (& git --version) -join "`n"
$mavenVersion = (& mvn --version 2>&1) -join "`n"
$outputDrive = Get-PSDrive -Name ([IO.Path]::GetPathRoot($outputPath).TrimEnd(':\'))

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
        javaToolOptions = $env:JAVA_TOOL_OPTIONS
        mavenOptions = $env:MAVEN_OPTS
        mavenVersion = $mavenVersion
        gitVersion = $gitVersion
        powerShellVersion = $PSVersionTable.PSVersion.ToString()
        artifactVolume = [ordered]@{
            name = $outputDrive.Name
            freeBytes = [int64]$outputDrive.Free
            usedBytes = [int64]$outputDrive.Used
        }
    }
    protocol = [ordered]@{
        warmups = $Warmups
        repetitions = $Repetitions
        seed = $Seed
        cacheState = 'warm JVM and page cache; one untimed pass over every manifest pair'
    }
    inputs = [ordered]@{
        dataset = [ordered]@{ file = $datasetPath; sha256 = (Get-FileHash -LiteralPath $datasetPath -Algorithm SHA256).Hash }
        queryManifest = [ordered]@{ file = $queryPath; sha256 = (Get-FileHash -LiteralPath $queryPath -Algorithm SHA256).Hash }
        config = [ordered]@{ file = $configPath; sha256 = (Get-FileHash -LiteralPath $configPath -Algorithm SHA256).Hash }
        distribution = [ordered]@{
            directory = $distributionPath
            runtimeJar = [ordered]@{
                file = $runtimeJars[0].FullName
                bytes = $runtimeJars[0].Length
                sha256 = (Get-FileHash -LiteralPath $runtimeJars[0].FullName -Algorithm SHA256).Hash
            }
            database = [ordered]@{
                directory = $databasePath
                fileCount = $databaseManifest.Count
                bytes = [int64](($databaseFiles | Measure-Object Length -Sum).Sum)
                manifestSha256 = $manifestHash
                files = $databaseManifest
            }
        }
    }
}

$metadata | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $outputPath -Encoding utf8NoBOM
Write-Host "Wrote $outputPath"
