package com.fintrack.api.adapter;

import com.fintrack.common.domain.SourceType;
import com.fintrack.common.exception.InvalidSourceTypeException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class AdapterRegistry {

    private final Map<SourceType, TransactionAdapter> registry;

    public AdapterRegistry(List<TransactionAdapter> adapters) {
        registry = new EnumMap<>(SourceType.class);
        for (TransactionAdapter adapter : adapters) {
            registry.put(adapter.supports(), adapter);
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
