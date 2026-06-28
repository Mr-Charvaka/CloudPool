package com.cloudpool.service;

import com.cloudpool.model.*;
import com.cloudpool.repository.*;
import com.cloudpool.util.EncryptionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectSecretRepository projectSecretRepository;
    @Mock private DatabaseConnectionRepository databaseConnectionRepository;
    @Mock private ProjectSnapshotRepository projectSnapshotRepository;
    @Mock private DevTableRepository devTableRepository;
    @Mock private DevTableFieldRepository devTableFieldRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private EncryptionUtil encryptionUtil;

    private ProjectService projectService;
    private UUID userId;
    private UUID projectId;
    private Project testProject;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository, projectSecretRepository,
                databaseConnectionRepository, projectSnapshotRepository,
                devTableRepository, devTableFieldRepository, jdbcTemplate,
                new ObjectMapper(), encryptionUtil);

        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();

        testProject = Project.builder()
                .id(projectId)
                .userId(userId)
                .name("test-project")
                .description("A test project")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createProject_shouldSaveAndReturn() {
        when(projectRepository.save(any(Project.class))).thenReturn(testProject);

        Project result = projectService.createProject(userId, "test-project", "A test project");

        assertNotNull(result);
        assertEquals("test-project", result.getName());
        assertEquals(userId, result.getUserId());
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void createProject_shouldTrimName() {
        when(projectRepository.save(any(Project.class))).thenReturn(testProject);

        projectService.createProject(userId, "  spaced-name  ", null);

        verify(projectRepository).save(argThat(p -> p.getName().equals("spaced-name")));
    }

    @Test
    void listProjects_withExisting_shouldReturnList() {
        when(projectRepository.findByUserId(userId)).thenReturn(List.of(testProject));

        List<Project> result = projectService.listProjects(userId);

        assertEquals(1, result.size());
        assertEquals(projectId, result.get(0).getId());
    }

    @Test
    void listProjects_withNone_shouldAutoCreateDefault() {
        when(projectRepository.findByUserId(userId)).thenReturn(List.of());
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Project> result = projectService.listProjects(userId);

        assertEquals(1, result.size());
        assertEquals("default-project", result.get(0).getName());
    }

    @Test
    void getProject_shouldReturnWhenAuthorized() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));

        Project result = projectService.getProject(projectId, userId);

        assertNotNull(result);
        assertEquals(projectId, result.getId());
    }

    @Test
    void getProject_notFound_shouldThrow() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () ->
                projectService.getProject(projectId, userId));
    }

    @Test
    void getProject_wrongUser_shouldThrow() {
        UUID otherUserId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));

        assertThrows(SecurityException.class, () ->
                projectService.getProject(projectId, otherUserId));
    }

    @Test
    void deleteProject_shouldDeleteAllRelatedData() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
        when(devTableRepository.findByProjectId(eq(projectId), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        projectService.deleteProject(projectId, userId);

        verify(projectSecretRepository).deleteByProjectId(projectId);
        verify(databaseConnectionRepository).deleteByProjectId(projectId);
        verify(projectSnapshotRepository).deleteByProjectId(projectId);
        verify(projectRepository).delete(testProject);
    }

    @Test
    void addSecret_shouldEncryptAndSave() throws Exception {
        String rawValue = "my-secret-value";
        byte[] encryptedBytes = "encrypted-data".getBytes();

        when(encryptionUtil.encrypt(rawValue.getBytes())).thenReturn(encryptedBytes);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
        when(projectSecretRepository.findByProjectIdAndSecretKey(projectId, "api-key"))
                .thenReturn(Optional.empty());
        when(projectSecretRepository.save(any(ProjectSecret.class))).thenAnswer(i -> i.getArgument(0));

        ProjectSecret result = projectService.addSecret(projectId, "api-key", rawValue, userId);

        assertNotNull(result);
        assertEquals("api-key", result.getSecretKey());
    }

    @Test
    void addSecret_existingKey_shouldOverwrite() throws Exception {
        String rawValue = "new-value";
        byte[] encryptedBytes = "encrypted-new".getBytes();

        ProjectSecret existingSecret = ProjectSecret.builder()
                .project(testProject)
                .secretKey("api-key")
                .secretValue("old-encrypted")
                .build();

        when(encryptionUtil.encrypt(rawValue.getBytes())).thenReturn(encryptedBytes);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
        when(projectSecretRepository.findByProjectIdAndSecretKey(projectId, "api-key"))
                .thenReturn(Optional.of(existingSecret));
        when(projectSecretRepository.save(any(ProjectSecret.class))).thenAnswer(i -> i.getArgument(0));

        ProjectSecret result = projectService.addSecret(projectId, "api-key", rawValue, userId);

        assertNotNull(result);
        assertEquals("api-key", result.getSecretKey());
    }

    @Test
    void listSecrets_shouldDecryptValues() throws Exception {
        byte[] encryptedBytes = "encrypted-data".getBytes();
        byte[] decryptedBytes = "decrypted-value".getBytes();

        ProjectSecret secret = ProjectSecret.builder()
                .secretKey("my-key")
                .secretValue(Base64.getEncoder().encodeToString(encryptedBytes))
                .build();

        when(encryptionUtil.decrypt(encryptedBytes)).thenReturn(decryptedBytes);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
        when(projectSecretRepository.findByProjectId(projectId)).thenReturn(List.of(secret));

        List<ProjectSecret> result = projectService.listSecrets(projectId, userId);

        assertEquals(1, result.size());
        assertEquals("decrypted-value", result.get(0).getSecretValue());
    }

    @Test
    void listSecrets_decryptionFailure_shouldShowError() throws Exception {
        ProjectSecret secret = ProjectSecret.builder()
                .secretKey("bad-key")
                .secretValue("invalid-base64!!")
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
        when(projectSecretRepository.findByProjectId(projectId)).thenReturn(List.of(secret));

        List<ProjectSecret> result = projectService.listSecrets(projectId, userId);

        assertEquals(1, result.size());
        assertEquals("[DECRYPTION_ERROR]", result.get(0).getSecretValue());
    }

    @Test
    void deleteSecret_shouldDelete() {
        UUID secretId = UUID.randomUUID();
        ProjectSecret secret = ProjectSecret.builder()
                .id(secretId)
                .project(testProject)
                .build();

        when(projectSecretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));

        projectService.deleteSecret(secretId, userId);

        verify(projectSecretRepository).delete(secret);
    }

    @Test
    void listConnections_shouldReturnList() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
        when(databaseConnectionRepository.findByProjectId(projectId)).thenReturn(List.of());

        var result = projectService.listConnections(projectId, userId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void saveConnection_shouldSaveNew() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
        when(databaseConnectionRepository.findByProjectIdAndDbType(projectId, "POSTGRESQL"))
                .thenReturn(Optional.empty());
        when(databaseConnectionRepository.save(any(DatabaseConnection.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(encryptionUtil.encrypt(any(byte[].class))).thenReturn("encrypted".getBytes());

        DatabaseConnection result = projectService.saveConnection(
                projectId, "POSTGRESQL", "8.8.8.8", 5432,
                "mydb", "admin", "pass", true, userId);

        assertNotNull(result);
        assertEquals("POSTGRESQL", result.getDbType());
    }

    @Test
    void provisionProjectDatabase_shouldThrow() {
        assertThrows(UnsupportedOperationException.class, () ->
                projectService.provisionProjectDatabase(projectId, userId));
    }

    @Test
    void deleteConnection_shouldDelete() {
        UUID connId = UUID.randomUUID();
        DatabaseConnection conn = DatabaseConnection.builder()
                .id(connId)
                .project(testProject)
                .build();

        when(databaseConnectionRepository.findById(connId)).thenReturn(Optional.of(conn));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));

        projectService.deleteConnection(connId, userId);

        verify(databaseConnectionRepository).delete(conn);
    }

    @Test
    void testConnection_unsupportedType_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
                projectService.testConnection("MONGODB", "host", 27017, "db", "u", "p"));
    }

    @Test
    void createSnapshot_shouldSave() throws Exception {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
        when(projectSecretRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(databaseConnectionRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(devTableRepository.findByProjectId(eq(projectId), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(projectSnapshotRepository.save(any(ProjectSnapshot.class)))
                .thenAnswer(i -> i.getArgument(0));

        ProjectSnapshot snapshot = projectService.createSnapshot(projectId, "snap-1", userId);

        assertNotNull(snapshot);
        assertEquals("snap-1", snapshot.getName());
    }

    @Test
    void listSnapshots_shouldReturnList() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
        when(projectSnapshotRepository.findByProjectId(projectId)).thenReturn(List.of());

        var result = projectService.listSnapshots(projectId, userId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void deleteSnapshot_shouldDelete() {
        UUID snapshotId = UUID.randomUUID();
        ProjectSnapshot snapshot = ProjectSnapshot.builder()
                .id(snapshotId)
                .project(testProject)
                .build();

        when(projectSnapshotRepository.findById(snapshotId)).thenReturn(Optional.of(snapshot));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));

        projectService.deleteSnapshot(snapshotId, userId);

        verify(projectSnapshotRepository).delete(snapshot);
    }
}
