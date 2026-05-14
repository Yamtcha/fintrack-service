package com.fintrack.api.adapter;

import com.fintrack.api.dto.request.RawTransactionDto;
import com.fintrack.api.security.SourceIdentity;
import com.fintrack.common.domain.SourceType;
import com.fintrack.common.domain.TransactionClass;
import com.fintrack.common.domain.TransactionStatus;
import com.fintrack.common.domain.TransactionType;
import com.fintrack.common.model.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class InvestmentsAdapter implements TransactionAdapter {

    @Override
    public SourceType supports() {
        return SourceType.INVESTMENTS;
    }

    @Override
    public Transaction adapt(RawTransactionDto raw, SourceIdentity source) {
        return Transaction.builder()
                .externalId(raw.externalId())
                .sourceId(source.sourceId().toString())
                .sourceType(source.sourceType())
                .transactionClass(TransactionClass.TRADE)
                .currency(raw.currency())
                .amount(BigDecimal.valueOf(raw.amount()).movePointLeft(2))
                .description(raw.description())
                .merchantName(raw.description())
                .type(TransactionType.DEBIT)
                .status(TransactionStatus.PENDING)
                .transactedAt(raw.transactedAt())
                .build();
    }
}
