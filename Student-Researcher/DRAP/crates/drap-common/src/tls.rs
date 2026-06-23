use std::fs::File;
use std::io::{BufReader, Cursor};
use std::path::Path;
use tokio_rustls::rustls::pki_types::{CertificateDer, PrivateKeyDer};

pub fn load_certs(path: &Path) -> std::io::Result<Vec<CertificateDer<'static>>> {
    let file = File::open(path)?;
    let mut reader = BufReader::new(file);
    let certs = rustls_pemfile::certs(&mut reader)
        .collect::<Result<Vec<_>, _>>()?;
    Ok(certs)
}

pub fn load_private_key(path: &Path) -> std::io::Result<PrivateKeyDer<'static>> {
    let file = File::open(path)?;
    let mut reader = BufReader::new(file);
    let key = rustls_pemfile::private_key(&mut reader)?
        .ok_or_else(|| std::io::Error::new(std::io::ErrorKind::NotFound, "No private key found"))?;
    Ok(key)
}

// For dev purposes: load from memory
pub fn load_certs_from_bytes(bytes: &[u8]) -> std::io::Result<Vec<CertificateDer<'static>>> {
    let mut reader = BufReader::new(Cursor::new(bytes));
    let certs = rustls_pemfile::certs(&mut reader)
        .collect::<Result<Vec<_>, _>>()?;
    Ok(certs)
}

pub fn load_private_key_from_bytes(bytes: &[u8]) -> std::io::Result<PrivateKeyDer<'static>> {
    let mut reader = BufReader::new(Cursor::new(bytes));
    let key = rustls_pemfile::private_key(&mut reader)?
        .ok_or_else(|| std::io::Error::new(std::io::ErrorKind::NotFound, "No private key found"))?;
    Ok(key)
}
