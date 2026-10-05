package com.guardian.app.uninstall;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UninstallAttemptRepository extends JpaRepository<UninstallAttempt, UUID> {
}
