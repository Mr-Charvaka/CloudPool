package com.cloudpool.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudpool.config.ConnectionProfileCacheConfig;
import com.cloudpool.dto.DatabaseConnectionProfile;
import com.cloudpool.model.DatabaseConnection;
import com.cloudpool.model.Project;
import com.cloudpool.repository.DatabaseConnectionRepository;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(SpringExtension.class)
@org.springframework.test.context.ContextConfiguration(classes = DatabaseConnectionProfileServiceTest.TestConfig.class)
class DatabaseConnectionProfileServiceTest {

    @Configuration
    @EnableCaching
    @Import(ConnectionProfileCacheConfig.class)
    static class TestConfig {
        @Bean
        DatabaseConnectionRepository databaseConnectionRepository() {
            return mock(DatabaseConnectionRepository.class);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(DatabaseConnectionProfileService.CACHE_NAME);
        }

        @Bean
        DatabaseConnectionProfileService databaseConnectionProfileService(
                DatabaseConnectionRepository repository, CacheManager cacheManager) {
            return new DatabaseConnectionProfileService(repository, cacheManager);
        }
    }

    @Autowired private DatabaseConnectionProfileService service;
    @Autowired private DatabaseConnectionRepository repository;
    @Autowired private CacheManager cacheManager;

    private UUID projectId;
    private DatabaseConnection connection;

    @BeforeEach
    void setUp() {
        reset(repository);
        cacheManager.getCache(DatabaseConnectionProfileService.CACHE_NAME).clear();

        projectId = UUID.randomUUID();
        connection = DatabaseConnection.builder()
                .id(UUID.randomUUID())
                .project(Project.builder().id(projectId).build())
                .dbType("POSTGRESQL")
                .host("db.internal")
                .port(5432)
                .databaseName("app")
                .username("admin")
                .password("super-secret")
                .active(true)
                .build();
        when(repository.findByProjectId(projectId)).thenReturn(List.of(connection));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void profilesAreMappedWithoutAnyPassword() {
        List<DatabaseConnectionProfile> profiles = service.getProfiles(projectId);

        assertEquals(1, profiles.size());
        DatabaseConnectionProfile profile = profiles.get(0);
        assertEquals(connection.getId(), profile.id());
        assertEquals(projectId, profile.projectId());
        assertEquals("POSTGRESQL", profile.dbType());
        assertEquals("db.internal", profile.host());
        assertEquals(5432, profile.port());
        assertEquals("app", profile.databaseName());
        assertEquals("admin", profile.username());
        assertTrue(profile.active());

        boolean exposesSecret = Arrays.stream(DatabaseConnectionProfile.class.getRecordComponents())
                .map(RecordComponent::getName)
                .anyMatch(name -> name.toLowerCase().contains("password"));
        assertFalse(exposesSecret, "the cached profile must never carry a password");
        assertFalse(profile.toString().contains("super-secret"));
    }

    @Test
    void profilesSurviveTheRedisSerializationRoundTrip() {
        // RedisCacheManager uses JDK serialization unless configured otherwise
        var serializer = new org.springframework.data.redis.serializer.JdkSerializationRedisSerializer();
        List<DatabaseConnectionProfile> original = service.getProfiles(projectId);

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertEquals(original, restored);
    }

    @Test
    void secondReadIsServedFromCache() {
        service.getProfiles(projectId);
        service.getProfiles(projectId);

        verify(repository, times(1)).findByProjectId(projectId);
    }

    @Test
    void profilesAreCachedPerProject() {
        UUID otherProject = UUID.randomUUID();
        when(repository.findByProjectId(otherProject)).thenReturn(List.of());

        service.getProfiles(projectId);
        List<DatabaseConnectionProfile> other = service.getProfiles(otherProject);

        assertTrue(other.isEmpty());
        verify(repository, times(1)).findByProjectId(projectId);
        verify(repository, times(1)).findByProjectId(otherProject);
    }

    @Test
    void evictWithoutTransactionForcesAReload() {
        service.getProfiles(projectId);

        service.evictAfterCommit(projectId);
        service.getProfiles(projectId);

        verify(repository, times(2)).findByProjectId(projectId);
    }

    @Test
    void evictInsideATransactionWaitsForTheCommit() {
        service.getProfiles(projectId);
        TransactionSynchronizationManager.initSynchronization();

        service.evictAfterCommit(projectId);

        Cache cache = cacheManager.getCache(DatabaseConnectionProfileService.CACHE_NAME);
        assertTrue(cache.get(projectId) != null, "entry must survive until the transaction commits");

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertTrue(cache.get(projectId) == null, "entry must be gone after the commit");
    }

    @Test
    void readFallsBackToTheDatabaseWhenTheCacheFails() {
        Cache brokenCache = mock(Cache.class);
        when(brokenCache.getName()).thenReturn(DatabaseConnectionProfileService.CACHE_NAME);
        when(brokenCache.get(any(Object.class))).thenThrow(new IllegalStateException("redis down"));
        doThrow(new IllegalStateException("redis down")).when(brokenCache).put(any(), any());
        CacheManager brokenManager = mock(CacheManager.class);
        when(brokenManager.getCache(DatabaseConnectionProfileService.CACHE_NAME)).thenReturn(brokenCache);
        when(brokenManager.getCacheNames()).thenReturn(List.of(DatabaseConnectionProfileService.CACHE_NAME));

        var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext();
        context.registerBean("databaseConnectionRepository", DatabaseConnectionRepository.class, () -> repository);
        context.registerBean("cacheManager", CacheManager.class, () -> brokenManager);
        context.register(CachingOnly.class, ConnectionProfileCacheConfig.class);
        context.registerBean(DatabaseConnectionProfileService.class,
                () -> new DatabaseConnectionProfileService(repository, brokenManager));
        context.refresh();
        try {
            List<DatabaseConnectionProfile> profiles =
                    context.getBean(DatabaseConnectionProfileService.class).getProfiles(projectId);

            assertEquals(1, profiles.size());
        } finally {
            context.close();
        }
    }

    @Test
    void evictSwallowsCacheFailures() {
        Cache brokenCache = mock(Cache.class);
        doThrow(new IllegalStateException("redis down")).when(brokenCache).evict(any());
        CacheManager brokenManager = mock(CacheManager.class);
        when(brokenManager.getCache(DatabaseConnectionProfileService.CACHE_NAME)).thenReturn(brokenCache);

        new DatabaseConnectionProfileService(repository, brokenManager).evictAfterCommit(projectId);

        verify(brokenCache).evict(projectId);
    }

    @Configuration
    @EnableCaching
    static class CachingOnly {
    }
}
