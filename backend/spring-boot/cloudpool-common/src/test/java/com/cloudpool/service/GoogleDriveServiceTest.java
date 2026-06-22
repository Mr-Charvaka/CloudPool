package com.cloudpool.service;

import com.cloudpool.model.User;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleDriveServiceTest {

    private GoogleDriveService googleDriveService;

    @Mock
    private Drive mockDrive;

    @Mock
    private Drive.Files mockFiles;

    @Mock
    private Drive.Files.Create mockCreate;

    @Mock
    private Drive.Files.Get mockGet;

    @Mock
    private Drive.About mockAbout;

    @Mock
    private Drive.About.Get mockAboutGet;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        
        googleDriveService = Mockito.spy(new GoogleDriveService());
        doReturn(mockDrive).when(googleDriveService).getDriveClient(any(User.class));
    }

    @Test
    void testUploadFile_Success() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "Hello".getBytes());
        File mockUploadedFile = new File();
        mockUploadedFile.setId("fake-drive-id");

        when(mockDrive.files()).thenReturn(mockFiles);
        when(mockFiles.create(any(File.class), any())).thenReturn(mockCreate);
        when(mockCreate.setFields("id")).thenReturn(mockCreate);
        when(mockCreate.execute()).thenReturn(mockUploadedFile);

        String fileId = googleDriveService.uploadFile(file, testUser);
        assertEquals("fake-drive-id", fileId);
    }

    @Test
    void testDownloadFile_Success() throws IOException {
        when(mockDrive.files()).thenReturn(mockFiles);
        when(mockFiles.get("fake-drive-id")).thenReturn(mockGet);
        
        doAnswer(invocation -> {
            OutputStream out = invocation.getArgument(0);
            out.write("Hello".getBytes());
            return null;
        }).when(mockGet).executeMediaAndDownloadTo(any(OutputStream.class));

        byte[] content = googleDriveService.downloadFile("fake-drive-id", testUser);
        assertArrayEquals("Hello".getBytes(), content);
    }

    @Test
    void testGetStorageQuota_Success() throws IOException {
        About.StorageQuota quota = new About.StorageQuota();
        quota.setLimit(1000L);
        quota.setUsage(200L);
        About about = new About();
        about.setStorageQuota(quota);

        when(mockDrive.about()).thenReturn(mockAbout);
        when(mockAbout.get()).thenReturn(mockAboutGet);
        when(mockAboutGet.setFields("storageQuota")).thenReturn(mockAboutGet);
        when(mockAboutGet.execute()).thenReturn(about);

        Map<String, Long> result = googleDriveService.getStorageQuota(testUser);
        assertEquals(800L, result.get("available"));
        assertEquals(200L, result.get("used"));
    }
}
