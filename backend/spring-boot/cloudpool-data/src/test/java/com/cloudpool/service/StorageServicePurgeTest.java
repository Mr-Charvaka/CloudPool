package com.cloudpool.service;

import com.cloudpool.repository.FileShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServicePurgeTest {

    @Mock
    private FileShareRepository fileShareRepository;

    private StorageService storageService;

    @BeforeEach
    void setUp() throws Exception {
        storageService = new StorageService(
            null, null, null, null, fileShareRepository, null, null, null, null, java.util.Optional.empty()
        );}

    @Test
    void purgeExpiredShares_deletesExpiredTokens() {
        when(fileShareRepository.deleteExpiredShares(any(LocalDateTime.class))).thenReturn(3);

        storageService.purgeExpiredShares();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(fileShareRepository).deleteExpiredShares(captor.capture());
        LocalDateTime passedTime = captor.getValue();
        assertTrue(passedTime.isBefore(LocalDateTime.now().plusSeconds(1)));
        assertTrue(passedTime.isAfter(LocalDateTime.now().minusSeconds(5)));
    }

    @Test
    void purgeExpiredShares_noExpiredTokens() {
        when(fileShareRepository.deleteExpiredShares(any(LocalDateTime.class))).thenReturn(0);

        storageService.purgeExpiredShares();

        verify(fileShareRepository).deleteExpiredShares(any(LocalDateTime.class));
    }
}
