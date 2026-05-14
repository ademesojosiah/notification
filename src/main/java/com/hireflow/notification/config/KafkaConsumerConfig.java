package com.hireflow.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hireflow.notification.event.EmailNotificationEvent;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

/**
 * Programmatic Kafka consumer wiring so the JsonDeserializer uses a JSR-310-aware
 * ObjectMapper. The properties-driven JsonDeserializer (set via
 * `spring.kafka.consumer.value-deserializer`) instantiates its own bare ObjectMapper
 * that doesn't know about `java.time.Instant`, which crashes deserialization of
 * EmailNotificationEvent.interviewStartTime/EndTime.
 *
 * Wrapping with ErrorHandlingDeserializer also keeps a single poison-pill record
 * from killing the consumer thread — the listener container's DefaultErrorHandler
 * can log/skip it instead of crashing the poll loop.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, EmailNotificationEvent> emailNotificationConsumerFactory(
            KafkaProperties kafkaProperties
    ) {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        JsonDeserializer<EmailNotificationEvent> jsonDeserializer =
                new JsonDeserializer<>(EmailNotificationEvent.class, objectMapper, false);
        jsonDeserializer.addTrustedPackages("com.hireflow.notification.event");

        ErrorHandlingDeserializer<EmailNotificationEvent> errorHandlingDeserializer =
                new ErrorHandlingDeserializer<>(jsonDeserializer);

        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        return new DefaultKafkaConsumerFactory<>(props, null, errorHandlingDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EmailNotificationEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, EmailNotificationEvent> emailNotificationConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, EmailNotificationEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(emailNotificationConsumerFactory);
        return factory;
    }
}
