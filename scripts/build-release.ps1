param(
    [Parameter(Mandatory=$true)][string]$Keystore,
    [Parameter(Mandatory=$true)][string]$Alias
)
$ErrorActionPreference='Stop'
if(!(Test-Path -LiteralPath $Keystore -PathType Leaf)){throw 'Keystore not found'}
$names=@('NOTHING_TYPE_KEYSTORE','NOTHING_TYPE_KEY_ALIAS','NOTHING_TYPE_STORE_PASSWORD','NOTHING_TYPE_KEY_PASSWORD')
$previous=@{}
foreach($name in $names){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
function Read-Secret([string]$Prompt){
    $secure=Read-Host $Prompt -AsSecureString
    $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)}
    finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer);$secure.Dispose()}
}
try {
    $env:NOTHING_TYPE_KEYSTORE=(Resolve-Path -LiteralPath $Keystore).Path
    $env:NOTHING_TYPE_KEY_ALIAS=$Alias
    $env:NOTHING_TYPE_STORE_PASSWORD=Read-Secret 'Keystore password'
    $env:NOTHING_TYPE_KEY_PASSWORD=Read-Secret 'Key password (Enter to use keystore password)'
    if(!$env:NOTHING_TYPE_KEY_PASSWORD){$env:NOTHING_TYPE_KEY_PASSWORD=$env:NOTHING_TYPE_STORE_PASSWORD}
    Push-Location (Split-Path $PSScriptRoot -Parent)
    try {
        & .\gradlew.bat assembleRelease --no-daemon
        if($LASTEXITCODE){throw 'Release build failed'}
        $output = 'app/build/outputs/apk/release'
        $apk = @( (Get-Content -LiteralPath "$output/output-metadata.json" -Raw | ConvertFrom-Json).elements )
        if ($apk.Count -ne 1 -or $apk[0].versionName -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$' -or $apk[0].outputFile -ne 'app-release.apk') {
            throw 'Expected a signed release APK and its version metadata'
        }
        $name = 'nothing-type-' + $apk[0].versionName + '.apk'
        Copy-Item -LiteralPath "$output/app-release.apk" -Destination "$output/$name" -Force
        Write-Output "Signed APK: $output/$name"
    } finally {Pop-Location}
} finally {
    foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}
}
