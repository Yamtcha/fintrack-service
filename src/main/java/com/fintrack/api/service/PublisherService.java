package com.fintrack.api.service;

import com.fintrack.api.config.RabbitMQConfig;
import com.fintrack.common.events.TransactionIngestedEvent;
import com.fintrack.common.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import static com.fintrack.api.config.RabbitMQConfig.ROUTING_KEY_TRANSACTION;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublisherService {

    private final RabbitTemplate rabbitTemplate;

    public void publish(Transaction transaction) {

        TransactionIngestedEvent event = TransactionIngestedEvent.builder()
                .eventId(java.util.UUID.randomUUID())
                .occurredAt(java.time.Instant.now())
                .transaction(transaction)
                .build();

        // Simple retry logic for transient publish failures
        int maxAttempts = 3;
        long backoffMillis = 200L;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                rabbitTemplate.convertAndSend(RabbitMQConfig.TRANSACTIONS_EXCHANGE, ROUTING_KEY_TRANSACTION, event);
                log.debug("Published transaction externalId={} routingKey={} attempt={}", transaction.getExternalId(), ROUTING_KEY_TRANSACTION, attempt);
                return;
            } catch (Exception e) {
                log.warn("Failed to publish transaction externalId={} attempt={} error={}", transaction.getExternalId(), attempt, e.getMessage());
                if (attempt == maxAttempts) {
                    log.error("Giving up publishing transaction externalId={} after {} attempts", transaction.getExternalId(), maxAttempts, e);
                    throw e; // let caller handle failure (will be counted as failed transaction)
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
