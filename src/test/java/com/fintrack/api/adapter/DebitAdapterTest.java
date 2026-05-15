package com.fintrack.api.adapter;

import com.fintrack.api.domain.ApiKeyScope;
import com.fintrack.api.dto.request.TransactionDto;
import com.fintrack.api.security.SourceIdentity;
import com.fintrack.common.domain.SourceType;
import com.fintrack.common.domain.TransactionClass;
import com.fintrack.common.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DebitAdapterTest {

    private DebitAdapter adapter;

    private final UUID sourceId = UUID.randomUUID();
    private final SourceIdentity identity = new SourceIdentity(
            sourceId, "Chase Checking", SourceType.DEBIT, ApiKeyScope.READ_WRITE);

    @BeforeEach
    void setUp() {
        adapter = new DebitAdapter();
    }

    @Test
    void supports_returnsDebit() {
        assertThat(adapter.supports()).isEqualTo(SourceType.DEBIT);
    }

    @Test
    void adapt_alwaysProducesPayment() {
        TransactionDto raw = new TransactionDto(
                "TXN-001", 4999L, "ZAR","WOOLWORTHS", "WOOLWORTHS SANDTON", Instant.now(), null);

        Transaction result = adapter.adapt(raw, identity);

        assertThat(result.getTransactionClass()).isEqualTo(TransactionClass.PAYMENT);
        assertThat(result.getExternalId()).isEqualTo("TXN-001");
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("49.99"));
        assertThat(result.getCurrency()).isEqualTo("ZAR");
        assertThat(result.getSourceId()).isEqualTo(sourceId.toString());
        assertThat(result.getSourceType()).isEqualTo(SourceType.DEBIT);
    }

    @Test
    void adapt_debtPaymentMetadata_stillProducesPayment() {
        TransactionDto raw = new TransactionDto(
                "TXN-002", 1000L, "ZAR","Capitec","Payment", Instant.now(), Map.of("kind", "DEBT_PAYMENT"));

        Transaction result = adapter.adapt(raw, identity);

        assertThat(result.getTransactionClass()).isEqualTo(TransactionClass.PAYMENT);
    }
}
