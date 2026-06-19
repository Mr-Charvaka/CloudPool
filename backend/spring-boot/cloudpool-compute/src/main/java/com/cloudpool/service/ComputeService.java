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

    public String executeServerlessFunction(UUID userId, UUID id, String paramsJson) {
        ServerlessFunction function = serverlessFunctionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Serverless function not found"));
        if (!function.getUserId().equals(userId)) throw new CloudPoolException("Unauthorized");
        return executeSandbox(function.getCode(), paramsJson);
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
        // Enforce product quota limits before allocating resources
        // quotaPolicy.enforceContainerQuota(user); // TODO: Refactor QuotaPolicy for UUID

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

    @Async("deploymentExecutor")
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
            Thread.sleep(1500); 
            log.info("Pulling docker image: {}", deployment.getDockerImage());
            Thread.sleep(2000);

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
        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("js");
            engine.put("PARAMS", paramsJson);
            engine.put("RESULT", "");
            String wrapped = "try { var args = JSON.parse(PARAMS); var res = (function(args) { " + code + " })(args); RESULT = JSON.stringify(res); } catch (e) { RESULT = 'ERROR: ' + e.getMessage(); }";
            engine.eval(wrapped);
            return (String) engine.get("RESULT");
        } catch (Exception e) {
            log.error("Error executing serverless function sandbox", e);
            throw new CloudPoolException("Sandbox Execution Error: " + e.getMessage());
        }
    }
}
