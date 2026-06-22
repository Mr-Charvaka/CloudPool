package com.cloudpool.service;

import com.cloudpool.model.User;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

@Service
public class GoogleDriveService {

    protected Drive getDriveClient(User user) {
        // In a real scenario, this uses the user's saved OAuth tokens.
        // We provide a skeleton client here.
        return new Drive.Builder(new NetHttpTransport(), new GsonFactory(), request -> {})
                .setApplicationName("CloudPool")
                .build();
    }

    public String getAuthorizationUrl(User user) {
        return "https://accounts.google.com/o/oauth2/auth?client_id=mock-id&response_type=code&scope=https://www.googleapis.com/auth/drive";
    }

    public void exchangeCodeForTokens(String code, User user) {
        // Implementation for OAuth code exchange
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
