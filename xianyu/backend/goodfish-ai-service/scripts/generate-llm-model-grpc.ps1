param(
    [string]$PythonPath = ""
)

$ErrorActionPreference = "Stop"
$ServiceRoot = Split-Path -Parent $PSScriptRoot
$BackendRoot = Split-Path -Parent $ServiceRoot
if (-not $PythonPath) {
    $PythonPath = Join-Path $BackendRoot ".venv\Scripts\python.exe"
}
$ProtoRoot = Join-Path $BackendRoot "llm-manage\api\protos"
$ProtoFile = Join-Path $ProtoRoot "admin\service\v1\llm_model.proto"
$SitePackages = & $PythonPath -c "import site; print(site.getsitepackages()[0])"
$GrpcToolsProtoRoot = Join-Path $SitePackages "grpc_tools\_proto"

if (-not (Test-Path $PythonPath)) {
    throw "Python interpreter not found: $PythonPath"
}
if (-not (Test-Path $ProtoFile)) {
    throw "Proto file not found: $ProtoFile"
}

& $PythonPath -m grpc_tools.protoc `
    "-I$ProtoRoot" `
    "-I$SitePackages" `
    "-I$GrpcToolsProtoRoot" `
    "--python_out=$ServiceRoot" `
    "--grpc_python_out=$ServiceRoot" `
    "$ProtoFile"

if ($LASTEXITCODE -ne 0) {
    throw "grpc_tools.protoc failed with exit code $LASTEXITCODE"
}

$package_dirs = @(
    (Join-Path $ServiceRoot "admin"),
    (Join-Path $ServiceRoot "admin\service"),
    (Join-Path $ServiceRoot "admin\service\v1")
)
foreach ($package_dir in $package_dirs) {
    if (-not (Test-Path $package_dir)) {
        throw "Expected generated package directory is missing: $package_dir"
    }
    New-Item -ItemType File -Force -Path (Join-Path $package_dir "__init__.py") | Out-Null
}

Write-Host "Generated admin.service.v1 LlmModel Python stubs."
