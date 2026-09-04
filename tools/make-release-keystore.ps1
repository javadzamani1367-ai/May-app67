# Creates the release signing key on Windows and prints what to paste into the
# four GitHub secrets. The key never leaves this machine; only its base64 form
# is copied, and that goes into an encrypted secret.
#
# Keep release.keystore somewhere safe and backed up: losing it means no future
# build can update an already installed app.
#
# Run:  powershell -ExecutionPolicy Bypass -File tools\make-release-keystore.ps1

param(
    [string]$Keystore = "release.keystore",
    [string]$Alias = "inspection"
)

$ErrorActionPreference = "Stop"

if (Test-Path $Keystore) {
    throw "refusing to overwrite an existing $Keystore"
}

# keytool ships with any JDK, including the one inside Android Studio.
$keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
if (-not $keytool) {
    $candidates = @(
        "$env:ProgramFiles\Android\Android Studio\jbr\bin\keytool.exe",
        "$env:ProgramFiles\Java\*\bin\keytool.exe"
    )
    $keytool = ($candidates | ForEach-Object { Get-Item $_ -ErrorAction SilentlyContinue } | Select-Object -First 1).FullName
}
if (-not $keytool) { throw "keytool not found; install a JDK or Android Studio" }

$secure = Read-Host -AsSecureString "Password for the new keystore (remember it)"
$plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
if ($plain.Length -lt 6) { throw "use at least six characters" }

& $keytool -genkeypair -v `
    -keystore $Keystore `
    -storetype PKCS12 `
    -alias $Alias `
    -keyalg RSA -keysize 2048 -validity 10000 `
    -storepass $plain -keypass $plain `
    -dname "CN=Ilam Electricity Distribution, OU=Crypto inspection, O=Tavanir, C=IR"

$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path $Keystore)))
Set-Content -Path "$Keystore.base64.txt" -Value $base64 -NoNewline

Write-Host ""
Write-Host "Done. Add these four repository secrets:"
Write-Host "  Settings > Secrets and variables > Actions > New repository secret"
Write-Host ""
Write-Host "RELEASE_KEYSTORE_BASE64   = contents of $Keystore.base64.txt"
Write-Host "RELEASE_KEYSTORE_PASSWORD = the password you just chose"
Write-Host "RELEASE_KEY_ALIAS         = $Alias"
Write-Host "RELEASE_KEY_PASSWORD      = the same password"
Write-Host ""
Write-Host "Delete $Keystore.base64.txt once the secret is saved; keep $Keystore backed up."
