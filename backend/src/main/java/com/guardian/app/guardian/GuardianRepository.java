package com.guardian.app.guardian;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GuardianRepository extends JpaRepository<Guardian, UUID> {
    Optional<Guardian> findByEmailLookup(String emailLookup);
    boolean existsByEmailLookup(String emailLookup);
}
