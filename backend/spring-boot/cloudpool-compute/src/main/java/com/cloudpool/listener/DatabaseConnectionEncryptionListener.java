package com.cloudpool.listener;
 
import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;
import com.cloudpool.model.DatabaseConnection;
import com.cloudpool.util.EncryptionUtil;
import com.cloudpool.util.SpringContextHolder;
 
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
            String encrypted = util.encrypt(plainPassword);
            connection.setEncryptedPassword(encrypted);
            log.debug("DatabaseConnection password encrypted successfully");
        }
    }
 
    @PostLoad
    public void decryptPassword(DatabaseConnection connection) {
        EncryptionUtil util = getEncryptionUtil();
        String encryptedPassword = connection.getEncryptedPassword();
        if (util != null && encryptedPassword != null && !encryptedPassword.isBlank()) {
            try {
                String decrypted = util.decrypt(encryptedPassword);
                connection.setDecryptedPassword(decrypted);
                log.debug("DatabaseConnection password decrypted successfully");
            } catch (Exception e) {
                log.error("Failed to decrypt database connection password: {}", e.getMessage());
                // Fall back to returning the encrypted string so the application doesn't crash on load
                connection.setDecryptedPassword(encryptedPassword);
            }
        }
    }
}
