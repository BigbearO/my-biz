# goodfish-ai-service

FastAPI service skeleton integrated with an existing Nacos installation. This phase supports local configuration merging, optional Nacos configuration, and HTTP ephemeral instance registration.

Python 3.10 or later is required by `nacos-sdk-python` 3.2.0.

## Configuration

Copy `.env.example` to `.env` or `.env.dev`, then fill in the existing Nacos environment values. `NACOS_SERVICE_NAME` controls the service name registered in Nacos, and `NACOS_DATA_ID` can be empty.

Local runtime configuration is read from `LOCAL_CONFIG_PATH` and defaults to `config/application.yaml`. Nacos configuration is loaded only when `NACOS_DATA_ID` is not empty. The effective configuration is the deep union of both sources; for the same key, Nacos overrides local configuration.

Configuration placeholders such as `${DB_PASSWORD}` are expanded from process environment variables. Missing placeholders remain unchanged so the invalid value can be identified during startup or use.

## Startup

Prepare the virtual environment and dependencies yourself:

```powershell
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 1
```

The following commands also work:

```powershell
.\.venv\Scripts\python.exe -m app.main
.\.venv\Scripts\python.exe .\main.py
```

Do not directly run `app\main.py` from an IDE. It is a module inside the `app` package and must be loaded with the project root as the working directory.

The service registers a temporary HTTP instance after configuration is ready. On graceful shutdown it deregisters the instance before closing Nacos clients. `workers` must remain `1` because all workers would otherwise register the same address.

Health check:

```powershell
Invoke-RestMethod http://127.0.0.1:8000/health
```

## gRPC client discovery

For the full installation, discovery configuration, proto generation, and startup guide, see [docs/grpc-nacos.md](docs/grpc-nacos.md).

The service uses the reusable `xxs-nacos-grpc` package for downstream gRPC discovery. Configure each downstream by alias and Nacos service name:

```yaml
grpc:
  discovery:
    services:
      llm-model: goodfish-llm-service-grpc
```

The provider must register its gRPC port under exactly that Nacos service name in the same namespace and group. This Python service registers only its HTTP instance; it does not register a `-grpc` service in this phase.

Incoming `X-User-Id`, `X-User-Info`, and `Authorization` headers are propagated to unary gRPC calls. `X-Client-Id` is set from `grpc.client.client_id`.

Generate Python stubs from the existing LLM model proto manually:

```powershell
.\scripts\generate-llm-model-grpc.ps1
```

The script creates `admin\service\v1\llm_model_pb2.py` and `admin\service\v1\llm_model_pb2_grpc.py`. After generation, obtain the client stub with:

```python
from admin.service.v1 import llm_model_pb2_grpc

stub = request.app.state.grpc_registry.stub(
    "llm-model",
    llm_model_pb2_grpc.LlmModelStub,
)
```

## Tests

Unit tests use fake Nacos clients and do not access the remote environment:

```powershell
.\.venv\Scripts\pip install -r requirements-dev.txt
.\.venv\Scripts\python -m pytest
```
