package com.cloudpool.service;

import com.cloudpool.dto.ApiKeyUsageLogDto;
import com.cloudpool.model.ApiKey;
import com.cloudpool.model.ApiKeyUsageLog;
import com.cloudpool.model.User;
import com.cloudpool.repository.ApiKeyRepository;
import com.cloudpool.repository.ApiKeyUsageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiKeyUsageService {

    private final ApiKeyUsageLogRepository logRepository;
    private final ApiKeyRepository apiKeyRepository;

    @Transactional
    public void logUsage(UUID apiKeyId, String endpoint, String method, int statusCode, String ipAddress) {
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new IllegalArgumentException("API Key not found"));
        
        ApiKeyUsageLog usageLog = ApiKeyUsageLog.builder()
                .id(UUID.randomUUID())
                .apiKey(apiKey)
                .endpoint(endpoint)
                .method(method)
                .statusCode(statusCode)
                .ipAddress(ipAddress)
                .createdAt(LocalDateTime.now())
                .build();
        logRepository.save(usageLog);
    }

    public List<ApiKeyUsageLogDto> getLogsForUser(User user) {
        return logRepository.findByUser(user).stream()
                .map(log -> ApiKeyUsageLogDto.builder()
                        .id(log.getId())
                        .apiKeyId(log.getApiKey().getId())
                        .apiKeyName(log.getApiKey().getName())
                        .endpoint(log.getEndpoint())
                        .method(log.getMethod())
                        .statusCode(log.getStatusCode())
                        .ipAddress(log.getIpAddress())
                        .createdAt(log.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getUsageByKey(User user) {
        return logRepository.countRequestsByKey(user);
    }

    public List<Map<String, Object>> getUsageByStatus(User user) {
        return logRepository.countRequestsByStatus(user);
    }

    public List<Map<String, Object>> getUsageByEndpoint(User user) {
        return logRepository.countRequestsByEndpoint(user);
    }
}
