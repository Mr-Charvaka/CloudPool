use rcgen::{generate_simple_self_signed, CertifiedKey};
use std::fs;
use std::path::Path;

fn main() -> anyhow::Result<()> {
    let subject_alt_names = vec!["localhost".to_string(), "127.0.0.1".to_string()];
    let CertifiedKey { cert, key_pair } = generate_simple_self_signed(subject_alt_names)?;
    
    let cert_dir = Path::new("certs");
    if !cert_dir.exists() {
        fs::create_dir(cert_dir)?;
    }
    
    fs::write(cert_dir.join("cert.pem"), cert.pem())?;
    fs::write(cert_dir.join("key.pem"), key_pair.serialize_pem())?;
    
    println!("Successfully generated certs/cert.pem and certs/key.pem");
    Ok(())
}
