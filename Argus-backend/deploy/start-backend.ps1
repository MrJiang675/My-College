param(
    [string]$EnvFile = (Join-Path $PSScriptRoot "local-run.env"),
    [string]$JavaPath = "C:\Users\Lenovo\.jdks\corretto-21.0.13\bin\java.exe",
    [switch]$ValidateOnly
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Test-Placeholder {
    param([string]$Value)
    return [string]::IsNullOrWhiteSpace($Value) -or $Value.StartsWith("REPLACE_")
}

if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
    throw "Environment file not found: $EnvFile"
}

$resolvedEnvFile = (Resolve-Path -LiteralPath $EnvFile).Path
foreach ($rawLine in [System.IO.File]::ReadAllLines($resolvedEnvFile)) {
    $line = $rawLine.Trim()
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
        continue
    }

    $separator = $line.IndexOf("=")
    if ($separator -lt 1) {
        throw "Invalid environment line: $line"
    }

    $name = $line.Substring(0, $separator).Trim()
    $value = $line.Substring($separator + 1).Trim()
    if ($name -notmatch "^[A-Za-z_][A-Za-z0-9_]*$") {
        throw "Invalid environment variable name: $name"
    }
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
        ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }

    Set-Item -Path ("Env:" + $name) -Value $value
}

$requiredNames = @(
    "SPRING_PROFILES_ACTIVE",
    "ARGUS_RAG_CORS_ALLOWED_ORIGINS",
    "DB_HOST", "DB_PORT", "DB_NAME", "DB_USERNAME", "DB_PASSWORD",
    "MINIO_ENDPOINT", "MINIO_ACCESS_KEY", "MINIO_SECRET_KEY", "MINIO_BUCKET",
    "ES_HOST", "ES_PORT", "ES_SCHEME", "ES_INDEX_NAME",
    "ARGUS_RAG_JWT_SECRET", "ZHIPUAI_API_KEY",
    "ARGUS_RAG_AUTH_REFRESH_COOKIE_SAME_SITE", "ARGUS_RAG_AUTH_REFRESH_COOKIE_SECURE"
)
$missingNames = @($requiredNames | Where-Object {
    $value = [Environment]::GetEnvironmentVariable($_)
    (Test-Placeholder $value) -and $_ -notin @("MINIO_ACCESS_KEY", "MINIO_SECRET_KEY", "ZHIPUAI_API_KEY")
})
if ($missingNames.Count -gt 0) {
    throw ("Missing required values: " + ($missingNames -join ", "))
}

if ($env:SPRING_PROFILES_ACTIVE -ne "prod") {
    throw "This launcher only allows the prod profile to avoid dev admin initialization."
}

if ($env:ARGUS_RAG_JWT_SECRET -eq "AUTO_GENERATE_ON_START") {
    $jwtBytes = New-Object byte[] 48
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($jwtBytes)
    }
    finally {
        $rng.Dispose()
    }
    $env:ARGUS_RAG_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
}
elseif ([Text.Encoding]::UTF8.GetByteCount($env:ARGUS_RAG_JWT_SECRET) -lt 32) {
    throw "ARGUS_RAG_JWT_SECRET must contain at least 32 UTF-8 bytes."
}

if (Test-Placeholder $env:MINIO_ACCESS_KEY -or Test-Placeholder $env:MINIO_SECRET_KEY) {
    Write-Warning "MinIO credentials are placeholders. Backend startup can continue, but upload and object storage tests will fail."
}
if (Test-Placeholder $env:ZHIPUAI_API_KEY) {
    Write-Warning "ZHIPUAI_API_KEY is a placeholder. Backend startup can continue, but document processing, embedding and QA tests will fail."
}

$backendDir = Split-Path $PSScriptRoot -Parent
$jarPath = Join-Path $backendDir "target\Argus-backend-0.0.1-SNAPSHOT.jar"
if (-not (Test-Path -LiteralPath $JavaPath -PathType Leaf)) {
    throw "Java 21 executable not found: $JavaPath"
}
if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    throw "Backend JAR not found: $jarPath. Run Maven package first."
}

Write-Host "Environment file: $resolvedEnvFile"
Write-Host "Profile: prod"
Write-Host "Backend JAR: $jarPath"

if ($ValidateOnly) {
    Write-Host "Configuration validated. Backend was not started."
    exit 0
}

Push-Location $backendDir
try {
    & $JavaPath "-Dfile.encoding=UTF-8" "-Dsun.stdout.encoding=UTF-8" "-Dsun.stderr.encoding=UTF-8" "-jar" $jarPath "--spring.profiles.active=prod"
    $exitCode = $LASTEXITCODE
    if ($null -eq $exitCode) {
        $exitCode = 0
    }
}
finally {
    Pop-Location
}

exit $exitCode
