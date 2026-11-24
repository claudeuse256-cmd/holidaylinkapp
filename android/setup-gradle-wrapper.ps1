$ErrorActionPreference = "Stop"
$GradleVersion = "8.9"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Cache = Join-Path $env:USERPROFILE ".gradle\holidaylink-bootstrap"
$Zip = Join-Path $Cache "gradle-$GradleVersion-bin.zip"
$Dist = Join-Path $Cache "gradle-$GradleVersion"
$Gradle = Join-Path $Dist "bin\gradle.bat"
New-Item -ItemType Directory -Force -Path $Cache | Out-Null
if (!(Test-Path $Gradle)) {
    Write-Host "Downloading Gradle $GradleVersion (one-time setup)..."
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" -OutFile $Zip
    Expand-Archive -Path $Zip -DestinationPath $Cache -Force
    $Extracted = Join-Path $Cache "gradle-$GradleVersion"
    if (!(Test-Path (Join-Path $Extracted "bin\gradle.bat"))) { throw "Gradle download did not contain gradle.bat." }
}
Push-Location $Root
try {
    & $Gradle wrapper --gradle-version $GradleVersion --distribution-type bin
    if ($LASTEXITCODE -ne 0) { throw "Gradle wrapper generation failed with exit code $LASTEXITCODE." }
    Write-Host "Gradle wrapper created. You can now run .\gradlew.bat tasks or open this android folder in Android Studio."
}
finally { Pop-Location }
