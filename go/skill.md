# fixgow — Fix GoWind Generated Service Module

When `gow add service <name> -s rest -s grpc -d ent -d redis` generates a service module that doesn't compile, use this skill.

## Problem

After running `gow new <project>` (creates a working admin module) followed by `gow add service <name> -s rest -s grpc -d ent -d redis`, the generated service module has these issues:

1. `wire_gen.go` missing → `initApp` undefined in `main.go`
2. `rest_server.go` has broken auth imports referencing non-existent packages
3. `data/providers/wire_set.go` references `data.NewEntClient` and `data.NewRedisClient` that don't exist
4. `service/providers/wire_set.go` is empty (no service constructors)
5. `server/providers/wire_set.go` missing middleware constructors
6. Missing `openapi.yaml` for `//go:embed`
7. Missing `go-crud` and `kratos-bootstrap/database/ent` dependencies
8. `data/` directory has no source files (no `ent_client.go`, `redis_client.go`)
9. No ent schema → can't generate ent code → can't use go-crud/entgo
10. `main.go` may have unused imports (e.g., `registry`) that block wire generation

## Fix Steps (10 steps)

### Step 1 — Add go-crud + ent dependencies

```bash
cd <project-root>
go get github.com/tx7do/go-crud/entgo@v0.0.51
go get github.com/tx7do/go-crud/api@v0.0.7
go get github.com/tx7do/go-crud/pagination@v0.0.14
go get github.com/tx7do/go-crud/viewer@v0.0.6
go get github.com/tx7do/kratos-bootstrap/database/ent@v0.1.5
```

### Step 2 — Create ent schema + generate ent code

Create `app/<name>/service/internal/data/ent/schema/greeter.go`:

```go
package schema

import (
    "entgo.io/ent"
    "entgo.io/ent/dialect/entsql"
    "entgo.io/ent/schema"
    "entgo.io/ent/schema/field"
)

type Greeter struct{ ent.Schema }

func (Greeter) Annotations() []schema.Annotation {
    return []schema.Annotation{
        entsql.Annotation{Table: "<name>_greeters"},
    }
}

func (Greeter) Fields() []ent.Field {
    return []ent.Field{
        field.String("name").Comment("名称"),
    }
}
```

Run ent code generation:

```bash
gow ent <name>
```

### Step 3 — Create `redis_client.go`

Create `app/<name>/service/internal/data/redis_client.go`:

```go
package data

import (
    "github.com/redis/go-redis/v9"
    "github.com/tx7do/kratos-bootstrap/bootstrap"
    redisClient "github.com/tx7do/kratos-bootstrap/cache/redis"
)

func NewRedisClient(ctx *bootstrap.Context) (*redis.Client, func(), error) {
    cfg := ctx.GetConfig()
    if cfg == nil {
        return nil, func() {}, nil
    }
    l := ctx.NewLoggerHelper("redis/data/<name>-service")
    cli := redisClient.NewClient(cfg.Data, l)
    return cli, func() {
        if err := cli.Close(); err != nil {
            l.Error(err)
        }
    }, nil
}
```

### Step 4 — Create `ent_client.go` (go-crud wrapper)

Create `app/<name>/service/internal/data/ent_client.go`:

```go
package data

import (
    "entgo.io/ent/dialect/sql"
    _ "github.com/go-sql-driver/mysql"
    _ "github.com/lib/pq"
    entCrud "github.com/tx7do/go-crud/entgo"
    "github.com/tx7do/kratos-bootstrap/bootstrap"
    entBootstrap "github.com/tx7do/kratos-bootstrap/database/ent"
    "<module>/app/<name>/service/internal/data/ent"
    "<module>/app/<name>/service/internal/data/ent/migrate"
)

func NewEntClient(ctx *bootstrap.Context) (*entCrud.EntClient[*ent.Client], func(), error) {
    l := ctx.NewLoggerHelper("ent/data/<name>-service")
    cfg := ctx.GetConfig()
    if cfg == nil || cfg.Data == nil {
        l.Warn("[ENT] no config found")
        return nil, func() {}, nil
    }
    cli, err := entBootstrap.NewEntClient(cfg, func(drv *sql.Driver) *ent.Client {
        client := ent.NewClient(
            ent.Driver(drv),
            ent.Log(func(a ...any) { l.Debug(a...) }),
        )
        if err != nil {
            l.Fatalf("[ENT] failed creating ent client")
            return nil
        }
        if cfg.Data.Database.GetMigrate() {
            if err := client.Schema.Create(ctx.Context(), migrate.WithForeignKeys(true)); err != nil {
                l.Fatalf("[ENT] failed creating schema: %v", err)
            }
        }
        return client
    })
    if err != nil {
        l.Fatalf("[ENT] failed creating ent client: %v", err)
        return nil, func() {}, err
    }
    return cli, func() {
        if cleanErr := cli.Close(); cleanErr != nil {
            l.Errorf("[ENT] failed closing ent client: %v", cleanErr)
        }
    }, nil
}
```

