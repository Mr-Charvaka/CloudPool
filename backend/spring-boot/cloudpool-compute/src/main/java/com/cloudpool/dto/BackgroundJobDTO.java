package com.cloudpool.dto;

import com.cloudpool.model.BackgroundJob;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class BackgroundJobDTO {
    private UUID id;
    private String jobType;
    private String status;
    private String payload;

    public static BackgroundJobDTO fromEntity(BackgroundJob job) {
        if (job == null) {
            return null;
        }
        return BackgroundJobDTO.builder()
                .id(job.getId())
                .jobType(job.getJobType())
                .status(job.getStatus())
                .payload(job.getPayload())
                .build();
    }
}
