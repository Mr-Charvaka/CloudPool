# Security Policy

## Supported Versions

Only the latest release versions are supported with security updates.

| Version | Supported          |
| ------- | ------------------ |
| 0.1.x   | :white_check_mark: |
| < 0.1.0 | :x:                |

## Reporting a Vulnerability

We take the security of CloudPool seriously. If you find a security vulnerability, please do not report it publicly via GitHub issues. Instead, follow these steps:

1. Send an email to the security team or maintainers(aman71204@hotmail.com) or open a private vulnerability report on GitHub if available.
2. Provide a detailed description of the vulnerability, including step-by-step reproduction steps, payload samples, and affected components.
3. We will acknowledge receipt of your report within 48 hours and work on a fix promptly.

## Security Best Practices for Deployment

To keep your CloudPool installation secure, please ensure you implement the following guidelines in production:

### 1. Configure Strong Secret Keys

CloudPool uses secrets for signing JWT tokens and encrypting database connection credentials. **Never deploy with the default development keys.**

*   **JWT Secret Key (`cloudpool.jwt.secret`):** Configure a strong, cryptographically secure 512-bit HS512 key.
*   **Encryption Master Key (`cloudpool.encryption.master-key`):** Configure a 256-bit AES key.

Set these via environment variables in production:
```bash
export CLOUDPOOL_JWT_SECRET="your-512-bit-long-cryptographically-secure-random-key"
export CLOUDPOOL_ENCRYPTION_MASTER_KEY="your-base64-encoded-32-byte-master-key"
```

### 2. Encryption Mechanism

CloudPool uses **AES-GCM (128-bit authentication tag)** for encrypting sensitive fields like database passwords, using a random IV for each entry. A fallback mechanism is in place to decrypt legacy ECB-encrypted data, but all new writes will use GCM.

### 3. File Upload Safety

The platform validates file types, MIME types, and file sizes. Ensure that:
*   `cloudpool.storage.max-file-size` is adjusted appropriately for your environment to prevent Denial of Service (DoS) attacks.
*   Path traversal protection and filename sanitization are enabled to keep the local filesystem secure.