### Step 5 — Rewrite `rest_server.go` (remove auth)

Fully rewrite `app/<name>/service/internal/server/rest_server.go`.

**Important**: The helloworld proto (`greeter.proto`) only generates gRPC registration (`RegisterGreeterServer`), not HTTP registration. The HTTP registration (`RegisterGreeterHTTPServer`) comes from the admin service's proto (`i_greeter.proto`) which has `google.api.http` annotations. So use `adminV1` for HTTP:

```go
package server

import (
    "github.com/go-kratos/kratos/v2/middleware"
    "github.com/go-kratos/kratos/v2/middleware/logging"
    "github.com/go-kratos/kratos/v2/transport/http"
    swaggerUI "github.com/tx7do/kratos-swagger-ui"
    "github.com/tx7do/kratos-bootstrap/bootstrap"
    "github.com/tx7do/kratos-bootstrap/rpc"
    "<module>/app/<name>/service/cmd/server/assets"
    "<module>/app/<name>/service/internal/service"
    adminV1 "<module>/api/gen/go/admin/service/v1"
)

type RestMiddlewares []middleware.Middleware

func NewRestMiddleware(ctx *bootstrap.Context) RestMiddlewares {
    var ms []middleware.Middleware
    ms = append(ms, logging.Server(ctx.GetLogger()))
    return ms
}

func NewRestServer(
    ctx *bootstrap.Context,
    middlewares RestMiddlewares,
    greeterService *service.GreeterService,
) *http.Server {
    cfg := ctx.GetConfig()
    if cfg == nil || cfg.Server == nil || cfg.Server.Rest == nil {
        return nil
    }
    srv, err := rpc.CreateRestServer(cfg, middlewares...)
    if err != nil {
        panic(err)
    }
    if cfg.GetServer().GetRest().GetEnableSwagger() {
        swaggerUI.RegisterSwaggerUIServerWithOption(
            srv,
            swaggerUI.WithTitle("<name> Service"),
            swaggerUI.WithMemoryData(assets.OpenApiData, "yaml"),
        )
    }
    adminV1.RegisterGreeterHTTPServer(srv, greeterService)
    return srv
}
```

### Step 6 — Rewrite `grpc_server.go`

Fully rewrite `app/<name>/service/internal/server/grpc_server.go`. The helloworld proto does generate gRPC server registration (`RegisterGreeterServer`), so use `helloworldV1`:

```go
package server

import (
    "github.com/go-kratos/kratos/v2/middleware"
    "github.com/go-kratos/kratos/v2/middleware/logging"
    "github.com/go-kratos/kratos/v2/transport/grpc"
    "github.com/tx7do/kratos-bootstrap/bootstrap"
    "github.com/tx7do/kratos-bootstrap/rpc"
    "<module>/app/<name>/service/internal/service"
    helloworldV1 "<module>/api/gen/go/helloworld/service/v1"
)

type GrpcMiddlewares []middleware.Middleware

func NewGrpcMiddleware(ctx *bootstrap.Context) GrpcMiddlewares {
    var ms []middleware.Middleware
    ms = append(ms, logging.Server(ctx.GetLogger()))
    return ms
}

func NewGrpcServer(
    ctx *bootstrap.Context,
    middlewares GrpcMiddlewares,
    greeterService *service.GreeterService,
) *grpc.Server {
    cfg := ctx.GetConfig()
    if cfg == nil || cfg.Server == nil || cfg.Server.Grpc == nil {
        return nil
    }
    srv, err := rpc.CreateGrpcServer(cfg, middlewares...)
    if err != nil {
        panic(err)
    }
    helloworldV1.RegisterGreeterServer(srv, greeterService)
    return srv
}
```

### Step 7 — Create GreeterService + GreeterRepo

Create `app/<name>/service/internal/service/greeter_service.go`:

```go
package service

import (
    "context"
    "github.com/go-kratos/kratos/v2/log"
    "github.com/tx7do/kratos-bootstrap/bootstrap"
    "<module>/app/<name>/service/internal/data"
    helloworldV1 "<module>/api/gen/go/helloworld/service/v1"
)

type GreeterService struct {
    helloworldV1.UnimplementedGreeterServer
    log  *log.Helper
    repo *data.GreeterRepo
}

func NewGreeterService(ctx *bootstrap.Context, repo *data.GreeterRepo) *GreeterService {
    return &GreeterService{
        repo: repo,
        log:  ctx.NewLoggerHelper("greeter/service/<name>-service"),
    }
}

func (s *GreeterService) SayHello(ctx context.Context, req *helloworldV1.HelloRequest) (*helloworldV1.HelloReply, error) {
    return s.repo.SayHello(ctx, req)
}
```

