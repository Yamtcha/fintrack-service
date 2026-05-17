package com.fintrack.api.adapter;

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

class LoansAdapterTest {

    private LoansAdapter adapter;

    private final UUID sourceId = UUID.randomUUID();
    private final SourceIdentity identity = new SourceIdentity(sourceId, SourceType.LOANS);

    @BeforeEach
    void setUp() {
        adapter = new LoansAdapter();
    }

    @Test
    void supports_returnsLoans() {
        assertThat(adapter.supports()).isEqualTo(SourceType.LOANS);
    }

    @Test
    void adapt_alwaysProducesDebtPayment() {
        TransactionDto raw = new TransactionDto(
                "LN-001", 150000L, "ZAR","CApitec", "Mortgage Payment", Instant.now(), null);

        Transaction result = adapter.adapt(raw, identity);

        assertThat(result.getTransactionClass()).isEqualTo(TransactionClass.DEBT_PAYMENT);
        assertThat(result.getExternalId()).isEqualTo("LN-001");
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(result.getSourceType()).isEqualTo(SourceType.LOANS);
    }

    @Test
    void adapt_withMetadata_stillProducesDebtPayment() {
        Map<String, Object> metadata = Map.of("loanType", "MORTGAGE", "installment", 36);
        TransactionDto raw = new TransactionDto(
                "LN-002", 200000L, "ZAR","Capitec","Auto Loan Payment", Instant.now(), metadata);

        Transaction result = adapter.adapt(raw, identity);

        assertThat(result.getTransactionClass()).isEqualTo(TransactionClass.DEBT_PAYMENT);
    }
}
