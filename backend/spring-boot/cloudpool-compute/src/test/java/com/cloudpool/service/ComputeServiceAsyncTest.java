package com.cloudpool.service;

import com.cloudpool.model.*;
import com.cloudpool.model.enums.BackgroundJobStatus;
import com.cloudpool.model.enums.ContainerStatus;
import com.cloudpool.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComputeServiceAsyncTest {

    @Mock private StaticSiteRepository staticSiteRepository;
    @Mock private ServerlessFunctionRepository serverlessFunctionRepository;
    @Mock private ContainerDeploymentRepository containerDeploymentRepository;
    @Mock private BucketRepository bucketRepository;
    @Mock private BackgroundJobRepository backgroundJobRepository;


    private GraphQLSubscriptionService subscriptionService;
    private int publishCount;
    private com.cloudpool.policy.QuotaPolicy quotaPolicy;
    private ObjectMapper objectMapper;
    private KubernetesDeploymentService kubernetesDeploymentService;
    private boolean k8sDeployCalled;
    private RuntimeException k8sThrowException;
    
    private ComputeService computeService;

    @Captor private ArgumentCaptor<BackgroundJob> jobCaptor;
    @Captor private ArgumentCaptor<ContainerDeployment> deploymentCaptor;

    private ContainerDeployment deployment;

    @BeforeEach
    void setUp() {
        publishCount = 0;
        k8sDeployCalled = false;
        k8sThrowException = null;
        
        subscriptionService = new GraphQLSubscriptionService() {
            @Override
            public void publishJobUpdate(BackgroundJob job) {
                publishCount++;
            }
        };
        
        quotaPolicy = new com.cloudpool.policy.QuotaPolicy(containerDeploymentRepository);
        objectMapper = new ObjectMapper();
        
        kubernetesDeploymentService = new KubernetesDeploymentService(containerDeploymentRepository) {
            @Override
            public void deployContainer(ContainerDeployment dep) {
                if (k8sThrowException != null) {
                    throw k8sThrowException;
                }
                k8sDeployCalled = true;
            }
        };
        
        computeService = new ComputeService(staticSiteRepository, serverlessFunctionRepository,
                containerDeploymentRepository, bucketRepository, backgroundJobRepository,
                subscriptionService, quotaPolicy, objectMapper, kubernetesDeploymentService);

        deployment = ContainerDeployment.builder()
                .id(UUID.randomUUID())
                .name("test-container")
                .dockerImage("nginx:latest")
                .cpu(0.5)
                .memory(256)
                .replicas(1)
                .status(ContainerStatus.BUILDING)
                .userId(UUID.randomUUID())
                .logs("Starting deployment initialization...\n")
                .build();
    }

    @Test
    @DisplayName("Should create running job, deploy to K8s, then mark completed")
    void testProcessContainerDeploymentAsyncSuccess() throws Exception {
        when(backgroundJobRepository.save(jobCaptor.capture())).thenAnswer(i -> i.getArgument(0));
        when(containerDeploymentRepository.save(deploymentCaptor.capture())).thenAnswer(i -> i.getArgument(0));
        
        CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
            computeService.processContainerDeploymentAsync(deployment));
        future.get(5, TimeUnit.SECONDS);

        verify(backgroundJobRepository, times(2)).save(any(BackgroundJob.class));
        verify(containerDeploymentRepository, times(1)).save(any(ContainerDeployment.class));
        assertTrue(k8sDeployCalled);
        assertEquals(2, publishCount);

        BackgroundJob firstJob = jobCaptor.getAllValues().get(0);
        assertEquals(BackgroundJobStatus.COMPLETED, firstJob.getStatus());
        assertEquals("CONTAINER_DEPLOYMENT", firstJob.getJobType());

        BackgroundJob secondJob = jobCaptor.getAllValues().get(1);
        assertEquals(BackgroundJobStatus.COMPLETED, secondJob.getStatus());

        ContainerDeployment savedDeployment = deploymentCaptor.getAllValues().get(0);
        assertEquals(ContainerStatus.DEPLOYING, savedDeployment.getStatus());
    }

    @Test
    @DisplayName("Should mark job and deployment as failed when K8s deploy throws")
    void testProcessContainerDeploymentAsyncFailure() throws Exception {
        when(backgroundJobRepository.save(jobCaptor.capture())).thenAnswer(i -> i.getArgument(0));
        when(containerDeploymentRepository.save(any(ContainerDeployment.class))).thenAnswer(i -> i.getArgument(0));
        k8sThrowException = new RuntimeException("Kubernetes API timeout");
        when(containerDeploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));

        CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
            computeService.processContainerDeploymentAsync(deployment));
        future.get(5, TimeUnit.SECONDS);

        verify(backgroundJobRepository, times(2)).save(any(BackgroundJob.class));
        verify(containerDeploymentRepository, times(2)).save(any(ContainerDeployment.class));
        assertEquals(2, publishCount);
        verify(containerDeploymentRepository).findById(deployment.getId());

        BackgroundJob secondJob = jobCaptor.getAllValues().get(1);
        assertEquals(BackgroundJobStatus.FAILED, secondJob.getStatus());
        assertTrue(deployment.getLogs().contains("Deployment failed"));
    }

    @Test
    @DisplayName("Should handle case where deployment is deleted before async completion")
    void testProcessContainerDeploymentAsyncDeploymentGone() throws Exception {
        when(backgroundJobRepository.save(jobCaptor.capture())).thenAnswer(i -> i.getArgument(0));
        when(containerDeploymentRepository.save(any(ContainerDeployment.class))).thenAnswer(i -> i.getArgument(0));
        k8sThrowException = new RuntimeException("Kubernetes error");
        when(containerDeploymentRepository.findById(deployment.getId())).thenReturn(Optional.empty());

        CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
            computeService.processContainerDeploymentAsync(deployment));
        future.get(5, TimeUnit.SECONDS);

        assertEquals(2, publishCount);
        BackgroundJob failedJob = jobCaptor.getAllValues().get(1);
        assertEquals(BackgroundJobStatus.FAILED, failedJob.getStatus());
    }

    @Test
    @DisplayName("Should initialize background job with reference to deployment ID")
    void testProcessContainerDeploymentAsyncJobReference() throws Exception {
        when(backgroundJobRepository.save(jobCaptor.capture())).thenAnswer(i -> i.getArgument(0));
        when(containerDeploymentRepository.save(any(ContainerDeployment.class))).thenAnswer(i -> i.getArgument(0));

        CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
            computeService.processContainerDeploymentAsync(deployment));
        future.get(5, TimeUnit.SECONDS);

        BackgroundJob job = jobCaptor.getValue();
        assertEquals(deployment.getId(), job.getReferenceId());
    }
}