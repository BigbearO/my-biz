import logging
from typing import Any

from opentelemetry import trace
from opentelemetry._logs import set_logger_provider
from opentelemetry.exporter.otlp.proto.grpc._log_exporter import OTLPLogExporter
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.exporter.otlp.proto.http._log_exporter import OTLPLogExporter as HttpOTLPLogExporter
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter as HttpOTLPSpanExporter
from opentelemetry.instrumentation.grpc import aio_client_interceptors
from opentelemetry.propagate import set_global_textmap
from opentelemetry.propagators.composite import CompositePropagator
from opentelemetry.sdk._logs import LoggerProvider, LoggingHandler
from opentelemetry.sdk._logs.export import BatchLogRecordProcessor
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.sdk.trace.sampling import ParentBased, TraceIdRatioBased
from opentelemetry.baggage.propagation import W3CBaggagePropagator
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator

from ..constants import (
    OPENTELEMETRY_EXPORT_PROTOCOL_HTTP,
    OPENTELEMETRY_FLUSH_TIMEOUT_MS,
    OPENTELEMETRY_LOG_ENDPOINT_PATH,
    OPENTELEMETRY_SECURE_SCHEME,
    OPENTELEMETRY_TRACE_ENDPOINT_PATH,
    SERVICE_VERSION,
)
from .logging import TelemetryLogFilter
from .settings import OpenTelemetrySettings

logger = logging.getLogger(__name__)


class TelemetryManager:
    def __init__(
        self,
        settings: OpenTelemetrySettings,
        environment: str,
        default_service_name: str,
    ) -> None:
        self._settings = settings
        self._environment = environment
        self._default_service_name = default_service_name
        self._tracer_provider: TracerProvider | None = None
        self._logger_provider: LoggerProvider | None = None
        self._log_handler: LoggingHandler | None = None
        self._started = False

    @property
    def settings(self) -> OpenTelemetrySettings:
        return self._settings

    @property
    def started(self) -> bool:
        return self._started

    def start(self) -> None:
        if not self._settings.enabled or self._started:
            return
        set_global_textmap(
            CompositePropagator(
                [
                    TraceContextTextMapPropagator(),
                    W3CBaggagePropagator(),
                ]
            )
        )
        resource = self._create_resource()
        if self._settings.traces.enabled:
            self._start_traces(resource)
        if self._settings.logs.enabled:
            self._start_logs(resource)
        self._started = True
        logger.info(
            "OpenTelemetry started: service=%s traces=%s logs=%s protocol=%s",
            self._service_name(),
            self._settings.traces.enabled,
            self._settings.logs.enabled,
            self._settings.exporter.protocol or "default",
        )

    def grpc_client_interceptors(self) -> list[Any]:
        if not self._started or not self._settings.traces.enabled:
            return []
        return aio_client_interceptors()

    async def close(self) -> None:
        if not self._started:
            return
        if self._logger_provider is not None:
            try:
                self._logger_provider.force_flush(
                    timeout_millis=OPENTELEMETRY_FLUSH_TIMEOUT_MS,
                )
                self._logger_provider.shutdown()
            except Exception:
                logger.exception("Failed to flush OpenTelemetry logs")
        if self._tracer_provider is not None:
            try:
                self._tracer_provider.force_flush(
                    timeout_millis=OPENTELEMETRY_FLUSH_TIMEOUT_MS,
                )
                self._tracer_provider.shutdown()
            except Exception:
                logger.exception("Failed to flush OpenTelemetry traces")
        self._remove_log_handler()
        self._logger_provider = None
        self._tracer_provider = None
        self._started = False

    def _start_traces(self, resource: Resource) -> None:
        sampler = ParentBased(
            TraceIdRatioBased(self._settings.traces.sampling_ratio),
        )
        provider = TracerProvider(resource=resource, sampler=sampler)
        provider.add_span_processor(
            BatchSpanProcessor(
                self._create_span_exporter(),
                schedule_delay_millis=self._settings.traces.schedule_delay_millis,
                max_export_timeout_millis=self._settings.traces.export_timeout_millis,
            )
        )
        trace.set_tracer_provider(provider)
        self._tracer_provider = provider

    def _start_logs(self, resource: Resource) -> None:
        provider = LoggerProvider(resource=resource)
        provider.add_log_record_processor(
            BatchLogRecordProcessor(
                self._create_log_exporter(),
                schedule_delay_millis=self._settings.logs.schedule_delay_millis,
                max_export_timeout_millis=self._settings.logs.export_timeout_millis,
            )
        )
        set_logger_provider(provider)
        handler = LoggingHandler(
            level=logging.INFO,
            logger_provider=provider,
        )
        handler.addFilter(TelemetryLogFilter())
        logging.getLogger().addHandler(handler)
        self._logger_provider = provider
        self._log_handler = handler

    def _create_resource(self) -> Resource:
        return Resource.create(
            {
                "service.name": self._service_name(),
                "service.version": SERVICE_VERSION,
                "deployment.environment.name": self._environment,
            }
        )

    def _create_span_exporter(self) -> Any:
        endpoint = self._signal_endpoint(
            self._settings.exporter.trace_endpoint,
            OPENTELEMETRY_TRACE_ENDPOINT_PATH,
        )
        if self._settings.exporter.protocol == OPENTELEMETRY_EXPORT_PROTOCOL_HTTP:
            return HttpOTLPSpanExporter(
                endpoint=endpoint or None,
                headers=self._settings.exporter.headers or None,
                timeout=self._settings.traces.export_timeout_millis / 1000,
            )
        return OTLPSpanExporter(
            endpoint=endpoint or None,
            headers=self._settings.exporter.headers or None,
            insecure=self._resolve_insecure(endpoint),
            timeout=self._settings.traces.export_timeout_millis / 1000,
        )

    def _create_log_exporter(self) -> Any:
        endpoint = self._signal_endpoint(
            self._settings.exporter.log_endpoint,
            OPENTELEMETRY_LOG_ENDPOINT_PATH,
        )
        if self._settings.exporter.protocol == OPENTELEMETRY_EXPORT_PROTOCOL_HTTP:
            return HttpOTLPLogExporter(
                endpoint=endpoint or None,
                headers=self._settings.exporter.headers or None,
                timeout=self._settings.logs.export_timeout_millis / 1000,
            )
        return OTLPLogExporter(
            endpoint=endpoint or None,
            headers=self._settings.exporter.headers or None,
            insecure=self._resolve_insecure(endpoint),
            timeout=self._settings.logs.export_timeout_millis / 1000,
        )

    def _signal_endpoint(self, configured_endpoint: str, http_path: str) -> str:
        endpoint = configured_endpoint or self._settings.exporter.endpoint
        if (
            self._settings.exporter.protocol != OPENTELEMETRY_EXPORT_PROTOCOL_HTTP
            or not endpoint
            or endpoint.rstrip("/").endswith(http_path)
        ):
            return endpoint
        return endpoint.rstrip("/") + http_path

    def _resolve_insecure(self, endpoint: str) -> bool | None:
        if self._settings.exporter.insecure is not None:
            return self._settings.exporter.insecure
        if not endpoint:
            return None
        return not endpoint.startswith(OPENTELEMETRY_SECURE_SCHEME)

    def _remove_log_handler(self) -> None:
        if self._log_handler is None:
            return
        logging.getLogger().removeHandler(self._log_handler)
        self._log_handler.close()
        self._log_handler = None

    def _service_name(self) -> str:
        return self._settings.service_name or self._default_service_name
