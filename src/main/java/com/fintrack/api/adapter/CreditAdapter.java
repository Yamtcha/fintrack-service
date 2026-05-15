package com.fintrack.api.adapter;

import com.fintrack.api.dto.request.TransactionDto;
import com.fintrack.api.security.SourceIdentity;
import com.fintrack.common.domain.SourceType;
import com.fintrack.common.domain.TransactionStatus;
import com.fintrack.common.domain.TransactionType;
import com.fintrack.common.model.Transaction;
import com.fintrack.common.util.TransactionClassResolver;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CreditAdapter implements TransactionAdapter {

    @Override
    public SourceType supports() {
        return SourceType.CREDIT;
    }

    @Override
    public Transaction adapt(TransactionDto raw, SourceIdentity source) {
        String kind = raw.metadata() != null ? (String) raw.metadata().get("kind") : null;

        return Transaction.builder()
                .externalId(raw.externalId())
                .sourceId(source.sourceId().toString())
                .sourceType(source.sourceType())
                .transactionClass(TransactionClassResolver.resolve(SourceType.CREDIT, kind))
                .currency(raw.currency())
                .amount(BigDecimal.valueOf(raw.amount()).movePointLeft(2))
                .description(raw.description())
                .merchantName(raw.merchantName())
                .type(TransactionType.CREDIT)
                .status(TransactionStatus.PENDING)
                .transactedAt(raw.transactedAt())
                .build();
    }
}