Create `app/<name>/service/internal/data/greeter_repo.go`:

```go
package data

import (
    "context"
    "github.com/go-kratos/kratos/v2/log"
    "github.com/tx7do/kratos-bootstrap/bootstrap"
    helloworldV1 "<module>/api/gen/go/helloworld/service/v1"
)

type GreeterRepo struct {
    log *log.Helper
}

func NewGreeterRepo(ctx *bootstrap.Context) *GreeterRepo {
    return &GreeterRepo{
        log: ctx.NewLoggerHelper("greeter/repo/<name>-service"),
    }
}

func (r *GreeterRepo) SayHello(_ context.Context, req *helloworldV1.HelloRequest) (*helloworldV1.HelloReply, error) {
    return &helloworldV1.HelloReply{Message: "Hello " + req.GetName()}, nil
}
```

### Step 8 — Fix wire_set.go files (3 files)

**`data/providers/wire_set.go`** — add repo constructors:

```go
var ProviderSet = wire.NewSet(
    data.NewRedisClient,
    data.NewEntClient,
    data.NewGreeterRepo,
)
```

**`server/providers/wire_set.go`** — add middleware constructors:

```go
var ProviderSet = wire.NewSet(
    server.NewRestMiddleware,
    server.NewRestServer,
    server.NewGrpcMiddleware,
    server.NewGrpcServer,
)
```

**`service/providers/wire_set.go`** — add service constructor:

```go
var ProviderSet = wire.NewSet(
    service.NewGreeterService,
)
```

### Step 9a — Add middleware config + change ports in `server.yaml`

Edit `app/<name>/service/configs/server.yaml`:

- **Change ports** to avoid conflict with admin service (which uses `:7788` / `:8899`)
- **Add `rest.middleware`** section (generated file is missing this)

```yaml
server:
  rest:
    addr: ":7799"              # change from :7788
    timeout: 10s
    enable_swagger: true
    enable_pprof: true
    cors:
      headers:
        - "X-Requested-With"
        - "Content-Type"
        - "Authorization"
      methods:
        - "GET"
        - "POST"
        - "PUT"
        - "DELETE"
        - "HEAD"
        - "OPTIONS"
      origins:
        - "*"
    middleware:                  # add this section
      enable_logging: true
      enable_recovery: true
      enable_tracing: true
      enable_validate: true
      enable_circuit_breaker: true
      enable_metadata: true

  grpc:
    addr: ":8900"              # change from :8899
    timeout: 10s
    middleware:
```

**Note**: Don't include the `auth:` block in middleware — we've removed auth from the service.

### Step 9b — Create `openapi.yaml` placeholder

Create `app/<name>/service/cmd/server/assets/openapi.yaml`:

```yaml
openapi: "3.0.0"
info:
  title: <name> Service
  version: "1.0.0"
paths: {}
```

### Step 10 — Generate wire_gen.go

First clean `main.go` — remove any unused imports (especially `"github.com/go-kratos/kratos/v2/registry"`).

Then run wire:

```bash
gow wire <name>
```

### Verify

```bash
go build ./app/<name>/service/cmd/server/...
```

## Notes

- **HTTP vs gRPC registration**: The `helloworld` proto only generates gRPC code (`RegisterGreeterServer`). HTTP registration (`RegisterGreeterHTTPServer`) is in the `admin` proto package which has `google.api.http` annotations. For the new service's HTTP server, import `adminV1`.
- **Wire only resolves needed deps**: `NewRedisClient` and `NewEntClient` are in the data provider set but Wire won't instantiate them until a repo actually depends on them. The `GreeterRepo` doesn't use ent/redis, so wire_gen.go is clean with just the Greeter chain.
- **Unused imports block wire**: `main.go` may have `"github.com/go-kratos/kratos/v2/registry"` imported but not used — remove it before running `gow wire`.
- **go-crud/entgo dependency**: The `ent_client.go` uses `entCrud.EntClient[*ent.Client]`. This requires `*ent.Client` type which comes from the generated ent code (Step 2).
- The ent schema `greeter.go` is a minimal valid schema. Replace/add schemas as needed.
- If MySQL driver is not used, replace `_ "github.com/go-sql-driver/mysql"` with the appropriate driver.
- The module path `<module>` and service name `<name>` are placeholders — replace with actual values.
