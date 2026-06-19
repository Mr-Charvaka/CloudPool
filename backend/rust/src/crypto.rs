use std::error::Error;
use aes::cipher::{BlockDecrypt, KeyInit};
use aes::Aes256;
use aes_gcm::aead::Aead;
use aes_gcm::{Aes256Gcm, Key, Nonce};
use base64::engine::general_purpose::STANDARD;
use base64::Engine;
use rand::RngCore;
use hkdf::Hkdf;
use sha2::Sha256;

pub struct CryptoUtil {
    key: [u8; 32],
}

impl CryptoUtil {
    /// Initialize CryptoUtil with a master key.
    pub fn new(master_key: &str) -> Self {
        Self {
            key: Self::derive_key(master_key),
        }
    }

    /// Derive 32-byte key from raw or base64 key string, matching Java's implementation.
    pub fn derive_key(master_key: &str) -> [u8; 32] {
        let hk = Hkdf::<Sha256>::new(None, master_key.trim().as_bytes());
        let mut okm = [0u8; 32];
        hk.expand(b"cloudpool-aes-gcm-v1", &mut okm)
            .expect("32 bytes is a valid HKDF output length");
        okm
    }

    /// Encrypt plaintext using AES-GCM (returns base64 string).
    pub fn encrypt(&self, plaintext: &str) -> Result<String, Box<dyn Error>> {
        let encrypted = self.encrypt_bytes(plaintext.as_bytes())?;
        Ok(STANDARD.encode(encrypted))
    }

    /// Decrypt ciphertext supporting both new AES-GCM and legacy AES-ECB fallback (accepts base64 string).
    pub fn decrypt(&self, ciphertext: &str) -> Result<String, Box<dyn Error>> {
        let decoded = STANDARD.decode(ciphertext.trim())?;
        let decrypted_bytes = self.decrypt_bytes(&decoded)?;
        let plaintext = String::from_utf8(decrypted_bytes)?;
        Ok(plaintext)
    }

    /// Encrypt raw bytes using AES-GCM (returns [IV][ciphertext+tag]).
    pub fn encrypt_bytes(&self, data: &[u8]) -> Result<Vec<u8>, Box<dyn Error>> {
        let key = Key::<Aes256Gcm>::from_slice(&self.key);
        let cipher = Aes256Gcm::new(key);

        let mut iv = [0u8; 12];
        rand::thread_rng().fill_bytes(&mut iv);
        let nonce = Nonce::from_slice(&iv);

        let ciphertext = cipher
            .encrypt(nonce, data)
            .map_err(|e| format!("AES-GCM encryption failed: {}", e))?;

        let mut encrypted_buffer = Vec::with_capacity(iv.len() + ciphertext.len());
        encrypted_buffer.extend_from_slice(&iv);
        encrypted_buffer.extend_from_slice(&ciphertext);

        Ok(encrypted_buffer)
    }

    /// Only call this explicitly from a one-time migration job, never from the hot decrypt path.
    pub fn migrate_legacy_ciphertext(&self, data: &[u8]) -> Result<Vec<u8>, Box<dyn Error>> {
        log::warn!("Decrypting legacy ECB ciphertext during migration — this should not happen post-cutover");
        self.decrypt_legacy_bytes(data)
    }

    /// Decrypt raw bytes using AES-GCM only. No legacy fallback.
    pub fn decrypt_bytes(&self, data: &[u8]) -> Result<Vec<u8>, Box<dyn Error>> {
        if data.len() < 12 + 16 {
            return Err("Ciphertext too short for AES-GCM; use migrate_legacy_ciphertext explicitly if this is known legacy data".into());
        }

        let key = Key::<Aes256Gcm>::from_slice(&self.key);
        let cipher = Aes256Gcm::new(key);

        let iv = &data[..12];
        let encrypted_payload = &data[12..];
        let nonce = Nonce::from_slice(iv);

        cipher.decrypt(nonce, encrypted_payload)
            .map_err(|e| format!("AES-GCM decryption failed: {}", e).into())
    }

    /// Decrypt raw bytes using legacy AES-ECB and PKCS#7 unpadding.
    fn decrypt_legacy_bytes(&self, data: &[u8]) -> Result<Vec<u8>, Box<dyn Error>> {
        if data.is_empty() || data.len() % 16 != 0 {
            return Err("Invalid legacy data length: not a block size multiple".into());
        }

        let cipher = Aes256::new_from_slice(&self.key)?;
        let mut decrypted = data.to_vec();

        for chunk in decrypted.chunks_mut(16) {
            let block = aes::cipher::generic_array::GenericArray::from_mut_slice(chunk);
            cipher.decrypt_block(block);
        }

        let padding_len = match decrypted.last() {
            Some(&p) => p as usize,
            None => return Err("Empty decrypted data".into()),
        };

        if padding_len == 0 || padding_len > 16 {
            return Err("Invalid padding length".into());
        }

        let len = decrypted.len();
        for i in (len - padding_len)..len {
            if decrypted[i] as usize != padding_len {
                return Err("Invalid padding values".into());
            }
        }

        decrypted.truncate(len - padding_len);
        Ok(decrypted)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use aes::cipher::BlockEncrypt;

    const DEFAULT_KEY: &str = "d1f88c8078c1db294e82b71be5e8f6e80b2a75ffca79b9e6e6a1a8c3d6e5a6b0c2e3f4g5h6j7k8l9m0n1p2q3r4s5t6u7v8w9x0y1z2a3b4c5d6e7f8g9";

    #[test]
    fn test_encrypt_decrypt_gcm() {
        let crypto = CryptoUtil::new(DEFAULT_KEY);
        let plaintext = "secret-passphrase-777";
        let ciphertext = crypto.encrypt(plaintext).unwrap();
        assert_ne!(plaintext, ciphertext);

        let decrypted = crypto.decrypt(&ciphertext).unwrap();
        assert_eq!(plaintext, decrypted);
    }

    #[test]
    fn test_encrypt_decrypt_bytes() {
        let crypto = CryptoUtil::new(DEFAULT_KEY);
        let raw_data = b"my-binary-raw-bytes-data";
        let encrypted = crypto.encrypt_bytes(raw_data).unwrap();
        assert_ne!(raw_data.to_vec(), encrypted);

        let decrypted = crypto.decrypt_bytes(&encrypted).unwrap();
        assert_eq!(raw_data.to_vec(), decrypted);
    }

    #[test]
    fn test_decrypt_legacy_ecb() {
        let key_bytes = CryptoUtil::derive_key(DEFAULT_KEY);
        let cipher = Aes256::new_from_slice(&key_bytes).unwrap();
        
        let plaintext = b"legacy-data-888";
        let padding_len = 16 - (plaintext.len() % 16);
        let mut padded = plaintext.to_vec();
        padded.extend(std::iter::repeat(padding_len as u8).take(padding_len));

        for chunk in padded.chunks_mut(16) {
            let block = aes::cipher::generic_array::GenericArray::from_mut_slice(chunk);
            cipher.encrypt_block(block);
        }

        let base64_ciphertext = STANDARD.decode(STANDARD.encode(padded)).unwrap();
        let crypto = CryptoUtil::new(DEFAULT_KEY);
        let decrypted = crypto.migrate_legacy_ciphertext(&base64_ciphertext).unwrap();
        assert_eq!(b"legacy-data-888".to_vec(), decrypted);
    }
}
