import { CloudPoolClient } from './index';

export class FilesClient {
  constructor(private readonly client: CloudPoolClient) {}

  /**
   * Upload a file to a specific storage bucket.
   */
  public async upload(file: Blob | File, bucket: string = 'default-pool'): Promise<any> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('bucket', bucket);

    const response = await this.client.request('POST', 'files/upload', {
      body: formData,
    });
    return response.json();
  }

  /**
   * List all metadata records of the user's files.
   */
  public async list(): Promise<any[]> {
    const response = await this.client.request('GET', 'files');
    return response.json();
  }

  /**
   * Download a file by ID. Returns a Blob containing the binary data.
   */
  public async download(fileId: string): Promise<Blob> {
    const response = await this.client.request('GET', `files/download/${fileId}`);
    return response.blob();
  }

  /**
   * Share a file with an email address or generate a public link.
   */
  public async share(
    fileId: string,
    sharedWithEmail?: string,
    expiryHours?: number
  ): Promise<any> {
    const body: Record<string, any> = {};
    if (sharedWithEmail) body.sharedWithEmail = sharedWithEmail;
    if (expiryHours !== undefined) body.expiryHours = expiryHours;

    const response = await this.client.request('POST', `files/${fileId}/share`, {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    return response.json();
  }

  /**
   * Download a shared file anonymously via share token. Returns a Blob.
   */
  public async downloadShared(token: string): Promise<Blob> {
    const response = await this.client.request('GET', `files/shared/${token}`);
    return response.blob();
  }
}
