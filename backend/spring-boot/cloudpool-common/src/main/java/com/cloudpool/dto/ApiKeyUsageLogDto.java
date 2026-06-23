package com.cloudpool.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyUsageLogDto {
    private UUID id;
    private UUID apiKeyId;
    private String apiKeyName;
    private String endpoint;
    private String method;
    private int statusCode;
    private String ipAddress;
    private LocalDateTime createdAt;
}
