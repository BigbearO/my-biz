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

## Tests

Unit tests use fake Nacos clients and do not access the remote environment:

```powershell
.\.venv\Scripts\pip install -r requirements-dev.txt
.\.venv\Scripts\python -m pytest
```
