package com.cloudpool.policy;

import com.cloudpool.model.User;
import com.cloudpool.exception.CloudPoolException;
import com.cloudpool.repository.ContainerDeploymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuotaPolicy {

    private final ContainerDeploymentRepository containerDeploymentRepository;

    public void enforceContainerQuota(User user) {
        // Enforce a hard limit of 5 containers for free-tier users
        long containerCount = containerDeploymentRepository.countByUserId(user.getId());
        if (containerCount >= 5) {
            throw new CloudPoolException("Quota Exceeded: Free tier users are limited to 5 concurrent containers. Please upgrade your plan to deploy more.");
        }
    }
}
