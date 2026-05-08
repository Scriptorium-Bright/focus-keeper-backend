package com.focuskeeper.reboot.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.focuskeeper.reboot.common.observability.dto.OperationsAlertResponse;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class WebhookOperationsAlertNotifierTest {

    @Test
    void enabledWebhookPostsLifecyclePayloadAndRecordsSuccessMetric() throws Exception {
        AtomicReference<String> capturedHeader = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = startServer(exchange -> {
            capturedHeader.set(exchange.getRequestHeaders().getFirst("X-Ops-Token"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        OperationsAlertWebhookProperties properties = enabledProperties(server);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OperationsMetricRecorder operationsMetricRecorder = new OperationsMetricRecorder(meterRegistry);

        WebhookOperationsAlertNotifier notifier = new WebhookOperationsAlertNotifier(
                properties,
                operationsMetricRecorder,
                RestClient.builder(),
                "rebootfocus-api"
        );

        try {
            notifier.notify(sampleEvent(OperationsAlertTransitionType.OPENED));

            assertThat(capturedHeader.get()).isEqualTo("phase4");
            assertThat(capturedBody.get()).contains("\"eventType\":\"OPENED\"");
            assertThat(capturedBody.get()).contains("\"service\":\"rebootfocus-api\"");
            assertThat(capturedBody.get()).contains("\"alertKey\":\"batch_failure:daily_kpi_pipeline:launch:demo-user\"");
            assertThat(
                    meterRegistry.get("reboot_ops_alert_notifications_total")
                            .tag("event", "OPENED")
                            .tag("result", "success")
                            .counter()
                            .count()
            ).isEqualTo(1.0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void webhookFailureDoesNotThrowAndRecordsFailureMetric() throws Exception {
        HttpServer server = startServer(exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        OperationsAlertWebhookProperties properties = enabledProperties(server);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OperationsMetricRecorder operationsMetricRecorder = new OperationsMetricRecorder(meterRegistry);

        WebhookOperationsAlertNotifier notifier = new WebhookOperationsAlertNotifier(
                properties,
                operationsMetricRecorder,
                RestClient.builder(),
                "rebootfocus-api"
        );

        try {
            notifier.notify(sampleEvent(OperationsAlertTransitionType.RESOLVED));

            assertThat(
                    meterRegistry.get("reboot_ops_alert_notifications_total")
                            .tag("event", "RESOLVED")
                            .tag("result", "failure")
                            .counter()
                            .count()
            ).isEqualTo(1.0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void webhookTimeoutDoesNotThrowAndRecordsFailureMetric() throws Exception {
        HttpServer server = startServer(exchange -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        OperationsAlertWebhookProperties properties = enabledProperties(server);
        properties.setReadTimeoutMs(50);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OperationsMetricRecorder operationsMetricRecorder = new OperationsMetricRecorder(meterRegistry);

        WebhookOperationsAlertNotifier notifier = new WebhookOperationsAlertNotifier(
                properties,
                operationsMetricRecorder,
                RestClient.builder(),
                "rebootfocus-api"
        );

        try {
            notifier.notify(sampleEvent(OperationsAlertTransitionType.REOPENED));

            assertThat(
                    meterRegistry.get("reboot_ops_alert_notifications_total")
                            .tag("event", "REOPENED")
                            .tag("result", "failure")
                            .counter()
                            .count()
            ).isEqualTo(1.0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void disabledWebhookDoesNothing() {
        OperationsAlertWebhookProperties properties = new OperationsAlertWebhookProperties();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        OperationsMetricRecorder operationsMetricRecorder = new OperationsMetricRecorder(meterRegistry);

        WebhookOperationsAlertNotifier notifier = new WebhookOperationsAlertNotifier(
                properties,
                operationsMetricRecorder,
                RestClient.builder(),
                "rebootfocus-api"
        );

        notifier.notify(sampleEvent(OperationsAlertTransitionType.OPENED));

        assertThat(meterRegistry.find("reboot_ops_alert_notifications_total").counters()).isEmpty();
    }

    private static OperationsAlertWebhookProperties enabledProperties(HttpServer server) {
        OperationsAlertWebhookProperties properties = new OperationsAlertWebhookProperties();
        properties.setEnabled(true);
        properties.setUrl("http://127.0.0.1:%d/hooks".formatted(server.getAddress().getPort()));
        properties.setHeaders(Map.of("X-Ops-Token", "phase4"));
        return properties;
    }

    private static HttpServer startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/hooks", handler);
        server.start();
        return server;
    }

    private static OperationsAlertTransitionEvent sampleEvent(OperationsAlertTransitionType eventType) {
        return new OperationsAlertTransitionEvent(
                eventType,
                "2026-05-08T10:00:00+09:00",
                "ACTIVE",
                "WARNING",
                new OperationsAlertResponse(
                        "batch_failure:daily_kpi_pipeline:launch:demo-user",
                        "daily_kpi_pipeline",
                        "launch",
                        "demo-user",
                        "CRITICAL",
                        true,
                        "ACTIVE",
                        "Daily KPI launch failed.",
                        Map.of("metricDate", "2026-05-08"),
                        "2026-05-08T09:55:00+09:00",
                        "2026-05-08T10:00:00+09:00",
                        null,
                        2,
                        0,
                        "2026-05-08T10:00:00+09:00"
                )
        );
    }
}
