package com.fintrack.api.domain.repository;

import com.fintrack.api.domain.SourceStatus;
import com.fintrack.api.domain.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SourceRepository extends JpaRepository<Source, UUID> {

    Optional<Source> findByIdAndStatus(UUID id, SourceStatus status);
}
