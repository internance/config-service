package com.internance.config.event;

import com.internance.common.kafka.event.DomainEvent;
import java.util.Set;

/**
 * Domain event published whenever the backing config Git repository changes for a
 * given application. It is emitted once per affected application after a push to
 * the config repo is received on the {@code /monitor} webhook.
 *
 * <p>Wrapped in the standard {@code EventEnvelope} and sent through common-lib's
 * {@code EventPublisher}; consumers (the config clients) read it back as
 * {@code EventEnvelope<ConfigChangedEvent>} and trigger a context refresh for the
 * matching application.
 *
 * @param application the config application whose files changed (e.g. {@code auth-service}),
 *                    derived from the top-level directory of the changed paths
 * @param label       the Git branch/label the change landed on (e.g. {@code main})
 * @param paths       the changed file paths belonging to {@code application}
 * @param commitId    the head commit SHA of the push, or {@code null} if unknown
 */
public record ConfigChangedEvent(String application, String label, Set<String> paths, String commitId)
        implements DomainEvent {

    /** Stable wire type name, mirrored onto the {@code x-event-type} Kafka header. */
    public static final String EVENT_TYPE = "config.changed";

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    public ConfigChangedEvent {
        if (application == null || application.isBlank()) {
            throw new IllegalArgumentException("application must not be null or blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label must not be null or blank");
        }
        if (paths == null) {
            throw new IllegalArgumentException("paths must not be null");
        }
        paths = Set.copyOf(paths);
    }
}
