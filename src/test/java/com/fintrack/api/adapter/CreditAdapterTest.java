package com.fintrack.api.adapter;

import com.fintrack.api.dto.request.TransactionDto;
import com.fintrack.api.security.SourceIdentity;
import com.fintrack.common.domain.SourceType;
import com.fintrack.common.domain.TransactionClass;
import com.fintrack.common.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreditAdapterTest {

    private CreditAdapter adapter;

    private final UUID sourceId = UUID.randomUUID();
    private final SourceIdentity identity = new SourceIdentity(sourceId, SourceType.CREDIT);

    @BeforeEach
    void setUp() {
        adapter = new CreditAdapter();
    }

    @Test
    void supports_returnsCredit() {
        assertThat(adapter.supports()).isEqualTo(SourceType.CREDIT);
    }

    @Test
    void adapt_noMetadata_producesCharge() {
        TransactionDto raw = new TransactionDto(
                "TXN-001", 2500L, "ZAR","Amazon", "Amazon", Instant.now(), null);

        Transaction result = adapter.adapt(raw, identity);

        assertThat(result.getTransactionClass()).isEqualTo(TransactionClass.SPENDING);
        assertThat(result.getExternalId()).isEqualTo("TXN-001");
        assertThat(result.getSourceType()).isEqualTo(SourceType.CREDIT);
    }

    @Test
    void adapt_kindDebtPayment_producesDebtPayment() {
        TransactionDto raw = new TransactionDto(
                "TXN-002", 50000L, "ZAR","Capitec","Monthly Payment", Instant.now(), Map.of("kind", "DEBT_PAYMENT"));

        Transaction result = adapter.adapt(raw, identity);

        assertThat(result.getTransactionClass()).isEqualTo(TransactionClass.DEBT_PAYMENT);
    }

    @Test
    void adapt_kindOther_producesCharge() {
        TransactionDto raw = new TransactionDto(
                "TXN-003", 1500L, "ZAR","Starbucks","Starbucks", Instant.now(), Map.of("kind", "PURCHASE"));

        Transaction result = adapter.adapt(raw, identity);

        assertThat(result.getTransactionClass()).isEqualTo(TransactionClass.SPENDING);
    }
}
