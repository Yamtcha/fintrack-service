package com.fintrack.api.adapter;

import com.fintrack.api.dto.request.TransactionDto;
import com.fintrack.api.security.SourceIdentity;
import com.fintrack.common.domain.SourceType;
import com.fintrack.common.model.Transaction;

public interface TransactionAdapter {

    SourceType supports();

    Transaction adapt(TransactionDto raw, SourceIdentity source);
}