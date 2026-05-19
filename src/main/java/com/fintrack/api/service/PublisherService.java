package com.fintrack.api.service;

import com.fintrack.common.events.TransactionIngestedEvent;
import com.fintrack.common.model.Transaction;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublisherService {

    private final RabbitTemplate rabbitTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${fintrack.rabbitmq.exchange}")
    private String exchange;

    @Value("${fintrack.rabbitmq.routing-key-transaction}")
    private String routingKey;

    public void publish(Transaction transaction) {

        TransactionIngestedEvent event = TransactionIngestedEvent.builder()
                .eventId(java.util.UUID.randomUUID())
                .occurredAt(java.time.Instant.now())
                .transaction(transaction)
                .build();

        int maxAttempts = 3;
        long backoffMillis = 200L;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                rabbitTemplate.convertAndSend(exchange, routingKey, event);
                log.debug("Published transaction externalId={} routingKey={} attempt={}", transaction.getExternalId(), routingKey, attempt);
                meterRegistry.counter("fintrack.publish.transactions", "result", "success").increment();
                return;
            } catch (Exception e) {
                log.warn("Failed to publish transaction externalId={} attempt={} error={}", transaction.getExternalId(), attempt, e.getMessage());
                if (attempt == maxAttempts) {
                    log.error("Giving up publishing transaction externalId={} after {} attempts", transaction.getExternalId(), maxAttempts, e);
                    meterRegistry.counter("fintrack.publish.transactions", "result", "failure").increment();
                    throw e;
                }
                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while retrying publish", ie);
                }
                backoffMillis *= 2;
            }
        }
    }
}
