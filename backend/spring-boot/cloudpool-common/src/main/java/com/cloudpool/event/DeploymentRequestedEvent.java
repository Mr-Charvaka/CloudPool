package com.cloudpool.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DeploymentRequestedEvent extends BaseEvent {
    private UUID userId;
    private String name;
    private String dockerImage;
    private double cpu;
    private int memory;
    private int replicas;
}
