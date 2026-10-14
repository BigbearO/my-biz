# xxs-nacos-grpc

A reusable Python library for calling gRPC services discovered from Nacos.

The library owns instance subscription, healthy-instance filtering, channel
rebuilding, round-robin load balancing, and user-context propagation. It does
not contain business proto definitions and does not depend on FastAPI.

## Usage

```python
from xxs_nacos_grpc import GrpcChannelRegistry, GrpcSettings

settings = GrpcSettings.from_mapping(
    {
        "grpc": {
            "discovery": {
                "services": {"example": "example-service-grpc"}
            }
        }
    }
)
registry = GrpcChannelRegistry(settings, nacos_instance_source)
await registry.start()

stub = registry.stub("example", ExampleServiceStub)
```

The instance source only needs to implement:

```python
async def subscribe_instances(service_name, callback): ...
async def list_healthy_instances(service_name): ...
```
