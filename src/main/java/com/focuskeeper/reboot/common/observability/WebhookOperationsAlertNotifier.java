package com.focuskeeper.reboot.common.observability;

import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WebhookOperationsAlertNotifier implements OperationsAlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookOperationsAlertNotifier.class);

    private final OperationsAlertWebhookProperties properties;
    private final OperationsMetricRecorder operationsMetricRecorder;
    private final String serviceName;
    private final RestClient restClient;

    public WebhookOperationsAlertNotifier(
            OperationsAlertWebhookProperties properties,
            OperationsMetricRecorder operationsMetricRecorder,
            RestClient.Builder restClientBuilder,
            @Value("${spring.application.name:rebootfocus-api}") String serviceName
    ) {
        this.properties = properties;
        this.operationsMetricRecorder = operationsMetricRecorder;
        this.serviceName = serviceName;
        this.restClient = buildRestClient(restClientBuilder);
    }

    @Override
    public void notify(OperationsAlertTransitionEvent event) {
        if (!properties.isEnabled() || properties.getUrl() == null || properties.getUrl().isBlank()) {
            return;
        }

        try {
            restClient.post()
                    .uri(properties.getUrl())
                    .body(new WebhookPayload(
                            event.eventType().name(),
                            serviceName,
                            event.emittedAt(),
                            event.previousStatus(),
                            event.previousSeverity(),
                            event.alert()
                    ))
                    .retrieve()
                    .toBodilessEntity();
            operationsMetricRecorder.recordAlertNotification(event.eventType().name(), "success");
        } catch (Exception exception) {
            operationsMetricRecorder.recordAlertNotification(event.eventType().name(), "failure");
            log.warn(
                    "ops webhook notifier failed eventType={} url={} reason={}",
                    event.eventType(),
                    properties.getUrl(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    private RestClient buildRestClient(RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));

        RestClient.Builder builder = restClientBuilder.requestFactory(requestFactory);
        for (Map.Entry<String, String> header : properties.getHeaders().entrySet()) {
            builder.defaultHeader(header.getKey(), header.getValue());
        }
        return builder.build();
    }

    private record WebhookPayload(
            String eventType,
            String service,
            String emittedAt,
            String previousStatus,
            String previousSeverity,
            Object alert
    ) {
    }
}
