package com.cloudpool.service;

import com.cloudpool.model.ContainerDeployment;
import com.cloudpool.model.enums.ContainerStatus;
import com.cloudpool.repository.ContainerDeploymentRepository;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.RollingUpdateDeployment;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KubernetesDeploymentService {

    private static final String NAMESPACE = "cloudpool";
    private static final String CONTAINER_IMAGE_PULL_POLICY = "IfNotPresent";

    private final ContainerDeploymentRepository containerDeploymentRepository;

    private KubernetesClient getClient() {
        Config config = new ConfigBuilder()
                .withNamespace(NAMESPACE)
                .build();
        return new KubernetesClientBuilder().withConfig(config).build();
    }

    public void deployContainer(ContainerDeployment deployment) {
        String name = sanitizeName(deployment.getName());
        UUID deploymentId = deployment.getId();
        Map<String, String> labels = Map.of(
            "app", "cloudpool-container",
            "deployment-id", deploymentId.toString(),
            "user-id", deployment.getUserId().toString()
        );
        int port = 8080;

        Probe livenessProbe = new ProbeBuilder()
            .withHttpGet(new HTTPGetActionBuilder()
                .withPath("/health")
                .withPort(new IntOrString(port))
                .build())
            .withInitialDelaySeconds(30)
            .withPeriodSeconds(10)
            .withTimeoutSeconds(5)
            .withFailureThreshold(3)
            .build();

        Probe readinessProbe = new ProbeBuilder()
            .withHttpGet(new HTTPGetActionBuilder()
                .withPath("/health")
                .withPort(new IntOrString(port))
                .build())
            .withInitialDelaySeconds(5)
            .withPeriodSeconds(5)
            .withTimeoutSeconds(3)
            .withFailureThreshold(2)
            .build();

        Probe startupProbe = new ProbeBuilder()
            .withHttpGet(new HTTPGetActionBuilder()
                .withPath("/health")
                .withPort(new IntOrString(port))
                .build())
            .withInitialDelaySeconds(0)
            .withPeriodSeconds(2)
            .withTimeoutSeconds(3)
            .withFailureThreshold(30)
            .build();

        RollingUpdateDeployment rollingUpdate = new RollingUpdateDeployment();
        rollingUpdate.setMaxSurge(new IntOrString(1));
        rollingUpdate.setMaxUnavailable(new IntOrString(0));

        Deployment k8sDeployment = new DeploymentBuilder()
            .withApiVersion("apps/v1")
            .withNewMetadata()
                .withName(name)
                .withNamespace(NAMESPACE)
                .withLabels(labels)
            .endMetadata()
            .withNewSpec()
                .withReplicas(deployment.getReplicas())
                .withNewStrategy()
                    .withType("RollingUpdate")
                    .withRollingUpdate(rollingUpdate)
                .endStrategy()
                .withNewSelector()
                    .withMatchLabels(Map.of("app", "cloudpool-container", "deployment-id", deploymentId.toString()))
                .endSelector()
                .withNewTemplate()
                    .withNewMetadata()
                        .withLabels(labels)
                    .endMetadata()
                    .withNewSpec()
                        .withContainers(new ContainerBuilder()
                            .withName(name)
                            .withImage(deployment.getDockerImage())
                            .withImagePullPolicy(CONTAINER_IMAGE_PULL_POLICY)
                            .withPorts(new ContainerPortBuilder()
                                .withContainerPort(port)
                                .withProtocol("TCP")
                                .build())
                            .withNewResources()
                                .withRequests(Map.of(
                                    "cpu", new Quantity(String.valueOf(deployment.getCpu())),
                                    "memory", new Quantity(deployment.getMemory() + "Mi")
                                ))
                                .withLimits(Map.of(
                                    "cpu", new Quantity(String.valueOf(deployment.getCpu())),
                                    "memory", new Quantity(deployment.getMemory() + "Mi")
                                ))
                            .endResources()
                            .withLivenessProbe(livenessProbe)
                            .withReadinessProbe(readinessProbe)
                            .withStartupProbe(startupProbe)
                            .withSecurityContext(new SecurityContextBuilder()
                                .withRunAsUser(1000L)
                                .withRunAsNonRoot(true)
                                .build())
                            .build())
                        .withRestartPolicy("Always")
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();

        try (KubernetesClient client = getClient()) {
            ensureNamespace(client);

            Deployment created = client.apps().deployments()
                    .inNamespace(NAMESPACE)
                    .resource(k8sDeployment)
                    .createOrReplace();

            ensurePodDisruptionBudget(client, name, labels);

            deployment.setStatus(ContainerStatus.LIVE);
            containerDeploymentRepository.save(deployment);

            log.info("K8s deployment created: {} (image: {}, replicas: {}, probes: liveness/readiness/startup)",
                    created.getMetadata().getName(), deployment.getDockerImage(), deployment.getReplicas());
        } catch (Exception e) {
            deployment.setStatus(ContainerStatus.FAILED);
            containerDeploymentRepository.save(deployment);
            log.error("Failed to create K8s deployment for container {}: {}", deploymentId, e.getMessage());
        }
    }

    public void deleteContainer(UUID deploymentId) {
        try (KubernetesClient client = getClient()) {
            Map<String, String> matchLabels = Map.of("deployment-id", deploymentId.toString());

            client.resources(PodDisruptionBudget.class)
                    .inNamespace(NAMESPACE)
                    .withLabels(matchLabels)
                    .delete();

            client.apps().deployments()
                    .inNamespace(NAMESPACE)
                    .withLabels(matchLabels)
                    .delete();

            log.info("K8s deployment and PDB deleted for deployment ID: {}", deploymentId);
        }
    }

    private void ensurePodDisruptionBudget(KubernetesClient client, String name, Map<String, String> labels) {
        PodDisruptionBudget pdb = new PodDisruptionBudgetBuilder()
            .withApiVersion("policy/v1")
            .withNewMetadata()
                .withName(name + "-pdb")
                .withNamespace(NAMESPACE)
                .withLabels(labels)
            .endMetadata()
            .withNewSpec()
                .withMinAvailable(new IntOrString(1))
                .withNewSelector()
                    .withMatchLabels(Map.of("app", "cloudpool-container", "deployment-id", labels.get("deployment-id")))
                .endSelector()
            .endSpec()
            .build();

        client.resources(PodDisruptionBudget.class)
                .inNamespace(NAMESPACE)
                .resource(pdb)
                .createOrReplace();
        log.info("PodDisruptionBudget created for deployment: {}", name);
    }

    private void ensureNamespace(KubernetesClient client) {
        if (client.namespaces().withName(NAMESPACE).get() == null) {
            client.namespaces().resource(new NamespaceBuilder()
                    .withNewMetadata().withName(NAMESPACE).endMetadata()
                    .build()).create();
        }
    }

    private String sanitizeName(String name) {
        return "cp-" + name.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
