package com.cloudpool.repository;

import com.cloudpool.event.InboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface InboxEventRepository extends JpaRepository<InboxEvent, UUID> {

    @Modifying
    @Query("DELETE FROM InboxEvent i WHERE i.processedAt < :cutoff")
    int deleteProcessedBefore(@org.springframework.data.repository.query.Param("cutoff") Instant cutoff);
}
