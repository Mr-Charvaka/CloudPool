use crate::{CloudpoolError, Result};
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

pub struct Cache {
    data: Arc<Mutex<HashMap<String, CacheEntry>>>,
    max_entries: usize,
}

struct CacheEntry {
    value: Arc<Vec<u8>>,
    expires_at: u64,
}

fn now_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}

impl Cache {
    pub fn new(max_entries: usize) -> Self {
        let data: Arc<Mutex<HashMap<String, CacheEntry>>> = Arc::new(Mutex::new(HashMap::new()));
        let cleanup_data = data.clone();

        std::thread::spawn(move || loop {
            std::thread::sleep(Duration::from_secs(60));
            let now = now_secs();
            if let Ok(mut data) = cleanup_data.lock() {
                data.retain(|_, entry| entry.expires_at > now);
            }
        });

        Cache { data, max_entries }
    }

    pub fn set(&self, key: String, value: Vec<u8>, ttl: u64) -> Result<()> {
        let entry = CacheEntry {
            value: Arc::new(value),
            expires_at: now_secs() + ttl,
        };

        let mut data = self.data.lock()
            .map_err(|_| CloudpoolError::CacheError("Lock failed".to_string()))?;

        let now = now_secs();
        data.retain(|_, e| e.expires_at > now);

        while data.len() >= self.max_entries {
            if let Some(oldest) = data.iter()
                .min_by_key(|(_, e)| e.expires_at)
                .map(|(k, _)| k.clone())
            {
                data.remove(&oldest);
            } else {
                break;
            }
        }

        data.insert(key, entry);
        Ok(())
    }

    pub fn get(&self, key: &str) -> Result<Option<Arc<Vec<u8>>>> {
        let mut data = self.data.lock()
            .map_err(|_| CloudpoolError::CacheError("Lock failed".to_string()))?;

        let now = now_secs();
        if let Some(entry) = data.get(key) {
            if entry.expires_at > now {
                return Ok(Some(entry.value.clone()));
            } else {
                data.remove(key);
            }
        }

        Ok(None)
    }

    pub fn delete(&self, key: &str) -> Result<()> {
        let mut data = self.data.lock()
            .map_err(|_| CloudpoolError::CacheError("Lock failed".to_string()))?;
        data.remove(key);
        Ok(())
    }

    pub fn clear(&self) -> Result<()> {
        let mut data = self.data.lock()
            .map_err(|_| CloudpoolError::CacheError("Lock failed".to_string()))?;
        data.clear();
        Ok(())
    }

    pub fn size(&self) -> Result<usize> {
        let data = self.data.lock()
            .map_err(|_| CloudpoolError::CacheError("Lock failed".to_string()))?;
        Ok(data.len())
    }
}
