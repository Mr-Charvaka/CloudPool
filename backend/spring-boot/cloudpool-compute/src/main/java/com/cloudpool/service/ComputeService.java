package com.cloudpool.service;

import com.cloudpool.model.*;
import com.cloudpool.repository.*;
import com.cloudpool.exception.ResourceNotFoundException;
import com.cloudpool.exception.CloudPoolException;
import com.cloudpool.policy.QuotaPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Value;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComputeService {

    private final StaticSiteRepository staticSiteRepository;
    private final ServerlessFunctionRepository serverlessFunctionRepository;
    private final ContainerDeploymentRepository containerDeploymentRepository;
    private final BucketRepository bucketRepository;
    private final BackgroundJobRepository backgroundJobRepository;
    private final GraphQLSubscriptionService subscriptionService;
    private final QuotaPolicy quotaPolicy;
    private final ObjectMapper objectMapper;
    private final KubernetesDeploymentService kubernetesDeploymentService;

    private static final int EXECUTION_TIMEOUT_SECONDS = 10;

    private final ExecutorService sandboxExecutor = new ThreadPoolExecutor(
            4, 16, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(100),
            new ThreadPoolExecutor.AbortPolicy());

    @Transactional
    public StaticSite deployStaticSite(User user, String name, String bucketName, String domain) {
        Bucket bucket = bucketRepository.findByUserAndName(user, bucketName)
                .orElseThrow(() -> new ResourceNotFoundException("Bucket not found: " + bucketName));
        StaticSite site = StaticSite.builder()
                .name(name)
                .bucketName(bucketName)
                .domain(domain)
                .status(com.cloudpool.model.enums.StaticSiteStatus.DEPLOYED)
                .user(user)
                .build();
        return staticSiteRepository.save(site);
    }

    public List<StaticSite> listStaticSites(User user) {
        return staticSiteRepository.findByUser(user);
    }

    @Transactional
    public void deleteStaticSite(User user, UUID id) {
        StaticSite site = staticSiteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Static site not found"));
        if (!site.getUser().getId().equals(user.getId())) throw new CloudPoolException("Unauthorized");
        staticSiteRepository.delete(site);
    }

    public StaticSite getStaticSiteByDomain(String domain) {
        return staticSiteRepository.findByDomain(domain)
                .orElseThrow(() -> new ResourceNotFoundException("Static site not found for domain: " + domain));
    }

    @Transactional
    public ServerlessFunction deployServerlessFunction(UUID userId, String name, String triggerRoute, String code) {
        ServerlessFunction function = ServerlessFunction.builder()
                .name(name)
                .triggerRoute(triggerRoute)
                .code(code)
                .status(com.cloudpool.model.enums.ServerlessStatus.ACTIVE)
                .wasmCompiled(true)
                .userId(userId)
                .build();
        return serverlessFunctionRepository.save(function);
    }

    public List<ServerlessFunction> listServerlessFunctions(User user) {
        return serverlessFunctionRepository.findByUserId(user.getId());
    }

    public java.util.concurrent.CompletableFuture<String> executeServerlessFunctionAsync(UUID userId, UUID id, String paramsJson) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            ServerlessFunction function = serverlessFunctionRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Serverless function not found"));
            if (!function.getUserId().equals(userId)) throw new CloudPoolException("Unauthorized");
            return executeSandbox(function.getCode(), paramsJson);
        });
    }

    @Transactional
    public void deleteServerlessFunction(UUID userId, UUID id) {
        ServerlessFunction function = serverlessFunctionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Serverless function not found"));
        if (!function.getUserId().equals(userId)) throw new CloudPoolException("Unauthorized");
        serverlessFunctionRepository.delete(function);
    }

    @Transactional
    public ContainerDeployment deployContainer(UUID userId, String name, String dockerImage, double cpu, int memory, int replicas) {
        User user = new User();
        user.setId(userId);
        // Enforce product quota limits before allocating resources
        quotaPolicy.enforceContainerQuota(user);

        ContainerDeployment deployment = ContainerDeployment.builder()
                .name(name)
                .dockerImage(dockerImage)
                .cpu(cpu)
                .memory(memory)
                .replicas(replicas)
                .status(com.cloudpool.model.enums.ContainerStatus.BUILDING)
                .userId(userId)
                .logs("Starting deployment initialization...\n")
                .build();
        return containerDeploymentRepository.save(deployment);
    }

    public List<ContainerDeployment> listContainers(User user) {
        return containerDeploymentRepository.findByUserId(user.getId());
    }

    @Transactional
    public void deleteContainer(UUID userId, UUID id) {
        ContainerDeployment deployment = containerDeploymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Container not found"));
        if (!deployment.getUserId().equals(userId)) throw new CloudPoolException("Unauthorized");
        containerDeploymentRepository.delete(deployment);
    }

    @Async
    public void processContainerDeploymentAsync(ContainerDeployment deployment) {
        BackgroundJob job = BackgroundJob.builder()
                .jobType("CONTAINER_DEPLOYMENT")
                .referenceId(deployment.getId())
                .status(com.cloudpool.model.enums.BackgroundJobStatus.RUNNING)
                .build();
        backgroundJobRepository.save(job);
        subscriptionService.publishJobUpdate(job);

        try {
            log.info("Deploying container via Kubernetes: {}", deployment.getName());
            deployment.setStatus(com.cloudpool.model.enums.ContainerStatus.DEPLOYING);
            containerDeploymentRepository.save(deployment);

            kubernetesDeploymentService.deployContainer(deployment);

            job.setStatus(com.cloudpool.model.enums.BackgroundJobStatus.COMPLETED);
            backgroundJobRepository.save(job);
            subscriptionService.publishJobUpdate(job);
        } catch (Exception e) {
            log.error("Container deployment failed for {}: {}", deployment.getName(), e.getMessage());
            ContainerDeployment active = containerDeploymentRepository.findById(deployment.getId()).orElse(null);
            if (active != null) {
                active.setStatus(com.cloudpool.model.enums.ContainerStatus.FAILED);
                active.setLogs(active.getLogs() + "Deployment failed: " + e.getMessage() + "\n");
                containerDeploymentRepository.save(active);
            }
            job.setStatus(com.cloudpool.model.enums.BackgroundJobStatus.FAILED);
            backgroundJobRepository.save(job);
            subscriptionService.publishJobUpdate(job);
        }
    }

    private String executeSandbox(String code, String paramsJson) {
        Object paramsObj;
        try {
            paramsObj = objectMapper.readValue(
                paramsJson != null && !paramsJson.isBlank() ? paramsJson : "{}", Object.class);
        } catch (Exception e) {
            throw new CloudPoolException("Invalid params JSON: " + e.getMessage());
        }

        Context context = Context.newBuilder("js")
            .allowHostAccess(HostAccess.NONE)
            .allowIO(false)
            .allowCreateThread(false)
            .allowNativeAccess(false)
            .allowPolyglotAccess(PolyglotAccess.NONE)
            .option("js.sandbox", "true")
            .option("js.stackoverflow", "true")
            .option("js.ecmascript-version", "2022")
            .build();

        try {
            context.getBindings("js").putMember("params", paramsObj);

            Future<Value> future = sandboxExecutor.submit(() ->
                context.eval("js", "(function(args) { " + code + " })(params)"));

            Value result = future.get(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return objectMapper.writeValueAsString(result.as(Object.class));

        } catch (TimeoutException e) {
            context.close(true);
            throw new CloudPoolException("Execution timed out after " + EXECUTION_TIMEOUT_SECONDS + "s");
        } catch (Exception e) {
            log.error("Error executing serverless function sandbox", e);
            throw new CloudPoolException("Sandbox Execution Error: " + e.getMessage());
        } finally {
            context.close(true);
        }
    }
}
