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

class InvestmentsAdapterTest {

    private InvestmentsAdapter adapter;

    private final UUID sourceId = UUID.randomUUID();
    private final SourceIdentity identity = new SourceIdentity(
            sourceId, "Fidelity Portfolio", SourceType.INVESTMENTS, ApiKeyScope.READ_WRITE);

    @BeforeEach
    void setUp() {
        adapter = new InvestmentsAdapter();
    }

    @Test
    void supports_returnsInvestments() {
        assertThat(adapter.supports()).isEqualTo(SourceType.INVESTMENTS);
    }

    @Test
    void adapt_alwaysProducesTrade() {
        TransactionDto raw = new TransactionDto(
                "INV-001", 500000L, "ZAR", "Robinhood", "AAPL Buy 10 shares", Instant.now(), null);

        Transaction result = adapter.adapt(raw, identity);

        assertThat(result.getTransactionClass()).isEqualTo(TransactionClass.TRADE);
        assertThat(result.getExternalId()).isEqualTo("INV-001");
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(result.getSourceType()).isEqualTo(SourceType.INVESTMENTS);
    }

    @Test
    void adapt_withTradeMetadata_stillProducesTrade() {
        Map<String, Object> metadata = Map.of("ticker", "TSLA", "action", "SELL", "shares", 5);
        TransactionDto raw = new TransactionDto(
                "INV-002", 125000L, "ZAR","EasyEquities", "TSLA Sell 5 shares", Instant.now(), metadata);

        Transaction result = adapter.adapt(raw, identity);

        assertThat(result.getTransactionClass()).isEqualTo(TransactionClass.TRADE);
    }
}
