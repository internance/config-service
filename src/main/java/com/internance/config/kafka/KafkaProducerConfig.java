package com.internance.config.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Configures the producer side so common-lib's {@code EventPublisher} (auto-wired
 * by {@code CommonKafkaPublisherAutoConfiguration} once a {@code KafkaTemplate}
 * exists) can serialize the {@code EventEnvelope} as JSON.
 *
 * <p>The serializer is built from the application's Jackson {@link ObjectMapper}
 * (which Spring Boot has already configured with the JSR-310 module) so the
 * envelope's {@code Instant occurredAt} serializes correctly. Type-id headers are
 * disabled — consumers deserialize through their listener method signature
 * ({@code EventEnvelope<ConfigChangedEvent>}), not from an embedded class name, so
 * the wire payload stays clean JSON and isn't coupled to this service's classes.
 *
 * <p>Registered as a {@link DefaultKafkaProducerFactoryCustomizer} so it composes
 * with the user-context interceptor customizer common-lib contributes — Spring
 * Boot applies every such bean to the auto-configured producer factory.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    public DefaultKafkaProducerFactoryCustomizer configEventValueSerializerCustomizer(ObjectMapper objectMapper) {
        JsonSerializer<Object> valueSerializer = new JsonSerializer<>(objectMapper);
        valueSerializer.setAddTypeInfo(false);
        return factory -> {
            @SuppressWarnings("unchecked")
            DefaultKafkaProducerFactory<String, Object> typed = (DefaultKafkaProducerFactory<String, Object>) factory;
            typed.setValueSerializer(valueSerializer);
        };
    }
}
