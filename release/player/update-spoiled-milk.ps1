$ErrorActionPreference = "Stop"

$Repo = "An-actual-duck/open-rsc-spoiled-milk"
$CurrentVersion = "@VERSION@"
$PackageKind = "@PACKAGE_KIND@"
$GameDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ApiUrl = "https://api.github.com/repos/$Repo/releases"

Write-Host "Checking for Spoiled Milk updates..."
$releases = Invoke-RestMethod -Uri $ApiUrl -Headers @{ "User-Agent" = "Spoiled-Milk-Updater" }
if (-not $releases -or $releases.Count -lt 1) {
    throw "Unable to determine the latest release from GitHub."
}

$latest = $releases |
    Where-Object { $_.tag_name -match '^v\d+\.\d+\.\d+-alpha\.(\d+)$' } |
    Sort-Object { [int]([regex]::Match($_.tag_name, '-alpha\.(\d+)$').Groups[1].Value) } -Descending |
    Select-Object -First 1
if (-not $latest) {
    throw "Unable to determine the latest alpha release from GitHub."
}
$latestVersion = $latest.tag_name
$currentAlpha = 0
$latestAlpha = 0
$currentHasAlpha = [int]::TryParse(($CurrentVersion -replace '^.*-alpha\.', ''), [ref]$currentAlpha)
$latestHasAlpha = [int]::TryParse(($latestVersion -replace '^.*-alpha\.', ''), [ref]$latestAlpha)
if ($latestVersion -eq $CurrentVersion -or ($currentHasAlpha -and $latestHasAlpha -and $latestAlpha -le $currentAlpha)) {
    Write-Host "Spoiled Milk is up to date ($CurrentVersion)."
    exit 0
}

$assetName = "spoiled-milk-$latestVersion-$PackageKind.zip"
$asset = $latest.assets | Where-Object { $_.name -eq $assetName } | Select-Object -First 1
if (-not $asset) {
    throw "Latest release $latestVersion does not include $assetName."
}

$updateDir = Join-Path $GameDir "updates"
$archive = Join-Path $updateDir $assetName
$extractDir = Join-Path $updateDir "extracted"
New-Item -ItemType Directory -Force -Path $updateDir | Out-Null
if (Test-Path $extractDir) {
    Remove-Item -Recurse -Force $extractDir
}

Write-Host "Downloading $assetName..."
Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $archive -Headers @{ "User-Agent" = "Spoiled-Milk-Updater" }

Write-Host "Installing $latestVersion..."
Expand-Archive -Path $archive -DestinationPath $extractDir -Force
$packageRoot = Join-Path $extractDir "spoiled-milk-$latestVersion-$PackageKind"
if (-not (Test-Path $packageRoot)) {
    throw "Downloaded package did not contain expected folder: $packageRoot"
}

Copy-Item -Path (Join-Path $packageRoot "*") -Destination $GameDir -Recurse -Force
Remove-Item -Recurse -Force $extractDir

Write-Host "Updated Spoiled Milk from $CurrentVersion to $latestVersion."
