package com.bigbear.spring.oltp;


import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.ServiceAttributes;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Log4j2
@Configuration
public class TraceConfig {

    private static final String ENDPOINT_V2_SPANS = "/api/v2/spans";

    @Value("${spring.application.name}")
    private String name;


    @Bean
    public OpenTelemetry openTelemetry() {
        SpanProcessor spanProcessor = getOtlpProcessor();
        Resource serviceNameResource = Resource.create(
            Attributes.of(ServiceAttributes.SERVICE_NAME, name));


        SdkTracerProvider tracerProvider =
            SdkTracerProvider.builder()
                .addSpanProcessor(spanProcessor)
                .setResource(Resource.getDefault().merge(serviceNameResource))
                .build();
        OpenTelemetrySdk openTelemetry =
            OpenTelemetrySdk.builder().setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();

        // add a shutdown hook to shut down the SDK
        Runtime.getRuntime().addShutdownHook(new Thread(tracerProvider::close));

        // return the configured instance so it can be used for instrumentation.
        return openTelemetry;
    }

    private SpanProcessor getOtlpProcessor() {
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
            .setTimeout(2, TimeUnit.SECONDS).build();
        return BatchSpanProcessor.builder(spanExporter)
            .setScheduleDelay(100, TimeUnit.MILLISECONDS)
            .build();

    }
}
