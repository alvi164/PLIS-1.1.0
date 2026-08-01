$ErrorActionPreference = "Stop"

$mavenVersion = "3.9.16"
$distributionUrl = "https://downloads.apache.org/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip"
$expectedSha512 = "ed41650d42485cfc243fad22158caf9cbb5dc408ce7a09ddb94dd42a019de929ca43065bfa450612cf12bf78b5cafa3884b96c090de326ff590448c933454af3"
$distributionRoot = Join-Path $env:USERPROFILE ".m2\wrapper\dists\plis"
$mavenHome = Join-Path $distributionRoot "apache-maven-$mavenVersion"
$mavenCommand = Join-Path $mavenHome "bin\mvn.cmd"

if (-not (Test-Path -LiteralPath $mavenCommand)) {
    New-Item -ItemType Directory -Force -Path $distributionRoot | Out-Null
    $archive = Join-Path ([System.IO.Path]::GetTempPath()) ("plis-maven-" + [guid]::NewGuid() + ".zip")
    try {
        Write-Host "Downloading Apache Maven $mavenVersion..."
        Invoke-WebRequest -UseBasicParsing -Uri $distributionUrl -OutFile $archive
        $actualSha512 = (Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash.ToLowerInvariant()
        if ($actualSha512 -ne $expectedSha512) {
            throw "The downloaded Maven archive failed its SHA-512 integrity check."
        }
        Expand-Archive -LiteralPath $archive -DestinationPath $distributionRoot
    }
    finally {
        if (Test-Path -LiteralPath $archive) {
            Remove-Item -LiteralPath $archive -Force
        }
    }
}

& $mavenCommand @args
exit $LASTEXITCODE
