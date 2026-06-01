import type { CloudPool } from './client';
import type { Bucket, FileMetadata, FileSearchResult, UploadResult } from './types';

export class StorageClient {
  constructor(private readonly cp: CloudPool) {}

  /** List all storage buckets for the current user */
  async listBuckets(): Promise<Bucket[]> {
    return this.cp.request<Bucket[]>('GET', '/api/files/buckets');
  }

  /** Create a new storage bucket */
  async createBucket(name: string): Promise<Bucket> {
    return this.cp.request<Bucket>('POST', '/api/files/buckets', { name });
  }

  /**
   * Upload a file to a bucket.
   * Works in both browser (File/Blob) and Node.js (Buffer) environments.
   */
  async upload(bucketName: string, file: File | Blob, fileName?: string): Promise<UploadResult> {
    const formData = new FormData();
    formData.append('file', file, fileName);
    formData.append('bucketName', bucketName);

    const headers: Record<string, string> = {};
    const apiKey = (this.cp as any).apiKey;
    const token = (this.cp as any).authToken;
    if (apiKey) headers['X-API-Key'] = apiKey;
    else if (token) headers['Authorization'] = `Bearer ${token}`;

    const response = await fetch(`${this.cp.getBaseUrl()}/api/files/upload`, {
      method: 'POST',
      headers,
      body: formData,
    });

    if (!response.ok) {
      throw new Error(`Upload failed (${response.status}): ${await response.text()}`);
    }
    return response.json();
  }

  /** List all files in a bucket */
  async listFiles(bucketName: string): Promise<FileMetadata[]> {
    return this.cp.request<FileMetadata[]>('GET', `/api/files?bucketName=${bucketName}`);
  }

  /** Delete a file by ID */
  async deleteFile(fileId: string): Promise<void> {
    await this.cp.request('DELETE', `/api/files/${fileId}`);
  }

  /** Generate a temporary shareable download link */
  async share(fileId: string, expiresInHours = 24): Promise<{ shareUrl: string; expiresAt: string }> {
    return this.cp.request('POST', `/api/files/${fileId}/share`, { expiresInHours });
  }

  /** Semantic search across all uploaded files */
  async search(query: string): Promise<FileSearchResult[]> {
    return this.cp.request<FileSearchResult[]>('GET', `/api/vector/search?query=${encodeURIComponent(query)}`);
  }
}
