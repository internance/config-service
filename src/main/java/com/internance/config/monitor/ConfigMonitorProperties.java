package com.internance.config.monitor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Configuration for the config-repo monitor webhook.
 *
 * <pre>
 * config:
 *   monitor:
 *     topic: config-changed          # Kafka topic the change events are published to
 *     webhook-secret: ${...}         # optional GitHub webhook HMAC secret; empty disables verification
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "config.monitor")
public class ConfigMonitorProperties {

    /** Kafka topic the {@code config.changed} events are published to. */
    @NotBlank
    private String topic = "config-changed";

    /**
     * Shared secret configured on the GitHub webhook. When non-blank, every
     * incoming request must carry a valid {@code X-Hub-Signature-256} header or it
     * is rejected with {@code 401}. Empty (default) disables verification.
     */
    private String webhookSecret = "";

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }
}
