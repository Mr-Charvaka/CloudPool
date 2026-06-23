package com.cloudpool.service;

import com.cloudpool.model.User;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class GoogleDriveService {

    @Value("${cloudpool.google-drive.client-id:}")
    private String clientId;

    @Value("${cloudpool.google-drive.client-secret:}")
    private String clientSecret;

    @Value("${cloudpool.google-drive.redirect-uri:}")
    private String redirectUri;

    protected Drive getDriveClient(User user) {
        if (user == null || user.getGoogleAccessToken() == null) {
            throw new IllegalArgumentException("User Google credentials are required");
        }

        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(new NetHttpTransport())
                .setJsonFactory(new GsonFactory())
                .setClientSecrets(clientId, clientSecret)
                .build()
                .setRefreshToken(user.getGoogleRefreshToken())
                .setAccessToken(user.getGoogleAccessToken());

        return new Drive.Builder(new NetHttpTransport(), new GsonFactory(), credential)
                .setApplicationName("CloudPool")
                .build();
    }

    public String getAuthorizationUrl(User user) {
        String scope = URLEncoder.encode("https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.metadata.readonly", StandardCharsets.UTF_8);
        return "https://accounts.google.com/o/oauth2/auth"
            + "?client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
            + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
            + "&response_type=code"
            + "&scope=" + scope
            + "&access_type=offline";
    }

    public void exchangeCodeForTokens(String code, User user) {
        try {
            GoogleTokenResponse response = new GoogleAuthorizationCodeTokenRequest(
                    new NetHttpTransport(), new GsonFactory(),
                    "https://oauth2.googleapis.com/token",
                    clientId, clientSecret,
                    code, redirectUri)
                    .execute();
            
            user.setGoogleAccessToken(response.getAccessToken());
            if (response.getRefreshToken() != null) {
                user.setGoogleRefreshToken(response.getRefreshToken());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to exchange code for tokens", e);
        }
    }

    public String uploadFile(MultipartFile file, User user) {
        try {
            Drive driveService = getDriveClient(user);
            File fileMetadata = new File();
            fileMetadata.setName(file.getOriginalFilename());

            InputStreamContent mediaContent = new InputStreamContent(
                    file.getContentType(), file.getInputStream());

            File uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute();
            return uploadedFile.getId();
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to Google Drive", e);
        }
    }

    public byte[] downloadFile(String driveFileId, User user) {
        try {
            Drive driveService = getDriveClient(user);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            driveService.files().get(driveFileId).executeMediaAndDownloadTo(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to download file from Google Drive", e);
        }
    }

    public Map<String, Long> getStorageQuota(User user) {
        try {
            Drive driveService = getDriveClient(user);
            About about = driveService.about().get().setFields("storageQuota").execute();
            if (about != null && about.getStorageQuota() != null) {
                long limit = about.getStorageQuota().getLimit() != null ? about.getStorageQuota().getLimit() : 0L;
                long usage = about.getStorageQuota().getUsage() != null ? about.getStorageQuota().getUsage() : 0L;
                return Map.of("available", limit - usage, "used", usage);
            }
        } catch (IOException e) {
            // fallback
        }
        return Map.of("available", 0L, "used", 0L);
    }
}
