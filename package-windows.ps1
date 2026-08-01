param(
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$workspaceRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$wixRoot = Join-Path $workspaceRoot '.tools\wix314'
$wixArchive = Join-Path $workspaceRoot '.tools\downloads\wix314-binaries.zip'
$wixUrl = 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip'
$wixSha256 = '6AC824E1642D6F7277D0ED7EA09411A508F6116BA6FAE0AA5F2C7DAA2FF43D31'

function Assert-WorkspacePath([string]$candidate) {
    $resolved = [System.IO.Path]::GetFullPath($candidate)
    if (-not $resolved.StartsWith($workspaceRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Build path escaped the workspace: $resolved"
    }
    return $resolved
}

function Reset-BuildDirectory([string]$directory) {
    $resolved = Assert-WorkspacePath $directory
    if (Test-Path -LiteralPath $resolved) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
    New-Item -ItemType Directory -Path $resolved -Force | Out-Null
}

if (-not (Test-Path -LiteralPath (Join-Path $wixRoot 'candle.exe'))) {
    New-Item -ItemType Directory -Path (Split-Path $wixArchive) -Force | Out-Null
    Invoke-WebRequest -Uri $wixUrl -OutFile $wixArchive
    $actualHash = (Get-FileHash -LiteralPath $wixArchive -Algorithm SHA256).Hash
    if ($actualHash -ne $wixSha256) {
        throw "WiX archive checksum mismatch. Expected $wixSha256 but received $actualHash"
    }
    Reset-BuildDirectory $wixRoot
    Expand-Archive -LiteralPath $wixArchive -DestinationPath $wixRoot -Force
}

$mavenArguments = @('clean', 'package')
if ($SkipTests) {
    $mavenArguments += '-DskipTests'
}
& (Join-Path $workspaceRoot 'mvnw.cmd') @mavenArguments
if ($LASTEXITCODE -ne 0) {
    throw "Maven build failed with exit code $LASTEXITCODE"
}

$packageInput = Assert-WorkspacePath (Join-Path $workspaceRoot 'desktop-app\target\package-input')
Copy-Item -LiteralPath (Join-Path $workspaceRoot 'desktop-app\target\plis-desktop.jar') `
    -Destination (Join-Path $packageInput 'plis-desktop.jar') -Force
Copy-Item -LiteralPath (Join-Path $workspaceRoot 'LICENSE.txt') -Destination $packageInput -Force
Copy-Item -LiteralPath (Join-Path $workspaceRoot 'CONTRIBUTORS.md') -Destination $packageInput -Force
Copy-Item -LiteralPath (Join-Path $workspaceRoot 'THIRD-PARTY-NOTICES.md') -Destination $packageInput -Force

$releaseRoot = Assert-WorkspacePath (Join-Path $workspaceRoot 'release')
$appImageDestination = Join-Path $releaseRoot 'app-image'
$installerDestination = Join-Path $releaseRoot 'installer'
Reset-BuildDirectory $appImageDestination
Reset-BuildDirectory $installerDestination

$commonArguments = @(
    '--name', 'PLIS',
    '--app-version', '1.1.0',
    '--vendor', 'Syad Mehedi Hasan Alvi',
    '--description', 'Programming Lab Integrity Suite',
    '--copyright', 'Copyright (c) 2026 Syad Mehedi Hasan Alvi. All rights reserved.',
    '--input', $packageInput,
    '--main-jar', 'plis-desktop.jar',
    '--main-class', 'edu.university.plis.desktop.PlisDesktopLauncher',
    '--java-options', '-Dfile.encoding=UTF-8'
)

& jpackage '--type' 'app-image' '--dest' $appImageDestination @commonArguments
if ($LASTEXITCODE -ne 0) {
    throw "jpackage app-image failed with exit code $LASTEXITCODE"
}

$previousPath = $env:PATH
try {
    $env:PATH = "$wixRoot;$previousPath"
    & jpackage '--type' 'exe' '--dest' $installerDestination @commonArguments `
        '--license-file' (Join-Path $workspaceRoot 'LICENSE.txt') `
        '--win-dir-chooser' '--win-menu' '--win-menu-group' 'PLIS' '--win-shortcut' `
        '--win-per-user-install' '--win-upgrade-uuid' '2b299d87-c6d6-4fa8-8f5e-0e3daf30f7a4'
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage installer failed with exit code $LASTEXITCODE"
    }
} finally {
    $env:PATH = $previousPath
}

Write-Host "Runnable application: $appImageDestination\PLIS\PLIS.exe"
Write-Host "Single installer EXE: $installerDestination\PLIS-1.1.0.exe"
