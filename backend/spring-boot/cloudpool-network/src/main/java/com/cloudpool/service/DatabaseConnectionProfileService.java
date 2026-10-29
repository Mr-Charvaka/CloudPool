package com.cloudpool.service;

import com.cloudpool.dto.DatabaseConnectionProfile;
import com.cloudpool.repository.DatabaseConnectionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Cached reads of the database connection profiles of a project. Profiles are read often and
 * change rarely, so they are cached per project and evicted whenever a connection is written.
 * Access control is the caller's job and must happen before calling {@link #getProfiles(UUID)}.
 */
@Service
@Slf4j
public class DatabaseConnectionProfileService {

    public static final String CACHE_NAME = "db-connection-profiles";

    private final DatabaseConnectionRepository repository;
    private final CacheManager cacheManager;

    public DatabaseConnectionProfileService(DatabaseConnectionRepository repository, CacheManager cacheManager) {
        this.repository = repository;
        this.cacheManager = cacheManager;
    }

    @Cacheable(cacheNames = CACHE_NAME, key = "#projectId")
    @Transactional(readOnly = true)
    public List<DatabaseConnectionProfile> getProfiles(UUID projectId) {
        List<DatabaseConnectionProfile> profiles = new ArrayList<>();
        repository.findByProjectId(projectId).forEach(connection -> profiles.add(DatabaseConnectionProfile.from(connection)));
        return profiles;
    }

    /**
     * Evicts the cached profiles of a project once the surrounding transaction commits, so a
     * concurrent reader cannot cache the old data again before the change is visible. Outside a
     * transaction the entry is evicted immediately. A cache failure never fails the write.
     */
    public void evictAfterCommit(UUID projectId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(projectId);
                }
            });
        } else {
            evict(projectId);
        }
    }

    private void evict(UUID projectId) {
        try {
            Cache cache = cacheManager.getCache(CACHE_NAME);
            if (cache != null) {
                cache.evict(projectId);
            }
        } catch (RuntimeException e) {
            log.warn("Could not evict cached connection profiles for project {}: {}", projectId, e.getMessage());
        }
    }
}
