package com.cloudpool.model;
 
import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.cloudpool.listener.DatabaseConnectionEncryptionListener;
import java.time.LocalDateTime;
import java.util.UUID;
 
@Entity
@Table(name = "database_connections")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@EntityListeners(DatabaseConnectionEncryptionListener.class)
public class DatabaseConnection {
 
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
 
    @Column(nullable = false)
    private String dbType; // POSTGRESQL or REDIS
 
    @Column(nullable = false)
    private String host;
 
    @Column(nullable = false)
    private int port;
 
    private String databaseName;
 
    private String username;
 
    @Column(name = "password")
    private String encryptedPassword;
 
    @Transient
    private transient String decryptedPassword;
 
    private boolean active = true;

    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public DatabaseConnection(UUID id, Project project, String dbType, String host, int port, 
                              String databaseName, String username, String password, 
                              Boolean active, LocalDateTime createdAt) {
        this.id = id;
        this.project = project;
        this.dbType = dbType;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.username = username;
        this.decryptedPassword = password;
        this.encryptedPassword = password; // Set raw/initial password, listener will encrypt it before persist
        this.active = active != null ? active : true;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }
 
    public String getPassword() {
        return this.decryptedPassword != null ? this.decryptedPassword : this.encryptedPassword;
    }
 
    public void setPassword(String password) {
        this.decryptedPassword = password;
        this.encryptedPassword = password;
    }
}
