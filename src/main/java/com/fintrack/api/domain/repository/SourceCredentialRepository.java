package com.fintrack.api.domain.repository;

import com.fintrack.api.domain.entity.SourceCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SourceCredentialRepository extends JpaRepository<SourceCredential, UUID> {

    Optional<SourceCredential> findByUsernameAndActiveTrue(String username);
}
