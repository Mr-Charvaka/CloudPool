package com.cloudpool.publisher;

import com.cloudpool.event.DeploymentRequestedEvent;
import com.cloudpool.event.DeploymentSuccessEvent;
import com.cloudpool.event.DeploymentFailedEvent;
import org.springframework.stereotype.Component;

@Component
public class DeploymentPublisher {

    public void publishDeploymentRequested(DeploymentRequestedEvent event) {
    }

    public void publishDeploymentSuccess(DeploymentSuccessEvent event) {
    }

    public void publishDeploymentFailed(DeploymentFailedEvent event) {
    }

    public void requestDeployment(DeploymentRequestedEvent event) {
        publishDeploymentRequested(event);
    }
}
