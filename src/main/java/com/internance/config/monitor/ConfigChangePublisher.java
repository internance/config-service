package com.internance.config.monitor;

import com.internance.common.kafka.event.EventPublisher;
import com.internance.config.event.ConfigChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Publishes {@link ConfigChangedEvent}s to Kafka through common-lib's
 * {@link EventPublisher}. The application name is used as the partition key so
 * changes for the same application keep their relative order.
 */
@Component
public class ConfigChangePublisher {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangePublisher.class);

    private final EventPublisher eventPublisher;
    private final ConfigMonitorProperties properties;

    public ConfigChangePublisher(EventPublisher eventPublisher, ConfigMonitorProperties properties) {
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /**
     * Publishes a single change event for {@code application} to the configured
     * topic, keyed by the application name.
     */
    public void publish(String application, String label, Set<String> paths, String commitId) {
        ConfigChangedEvent event = new ConfigChangedEvent(application, label, paths, commitId);
        log.info("Publishing config change: application={} label={} paths={} commit={}",
                application, label, paths.size(), commitId);
        eventPublisher.publish(properties.getTopic(), application, event);
    }
}
