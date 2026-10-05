package com.guardian.app.device;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
    List<Device> findByGuardianId(UUID guardianId);
    List<Device> findByStatusIn(List<Device.Status> statuses);
}
