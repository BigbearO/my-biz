param(
    [string]$PythonPath = ""
)

$ErrorActionPreference = "Stop"
$ServiceRoot = Split-Path -Parent $PSScriptRoot
$RepoRoot = Split-Path -Parent (Split-Path -Parent $ServiceRoot)
$ProtoRoot = Join-Path $RepoRoot "proto"
if (-not $PythonPath) {
    $BackendRoot = Split-Path -Parent $ServiceRoot
    $PythonPath = Join-Path $BackendRoot ".venv\Scripts\python.exe"
}
$SitePackages = & $PythonPath -c "import site; print(site.getsitepackages()[0])"
$GrpcToolsProtoRoot = Join-Path $SitePackages "grpc_tools\_proto"

if (-not (Test-Path $PythonPath)) {
    throw "Python interpreter not found: $PythonPath"
}

$Protos = @(
    @{ Out = "app\pb\llm\v1";  File = "llm\v1\llm.proto";  Package = "app.pb.llm.v1" },
    @{ Out = "app\pb\item\v1"; File = "item\v1\item.proto"; Package = "app.pb.item.v1" }
)

foreach ($Proto in $Protos) {
    $ProtoFile = Join-Path $ProtoRoot $Proto.File
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
        throw "grpc_tools.protoc failed for $($Proto.File) with exit code $LASTEXITCODE"
    }

    # protoc emits paths rooted at the proto package (llm/v1/...), which we
    # keep under app/pb; the generated grpc module imports its sibling pb2
    # module with a package-relative path, so rewrite the import to app.pb.
    $GeneratedDir = Join-Path $ServiceRoot $Proto.Out
    $Dots = ($Proto.Package -split "\.").Count
    $RelativePrefix = ("." * $Dots) + " "
    $GrpcFile = Get-ChildItem $GeneratedDir -Filter "*_pb2_grpc.py" | Select-Object -First 1
    if (-not $GrpcFile) {
        throw "Generated grpc module missing under $GeneratedDir"
    }
    $Content = Get-Content $GrpcFile.FullName -Raw
    $ImportPattern = "from " + (($Proto.File -replace "[\\/]", ".") -replace "\.proto$", "")
    $Content = $Content -replace [regex]::Escape("from " + (($Proto.Package -replace "\.", ".") -replace "^app\.pb\.", "")), "from app.pb."
    Set-Content $GrpcFile.FullName $Content -Encoding UTF8
}

Write-Host "Generated llm.v1 / item.v1 stubs under app/pb."
