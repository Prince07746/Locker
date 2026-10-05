package com.guardian.app.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CategoryEventRepository extends JpaRepository<CategoryEvent, Long> {

    List<CategoryEvent> findByDeviceIdOrderByOccurredAtDesc(UUID deviceId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("delete from CategoryEvent e where e.receivedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
