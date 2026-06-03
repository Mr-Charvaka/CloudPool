package com.cloudpool.service;

import com.cloudpool.model.User;
import com.cloudpool.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GoogleDriveService {

    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${cloudpool.google-drive.client-id:}")
    private String clientId;

    @Value("${cloudpool.google-drive.client-secret:}")
    private String clientSecret;

    @Value("${cloudpool.google-drive.redirect-uri:http://localhost:8080/oauth/callback}")
    private String redirectUri;

    private String getClientIdForUser(User user) {
        if (user != null && user.getCustomClientId() != null && !user.getCustomClientId().trim().isEmpty()) {
            return user.getCustomClientId().trim();
        }
        return this.clientId;
    }

    private String getClientSecretForUser(User user) {
        if (user != null && user.getCustomClientSecret() != null && !user.getCustomClientSecret().trim().isEmpty()) {
            return user.getCustomClientSecret().trim();
        }
        return this.clientSecret;
    }

    public String getAuthorizationUrl(User user) {
        String effectiveClientId = getClientIdForUser(user);
        // Build OAuth Consent URL
        return "https://accounts.google.com/o/oauth2/auth?" +
                "client_id=" + effectiveClientId +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&response_type=code" +
                "&scope=" + URLEncoder.encode("https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.metadata.readonly", StandardCharsets.UTF_8) +
                "&access_type=offline" +
                "&prompt=consent" +
                "&state=" + user.getId().toString();
    }

    public void exchangeCodeForTokens(String code, User user) {
        String tokenUrl = "https://oauth2.googleapis.com/token";
        String effectiveClientId = getClientIdForUser(user);
        String effectiveClientSecret = getClientSecretForUser(user);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("code", code);
        map.add("client_id", effectiveClientId);
        map.add("client_secret", effectiveClientSecret);
        map.add("redirect_uri", redirectUri);
        map.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String accessToken = root.path("access_token").asText();
                String refreshToken = root.path("refresh_token").asText(); // Sent on consent prompt
                int expiresIn = root.path("expires_in").asInt();

                user.setGoogleAccessToken(accessToken);
                if (refreshToken != null && !refreshToken.isEmpty()) {
                    user.setGoogleRefreshToken(refreshToken);
                }
                user.setGoogleTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
                userRepository.save(user);
            } else {
                throw new RuntimeException("Failed to exchange code: " + response.getBody());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error during Google OAuth exchange", e);
        }
    }

    private String getValidAccessToken(User user) {
        if (user.getGoogleRefreshToken() == null) {
            throw new IllegalStateException("Google Drive is not linked. Refresh token is missing.");
        }

        // Refresh token if expired or expiring in less than 5 minutes
        if (user.getGoogleTokenExpiresAt() == null || user.getGoogleTokenExpiresAt().isBefore(LocalDateTime.now().plusMinutes(5))) {
            refreshGoogleAccessToken(user);
        }

        return user.getGoogleAccessToken();
    }

    private void refreshGoogleAccessToken(User user) {
        String tokenUrl = "https://oauth2.googleapis.com/token";
        String effectiveClientId = getClientIdForUser(user);
        String effectiveClientSecret = getClientSecretForUser(user);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("client_id", effectiveClientId);
        map.add("client_secret", effectiveClientSecret);
        map.add("refresh_token", user.getGoogleRefreshToken());
        map.add("grant_type", "refresh_token");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String accessToken = root.path("access_token").asText();
                int expiresIn = root.path("expires_in").asInt();

                user.setGoogleAccessToken(accessToken);
                user.setGoogleTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
                userRepository.save(user);
            } else {
                throw new RuntimeException("Failed to refresh token: " + response.getBody());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error refreshing access token", e);
        }
    }

    public String uploadFile(MultipartFile file, User user) throws IOException {
        String accessToken = getValidAccessToken(user);
        String uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart";

        // Boundary string
        String boundary = "CloudPoolBoundary_" + System.currentTimeMillis();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.parseMediaType("multipart/related; boundary=" + boundary));

        // Part 1: Metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", file.getOriginalFilename());
        
        String metadataJson = objectMapper.writeValueAsString(metadata);

        // Construct Multipart Request Body manually to ensure compliance with Drive's multipart spec
        StringBuilder body = new StringBuilder();
        body.append("--").append(boundary).append("\r\n");
        body.append("Content-Type: application/json; charset=UTF-8\r\n\r\n");
        body.append(metadataJson).append("\r\n");
        body.append("--").append(boundary).append("\r\n");
        body.append("Content-Type: ").append(file.getContentType()).append("\r\n\r\n");

        byte[] multipartHeaderBytes = body.toString().getBytes(StandardCharsets.UTF_8);
        byte[] fileBytes = file.getBytes();
        byte[] multipartFooterBytes = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        byte[] requestBody = new byte[multipartHeaderBytes.length + fileBytes.length + multipartFooterBytes.length];
        System.arraycopy(multipartHeaderBytes, 0, requestBody, 0, multipartHeaderBytes.length);
        System.arraycopy(fileBytes, 0, requestBody, multipartHeaderBytes.length, fileBytes.length);
        System.arraycopy(multipartFooterBytes, 0, requestBody, multipartHeaderBytes.length + fileBytes.length, multipartFooterBytes.length);

        HttpEntity<byte[]> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(uploadUrl, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                return root.path("id").asText(); // Returns the unique file ID in Google Drive
            } else {
                throw new RuntimeException("Failed to upload: " + response.getBody());
            }
        } catch (Exception e) {
            throw new IOException("Failed to send file to Google Drive REST API", e);
        }
    }

    public byte[] downloadFile(String driveFileId, User user) throws IOException {
        String accessToken = getValidAccessToken(user);
        String downloadUrl = "https://www.googleapis.com/drive/v3/files/" + driveFileId + "?alt=media";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(downloadUrl, HttpMethod.GET, entity, byte[].class);
            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                throw new RuntimeException("Failed to download file from Google Drive");
            }
        } catch (Exception e) {
            throw new IOException("Failed to retrieve file from Google Drive REST API", e);
        }
    }

    public Map<String, Long> getStorageQuota(User user) {
        String accessToken = getValidAccessToken(user);
        String aboutUrl = "https://www.googleapis.com/drive/v3/about?fields=storageQuota";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(aboutUrl, HttpMethod.GET, entity, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode quota = root.path("storageQuota");
                long limit = quota.path("limit").asLong();
                long usage = quota.path("usage").asLong();
                
                java.util.Map<String, Long> result = new java.util.HashMap<>();
                result.put("limit", limit);
                result.put("usage", usage);
                return result;
            }
        } catch (Exception e) {
            // Log warning
        }
        return null;
    }

    /**
     * Validate a user's Google refresh token by attempting a token refresh.
     */
    public boolean validateRefreshToken(User user) {
        try {
            refreshGoogleAccessToken(user);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
