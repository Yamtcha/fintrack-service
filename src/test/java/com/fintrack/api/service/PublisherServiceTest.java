package com.fintrack.api.service;

import com.fintrack.common.model.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublisherServiceTest {

    @Mock
    RabbitTemplate rabbitTemplate;

    @InjectMocks
    PublisherService publisherService;

    @Test
    void publish_retriesUntilSuccess() {
        doThrow(new RuntimeException("err1"))
                .doThrow(new RuntimeException("err2"))
                .doNothing()
                .when(rabbitTemplate).convertAndSend(eq(com.fintrack.api.config.RabbitMQConfig.TRANSACTIONS_EXCHANGE), eq(com.fintrack.api.config.RabbitMQConfig.ROUTING_KEY_TRANSACTION), any(Object.class));

        Transaction tx = new Transaction();
        tx.setExternalId("ext-1");

        publisherService.publish(tx);

        verify(rabbitTemplate, times(3)).convertAndSend(eq(com.fintrack.api.config.RabbitMQConfig.TRANSACTIONS_EXCHANGE), eq(com.fintrack.api.config.RabbitMQConfig.ROUTING_KEY_TRANSACTION), any(Object.class));
    }

    @Test
    void publish_throwsAfterMaxRetries() {
        doThrow(new RuntimeException("err1"))
                .when(rabbitTemplate).convertAndSend(eq(com.fintrack.api.config.RabbitMQConfig.TRANSACTIONS_EXCHANGE), eq(com.fintrack.api.config.RabbitMQConfig.ROUTING_KEY_TRANSACTION), any(Object.class));

        Transaction tx = new Transaction();
        tx.setExternalId("ext-2");

        assertThrows(RuntimeException.class, () -> publisherService.publish(tx));

        verify(rabbitTemplate, atLeast(1)).convertAndSend(eq(com.fintrack.api.config.RabbitMQConfig.TRANSACTIONS_EXCHANGE), eq(com.fintrack.api.config.RabbitMQConfig.ROUTING_KEY_TRANSACTION), any(Object.class));
    }
}
