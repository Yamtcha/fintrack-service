package com.fintrack.api.service;

import com.fintrack.api.config.RabbitMQConfig;
import com.fintrack.common.domain.TransactionClass;
import com.fintrack.common.events.TransactionIngestedEvent;
import com.fintrack.common.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublisherService {

    private final RabbitTemplate rabbitTemplate;

    public void publish(Transaction transaction) {
        List<String> targetAggregators = resolveAggregators(transaction.getTransactionClass());

        TransactionIngestedEvent event = TransactionIngestedEvent.builder()
                .eventId(java.util.UUID.randomUUID())
                .occurredAt(java.time.Instant.now())
                .transaction(transaction)
                .targetAggregators(targetAggregators)
                .build();

        String routingKey = "transaction." + transaction.getTransactionClass().name();
        rabbitTemplate.convertAndSend(RabbitMQConfig.TRANSACTIONS_EXCHANGE, routingKey, event);
        log.debug("Published transaction externalId={} routingKey={}", transaction.getExternalId(), routingKey);
    }

    private List<String> resolveAggregators(TransactionClass transactionClass) {
        return switch (transactionClass) {
            case PAYMENT      -> List.of("spending");
            case CHARGE       -> List.of("spending", "debt");
            case DEBT_PAYMENT -> List.of("debt");
            case TRADE        -> List.of("portfolio");
        };
    }
}
