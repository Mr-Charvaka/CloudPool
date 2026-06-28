package com.cloudpool.service;

import com.cloudpool.model.*;
import com.cloudpool.model.enums.ContainerStatus;
import com.cloudpool.model.enums.ServerlessStatus;
import com.cloudpool.model.enums.StaticSiteStatus;
import com.cloudpool.policy.QuotaPolicy;
import com.cloudpool.repository.*;
import com.cloudpool.exception.ResourceNotFoundException;
import com.cloudpool.exception.CloudPoolException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComputeServiceTest {

    @Mock private StaticSiteRepository staticSiteRepository;
    @Mock private ServerlessFunctionRepository serverlessFunctionRepository;
    @Mock private ContainerDeploymentRepository containerDeploymentRepository;
    @Mock private BucketRepository bucketRepository;
    @Mock private BackgroundJobRepository backgroundJobRepository;

    private KubernetesDeploymentService kubernetesDeploymentService;
    private QuotaPolicy quotaPolicy;
    private ObjectMapper objectMapper;
    private ComputeService computeService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        quotaPolicy = new QuotaPolicy(containerDeploymentRepository);
        kubernetesDeploymentService = new KubernetesDeploymentService(containerDeploymentRepository);
        computeService = new ComputeService(staticSiteRepository, serverlessFunctionRepository,
                containerDeploymentRepository, bucketRepository, backgroundJobRepository,
                new GraphQLSubscriptionService(), quotaPolicy, objectMapper, kubernetesDeploymentService);

        userId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .email("compute@cloudpool.com")
                .name("Compute User")
                .build();
        testUser.setId(userId);
    }

    @Test
    void testDeployStaticSite() {
        Bucket bucket = Bucket.builder()
                .id(UUID.randomUUID())
                .name("my-bucket")
                .user(testUser)
                .build();

        when(bucketRepository.findByUserAndName(testUser, "my-bucket"))
                .thenReturn(Optional.of(bucket));
        when(staticSiteRepository.save(any(StaticSite.class)))
                .thenAnswer(invocation -> {
                    StaticSite site = invocation.getArgument(0);
                    site.setId(UUID.randomUUID());
                    return site;
                });

        StaticSite result = computeService.deployStaticSite(testUser, "my-site", "my-bucket", "example.com");

        assertNotNull(result);
        assertEquals("my-site", result.getName());
        assertEquals("my-bucket", result.getBucketName());
        assertEquals("example.com", result.getDomain());
        assertEquals(StaticSiteStatus.DEPLOYED, result.getStatus());
        assertEquals(testUser, result.getUser());
    }

    @Test
    void testDeployStaticSiteBucketNotFound() {
        when(bucketRepository.findByUserAndName(testUser, "nonexistent"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> computeService.deployStaticSite(testUser, "site", "nonexistent", "example.com"));
    }

    @Test
    void testListStaticSites() {
        StaticSite site1 = StaticSite.builder().name("site1").build();
        StaticSite site2 = StaticSite.builder().name("site2").build();

        when(staticSiteRepository.findByUser(testUser)).thenReturn(List.of(site1, site2));

        List<StaticSite> sites = computeService.listStaticSites(testUser);

        assertEquals(2, sites.size());
    }

    @Test
    void testDeleteStaticSiteSuccess() {
        UUID siteId = UUID.randomUUID();
        StaticSite site = StaticSite.builder()
                .id(siteId)
                .name("my-site")
                .user(testUser)
                .build();

        when(staticSiteRepository.findById(siteId)).thenReturn(Optional.of(site));

        computeService.deleteStaticSite(testUser, siteId);

        verify(staticSiteRepository).delete(site);
    }

    @Test
    void testDeleteStaticSiteUnauthorized() {
        UUID siteId = UUID.randomUUID();
        User otherUser = User.builder().id(UUID.randomUUID()).build();
        StaticSite site = StaticSite.builder()
                .id(siteId)
                .name("my-site")
                .user(otherUser)
                .build();

        when(staticSiteRepository.findById(siteId)).thenReturn(Optional.of(site));

        assertThrows(CloudPoolException.class, () -> computeService.deleteStaticSite(testUser, siteId));
    }

    @Test
    void testDeployServerlessFunction() {
        String code = "return params.x + params.y;";
        UUID userUUID = UUID.randomUUID();

        when(serverlessFunctionRepository.save(any(ServerlessFunction.class)))
                .thenAnswer(invocation -> {
                    ServerlessFunction fn = invocation.getArgument(0);
                    fn.setId(UUID.randomUUID());
                    return fn;
                });

        ServerlessFunction result = computeService.deployServerlessFunction(userUUID, "add", "/add", code);

        assertNotNull(result);
        assertEquals("add", result.getName());
        assertEquals("/add", result.getTriggerRoute());
        assertEquals(code, result.getCode());
        assertEquals(ServerlessStatus.ACTIVE, result.getStatus());
        assertEquals(userUUID, result.getUserId());
    }

    @Test
    void testDeployContainer() {
        UUID userUUID = UUID.randomUUID();

        when(containerDeploymentRepository.save(any(ContainerDeployment.class)))
                .thenAnswer(invocation -> {
                    ContainerDeployment dep = invocation.getArgument(0);
                    dep.setId(UUID.randomUUID());
                    return dep;
                });

        ContainerDeployment result = computeService.deployContainer(
                userUUID, "my-container", "nginx:latest", 0.5, 256, 1);

        assertNotNull(result);
        assertEquals("my-container", result.getName());
        assertEquals("nginx:latest", result.getDockerImage());
        assertEquals(0.5, result.getCpu());
        assertEquals(256, result.getMemory());
        assertEquals(1, result.getReplicas());
        assertEquals(ContainerStatus.BUILDING, result.getStatus());
    }

    @Test
    void testListContainers() {
        ContainerDeployment dep1 = ContainerDeployment.builder().name("dep1").build();
        ContainerDeployment dep2 = ContainerDeployment.builder().name("dep2").build();

        when(containerDeploymentRepository.findByUserId(testUser.getId())).thenReturn(List.of(dep1, dep2));

        List<ContainerDeployment> result = computeService.listContainers(testUser);

        assertEquals(2, result.size());
    }

    @Test
    void testListServerlessFunctions() {
        ServerlessFunction fn1 = ServerlessFunction.builder().name("fn1").build();
        ServerlessFunction fn2 = ServerlessFunction.builder().name("fn2").build();

        when(serverlessFunctionRepository.findByUserId(testUser.getId())).thenReturn(List.of(fn1, fn2));

        List<ServerlessFunction> result = computeService.listServerlessFunctions(testUser);

        assertEquals(2, result.size());
    }
}
