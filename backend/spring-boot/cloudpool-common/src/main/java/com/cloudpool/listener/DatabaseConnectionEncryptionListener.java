package com.cloudpool.listener;
 
import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;
import com.cloudpool.model.DatabaseConnection;
import com.cloudpool.util.EncryptionUtil;
import com.cloudpool.util.SpringContextHolder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
 
@Slf4j
public class DatabaseConnectionEncryptionListener {
 
    private EncryptionUtil getEncryptionUtil() {
        return SpringContextHolder.getBean(EncryptionUtil.class);
    }
 
    @PrePersist
    @PreUpdate
    public void encryptPassword(DatabaseConnection connection) {
        EncryptionUtil util = getEncryptionUtil();
        String plainPassword = connection.getDecryptedPassword();
        if (util != null && plainPassword != null && !plainPassword.isBlank()) {
            byte[] encrypted = util.encrypt(plainPassword.getBytes(StandardCharsets.UTF_8));
            connection.setEncryptedPassword(Base64.getEncoder().encodeToString(encrypted));
            log.debug("DatabaseConnection password encrypted successfully");
        }
    }
 
    @PostLoad
    public void decryptPassword(DatabaseConnection connection) {
        EncryptionUtil util = getEncryptionUtil();
        String encryptedPassword = connection.getEncryptedPassword();
        if (util != null && encryptedPassword != null && !encryptedPassword.isBlank()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(encryptedPassword);
                byte[] decrypted = util.decrypt(decoded);
                connection.setDecryptedPassword(new String(decrypted, StandardCharsets.UTF_8));
                log.debug("DatabaseConnection password decrypted successfully");
            } catch (Exception e) {
                log.error("Failed to decrypt database connection password: {}", e.getMessage());
                // Fall back to returning the encrypted string so the application doesn't crash on load
                connection.setDecryptedPassword(encryptedPassword);
            }
        }
    }
}
