$ErrorActionPreference = "Stop"

$Repo = "An-actual-duck/open-rsc-spoiled-milk"
$CurrentVersion = "@VERSION@"
$PackageKind = "@PACKAGE_KIND@"
$PayloadDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$InstallDir = Split-Path -Parent $PayloadDir
$ApiUrl = "https://api.github.com/repos/$Repo/releases"

Write-Host "Checking for Spoiled Milk updates..."
$releases = Invoke-RestMethod -Uri $ApiUrl -Headers @{ "User-Agent" = "Spoiled-Milk-Updater" }
if (-not $releases -or $releases.Count -lt 1) {
    throw "Unable to determine the latest version from GitHub."
}

function Get-NumberSortKey($number) {
    $digits = $number.TrimStart('0')
    if ($digits.Length -eq 0) { $digits = "0" }
    return "{0:D6}:{1}" -f $digits.Length, $digits
}

function Get-VersionSortKey($tagName) {
    $match = [regex]::Match($tagName, '^v(\d+)\.(\d+)\.(\d+)(?:-alpha\.(\d+))?$')
    if (-not $match.Success) {
        return $null
    }

    $stableRank = 1
    $alphaRank = "0"
    if ($match.Groups[4].Success) {
        $stableRank = 0
        $alphaRank = $match.Groups[4].Value
    }

    return "{0}.{1}.{2}.{3}.{4}" -f `
        (Get-NumberSortKey $match.Groups[1].Value), `
        (Get-NumberSortKey $match.Groups[2].Value), `
        (Get-NumberSortKey $match.Groups[3].Value), `
        $stableRank, `
        (Get-NumberSortKey $alphaRank)
}

$latest = $releases |
    Where-Object { $_.tag_name -match '^v\d+\.\d+\.\d+(-alpha\.\d+)?$' } |
    Sort-Object { Get-VersionSortKey $_.tag_name } -Descending |
    Select-Object -First 1
if (-not $latest) {
    throw "Unable to determine the latest version from GitHub."
}
$latestVersion = $latest.tag_name
if ($latestVersion -eq $CurrentVersion) {
    Write-Host "Spoiled Milk is up to date ($CurrentVersion)."
    exit 0
}

$currentKey = Get-VersionSortKey $CurrentVersion
if (-not $currentKey) {
    throw "The installed version is unreadable; keeping the installed client."
}
if ([string]::CompareOrdinal((Get-VersionSortKey $latestVersion), $currentKey) -le 0) {
    Write-Host "Installed version $CurrentVersion is newer than $latestVersion; keeping it."
    exit 0
}

$assetName = "spoiled-milk-$latestVersion-$PackageKind.zip"
$asset = $latest.assets | Where-Object { $_.name -eq $assetName } | Select-Object -First 1
if (-not $asset) {
    throw "Latest version $latestVersion does not include $assetName."
}

$updateDir = Join-Path $PayloadDir "updates"
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

Copy-Item -Path (Join-Path $packageRoot "*") -Destination $InstallDir -Recurse -Force
$legacyFiles = @(
    "Spoiled_Milk_Client.jar",
    "update-spoiled-milk.sh",
    "update-spoiled-milk.ps1",
    "Update Spoiled Milk.cmd",
    "ASSET-SOURCES.txt",
    "VERSION.txt",
    "LICENSE"
) | ForEach-Object { Join-Path $InstallDir $_ }
$legacyDirs = @("Cache", "runtime", "updates") | ForEach-Object { Join-Path $InstallDir $_ }
Remove-Item -Force -ErrorAction SilentlyContinue -Path $legacyFiles
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue -Path $legacyDirs
Remove-Item -Recurse -Force $extractDir

Write-Host "Updated Spoiled Milk from $CurrentVersion to $latestVersion."
