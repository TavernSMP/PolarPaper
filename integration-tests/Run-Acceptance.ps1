param(
    [Parameter(Mandatory = $true)][string] $PaperJar,
    [Parameter(Mandatory = $true)][string] $AcceptedEula,
    [string] $Java = 'java',
    [int] $Port = 25585
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$paperPath = (Resolve-Path -LiteralPath $PaperJar).Path
$eulaPath = (Resolve-Path -LiteralPath $AcceptedEula).Path
if ((Get-Content -LiteralPath $eulaPath -Raw) -notmatch '(?m)^eula=true\s*$') {
    throw 'Supply an eula.txt that the operator has already accepted.'
}
$runDirectory = Join-Path $projectRoot ('.integration\acceptance-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
if (Test-Path -LiteralPath $runDirectory) {
    throw 'The acceptance directory already exists.'
}
New-Item -ItemType Directory -Path (Join-Path $runDirectory 'plugins') -Force | Out-Null
$taskTemp = Join-Path $projectRoot '.gradle\tmp'
New-Item -ItemType Directory -Path $taskTemp -Force | Out-Null
$previousTemp = $env:TEMP
$previousTmp = $env:TMP
try {
    $env:TEMP = $taskTemp
    $env:TMP = $taskTemp
    if (-not $env:GRADLE_USER_HOME) {
        $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle\user-home'
    }
    Push-Location -LiteralPath $projectRoot
    try {
        & .\gradlew.bat clean build acceptanceJar --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Clean build failed.' }
        Copy-Item -Path 'dist\*.jar' -Destination (Join-Path $runDirectory 'plugins')
        Copy-Item -LiteralPath 'build\acceptance\polarpaper-acceptance.jar' -Destination (Join-Path $runDirectory 'plugins')
    } finally {
        Pop-Location
    }
    Copy-Item -LiteralPath $paperPath -Destination (Join-Path $runDirectory 'paper.jar')
    $paperDirectory = Split-Path -Parent $paperPath
    foreach ($entry in @('libraries', 'versions', 'cache')) {
        $cachedPath = Join-Path $paperDirectory $entry
        if (Test-Path -LiteralPath $cachedPath) {
            Copy-Item -LiteralPath $cachedPath -Destination (Join-Path $runDirectory $entry) -Recurse
        }
    }
    Copy-Item -LiteralPath $eulaPath -Destination (Join-Path $runDirectory 'eula.txt')
    @"
server-ip=127.0.0.1
server-port=$Port
online-mode=false
level-name=acceptance_base
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}
view-distance=2
simulation-distance=2
spawn-protection=0
allow-nether=false
enable-rcon=false
enable-query=false
"@ | Set-Content -LiteralPath (Join-Path $runDirectory 'server.properties') -Encoding utf8
    "settings:`n  allow-end: false" | Set-Content -LiteralPath (Join-Path $runDirectory 'bukkit.yml') -Encoding utf8
    Push-Location -LiteralPath $runDirectory
    try {
        foreach ($phase in @('first-boot', 'restart')) {
            $log = Join-Path $runDirectory ($phase + '.log')
            & $Java '-Xms256M' '-Xmx1536M' "-Djava.io.tmpdir=$taskTemp" '--enable-native-access=ALL-UNNAMED' '-Dpolarpaper.acceptance=true' '-jar' 'paper.jar' '--nogui' *> $log
            if ($LASTEXITCODE -ne 0) { throw "$phase server process failed: $log" }
            $text = Get-Content -LiteralPath $log -Raw
            $expected = if ($phase -eq 'first-boot') { 'ACCEPTANCE FIRST BOOT PASS:' } else { 'ACCEPTANCE RESTART PASS:' }
            if (-not $text.Contains($expected) -or $text.Contains('ACCEPTANCE FAIL') -or $text.Contains('Gave up saving')) {
                throw "$phase checks failed: $log"
            }
            Write-Output "$phase passed: $log"
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:TEMP = $previousTemp
    $env:TMP = $previousTmp
}
