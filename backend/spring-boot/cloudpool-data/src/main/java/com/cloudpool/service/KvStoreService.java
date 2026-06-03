package com.cloudpool.service;

import com.cloudpool.model.KvStore;
import com.cloudpool.repository.KvStoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class KvStoreService {

    private final KvStoreRepository kvStoreRepository;
    
    // In-memory caching for faster reads (Cache key format: "projectId:keyName")
    private final ConcurrentHashMap<String, CachedValue> localCache = new ConcurrentHashMap<>();

    private record CachedValue(String value, LocalDateTime expiresAt) {
        boolean isExpired(LocalDateTime now) {
            return expiresAt != null && expiresAt.isBefore(now);
        }
    }

    private String buildCacheKey(UUID projectId, String keyName) {
        return projectId.toString() + ":" + keyName;
    }

    @Transactional
    public void set(UUID projectId, String keyName, String value, Integer ttlSeconds) {
        LocalDateTime expiresAt = (ttlSeconds != null && ttlSeconds > 0) 
                ? LocalDateTime.now().plusSeconds(ttlSeconds) 
                : null;

        KvStore kv = kvStoreRepository.findByProjectIdAndKeyName(projectId, keyName)
                .orElseGet(() -> {
                    KvStore newKv = new KvStore();
                    newKv.setProjectId(projectId);
                    newKv.setKeyName(keyName);
                    return newKv;
                });

        kv.setValue(value);
        kv.setExpiresAt(expiresAt);
        kvStoreRepository.save(kv);

        // Update local cache
        localCache.put(buildCacheKey(projectId, keyName), new CachedValue(value, expiresAt));
    }

    public Optional<String> get(UUID projectId, String keyName) {
        String cacheKey = buildCacheKey(projectId, keyName);
        LocalDateTime now = LocalDateTime.now();

        // 1. Check local cache
        CachedValue cached = localCache.get(cacheKey);
        if (cached != null) {
            if (cached.isExpired(now)) {
                localCache.remove(cacheKey); // Prune from memory
            } else {
                return Optional.of(cached.value());
            }
        }

        // 2. Fallback to database
        Optional<KvStore> dbEntry = kvStoreRepository.findByProjectIdAndKeyName(projectId, keyName);
        if (dbEntry.isPresent()) {
            KvStore kv = dbEntry.get();
            if (kv.getExpiresAt() != null && kv.getExpiresAt().isBefore(now)) {
                // Expired in DB but not yet pruned by cron job
                return Optional.empty();
            }
            // Populate local cache for future reads
            localCache.put(cacheKey, new CachedValue(kv.getValue(), kv.getExpiresAt()));
            return Optional.of(kv.getValue());
        }

        return Optional.empty();
    }

    @Transactional
    public void delete(UUID projectId, String keyName) {
        String cacheKey = buildCacheKey(projectId, keyName);
        localCache.remove(cacheKey);
        kvStoreRepository.deleteByProjectIdAndKeyName(projectId, keyName);
    }

    /**
     * Background job to purge expired keys from DB every 1 minute.
     * Prevents database bloat over time.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void purgeExpiredKeys() {
        LocalDateTime now = LocalDateTime.now();
        int deleted = kvStoreRepository.deleteExpiredKeys(now);
        if (deleted > 0) {
            log.info("Purged {} expired KV keys from database.", deleted);
            // Optionally, we could clear local cache fully, but since reads are TTL-aware, it is safe.
            // A simple wipe will ensure memory doesn't leak for expired keys that are never read again.
            localCache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        }
    }
}
