use aes_gcm::aead::{Aead, KeyInit};
use aes_gcm::{Aes256Gcm, Key, Nonce};
use rand::RngCore;

pub struct CryptoUtil {
    key: [u8; 32],
}

impl CryptoUtil {
    pub fn new(hex_key: &str) -> Self {
        let key_bytes = decode_hex(hex_key)
            .expect("Encryption key must be a 64-character hex string");
        assert_eq!(key_bytes.len(), 32, "Key must be exactly 32 bytes");
        let mut key = [0u8; 32];
        key.copy_from_slice(&key_bytes);
        CryptoUtil { key }
    }

    pub fn encrypt_bytes(
        &self,
        data: &[u8],
    ) -> std::result::Result<Vec<u8>, Box<dyn std::error::Error>> {
        let cipher = Aes256Gcm::new(Key::<Aes256Gcm>::from_slice(&self.key));
        let mut nonce = [0u8; 12];
        rand::rngs::OsRng.fill_bytes(&mut nonce);
        let ciphertext = cipher
            .encrypt(Nonce::from_slice(&nonce), data)
            .map_err(|e| format!("Encryption failed: {}", e))?;
        let mut result = nonce.to_vec();
        result.extend(ciphertext);
        Ok(result)
    }

    pub fn decrypt_bytes(
        &self,
        data: &[u8],
    ) -> std::result::Result<Vec<u8>, Box<dyn std::error::Error>> {
        if data.len() < 12 {
            return Err("Ciphertext too short".into());
        }
        let (nonce, ciphertext) = data.split_at(12);
        let cipher = Aes256Gcm::new(Key::<Aes256Gcm>::from_slice(&self.key));
        let plaintext = cipher
            .decrypt(Nonce::from_slice(nonce), ciphertext)
            .map_err(|e| format!("Decryption failed: {}", e))?;
        Ok(plaintext)
    }
}

fn decode_hex(hex: &str) -> Option<Vec<u8>> {
    if hex.len() % 2 != 0 {
        return None;
    }
    (0..hex.len())
        .step_by(2)
        .map(|i| u8::from_str_radix(&hex[i..i + 2], 16).ok())
        .collect()
}
