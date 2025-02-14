package com.example;

import com.azure.monitor.opentelemetry.exporter.AzureMonitorExporterBuilder;
import com.azure.monitor.opentelemetry.exporter.implementation.ResourceAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.logs.GlobalLoggerProvider;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class TrackLogback {

  private static final String CONNECTION_STRING = "<Your Connection String>";
  private static final org.slf4j.Logger slf4jLogger = LoggerFactory.getLogger("slf4j-logger");

  public static void main(String[] args) throws InterruptedException {
    initOpenTelemetry();

    trackWithSlf4j();
    Thread.sleep(6000); // wait at least 5 seconds to give batch LogRecord processor time to export
  }

  /**
   * track with logback using slf4j
   */
  private static void trackWithSlf4j() {
    // Log using slf4j API w/ logback backend
    runWithASpan(
        () ->
            slf4jLogger
                .atWarn()
                .setMessage("trackWithSlf4j - a slf4j log message with custom attributes")
                .addKeyValue("key", "trackWithLogback")
                .log(), true);
    runWithASpan(() -> slf4jLogger.error("trackWithSlf4j - a slf4j log message 2 without custom attributes"), false);
  }

  /**
   * initialize OpenTelemetry using Azure Monitor OpenTelemetry Exporter
   */
  private static void initOpenTelemetry() {
    LogRecordExporter logRecordExporter = new AzureMonitorExporterBuilder()
        .connectionString(CONNECTION_STRING)
        .buildLogRecordExporter();
    OpenTelemetrySdk sdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder().setSampler(Sampler.alwaysOn()).build())
            .setLoggerProvider(
                SdkLoggerProvider.builder()
                    .setResource(
                        Resource.getDefault().toBuilder()
                            .put(ResourceAttributes.SERVICE_NAME, "my cloud role name")
                            .put(ResourceAttributes.SERVICE_INSTANCE_ID, "my cloud role instance")
                            .build())
                    .addLogRecordProcessor(
                        BatchLogRecordProcessor.builder(logRecordExporter).build())
                    .build())
            .build();
    GlobalOpenTelemetry.set(sdk);
    GlobalLoggerProvider.set(sdk.getSdkLoggerProvider());
  }

  static void runWithASpan(Runnable runnable, boolean withSpan) {
    if (!withSpan) {
      runnable.run();
      return;
    }
    Span span = GlobalOpenTelemetry.getTracer("my tracer name").spanBuilder("my span name").startSpan();
    try (Scope ignore = span.makeCurrent()) {
      MDC.put("MDC key", "MDC value");
      runnable.run();
      MDC.remove("MDC key");
    } finally {
      span.end();
    }
  }
}
