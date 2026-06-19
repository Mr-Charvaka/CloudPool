package com.cloudpool.service;

import com.cloudpool.model.*;
import com.cloudpool.repository.*;
import com.cloudpool.exception.ResourceNotFoundException;
import com.cloudpool.exception.CloudPoolException;
import com.cloudpool.policy.QuotaPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Async;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.PostConstruct;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    // Warm worker pool for serverless functions (Nashorn/GraalVM)
    private final BlockingQueue<ScriptEngine> scriptEnginePool = new ArrayBlockingQueue<>(10);

    @PostConstruct
    public void initWorkerPool() {
        ScriptEngineManager manager = new ScriptEngineManager();
        for (int i = 0; i < 10; i++) {
            ScriptEngine engine = manager.getEngineByName("JavaScript");
            if (engine != null) {
                scriptEnginePool.offer(engine);
            }
        }
    }

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
        return serverlessFunctionRepository.findByUser(user);
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
        return containerDeploymentRepository.findByUser(user);
    }

    @Transactional
    public void deleteContainer(UUID userId, UUID id) {
        ContainerDeployment deployment = containerDeploymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Container not found"));
        if (!deployment.getUserId().equals(userId)) throw new CloudPoolException("Unauthorized");
        containerDeploymentRepository.delete(deployment);
    }

    public void processContainerDeploymentAsync(ContainerDeployment deployment) {
        BackgroundJob job = BackgroundJob.builder()
                .jobType("CONTAINER_DEPLOYMENT")
                .referenceId(deployment.getId())
                .status(com.cloudpool.model.enums.BackgroundJobStatus.RUNNING)
                .build();
        backgroundJobRepository.save(job);
        subscriptionService.publishJobUpdate(job);

        try {
            log.info("Provisioning infrastructure for container: {}", deployment.getName());
            
            // Actually run the container with quotas, non-root user, and hard-kill timeout
            ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "-d", 
                "--name", "deploy_" + deployment.getId(),
                "--user", "1000:1000", // Run as non-root user
                "--storage-opt", "size=2G", // Enforce storage quotas
                "--memory", deployment.getMemory() + "m", 
                "--cpus", String.valueOf(deployment.getCpu()), 
                deployment.getDockerImage()
            );
            
            Process process = pb.start();
            boolean finished = process.waitFor(60, TimeUnit.SECONDS); // Hard-kill timeout for provisioning
            
            if (!finished) {
                process.destroyForcibly();
                throw new CloudPoolException("Docker provisioning timed out and was forcibly killed.");
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new CloudPoolException("Docker run failed with exit code " + exitCode);
            }

            ContainerDeployment active = containerDeploymentRepository.findById(deployment.getId()).orElse(null);
            if (active != null) {
                active.setStatus(com.cloudpool.model.enums.ContainerStatus.LIVE);
                active.setLogs(active.getLogs() + "Container successfully started and live.\n");
                containerDeploymentRepository.save(active);
            }

            job.setStatus(com.cloudpool.model.enums.BackgroundJobStatus.COMPLETED);
            backgroundJobRepository.save(job);
            subscriptionService.publishJobUpdate(job);
        } catch (InterruptedException e) {
            log.error("Container deployment interrupted", e);
            ContainerDeployment active = containerDeploymentRepository.findById(deployment.getId()).orElse(null);
            if (active != null) {
                active.setStatus(com.cloudpool.model.enums.ContainerStatus.FAILED);
                active.setLogs(active.getLogs() + "Deployment aborted: " + e.getMessage() + "\n");
                containerDeploymentRepository.save(active);
            }
            job.setStatus(com.cloudpool.model.enums.BackgroundJobStatus.FAILED);
            backgroundJobRepository.save(job);
            subscriptionService.publishJobUpdate(job);
        }
    }

    private String executeSandbox(String code, String paramsJson) {
        ScriptEngine engine = null;
        try {
            engine = scriptEnginePool.poll(5, TimeUnit.SECONDS);
            if (engine == null) {
                throw new CloudPoolException("No warm workers available for execution.");
            }
            
            // Securely evaluate params using ObjectMapper to avoid JS Injection
            Object paramsObj = objectMapper.readValue(paramsJson != null && !paramsJson.isBlank() ? paramsJson : "{}", Object.class);
            engine.put("params", paramsObj);
            
            // Execute the code inside the warm engine
            String script = "const res = (function(args) { " + code + " })(params); res;";
            Object result = engine.eval(script);
            
            return objectMapper.writeValueAsString(result);
            
        } catch (Exception e) {
            log.error("Error executing serverless function sandbox", e);
            throw new CloudPoolException("Sandbox Execution Error: " + e.getMessage());
        } finally {
            if (engine != null) {
                // Wipe state if possible, then return to pool
                engine.put("params", null);
                scriptEnginePool.offer(engine);
            }
        }
    }
}
