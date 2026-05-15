package com.fintrack.api.adapter;

import com.fintrack.common.domain.SourceType;
import com.fintrack.common.exception.InvalidSourceTypeException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class AdapterRegistry {

    private final Map<SourceType, TransactionAdapter> registry;

    public AdapterRegistry(
            CreditAdapter creditAdapter,
            DebitAdapter debitAdapter,
            LoansAdapter loansAdapter,
            InvestmentsAdapter investmentsAdapter,
            @Value("${fintrack.ingestion.adapters.enabled}") List<String> enabledAdapters) {

        Map<SourceType, TransactionAdapter> enabledSourceTypes = Map.of(
                SourceType.CREDIT, creditAdapter,
                SourceType.DEBIT, debitAdapter,
                SourceType.LOANS, loansAdapter,
                SourceType.INVESTMENTS, investmentsAdapter
        );

        registry = new EnumMap<>(SourceType.class);
        for (String name : enabledAdapters) {
            SourceType type = SourceType.valueOf(name.toUpperCase());
            if (enabledSourceTypes.containsKey(type)) {
                registry.put(type, enabledSourceTypes.get(type));
            }
        }
    }

    public TransactionAdapter getAdapter(SourceType sourceType) {
        TransactionAdapter adapter = registry.get(sourceType);
        if (adapter == null) {
            throw new InvalidSourceTypeException(sourceType.name());
        }
        return adapter;
    }
}
