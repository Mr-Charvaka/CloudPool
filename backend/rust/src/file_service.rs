use crate::{CloudpoolError, Result};
use sha2::{Sha256, Digest};
use std::io::Read;

/// File service for handling file operations
pub struct FileService;

impl FileService {
    /// Calculate checksum of file
    pub fn calculate_checksum(data: &[u8]) -> Result<String> {
        let mut hasher = Sha256::new();
        hasher.update(data);
        let result = hasher.finalize();
        Ok(format!("{:x}", result))
    }

    /// Compress file content
    pub fn compress(data: &[u8]) -> Result<Vec<u8>> {
        use flate2::Compression;
        use flate2::write::GzEncoder;
        use std::io::Write;

        let mut encoder = GzEncoder::new(Vec::new(), Compression::default());
        encoder.write_all(data)
            .map_err(|e| CloudpoolError::IoError(e))?;
        
        encoder.finish()
            .map_err(|e| CloudpoolError::IoError(e))
    }

    /// Decompress file content
    pub fn decompress(data: &[u8]) -> Result<Vec<u8>> {
        use flate2::read::GzDecoder;

        let mut decoder = GzDecoder::new(data);
        let mut decompressed = Vec::new();
        decoder.read_to_end(&mut decompressed)
            .map_err(|e| CloudpoolError::IoError(e))?;
        
        Ok(decompressed)
    }

    /// Validate file size
    pub fn validate_size(data: &[u8], max_size: u64) -> Result<()> {
        if data.len() as u64 > max_size {
            return Err(CloudpoolError::InvalidInput(
                format!("File size exceeds maximum: {} bytes", max_size)
            ));
        }
        Ok(())
    }

    /// Get file mime type from extension
    pub fn get_mime_type(extension: &str) -> &'static str {
        match extension.to_lowercase().as_str() {
            "pdf" => "application/pdf",
            "txt" => "text/plain",
            "json" => "application/json",
            "csv" => "text/csv",
            "jpg" | "jpeg" => "image/jpeg",
            "png" => "image/png",
            "gif" => "image/gif",
            "mp4" => "video/mp4",
            "mp3" => "audio/mpeg",
            _ => "application/octet-stream",
        }
    }

    /// Convert image to WebP format
    pub fn convert_to_webp(data: &[u8]) -> Result<Vec<u8>> {
        use std::io::Cursor;
        let img = image::load_from_memory(data)
            .map_err(|e| CloudpoolError::InvalidInput(format!("Failed to load image: {}", e)))?;
        
        let mut bytes: Vec<u8> = Vec::new();
        img.write_to(&mut Cursor::new(&mut bytes), image::ImageFormat::WebP)
            .map_err(|e| CloudpoolError::IoError(std::io::Error::new(std::io::ErrorKind::Other, e.to_string())))?;
            
        Ok(bytes)
    }
}
