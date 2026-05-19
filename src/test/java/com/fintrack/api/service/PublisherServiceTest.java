package com.fintrack.api.service;

import com.fintrack.common.model.Transaction;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublisherServiceTest {

    private static final String EXCHANGE = "fintrack.transactions";
    private static final String ROUTING_KEY = "transaction.TRANSACTION";

    @Mock
    RabbitTemplate rabbitTemplate;

    private PublisherService publisherService;

    @BeforeEach
    void setUp() {
        publisherService = new PublisherService(rabbitTemplate, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(publisherService, "exchange", EXCHANGE);
        ReflectionTestUtils.setField(publisherService, "routingKey", ROUTING_KEY);
    }

    @Test
    void publish_retriesUntilSuccess() {
        doThrow(new RuntimeException("err1"))
                .doThrow(new RuntimeException("err2"))
                .doNothing()
                .when(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), any(Object.class));

        Transaction tx = new Transaction();
        tx.setExternalId("ext-1");

        publisherService.publish(tx);

        verify(rabbitTemplate, times(3)).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), any(Object.class));
    }

    @Test
    void publish_throwsAfterMaxRetries() {
        doThrow(new RuntimeException("err1"))
                .when(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), any(Object.class));

        Transaction tx = new Transaction();
        tx.setExternalId("ext-2");

        assertThrows(RuntimeException.class, () -> publisherService.publish(tx));

        verify(rabbitTemplate, atLeast(1)).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), any(Object.class));
    }
}
